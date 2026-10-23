package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Lê o outbox em ordem de criação e publica no Kafka. Garantia at-least-once: se o processo cair entre o
 * {@code send} confirmado e a marcação de {@code publicado_em}, o evento sai de novo no próximo ciclo; o
 * consumidor deduplica pelo {@code eventId} (ADR-003).
 */
@Component
public class OutboxPublicador {

    static final int TAMANHO_LOTE = 50;
    private static final long TIMEOUT_ENVIO_SEGUNDOS = 5;

    private static final Logger log = LoggerFactory.getLogger(OutboxPublicador.class);

    private final EventoOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublicador(EventoOutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** @return quantos eventos foram publicados neste ciclo */
    public int publicarPendentes() {
        List<EventoOutbox> pendentes = repository.findTop50ByPublicadoEmIsNullOrderByCriadoEmAsc();
        int publicados = 0;
        for (EventoOutbox evento : pendentes) {
            if (!publicar(evento)) {
                break; // para o lote: publicar o seguinte antes deste quebraria a ordem por usuário
            }
            publicados++;
        }
        return publicados;
    }

    private boolean publicar(EventoOutbox evento) {
        if (evento.getCorrelationId() != null) {
            MDC.put(Correlacao.MDC_KEY, evento.getCorrelationId());
        }
        try {
            RecordMetadata metadata = kafkaTemplate.send(registro(evento))
                    .get(TIMEOUT_ENVIO_SEGUNDOS, TimeUnit.SECONDS)
                    .getRecordMetadata();
            evento.marcarPublicado(Instant.now());
            repository.save(evento);
            log.info("⇢ KAFKA PUB {} key={} tipo={} eventId={} partição={} offset={}", evento.getTopico(),
                    evento.getChave(), evento.getTipo(), evento.getEventId(), metadata.partition(), metadata.offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarFalha(evento, e);
            return false;
        } catch (Exception e) {
            registrarFalha(evento, e);
            return false;
        } finally {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }

    private ProducerRecord<String, String> registro(EventoOutbox evento) {
        ProducerRecord<String, String> registro =
                new ProducerRecord<>(evento.getTopico(), evento.getChave(), evento.getPayload());
        if (evento.getCorrelationId() != null) {
            registro.headers().add(Topicos.HEADER_CORRELATION_ID,
                    evento.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        registro.headers().add(Topicos.HEADER_TIPO, evento.getTipo().name().getBytes(StandardCharsets.UTF_8));
        return registro;
    }

    private void registrarFalha(EventoOutbox evento, Exception e) {
        int tentativas = evento.registrarFalha();
        try {
            repository.save(evento);
        } catch (RuntimeException erroAoSalvar) {
            log.warn("⟳ OUTBOX não conseguiu registrar a tentativa do eventId={}: {}", evento.getEventId(),
                    erroAoSalvar.getMessage());
        }
        log.warn("⟳ OUTBOX falha ao publicar eventId={} (tentativa {}): {} — nova tentativa no próximo ciclo",
                evento.getEventId(), tentativas, motivo(e));
    }

    private static String motivo(Throwable e) {
        Throwable raiz = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return raiz.getClass().getSimpleName() + (raiz.getMessage() != null ? ": " + raiz.getMessage() : "");
    }
}
