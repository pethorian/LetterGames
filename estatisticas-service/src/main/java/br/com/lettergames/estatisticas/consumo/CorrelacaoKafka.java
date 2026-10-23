package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

/** Utilitários para ler headers do registro e colocar o correlationId no MDC. */
public final class CorrelacaoKafka {

    private CorrelacaoKafka() {
    }

    public static String header(ConsumerRecord<?, ?> registro, String nome) {
        Header header = registro.headers().lastHeader(nome);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    public static void colocarNoMdc(ConsumerRecord<?, ?> registro) {
        String correlationId = header(registro, Topicos.HEADER_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(Correlacao.MDC_KEY, correlationId);
        } else {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }

    /**
     * Executa o bloco com o correlationId do registro no MDC e restaura o valor anterior. Usado nos logs do error
     * handler, que podem rodar fora do ciclo do interceptor.
     */
    public static void comCorrelacao(ConsumerRecord<?, ?> registro, Runnable bloco) {
        String anterior = MDC.get(Correlacao.MDC_KEY);
        try {
            colocarNoMdc(registro);
            bloco.run();
        } finally {
            if (anterior == null) {
                MDC.remove(Correlacao.MDC_KEY);
            } else {
                MDC.put(Correlacao.MDC_KEY, anterior);
            }
        }
    }
}
