package br.com.lettergames.estatisticas.estatistica;

import br.com.lettergames.contratos.evento.StatusBacklog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Projeção de leitura: contadores e horas do backlog de um usuário. */
@Entity
@Table(name = "estatisticas_usuario")
public class EstatisticasUsuario {

    @Id
    @Column(name = "usuario_id")
    private String usuarioId;

    @Column(name = "quero_jogar")
    private int queroJogar;
    private int jogando;
    private int zerados;
    private int abandonados;

    @Column(name = "horas_pendentes")
    private int horasPendentes;

    @Column(name = "horas_zeradas")
    private int horasZeradas;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    // Além do lock otimista, versao nula marca a entidade como nova (persist em vez de merge).
    @Version
    private Long versao;

    protected EstatisticasUsuario() {
    }

    public EstatisticasUsuario(String usuarioId) {
        this.usuarioId = usuarioId;
        this.atualizadoEm = Instant.now();
    }

    /** Um item entra no status: soma no contador e nas horas do grupo correspondente. */
    void entrar(StatusBacklog status, int horas) {
        switch (status) {
            case QUERO_JOGAR -> queroJogar++;
            case JOGANDO -> jogando++;
            case ZERADO -> zerados++;
            case ABANDONADO -> abandonados++;
        }
        if (pendente(status)) {
            horasPendentes += horas;
        } else if (status == StatusBacklog.ZERADO) {
            horasZeradas += horas;
        }
    }

    /**
     * Um item sai do status. Valores nunca ficam negativos.
     *
     * @return {@code true} se algum valor precisou ser travado em zero (indício de evento fora de ordem)
     */
    boolean sair(StatusBacklog status, int horas) {
        boolean travou = switch (status) {
            case QUERO_JOGAR -> (queroJogar = queroJogar - 1) < 0;
            case JOGANDO -> (jogando = jogando - 1) < 0;
            case ZERADO -> (zerados = zerados - 1) < 0;
            case ABANDONADO -> (abandonados = abandonados - 1) < 0;
        };
        if (pendente(status)) {
            horasPendentes -= horas;
            travou |= horasPendentes < 0;
        } else if (status == StatusBacklog.ZERADO) {
            horasZeradas -= horas;
            travou |= horasZeradas < 0;
        }
        if (travou) {
            queroJogar = Math.max(queroJogar, 0);
            jogando = Math.max(jogando, 0);
            zerados = Math.max(zerados, 0);
            abandonados = Math.max(abandonados, 0);
            horasPendentes = Math.max(horasPendentes, 0);
            horasZeradas = Math.max(horasZeradas, 0);
        }
        return travou;
    }

    private static boolean pendente(StatusBacklog status) {
        return status == StatusBacklog.QUERO_JOGAR || status == StatusBacklog.JOGANDO;
    }

    void marcarAtualizacao(Instant quando) {
        this.atualizadoEm = quando;
    }

    public int total() {
        return queroJogar + jogando + zerados + abandonados;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public int getQueroJogar() {
        return queroJogar;
    }

    public int getJogando() {
        return jogando;
    }

    public int getZerados() {
        return zerados;
    }

    public int getAbandonados() {
        return abandonados;
    }

    public int getHorasPendentes() {
        return horasPendentes;
    }

    public int getHorasZeradas() {
        return horasZeradas;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
