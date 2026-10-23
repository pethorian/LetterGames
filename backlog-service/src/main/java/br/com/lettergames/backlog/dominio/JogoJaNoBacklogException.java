package br.com.lettergames.backlog.dominio;

/** O usuário já tem esse jogo no backlog (→ 409). */
public class JogoJaNoBacklogException extends RuntimeException {

    public JogoJaNoBacklogException(String usuarioId, Long jogoId) {
        super("O jogo " + jogoId + " já está no backlog de " + usuarioId);
    }
}
