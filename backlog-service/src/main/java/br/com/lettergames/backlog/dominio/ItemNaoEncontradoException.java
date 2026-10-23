package br.com.lettergames.backlog.dominio;

/** Item não existe ou pertence a outro usuário (→ 404; não revelamos qual dos dois). */
public class ItemNaoEncontradoException extends RuntimeException {

    public ItemNaoEncontradoException(String usuarioId, Long itemId) {
        super("Item " + itemId + " não encontrado no backlog de " + usuarioId);
    }
}
