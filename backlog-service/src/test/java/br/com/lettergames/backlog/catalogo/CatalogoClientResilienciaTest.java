package br.com.lettergames.backlog.catalogo;

import br.com.lettergames.contratos.catalogo.JogoDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Resiliência REST (critério 5) com contexto Spring, para os aspectos do Resilience4j atuarem de verdade,
 * contra um servidor HTTP falso do JDK que conta as requisições recebidas.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogoClientResilienciaTest {

    private static final HttpServer SERVIDOR = iniciarServidor();
    /**
     * Comportamento do servidor no teste atual. Cada requisição captura o cenário ao chegar: um handler
     * atrasado do teste anterior (timeout) não consome status nem conta requisição do teste seguinte.
     */
    private static final AtomicReference<Cenario> CENARIO = new AtomicReference<>(new Cenario());

    @Autowired
    private CatalogoClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;
    private Cenario cenario;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("catalogo.url", () -> "http://localhost:" + SERVIDOR.getAddress().getPort());
        registry.add("catalogo.timeout.leitura", () -> "300ms");
        // Curto só aqui, para o teste de HALF_OPEN não esperar os 15 s de produção.
        registry.add("resilience4j.circuitbreaker.instances.catalogo.wait-duration-in-open-state", () -> "1s");
    }

    @AfterAll
    static void pararServidor() {
        SERVIDOR.stop(0);
    }

    @BeforeEach
    void resetar() {
        cenario = new Cenario();
        CENARIO.set(cenario);
        breaker = circuitBreakerRegistry.circuitBreaker("catalogo");
        breaker.reset();
    }

    @Test
    void falhaTransitoriaSeResolveComRetry() {
        cenario.proximosStatus.add(500);
        cenario.proximosStatus.add(500);

        JogoDTO jogo = client.buscarJogo(42L);

        assertThat(jogo.titulo()).isEqualTo("Hollow Knight");
        assertThat(jogo.duracaoMediaHoras()).isEqualTo(27);
        assertThat(cenario.requisicoes).hasValue(3);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void jogoInexistenteNaoRepeteNemContaComoFalha() {
        cenario.statusPadrao = 404;

        assertThatThrownBy(() -> client.buscarJogo(999L))
                .isInstanceOf(JogoInexistenteNoCatalogoException.class);

        assertThat(cenario.requisicoes).hasValue(1);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void requisicaoInvalidaNaoRepeteNemEntraNaJanelaDoBreaker() {
        cenario.statusPadrao = 400;

        assertThatThrownBy(() -> client.buscarJogo(42L))
                .isInstanceOf(CatalogoRequisicaoInvalidaException.class)
                .isNotInstanceOf(CatalogoIndisponivelException.class)
                .hasMessageContaining("400");

        assertThat(cenario.requisicoes).as("4xx não é retentável").hasValue(1);
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).as("ignorada: nem falha nem sucesso").isZero();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void falhaPermanenteAbreOBreakerQueRecusaSemTocarNoCatalogo() {
        abrirBreaker();

        int antes = cenario.requisicoes.get();
        long inicio = System.nanoTime();
        assertThatThrownBy(() -> client.buscarJogo(42L))
                .isInstanceOf(CatalogoBreakerAbertoException.class)
                .isInstanceOf(CatalogoIndisponivelException.class);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(cenario.requisicoes).as("breaker aberto não chega no servidor").hasValue(antes);
        // Folga para CI lento; o que prova a recusa imediata é não haver requisição nova nem retry.
        assertThat(duracaoMs).as("recusa sem retry nem timeout").isLessThan(1_000);
    }

    @Test
    void breakerVoltaAFecharDepoisDeChamadasBemSucedidasEmHalfOpen() {
        abrirBreaker();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> breaker.getState() == CircuitBreaker.State.HALF_OPEN);
        cenario.statusPadrao = 200;
        for (int i = 0; i < 3; i++) { // permitted-number-of-calls-in-half-open-state
            assertThat(client.buscarJogo(42L).titulo()).isEqualTo("Hollow Knight");
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void timeoutDeLeituraViraCatalogoIndisponivel() {
        cenario.atrasoMs = 1_000; // maior que o read timeout de 300 ms do teste

        assertThatThrownBy(() -> client.buscarJogo(42L))
                .isInstanceOf(CatalogoIndisponivelException.class)
                .hasMessageContaining("falha ao chamar o catálogo");

        assertThat(cenario.requisicoes).as("timeout é retentável").hasValue(3);
    }

    private void abrirBreaker() {
        cenario.statusPadrao = 500;
        // Cada tentativa do retry entra na janela do breaker: com mínimo de 5 chamadas, abre na 2ª requisição.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.buscarJogo(42L)).isInstanceOf(CatalogoIndisponivelException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cenario.requisicoes).hasValue(5);
    }

    private static final class Cenario {
        final AtomicInteger requisicoes = new AtomicInteger();
        /** Status das próximas respostas, em ordem; vazia = {@link #statusPadrao}. */
        final Queue<Integer> proximosStatus = new ConcurrentLinkedQueue<>();
        volatile int statusPadrao = 200;
        volatile long atrasoMs = 0;
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/jogos/", CatalogoClientResilienciaTest::responder);
            servidor.setExecutor(Executors.newCachedThreadPool());
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void responder(HttpExchange troca) throws IOException {
        Cenario cenario = CENARIO.get();
        cenario.requisicoes.incrementAndGet();
        try (troca) {
            if (cenario.atrasoMs > 0) {
                Thread.sleep(cenario.atrasoMs);
            }
            Integer proximo = cenario.proximosStatus.poll();
            int status = proximo != null ? proximo : cenario.statusPadrao;
            if (status == 200) {
                byte[] corpo = """
                        {"id":42,"titulo":"Hollow Knight","plataforma":"PC","genero":"Metroidvania","duracaoMediaHoras":27}
                        """.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(200, corpo.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            } else {
                troca.sendResponseHeaders(status, -1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignorada) {
            // cliente desistiu (timeout) antes da resposta
        }
    }
}
