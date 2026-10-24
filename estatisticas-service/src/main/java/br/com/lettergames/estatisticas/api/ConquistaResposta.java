package br.com.lettergames.estatisticas.api;

import br.com.lettergames.estatisticas.conquista.Conquista;

import java.time.Instant;

public record ConquistaResposta(String codigo, String descricao, Instant desbloqueadaEm) {

    public static ConquistaResposta de(Conquista conquista) {
        return new ConquistaResposta(conquista.getTipo().name(), conquista.getTipo().descricao(),
                conquista.getDesbloqueadaEm());
    }
}
