package br.com.lettergames.estatisticas.conquista;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.consumo.RegistroIdempotencia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static br.com.lettergames.contratos.evento.StatusBacklog.ABANDONADO;
import static br.com.lettergames.contratos.evento.StatusBacklog.JOGANDO;
import static br.com.lettergames.contratos.evento.StatusBacklog.QUERO_JOGAR;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;

/** Regras de conquistas sobre o banco real (H2 + migrations do Flyway). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ProjecaoConquistas.class, RegistroIdempotencia.class})
class ProjecaoConquistasTest {

    @Autowired
    ProjecaoConquistas projecao;

    @Autowired
    ConquistaRepository conquistas;

    @Autowired
    ProgressoConquistasRepository progressos;

    private static BacklogEvento evento(TipoEvento tipo, String usuario, int horas, StatusBacklog de, StatusBacklog para) {
        return BacklogEvento.novo(tipo, usuario, 1L, 1L, "Jogo", horas, de, para);
    }

    private static BacklogEvento zerar(String usuario, int horas) {
        return evento(TipoEvento.STATUS_ALTERADO, usuario, horas, JOGANDO, ZERADO);
    }

    private List<TipoConquista> desbloqueadas(String usuario) {
        return conquistas.findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc(usuario).stream()
                .map(Conquista::getTipo).toList();
    }

    @Test
    void primeiroZeradoDesbloqueiaUmaVezSo() {
        assertThat(projecao.processar(zerar("ana", 10))).containsExactly(TipoConquista.PRIMEIRO_ZERADO);
        assertThat(projecao.processar(zerar("ana", 10))).isEmpty();

        assertThat(desbloqueadas("ana")).containsExactly(TipoConquista.PRIMEIRO_ZERADO);
        assertThat(progressos.findById("ana").orElseThrow().getZeramentos()).isEqualTo(2);
    }

    @Test
    void adicionarDiretoEmZeradoContaComoZeramento() {
        assertThat(projecao.processar(evento(TipoEvento.JOGO_ADICIONADO, "bia", 8, null, ZERADO)))
                .containsExactly(TipoConquista.PRIMEIRO_ZERADO);
    }

    @Test
    void cincoZeradosNoQuintoZeramento() {
        for (int i = 1; i <= 4; i++) {
            projecao.processar(zerar("caio", 1));
        }
        assertThat(desbloqueadas("caio")).doesNotContain(TipoConquista.CINCO_ZERADOS);

        assertThat(projecao.processar(zerar("caio", 1))).containsExactly(TipoConquista.CINCO_ZERADOS);
        projecao.processar(zerar("caio", 1));
        assertThat(desbloqueadas("caio")).containsExactlyInAnyOrder(
                TipoConquista.PRIMEIRO_ZERADO, TipoConquista.CINCO_ZERADOS);
    }

    @Test
    void maratonistaPorHorasZeradasAcumuladas() {
        projecao.processar(zerar("davi", 60));
        assertThat(desbloqueadas("davi")).doesNotContain(TipoConquista.MARATONISTA);

        assertThat(projecao.processar(zerar("davi", 40))).containsExactly(TipoConquista.MARATONISTA);
        assertThat(progressos.findById("davi").orElseThrow().getHorasZeradas()).isEqualTo(100);
    }

    @Test
    void maratonistaComUmJogoLongo() {
        assertThat(projecao.processar(zerar("eva", 120)))
                .containsExactly(TipoConquista.PRIMEIRO_ZERADO, TipoConquista.MARATONISTA);
    }

    @Test
    void desapegadoAoAbandonar() {
        assertThat(projecao.processar(evento(TipoEvento.STATUS_ALTERADO, "fabio", 10, JOGANDO, ABANDONADO)))
                .containsExactly(TipoConquista.DESAPEGADO);
        assertThat(projecao.processar(evento(TipoEvento.JOGO_ADICIONADO, "fabio", 10, null, ABANDONADO)))
                .isEmpty();
        assertThat(progressos.findById("fabio").orElseThrow().getAbandonos()).isEqualTo(2);
    }

    @Test
    void progressoNuncaDecrementa() {
        projecao.processar(zerar("gil", 30));
        projecao.processar(evento(TipoEvento.STATUS_ALTERADO, "gil", 30, ZERADO, JOGANDO));
        projecao.processar(evento(TipoEvento.JOGO_REMOVIDO, "gil", 30, JOGANDO, null));

        var progresso = progressos.findById("gil").orElseThrow();
        assertThat(progresso.getZeramentos()).isEqualTo(1);
        assertThat(progresso.getHorasZeradas()).isEqualTo(30);
        assertThat(desbloqueadas("gil")).containsExactly(TipoConquista.PRIMEIRO_ZERADO);
    }

    @Test
    void eventosQueNaoLevamAZeradoNemAbandonadoNaoDesbloqueiam() {
        assertThat(projecao.processar(evento(TipoEvento.JOGO_ADICIONADO, "hugo", 5, null, QUERO_JOGAR))).isEmpty();
        assertThat(projecao.processar(evento(TipoEvento.STATUS_ALTERADO, "hugo", 5, QUERO_JOGAR, JOGANDO))).isEmpty();
        assertThat(desbloqueadas("hugo")).isEmpty();
    }

    @Test
    void mesmoEventoDuasVezesContaUmaVez() {
        var evento = zerar("iris", 50);
        projecao.processar(evento);
        projecao.processar(evento);

        assertThat(progressos.findById("iris").orElseThrow().getZeramentos()).isEqualTo(1);
        assertThat(progressos.findById("iris").orElseThrow().getHorasZeradas()).isEqualTo(50);
    }
}
