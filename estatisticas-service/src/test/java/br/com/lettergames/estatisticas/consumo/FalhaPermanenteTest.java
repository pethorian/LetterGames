package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.support.KafkaHeaders;

import java.util.UUID;

import static br.com.lettergames.contratos.evento.StatusBacklog.QUERO_JOGAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Critério 5: poison pill (JSON inválido, evento sem eventId) vai direto para a DLT, sem retry, e não trava a
 * partição: o evento válido publicado depois, com a mesma key, é processado normalmente.
 */
@ExtendWith(OutputCaptureExtension.class)
class FalhaPermanenteTest extends IntegracaoKafkaBase {

    @Test
    void poisonPillVaiDiretoParaDltSemTravarAParticao(CapturedOutput saida) {
        String usuario = usuarioUnico("poison");
        String jsonInvalido = "{isto não é json";
        String semEventId = """
                {"tipo":"JOGO_ADICIONADO","versao":1,"ocorridoEm":"2026-10-24T14:03:11.123Z","usuarioId":"%s",\
                "itemId":17,"jogoId":1,"titulo":"Hollow Knight","duracaoMediaHoras":27,"statusNovo":"QUERO_JOGAR"}"""
                .formatted(usuario);
        var valido = evento(TipoEvento.JOGO_ADICIONADO, usuario, 27, null, QUERO_JOGAR);

        publicarBruto(usuario, jsonInvalido, "poison-01", null);
        publicarBruto(usuario, semEventId, "poison-02", TipoEvento.JOGO_ADICIONADO.name());
        publicar(valido, "poison-03");

        // Partição não travou: o evento válido, atrás dos dois inválidos, foi processado pelos dois groups.
        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getQueroJogar()).isEqualTo(1));
            assertThat(processados(valido.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
        });

        // Cada group publica a sua cópia na DLT (o header de consumer group diz quem falhou).
        var dlt = lerDlt(r -> usuario.equals(r.key()), 4, ESPERA);
        assertThat(dlt).hasSize(4);
        assertThat(dlt).extracting(r -> r.value())
                .containsExactlyInAnyOrder(jsonInvalido, jsonInvalido, semEventId, semEventId);
        assertThat(dlt).extracting(r -> header(r, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
                .containsExactlyInAnyOrder(Consumidores.ESTATISTICAS, Consumidores.CONQUISTAS,
                        Consumidores.ESTATISTICAS, Consumidores.CONQUISTAS);
        assertThat(dlt).extracting(r -> header(r, Topicos.HEADER_CORRELATION_ID))
                .containsOnly("poison-01", "poison-02");
        assertThat(dlt).extracting(r -> header(r, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .containsOnly(EventoInvalidoException.class.getName());

        // Uma tentativa só por group (sem retry): 2 leituras de cada registro inválido = 1 por group.
        verify(leitor, times(2)).ler(jsonInvalido);
        verify(leitor, times(2)).ler(semEventId);
        assertThat(semCores(saida))
                .contains("motivo=EventoInvalidoException: eventId ausente")
                .contains("motivo=EventoInvalidoException: JSON inválido")
                .doesNotContain("⟳ RETRY");
        assertThat(estatisticasDe(usuario).orElseThrow().total()).isEqualTo(1);
    }

    @Test
    void eventoBemFormadoComValorDesconhecidoVaiParaDltSemRetry(CapturedOutput saida) {
        String usuario = usuarioUnico("semantico");
        String tipoDesconhecido = """
                {"eventId":"%s","tipo":"JOGO_EXPLODIDO","versao":1,"ocorridoEm":"2026-10-24T14:03:11.123Z",\
                "usuarioId":"%s","itemId":17,"jogoId":1,"titulo":"Hollow Knight","duracaoMediaHoras":27,\
                "statusNovo":"QUERO_JOGAR"}""".formatted(UUID.randomUUID(), usuario);
        String statusDesconhecido = """
                {"eventId":"%s","tipo":"STATUS_ALTERADO","versao":1,"ocorridoEm":"2026-10-24T14:03:11.123Z",\
                "usuarioId":"%s","itemId":17,"jogoId":1,"titulo":"Hollow Knight","duracaoMediaHoras":27,\
                "statusAnterior":"JOGANDO","statusNovo":"FINALIZADO"}""".formatted(UUID.randomUUID(), usuario);
        var valido = evento(TipoEvento.JOGO_ADICIONADO, usuario, 27, null, QUERO_JOGAR);

        publicarBruto(usuario, tipoDesconhecido, "semantico-01", "JOGO_EXPLODIDO");
        publicarBruto(usuario, statusDesconhecido, "semantico-02", TipoEvento.STATUS_ALTERADO.name());
        publicar(valido, "semantico-03");

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getQueroJogar()).isEqualTo(1));
            assertThat(processados(valido.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
        });

        var dlt = lerDlt(r -> usuario.equals(r.key()), 4, ESPERA);
        assertThat(dlt).extracting(r -> r.value())
                .containsExactlyInAnyOrder(tipoDesconhecido, tipoDesconhecido, statusDesconhecido, statusDesconhecido);
        assertThat(dlt).extracting(r -> header(r, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .containsOnly(EventoInvalidoException.class.getName());

        // Uma leitura por group: nenhuma nova tentativa.
        verify(leitor, times(2)).ler(tipoDesconhecido);
        verify(leitor, times(2)).ler(statusDesconhecido);
        assertThat(semCores(saida))
                .containsPattern("☠ DLT backlog.eventos.DLT ← group=estatisticas .*motivo=EventoInvalidoException: "
                        + "JSON inválido: .*JOGO_EXPLODIDO")
                .containsPattern("☠ DLT backlog.eventos.DLT ← group=conquistas .*motivo=EventoInvalidoException: "
                        + "JSON inválido: .*FINALIZADO")
                .doesNotContain("⟳ RETRY");
        assertThat(estatisticasDe(usuario).orElseThrow().total()).isEqualTo(1);
    }
}
