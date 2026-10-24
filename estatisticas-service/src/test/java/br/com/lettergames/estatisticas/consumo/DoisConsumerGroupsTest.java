package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static br.com.lettergames.contratos.evento.StatusBacklog.JOGANDO;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** O mesmo evento é entregue aos dois consumer groups: gera estatística e conquista. */
@ExtendWith(OutputCaptureExtension.class)
class DoisConsumerGroupsTest extends IntegracaoKafkaBase {

    @Test
    void eventoZeradoGeraEstatisticaEConquista(CapturedOutput saida) {
        String usuario = usuarioUnico("grupos");
        var adicionado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 27, null, JOGANDO);
        var zerado = evento(TipoEvento.STATUS_ALTERADO, usuario, 27, JOGANDO, ZERADO);

        publicar(adicionado, "grupos-01");
        publicar(zerado, "grupos-02");

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> {
                assertThat(e.getZerados()).isEqualTo(1);
                assertThat(e.getJogando()).isZero();
                assertThat(e.getHorasZeradas()).isEqualTo(27);
                assertThat(e.getHorasPendentes()).isZero();
            });
            assertThat(conquistasDe(usuario)).containsExactly(TipoConquista.PRIMEIRO_ZERADO);
        });

        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
        // correlationId do header Kafka chega ao MDC nos dois groups.
        assertThat(semCores(saida))
                .containsPattern("\\[grupos-02] InterceptadorCorrelacao - ⇠ KAFKA SUB backlog.eventos group=estatisticas")
                .containsPattern("\\[grupos-02] InterceptadorCorrelacao - ⇠ KAFKA SUB backlog.eventos group=conquistas")
                .contains("[grupos-02] ProjecaoConquistas - 🏆 CONQUISTA DESBLOQUEADA " + usuario + ": Primeiro zerado!");
    }
}
