package br.com.lettergames.backlog.catalogo;

/** Catálogo respondeu 404. Não é falha de infraestrutura: não repete e não conta no breaker (→ 422). */
public class JogoInexistenteNoCatalogoException extends RuntimeException {

    private final Long jogoId;

    public JogoInexistenteNoCatalogoException(Long jogoId) {
        super("Jogo " + jogoId + " não existe no catálogo");
        this.jogoId = jogoId;
    }

    public Long getJogoId() {
        return jogoId;
    }
}
