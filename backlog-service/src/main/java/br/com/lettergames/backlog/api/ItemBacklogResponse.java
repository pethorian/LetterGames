package br.com.lettergames.backlog.api;

import br.com.lettergames.backlog.dominio.ItemBacklog;
import br.com.lettergames.contratos.evento.StatusBacklog;

import java.time.Instant;

public record ItemBacklogResponse(
        Long id,
        String usuarioId,
        Long jogoId,
        String titulo,
        Integer duracaoMediaHoras,
        StatusBacklog status,
        Instant criadoEm,
        Instant atualizadoEm) {

    static ItemBacklogResponse de(ItemBacklog item) {
        return new ItemBacklogResponse(item.getId(), item.getUsuarioId(), item.getJogoId(), item.getTitulo(),
                item.getDuracaoMediaHoras(), item.getStatus(), item.getCriadoEm(), item.getAtualizadoEm());
    }
}
