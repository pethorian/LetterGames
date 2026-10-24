package br.com.lettergames.backlog.catalogo;

/**
 * Catálogo respondeu 4xx que não é 404 (400, 401, 406...). O pedido é nosso, então é bug ou contrato
 * quebrado: repetir não muda a resposta e o catálogo está de pé, logo não repete nem conta no breaker (→ 502).
 */
public class CatalogoRequisicaoInvalidaException extends RuntimeException {

    private final int status;

    public CatalogoRequisicaoInvalidaException(int status) {
        super("catálogo recusou a requisição com " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
