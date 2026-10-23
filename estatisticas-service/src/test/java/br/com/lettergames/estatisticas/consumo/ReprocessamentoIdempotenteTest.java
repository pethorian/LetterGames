package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.conquista.ProjecaoConquistas;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import br.com.lettergames.estatisticas.estatistica.ProjecaoEstatisticas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static br.com.lettergames.contratos.evento.StatusBacklog.ABANDONADO;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Critério 3: reprocessar o mesmo evento N vezes tem o efeito de uma vez, em cada consumer group. */
@ExtendWith(OutputCaptureExtension.class)
class ReprocessamentoIdempotenteTest extends IntegracaoKafkaBase {

    @Autowired
    ProjecaoEstatisticas projecaoEstatisticas;

    @Autowired
    ProjecaoConquistas projecaoConquistas;

    @Test
    void mesmoRegistroPublicadoDuasVezesTemEfeitoDeUmaVez(CapturedOutput saida) {
        String usuario = usuarioUnico("repro");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        // Sentinela na mesma partição (mesma key): quando ele aparece, as duas cópias anteriores já foram consumidas.
        var sentinela = evento(TipoEvento.JOGO_ADICIONADO, usuario, 5, null, ABANDONADO);

        publicar(zerado, "repro-01");
        publicar(zerado, "repro-01");
        publicar(sentinela, "repro-02");

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getAbandonados()).isEqualTo(1));
            assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getAbandonos()).isEqualTo(1));
        });

        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(30);
        assertThat(est.total()).isEqualTo(2);

        var progresso = progressoDe(usuario).orElseThrow();
        assertThat(progresso.getZeramentos()).isEqualTo(1);
        assertThat(progresso.getHorasZeradas()).isEqualTo(30);
        assertThat(conquistasDe(usuario))
                .containsExactlyInAnyOrder(TipoConquista.PRIMEIRO_ZERADO, TipoConquista.DESAPEGADO);

        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);

        assertThat(semCores(saida))
                .contains("↺ DUPLICADO ignorado consumidor=estatisticas eventId=" + zerado.eventId())
                .contains("↺ DUPLICADO ignorado consumidor=conquistas eventId=" + zerado.eventId());
    }

    @Test
    void servicoChamadoDuasVezesDiretoTemEfeitoDeUmaVez() {
        String usuario = usuarioUnico("direto");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 120, null, ZERADO);

        assertThat(projecaoEstatisticas.processar(zerado)).isTrue();
        assertThat(projecaoEstatisticas.processar(zerado)).isFalse();
        assertThat(projecaoConquistas.processar(zerado))
                .containsExactly(TipoConquista.PRIMEIRO_ZERADO, TipoConquista.MARATONISTA);
        assertThat(projecaoConquistas.processar(zerado)).isEmpty();

        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(120);
        assertThat(progressoDe(usuario).orElseThrow().getZeramentos()).isEqualTo(1);
        assertThat(conquistasDe(usuario)).hasSize(2);
        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
    }
}
