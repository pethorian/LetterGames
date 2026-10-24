package br.com.lettergames.estatisticas.consumo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Marca que um evento já produziu efeito para um consumer group. */
@Entity
@Table(name = "eventos_processados")
public class EventoProcessado {

    @EmbeddedId
    private Id id;

    @Column(name = "processado_em")
    private Instant processadoEm;

    protected EventoProcessado() {
    }

    public EventoProcessado(UUID eventId, String consumidor, Instant processadoEm) {
        this.id = new Id(eventId, consumidor);
        this.processadoEm = processadoEm;
    }

    public Id getId() {
        return id;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    @Embeddable
    public record Id(@Column(name = "event_id") UUID eventId,
                     @Column(name = "consumidor") String consumidor) implements Serializable {
    }
}
