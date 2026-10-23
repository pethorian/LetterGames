package br.com.lettergames.backlog.catalogo;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Registra os logs de demo de retry e de transição do circuit breaker da instância {@code catalogo}. */
@Component
public class ResilienciaCatalogoLogs {

    static final String INSTANCIA = "catalogo";

    private static final Logger log = LoggerFactory.getLogger(ResilienciaCatalogoLogs.class);

    public ResilienciaCatalogoLogs(RetryRegistry retryRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        Retry retry = retryRegistry.retry(INSTANCIA);
        int maximo = retry.getRetryConfig().getMaxAttempts();
        retry.getEventPublisher().onRetry(evento -> log.warn("⟳ RETRY catalogo tentativa {}/{}: {}",
                evento.getNumberOfRetryAttempts() + 1, maximo, motivo(evento.getLastThrowable())));

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(INSTANCIA);
        breaker.getEventPublisher().onStateTransition(evento -> log.warn("⚡ BREAKER catalogo {} → {}",
                evento.getStateTransition().getFromState(), evento.getStateTransition().getToState()));
    }

    private static String motivo(Throwable e) {
        return e == null ? "?" : e.getMessage();
    }
}
