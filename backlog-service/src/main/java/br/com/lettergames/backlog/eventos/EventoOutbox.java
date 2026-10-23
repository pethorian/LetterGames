package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.evento.TipoEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** Linha da tabela {@code outbox_eventos}: um {@code BacklogEvento} já serializado, pendente ou publicado. */
@Entity
@Table(name = "outbox_eventos")
public class EventoOutbox implements Persistable<UUID> {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String topico;

    @Column(nullable = false, length = 100)
    private String chave;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TipoEvento tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Column(nullable = false)
    private int tentativas;

    // Id é atribuído (eventId): sem isto o save() faria um SELECT antes de cada INSERT.
    @Transient
    private boolean novo = true;

    protected EventoOutbox() {
    }

    public EventoOutbox(UUID eventId, String topico, String chave, TipoEvento tipo, String payload,
                        String correlationId, Instant criadoEm) {
        this.eventId = eventId;
        this.topico = topico;
        this.chave = chave;
        this.tipo = tipo;
        this.payload = payload;
        this.correlationId = correlationId;
        this.criadoEm = criadoEm;
    }

    public void marcarPublicado(Instant quando) {
        this.publicadoEm = quando;
    }

    public int registrarFalha() {
        return ++tentativas;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.novo = false;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTopico() {
        return topico;
    }

    public String getChave() {
        return chave;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }
}
