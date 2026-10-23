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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resiliência REST (critério 5) com contexto Spring, para os aspectos do Resilience4j atuarem de verdade,
 * contra um servidor HTTP falso do JDK que conta as requisições recebidas.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogoClientResilienciaTest {

    private static final HttpServer SERVIDOR = iniciarServidor();
    private static final AtomicInteger REQUISICOES = new AtomicInteger();
    /** Status das próximas respostas, em ordem; vazia = {@link #statusPadrao}. */
    private static final Queue<Integer> PROXIMOS_STATUS = new ConcurrentLinkedQueue<>();
    private static volatile int statusPadrao = 200;
    private static volatile long atrasoMs = 0;

    @Autowired
    private CatalogoClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("catalogo.url", () -> "http://localhost:" + SERVIDOR.getAddress().getPort());
        registry.add("catalogo.timeout.leitura", () -> "300ms");
    }

    @AfterAll
    static void pararServidor() {
        SERVIDOR.stop(0);
    }

    @BeforeEach
    void resetar() {
        REQUISICOES.set(0);
        PROXIMOS_STATUS.clear();
        statusPadrao = 200;
        atrasoMs = 0;
        breaker = circuitBreakerRegistry.circuitBreaker("catalogo");
        breaker.reset();
    }

    @Test
    void falhaTransitoriaSeResolveComRetry() {
        PROXIMOS_STATUS.add(500);
        PROXIMOS_STATUS.add(500);

        JogoDTO jogo = client.buscarJogo(42L);

        assertThat(jogo.titulo()).isEqualTo("Hollow Knight");
        assertThat(jogo.duracaoMediaHoras()).isEqualTo(27);
        assertThat(REQUISICOES).hasValue(3);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void jogoInexistenteNaoRepeteNemContaComoFalha() {
        statusPadrao = 404;

        assertThatThrownBy(() -> client.buscarJogo(999L))
                .isInstanceOf(JogoInexistenteNoCatalogoException.class);

        assertThat(REQUISICOES).hasValue(1);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void falhaPermanenteAbreOBreakerQueRecusaSemTocarNoCatalogo() {
        statusPadrao = 500;

        // Cada tentativa do retry entra na janela do breaker: com mínimo de 5 chamadas, abre na 2ª requisição.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.buscarJogo(42L)).isInstanceOf(CatalogoIndisponivelException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(REQUISICOES).hasValue(5);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> client.buscarJogo(42L))
                .isInstanceOf(CatalogoBreakerAbertoException.class)
                .isInstanceOf(CatalogoIndisponivelException.class);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(REQUISICOES).as("breaker aberto não chega no servidor").hasValue(5);
        assertThat(duracaoMs).as("recusa é imediata, sem retry").isLessThan(200);
    }

    @Test
    void timeoutDeLeituraViraCatalogoIndisponivel() {
        atrasoMs = 1_000; // maior que o read timeout de 300 ms do teste

        assertThatThrownBy(() -> client.buscarJogo(42L))
                .isInstanceOf(CatalogoIndisponivelException.class)
                .hasMessageContaining("falha ao chamar o catálogo");

        assertThat(REQUISICOES).as("timeout é retentável").hasValue(3);
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
        REQUISICOES.incrementAndGet();
        try (troca) {
            if (atrasoMs > 0) {
                Thread.sleep(atrasoMs);
            }
            Integer proximo = PROXIMOS_STATUS.poll();
            int status = proximo != null ? proximo : statusPadrao;
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
