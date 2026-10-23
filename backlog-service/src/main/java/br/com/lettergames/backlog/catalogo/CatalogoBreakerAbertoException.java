package br.com.lettergames.backlog.catalogo;

/**
 * Chamada recusada pelo circuit breaker aberto. É uma {@link CatalogoIndisponivelException} (→ 503),
 * mas fica em {@code ignore-exceptions} do Retry: repetir uma recusa só atrasaria a resposta.
 */
public class CatalogoBreakerAbertoException extends CatalogoIndisponivelException {

    public CatalogoBreakerAbertoException(Throwable causa) {
        super("Circuit breaker do catálogo aberto", causa);
    }
}
