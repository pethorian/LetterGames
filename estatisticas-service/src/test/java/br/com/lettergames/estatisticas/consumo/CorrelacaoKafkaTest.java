package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** O correlationId do header Kafka só entra no MDC se respeitar o padrão. */
class CorrelacaoKafkaTest {

    private static ConsumerRecord<String, String> comCorrelacao(String valor) {
        var registro = new ConsumerRecord<String, String>(Topicos.BACKLOG_EVENTOS, 0, 0, "joao", "{}");
        registro.headers().add(Topicos.HEADER_CORRELATION_ID, valor.getBytes(StandardCharsets.UTF_8));
        return registro;
    }

    @AfterEach
    void limpar() {
        MDC.clear();
    }

    @Test
    void headerValidoVaiParaOMdc() {
        CorrelacaoKafka.colocarNoMdc(comCorrelacao("demo-02.a_b"));

        assertThat(MDC.get(Correlacao.MDC_KEY)).isEqualTo("demo-02.a_b");
    }

    @Test
    void headerInvalidoEDescartado() {
        for (String invalido : new String[] {"", "demo 02", "demo\n12:00:00 INFO forjado", "x".repeat(65), "joão"}) {
            MDC.put(Correlacao.MDC_KEY, "anterior");

            CorrelacaoKafka.colocarNoMdc(comCorrelacao(invalido));

            assertThat(MDC.get(Correlacao.MDC_KEY)).as(invalido).isNull();
        }
    }
}
