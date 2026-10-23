package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.lang.Nullable;

/**
 * Publica o registro que falhou em {@code backlog.eventos.DLT}, na mesma partição, com o value original e os
 * headers originais (inclusive {@code correlationId}). O Spring Kafka acrescenta os headers {@code kafka_dlt-*}
 * (exceção, tópico/partição/offset de origem e consumer group).
 */
public class RecuperadorDlt extends DeadLetterPublishingRecoverer {

    private static final Logger log = LoggerFactory.getLogger(RecuperadorDlt.class);

    public RecuperadorDlt(KafkaOperations<?, ?> template) {
        super(template, (registro, ex) -> new TopicPartition(Topicos.BACKLOG_EVENTOS_DLT, registro.partition()));
    }

    @Override
    public void accept(ConsumerRecord<?, ?> registro, @Nullable Consumer<?, ?> consumer, Exception excecao) {
        CorrelacaoKafka.comCorrelacao(registro, () ->
                log.error("☠ DLT {} ← offset={} partição={} motivo={}", Topicos.BACKLOG_EVENTOS_DLT,
                        registro.offset(), registro.partition(), motivo(excecao)));
        super.accept(registro, consumer, excecao);
    }

    /** Descreve a causa real, sem o invólucro {@link ListenerExecutionFailedException} do container. */
    static String motivo(Throwable excecao) {
        Throwable causa = excecao;
        while (causa instanceof ListenerExecutionFailedException && causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa.getClass().getSimpleName() + ": " + causa.getMessage();
    }
}
