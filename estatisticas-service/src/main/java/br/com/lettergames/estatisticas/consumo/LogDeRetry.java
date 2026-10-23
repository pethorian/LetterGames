package br.com.lettergames.estatisticas.consumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RetryListener;

/** Loga cada nova tentativa agendada pelo error handler. */
public class LogDeRetry implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(LogDeRetry.class);

    private final String grupo;
    private final int totalTentativas;
    private final ObjectMapper objectMapper;

    public LogDeRetry(String grupo, int totalTentativas, ObjectMapper objectMapper) {
        this.grupo = grupo;
        this.totalTentativas = totalTentativas;
        this.objectMapper = objectMapper;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> registro, Exception excecao, int tentativa) {
        // A última falha não gera nova tentativa (vai para a DLT), então não é logada como RETRY.
        if (tentativa >= totalTentativas || naoRetentavel(excecao)) {
            return;
        }
        CorrelacaoKafka.comCorrelacao(registro, () ->
                log.warn("⟳ RETRY {}/{} consumidor={} eventId={} offset={} partição={}: {}", tentativa + 1,
                        totalTentativas, grupo, EventIds.extrair(objectMapper, registro.value()), registro.offset(),
                        registro.partition(), RecuperadorDlt.motivo(excecao)));
    }

    private static boolean naoRetentavel(Throwable excecao) {
        for (Throwable t = excecao; t != null; t = t.getCause()) {
            if (t instanceof EventoInvalidoException) {
                return true;
            }
        }
        return false;
    }
}
