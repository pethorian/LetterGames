package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/** Põe o correlationId do header no MDC antes do listener, loga a recepção e limpa o MDC depois. */
public class InterceptadorCorrelacao implements RecordInterceptor<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(InterceptadorCorrelacao.class);

    private final String grupo;
    private final ObjectMapper objectMapper;

    public InterceptadorCorrelacao(String grupo, ObjectMapper objectMapper) {
        this.grupo = grupo;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> registro,
                                                    Consumer<Object, Object> consumer) {
        CorrelacaoKafka.colocarNoMdc(registro);
        log.info("⇠ KAFKA SUB {} group={} partição={} offset={} key={} tipo={} eventId={}",
                registro.topic(), grupo, registro.partition(), registro.offset(), registro.key(),
                CorrelacaoKafka.header(registro, Topicos.HEADER_TIPO), EventIds.extrair(objectMapper, registro.value()));
        return registro;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> registro, Consumer<Object, Object> consumer) {
        MDC.remove(Correlacao.MDC_KEY);
    }
}
