package br.com.lettergames.catalogo.dominio;

public class JogoNaoEncontradoException extends RuntimeException {

    public JogoNaoEncontradoException(Long id) {
        super("Jogo " + id + " não encontrado");
    }
}
