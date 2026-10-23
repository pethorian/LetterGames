package br.com.lettergames.backlog.catalogo;

/** Catálogo não respondeu (IO, timeout, 5xx). Retentável e conta como falha no circuit breaker (→ 503). */
public class CatalogoIndisponivelException extends RuntimeException {

    public CatalogoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public CatalogoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
