package br.com.lettergames.estatisticas.conquista;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Marcos acumulados do usuário. Nunca decrementa: rejogar e zerar de novo conta outro zeramento. */
@Entity
@Table(name = "progresso_conquistas")
public class ProgressoConquistas {

    @Id
    @Column(name = "usuario_id")
    private String usuarioId;

    private int zeramentos;

    @Column(name = "horas_zeradas")
    private int horasZeradas;

    private int abandonos;

    @Version
    private Long versao;

    protected ProgressoConquistas() {
    }

    public ProgressoConquistas(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** @return {@code true} se o evento mudou o progresso */
    public boolean registrar(BacklogEvento evento) {
        if (evento.tipo() == TipoEvento.JOGO_REMOVIDO || evento.statusNovo() == evento.statusAnterior()) {
            return false;
        }
        if (evento.statusNovo() == StatusBacklog.ZERADO) {
            zeramentos++;
            horasZeradas += evento.duracaoMediaHoras() == null ? 0 : evento.duracaoMediaHoras();
            return true;
        }
        if (evento.statusNovo() == StatusBacklog.ABANDONADO) {
            abandonos++;
            return true;
        }
        return false;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public int getZeramentos() {
        return zeramentos;
    }

    public int getHorasZeradas() {
        return horasZeradas;
    }

    public int getAbandonos() {
        return abandonos;
    }
}
