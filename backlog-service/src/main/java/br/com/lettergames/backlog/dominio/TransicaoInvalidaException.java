package br.com.lettergames.backlog.dominio;

/** Mudança de status não permitida pelas regras do backlog (→ 422). */
public class TransicaoInvalidaException extends RuntimeException {

    public TransicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
