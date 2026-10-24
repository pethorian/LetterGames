package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Stubber;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Duration;
import java.util.UUID;

import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Critério 5: falha dentro da transação (depois de gravar em eventos_processados) é desfeita e retentada. Se o
 * rollback não acontecesse, a nova tentativa veria o evento como duplicado e o contador ficaria em zero.
 *
 * <p>Falha transitória (infraestrutura) segura a partição com backoff longo e não vai para a DLT; falha de
 * processamento (bug/regra) tem 4 tentativas e vai para a DLT. Backoffs encurtados em application-test.yml.
 */
@ExtendWith(OutputCaptureExtension.class)
class FalhaTransitoriaTest extends IntegracaoKafkaBase {

    private static final TransientDataAccessResourceException BANCO_INSTAVEL =
            new TransientDataAccessResourceException("banco indisponível (simulado)");

    private static final CannotCreateTransactionException BANCO_FORA =
            new CannotCreateTransactionException("sem conexão com o banco (simulado)");

    private static BacklogEvento comId(UUID id) {
        return argThat(e -> e != null && id.equals(e.eventId()));
    }

    @Test
    void falhaDuasVezesEDepoisProcessaExatamenteUmaVez(CapturedOutput saida) {
        String usuario = usuarioUnico("transitoria");
        var evento = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        doThrow(BANCO_INSTAVEL).doThrow(BANCO_INSTAVEL).doCallRealMethod()
                .when(regra).aplicar(any(), comId(evento.eventId()));

        publicar(evento, "transit-01");

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getZerados()).isEqualTo(1)));

        verify(regra, times(3)).aplicar(any(), comId(evento.eventId()));
        assertThat(estatisticasDe(usuario).orElseThrow().getHorasZeradas()).isEqualTo(30);
        assertThat(processados(evento.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        // n = número da nova tentativa; o intervalo é o que o backoff concedeu (50ms, 100ms no perfil de teste).
        assertThat(semCores(saida))
                .contains("⟳ RETRY 1 (transitória) consumidor=estatisticas eventId=" + evento.eventId()
                        + " próxima em 50 ms: TransientDataAccessResourceException: banco indisponível (simulado)")
                .contains("⟳ RETRY 2 (transitória) consumidor=estatisticas eventId=" + evento.eventId()
                        + " próxima em 100 ms")
                .doesNotContain("⟳ RETRY 3 (transitória) consumidor=estatisticas eventId=" + evento.eventId());
        // O group de conquistas não é afetado pela falha do outro group.
        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getZeramentos()).isEqualTo(1)));
        assertThat(lerDlt(r -> usuario.equals(r.key()), 0, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void bancoForaPorMaisTentativasQueOLimiteDeProcessamentoNaoVaiParaDlt(CapturedOutput saida) {
        String usuario = usuarioUnico("banco-fora");
        var evento = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        // 6 falhas seguidas: com o limite de processamento (4 tentativas) já teria ido para a DLT.
        Stubber falhas = doThrow(BANCO_FORA);
        for (int i = 1; i < 6; i++) {
            falhas = falhas.doThrow(BANCO_FORA);
        }
        falhas.doCallRealMethod().when(regra).aplicar(any(), comId(evento.eventId()));

        publicar(evento, "fora-01");

        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getZerados()).isEqualTo(1)));

        verify(regra, times(7)).aplicar(any(), comId(evento.eventId()));
        assertThat(estatisticasDe(usuario).orElseThrow().getHorasZeradas()).isEqualTo(30);
        assertThat(processados(evento.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(semCores(saida))
                .contains("⟳ RETRY 6 (transitória) consumidor=estatisticas eventId=" + evento.eventId()
                        + " próxima em 200 ms: CannotCreateTransactionException: sem conexão com o banco (simulado)")
                .doesNotContain("(processamento) consumidor=estatisticas eventId=" + evento.eventId());
        assertThat(lerDlt(r -> usuario.equals(r.key()), 0, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void falhaDeProcessamentoEsgotaQuatroTentativasEVaiParaDlt(CapturedOutput saida) {
        String usuario = usuarioUnico("esgota");
        var evento = evento(TipoEvento.JOGO_ADICIONADO, usuario, 40, null, ZERADO);
        doThrow(new IllegalStateException("regra quebrada (simulado)"))
                .when(regra).aplicar(any(), comId(evento.eventId()));

        publicar(evento, "esgota-01");

        var dlt = lerDlt(r -> usuario.equals(r.key()), 1, ESPERA);
        assertThat(dlt).hasSize(1);
        var registro = dlt.get(0);
        assertThat(registro.value()).isEqualTo(json(evento));
        assertThat(header(registro, Topicos.HEADER_CORRELATION_ID)).isEqualTo("esgota-01");
        assertThat(header(registro, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isEqualTo(Consumidores.ESTATISTICAS);
        assertThat(header(registro, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("regra quebrada");

        verify(regra, times(4)).aplicar(any(), comId(evento.eventId()));
        assertThat(estatisticasDe(usuario)).isEmpty();
        assertThat(processados(evento.eventId(), Consumidores.ESTATISTICAS)).isZero();
        assertThat(semCores(saida))
                .contains("⟳ RETRY 3 (processamento) consumidor=estatisticas eventId=" + evento.eventId()
                        + " próxima em 200 ms: IllegalStateException: regra quebrada (simulado)")
                .doesNotContain("⟳ RETRY 4 (processamento) consumidor=estatisticas eventId=" + evento.eventId())
                .containsPattern("☠ DLT backlog.eventos.DLT ← group=estatisticas offset=\\d+ partição="
                        + registro.partition() + " motivo=IllegalStateException: regra quebrada");
        // Conquistas processa normalmente: groups independentes.
        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getZeramentos()).isEqualTo(1)));
    }
}
