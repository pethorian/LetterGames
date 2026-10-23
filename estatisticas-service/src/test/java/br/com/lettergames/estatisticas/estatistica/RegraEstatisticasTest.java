package br.com.lettergames.estatisticas.estatistica;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static br.com.lettergames.contratos.evento.StatusBacklog.ABANDONADO;
import static br.com.lettergames.contratos.evento.StatusBacklog.JOGANDO;
import static br.com.lettergames.contratos.evento.StatusBacklog.QUERO_JOGAR;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;

class RegraEstatisticasTest {

    private final RegraEstatisticas regra = new RegraEstatisticas();
    private final EstatisticasUsuario est = new EstatisticasUsuario("joao");

    private static BacklogEvento adicionado(StatusBacklog status, Integer horas) {
        return BacklogEvento.novo(TipoEvento.JOGO_ADICIONADO, "joao", 1L, 1L, "Jogo", horas, null, status);
    }

    private static BacklogEvento alterado(StatusBacklog de, StatusBacklog para, Integer horas) {
        return BacklogEvento.novo(TipoEvento.STATUS_ALTERADO, "joao", 1L, 1L, "Jogo", horas, de, para);
    }

    private static BacklogEvento removido(StatusBacklog de, Integer horas) {
        return BacklogEvento.novo(TipoEvento.JOGO_REMOVIDO, "joao", 1L, 1L, "Jogo", horas, de, null);
    }

    private void assertContadores(int quero, int jogando, int zerados, int abandonados, int pendentes, int zeradas) {
        assertThat(est.getQueroJogar()).as("quero").isEqualTo(quero);
        assertThat(est.getJogando()).as("jogando").isEqualTo(jogando);
        assertThat(est.getZerados()).as("zerados").isEqualTo(zerados);
        assertThat(est.getAbandonados()).as("abandonados").isEqualTo(abandonados);
        assertThat(est.getHorasPendentes()).as("horas pendentes").isEqualTo(pendentes);
        assertThat(est.getHorasZeradas()).as("horas zeradas").isEqualTo(zeradas);
    }

    @Test
    void adicionarEmQueroJogarContaHorasPendentes() {
        regra.aplicar(est, adicionado(QUERO_JOGAR, 27));
        assertContadores(1, 0, 0, 0, 27, 0);
        assertThat(est.total()).isEqualTo(1);
    }

    @Test
    void adicionarDiretoEmZeradoContaHorasZeradas() {
        regra.aplicar(est, adicionado(ZERADO, 40));
        assertContadores(0, 0, 1, 0, 0, 40);
    }

    @Test
    void adicionarAbandonadoNaoContaHoras() {
        regra.aplicar(est, adicionado(ABANDONADO, 15));
        assertContadores(0, 0, 0, 1, 0, 0);
    }

    @Test
    void queroJogarParaJogandoMantemHorasPendentes() {
        regra.aplicar(est, adicionado(QUERO_JOGAR, 27));
        regra.aplicar(est, alterado(QUERO_JOGAR, JOGANDO, 27));
        assertContadores(0, 1, 0, 0, 27, 0);
    }

    @Test
    void jogandoParaZeradoMoveHorasDePendentesParaZeradas() {
        regra.aplicar(est, adicionado(QUERO_JOGAR, 27));
        regra.aplicar(est, alterado(QUERO_JOGAR, JOGANDO, 27));
        regra.aplicar(est, alterado(JOGANDO, ZERADO, 27));
        assertContadores(0, 0, 1, 0, 0, 27);
    }

    @Test
    void jogandoParaAbandonadoTiraDasPendentes() {
        regra.aplicar(est, adicionado(JOGANDO, 10));
        regra.aplicar(est, alterado(JOGANDO, ABANDONADO, 10));
        assertContadores(0, 0, 0, 1, 0, 0);
    }

    @Test
    void abandonadoParaJogandoVoltaParaPendentes() {
        regra.aplicar(est, adicionado(ABANDONADO, 10));
        regra.aplicar(est, alterado(ABANDONADO, JOGANDO, 10));
        assertContadores(0, 1, 0, 0, 10, 0);
    }

    @Test
    void rejogarZeradoTiraDasZeradasEPoeNasPendentes() {
        regra.aplicar(est, adicionado(ZERADO, 30));
        regra.aplicar(est, alterado(ZERADO, JOGANDO, 30));
        assertContadores(0, 1, 0, 0, 30, 0);

        regra.aplicar(est, alterado(JOGANDO, ZERADO, 30));
        assertContadores(0, 0, 1, 0, 0, 30);
    }

    @Test
    void removerZeradoTiraDasHorasZeradas() {
        regra.aplicar(est, adicionado(ZERADO, 30));
        regra.aplicar(est, adicionado(ZERADO, 12));
        regra.aplicar(est, removido(ZERADO, 30));
        assertContadores(0, 0, 1, 0, 0, 12);
    }

    @ParameterizedTest
    @EnumSource(StatusBacklog.class)
    void removerDeQualquerStatusVoltaAoEstadoVazio(StatusBacklog status) {
        regra.aplicar(est, adicionado(status, 20));
        regra.aplicar(est, removido(status, 20));
        assertContadores(0, 0, 0, 0, 0, 0);
    }

    @Test
    void duracaoNulaContaComoZeroHoras() {
        regra.aplicar(est, adicionado(QUERO_JOGAR, null));
        assertContadores(1, 0, 0, 0, 0, 0);
    }

    @Test
    void eventoForaDeOrdemNaoDeixaContadorNegativo() {
        // STATUS_ALTERADO chegou antes do JOGO_ADICIONADO correspondente.
        regra.aplicar(est, alterado(JOGANDO, ZERADO, 27));
        assertContadores(0, 0, 1, 0, 0, 27);

        regra.aplicar(est, removido(ABANDONADO, 5));
        assertContadores(0, 0, 1, 0, 0, 27);
    }

    @Test
    void horasNaoFicamNegativas() {
        regra.aplicar(est, adicionado(QUERO_JOGAR, 5));
        regra.aplicar(est, removido(QUERO_JOGAR, 50));
        assertContadores(0, 0, 0, 0, 0, 0);
    }

    @Test
    void atualizaDataDeAtualizacao() {
        var antes = est.getAtualizadoEm();
        regra.aplicar(est, adicionado(QUERO_JOGAR, 5));
        assertThat(est.getAtualizadoEm()).isAfterOrEqualTo(antes);
    }
}
