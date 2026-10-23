package br.com.lettergames.backlog.catalogo;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Cliente REST de {@code GET /jogos/{id}} do catalogo-service (contrato coberto pelo Pact, ADR-006).
 *
 * <p>Ordem dos aspectos (padrão do Resilience4j): {@code Retry( CircuitBreaker( chamada ) )}. Cada tentativa
 * do retry passa pelo breaker e entra na janela dele; o fallback roda dentro do retry.
 */
@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

    private final RestClient restClient;

    @Autowired
    public CatalogoClient(@Value("${catalogo.url}") String baseUrl,
                          @Value("${catalogo.timeout.conexao:1s}") Duration timeoutConexao,
                          @Value("${catalogo.timeout.leitura:2s}") Duration timeoutLeitura) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeoutConexao)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeoutLeitura);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
                    String correlationId = MDC.get(Correlacao.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(Correlacao.HEADER_HTTP, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /** Construtor para uso fora do Spring (ex.: teste Pact), com os timeouts padrão. */
    public CatalogoClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Retry(name = "catalogo")
    @CircuitBreaker(name = "catalogo", fallbackMethod = "fallbackBuscarJogo")
    public JogoDTO buscarJogo(Long id) {
        long inicio = System.nanoTime();
        log.info("→ REST GET catalogo-service /jogos/{}", id);
        try {
            return restClient.get()
                    .uri("/jogos/{id}", id)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        log.info("← REST {} catalogo-service /jogos/{} ({} ms)", status.value(), id, ms(inicio));
                        if (status.value() == 404) {
                            throw new JogoInexistenteNoCatalogoException(id);
                        }
                        if (!status.is2xxSuccessful()) {
                            throw new CatalogoIndisponivelException("catálogo respondeu " + status.value());
                        }
                        JogoDTO jogo = response.bodyTo(JogoDTO.class);
                        if (jogo == null) {
                            throw new CatalogoIndisponivelException("catálogo respondeu sem corpo");
                        }
                        return jogo;
                    });
        } catch (RestClientException e) {
            // IO, timeout de conexão/leitura ou corpo ilegível.
            log.warn("← REST FALHA catalogo-service /jogos/{} ({} ms): {}", id, ms(inicio), motivo(e));
            throw new CatalogoIndisponivelException("falha ao chamar o catálogo: " + motivo(e), e);
        }
    }

    @SuppressWarnings("unused") // chamado pelo aspecto do CircuitBreaker
    private JogoDTO fallbackBuscarJogo(Long id, CallNotPermittedException e) {
        log.warn("⚡ BREAKER catalogo ABERTO: chamada recusada sem tocar no catálogo");
        throw new CatalogoBreakerAbertoException(e);
    }

    @SuppressWarnings("unused") // chamado pelo aspecto do CircuitBreaker
    private JogoDTO fallbackBuscarJogo(Long id, Throwable e) {
        // 404 e indisponibilidade já chegam traduzidos; o Retry decide se repete.
        if (e instanceof JogoInexistenteNoCatalogoException || e instanceof CatalogoIndisponivelException) {
            throw (RuntimeException) e;
        }
        throw new CatalogoIndisponivelException("falha inesperada ao chamar o catálogo: " + motivo(e), e);
    }

    private static long ms(long inicioNanos) {
        return Duration.ofNanos(System.nanoTime() - inicioNanos).toMillis();
    }

    static String motivo(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String mensagem = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (mensagem != null ? ": " + mensagem : "");
    }
}
