package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.support.KafkaHeaders;

import java.time.Duration;
import java.util.UUID;

import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Critério 5: falha transitória dentro da transação (depois de gravar em eventos_processados) é desfeita e
 * retentada com backoff. Se o rollback não acontecesse, a nova tentativa veria o evento como duplicado e o
 * contador ficaria em zero.
 */
@ExtendWith(OutputCaptureExtension.class)
class FalhaTransitoriaTest extends IntegracaoKafkaBase {

    private static final TransientDataAccessResourceException BANCO_FORA =
            new TransientDataAccessResourceException("banco indisponível (simulado)");

    private static BacklogEvento comId(UUID id) {
        return argThat(e -> e != null && id.equals(e.eventId()));
    }

    @Test
    void falhaDuasVezesEDepoisProcessaExatamenteUmaVez(CapturedOutput saida) {
        String usuario = usuarioUnico("transitoria");
        var evento = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        doThrow(BANCO_FORA).doThrow(BANCO_FORA).doCallRealMethod()
                .when(regra).aplicar(any(), comId(evento.eventId()));

        publicar(evento, "transit-01");

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getZerados()).isEqualTo(1)));

        verify(regra, times(3)).aplicar(any(), comId(evento.eventId()));
        assertThat(estatisticasDe(usuario).orElseThrow().getHorasZeradas()).isEqualTo(30);
        assertThat(processados(evento.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(semCores(saida))
                .contains("⟳ RETRY 2/4 consumidor=estatisticas eventId=" + evento.eventId())
                .contains("⟳ RETRY 3/4 consumidor=estatisticas eventId=" + evento.eventId())
                .doesNotContain("⟳ RETRY 4/4 consumidor=estatisticas eventId=" + evento.eventId());
        // O group de conquistas não é afetado pela falha do outro group.
        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getZeramentos()).isEqualTo(1)));
        assertThat(lerDlt(r -> usuario.equals(r.key()), 0, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void falhaQueEsgotaAsTentativasVaiParaDlt(CapturedOutput saida) {
        String usuario = usuarioUnico("esgota");
        var evento = evento(TipoEvento.JOGO_ADICIONADO, usuario, 40, null, ZERADO);
        doThrow(BANCO_FORA).when(regra).aplicar(any(), comId(evento.eventId()));

        publicar(evento, "esgota-01");

        var dlt = lerDlt(r -> usuario.equals(r.key()), 1, ESPERA);
        assertThat(dlt).hasSize(1);
        var registro = dlt.get(0);
        assertThat(registro.value()).isEqualTo(json(evento));
        assertThat(header(registro, Topicos.HEADER_CORRELATION_ID)).isEqualTo("esgota-01");
        assertThat(header(registro, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isEqualTo(Consumidores.ESTATISTICAS);
        assertThat(header(registro, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("banco indisponível");

        verify(regra, times(4)).aplicar(any(), comId(evento.eventId()));
        assertThat(estatisticasDe(usuario)).isEmpty();
        assertThat(processados(evento.eventId(), Consumidores.ESTATISTICAS)).isZero();
        assertThat(semCores(saida))
                .contains("⟳ RETRY 4/4 consumidor=estatisticas eventId=" + evento.eventId())
                .containsPattern("☠ DLT backlog.eventos.DLT ← offset=\\d+ partição=" + registro.partition()
                        + " motivo=TransientDataAccessResourceException: banco indisponível");
        // Conquistas processa normalmente: groups independentes.
        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getZeramentos()).isEqualTo(1)));
    }
}
