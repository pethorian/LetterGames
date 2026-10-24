package br.com.lettergames.estatisticas.conquista;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/** Conquista desbloqueada por um usuário (uma única vez: a PK é usuário + código). */
@Entity
@Table(name = "conquistas")
public class Conquista {

    @EmbeddedId
    private Id id;

    @Column(name = "desbloqueada_em")
    private Instant desbloqueadaEm;

    protected Conquista() {
    }

    public Conquista(String usuarioId, TipoConquista tipo, Instant desbloqueadaEm) {
        this.id = new Id(usuarioId, tipo);
        this.desbloqueadaEm = desbloqueadaEm;
    }

    public TipoConquista getTipo() {
        return id.codigo();
    }

    public String getUsuarioId() {
        return id.usuarioId();
    }

    public Instant getDesbloqueadaEm() {
        return desbloqueadaEm;
    }

    @Embeddable
    public record Id(@Column(name = "usuario_id") String usuarioId,
                     @Enumerated(EnumType.STRING) @Column(name = "codigo") TipoConquista codigo)
            implements Serializable {
    }
}
