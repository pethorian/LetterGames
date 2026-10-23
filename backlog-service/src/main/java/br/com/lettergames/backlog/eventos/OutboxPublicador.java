package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lê o outbox e publica no Kafka preservando a ordem por usuário (chave). Garantia at-least-once: se o
 * {@code send} é confirmado mas a marcação de {@code publicado_em} falha, o evento sai de novo depois; o
 * consumidor deduplica pelo {@code eventId} (ADR-003).
 *
 * <p>Cada ciclo roda em rodadas; cada rodada, numa transação, trava a cabeça de fila de cada chave
 * ({@link EventoOutboxRepository#travarCabecasPendentes}), envia e marca. Falha de um evento bloqueia só a
 * chave dele até o fim do ciclo: os outros usuários seguem.
 *
 * <p>Depois de {@code backlog.outbox.max-tentativas} falhas não transitórias o evento é estacionado
 * ({@code falhou_em}) e a chave <b>continua bloqueada</b> até alguém reprocessá-lo: pular um evento quebraria
 * o estado derivado no consumidor (ex.: STATUS_ALTERADO sem o JOGO_ADICIONADO). Ordem vale mais que
 * disponibilidade de um usuário só. Falhas transitórias (broker fora, timeout) contam tentativa, mas não
 * estacionam: senão uma queda do Kafka estacionaria a cabeça de todos os usuários.
 */
@Component
public class OutboxPublicador {

    static final int TAMANHO_LOTE = 50;
    /** Limita o trabalho de um ciclo; o que sobrar sai no próximo. */
    static final int MAX_RODADAS = 20;
    /** Folga sobre o delivery.timeout.ms: quem desiste primeiro é o producer, com o motivo real da falha. */
    static final Duration FOLGA_ESPERA = Duration.ofSeconds(2);

    private static final Logger log = LoggerFactory.getLogger(OutboxPublicador.class);

    private final EventoOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transacao;
    private final int maxTentativas;
    private final Duration esperaEnvio;

    @Autowired
    public OutboxPublicador(EventoOutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                            TransactionTemplate transacao,
                            @Value("${backlog.outbox.max-tentativas:10}") int maxTentativas) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transacao = transacao;
        this.maxTentativas = maxTentativas;
        this.esperaEnvio = esperaEnvio(kafkaTemplate);
    }

    /**
     * Se o get desistisse antes do delivery.timeout.ms, o producer ainda poderia entregar depois: contaríamos
     * falha de um evento publicado. Por isso a espera é delivery.timeout.ms (padrão do Kafka: 120 s) + folga.
     */
    static Duration esperaEnvio(KafkaTemplate<?, ?> kafkaTemplate) {
        ProducerFactory<?, ?> fabrica = kafkaTemplate.getProducerFactory();
        Object configurado = fabrica == null ? null
                : fabrica.getConfigurationProperties().get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
        long deliveryMs = configurado == null ? 120_000 : Long.parseLong(configurado.toString());
        return Duration.ofMillis(deliveryMs).plus(FOLGA_ESPERA);
    }

    /** @return quantos eventos foram publicados e marcados neste ciclo */
    public int publicarPendentes() {
        Set<String> chavesBloqueadas = new HashSet<>();
        int publicados = 0;
        for (int i = 0; i < MAX_RODADAS; i++) {
            Rodada rodada = new Rodada();
            try {
                transacao.executeWithoutResult(status -> publicarRodada(rodada, chavesBloqueadas));
            } catch (RuntimeException e) {
                // Commit desfeito: os enviados desta rodada seguem pendentes e serão republicados.
                rodada.marcados.forEach(eventId -> log.warn(
                        "⟳ OUTBOX publicado mas não marcado eventId={}: {} — sai de novo no próximo ciclo",
                        eventId, motivo(e)));
                break;
            }
            publicados += rodada.marcados.size();
            if (rodada.marcados.isEmpty() || rodada.brokerIndisponivel) {
                break;
            }
        }
        return publicados;
    }

    private void publicarRodada(Rodada rodada, Set<String> chavesBloqueadas) {
        List<EventoOutbox> cabecas = repository.travarCabecasPendentes(Limit.of(TAMANHO_LOTE)).stream()
                .filter(evento -> !chavesBloqueadas.contains(evento.getChave()))
                .toList();
        // No máximo um evento por chave: enviar todos antes de esperar não inverte a ordem de ninguém.
        List<Envio> envios = new ArrayList<>();
        for (EventoOutbox evento : cabecas) {
            correlacionar(evento);
            try {
                envios.add(new Envio(evento, kafkaTemplate.send(registro(evento))));
            } catch (RuntimeException e) {
                registrarFalha(evento, e, chavesBloqueadas);
                if (transitoria(e)) {
                    rodada.brokerIndisponivel = true; // os próximos esperariam o mesmo max.block.ms à toa
                    break;
                }
            } finally {
                MDC.remove(Correlacao.MDC_KEY);
            }
        }
        envios.forEach(envio -> concluir(envio, rodada, chavesBloqueadas));
    }

    private void concluir(Envio envio, Rodada rodada, Set<String> chavesBloqueadas) {
        EventoOutbox evento = envio.evento();
        correlacionar(evento);
        try {
            RecordMetadata metadata;
            try {
                metadata = envio.futuro().get(esperaEnvio.toMillis(), TimeUnit.MILLISECONDS).getRecordMetadata();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                registrarFalha(evento, e, chavesBloqueadas);
                return;
            } catch (ExecutionException | TimeoutException e) {
                registrarFalha(evento, e, chavesBloqueadas);
                rodada.brokerIndisponivel |= transitoria(e);
                return;
            }
            log.info("⇢ KAFKA PUB {} key={} tipo={} eventId={} partição={} offset={}", evento.getTopico(),
                    evento.getChave(), evento.getTipo(), evento.getEventId(), metadata.partition(), metadata.offset());
            marcarPublicado(evento, rodada, chavesBloqueadas);
        } finally {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }

    private void marcarPublicado(EventoOutbox evento, Rodada rodada, Set<String> chavesBloqueadas) {
        try {
            repository.marcarPublicado(evento.getEventId(), Instant.now());
            rodada.marcados.add(evento.getEventId());
        } catch (RuntimeException e) {
            // O Kafka já tem o evento, mas ele segue pendente (e cabeça da chave). Bloquear evita reenviá-lo
            // em todas as rodadas deste ciclo; o reenvio fica para o próximo.
            chavesBloqueadas.add(evento.getChave());
            log.warn("⟳ OUTBOX publicado mas não marcado eventId={}: {} — sai de novo no próximo ciclo",
                    evento.getEventId(), motivo(e));
        }
    }

    private void registrarFalha(EventoOutbox evento, Exception e, Set<String> chavesBloqueadas) {
        chavesBloqueadas.add(evento.getChave());
        int tentativas = evento.getTentativas() + 1; // linha travada nesta transação: o valor lido é o atual
        boolean estacionar = !transitoria(e) && tentativas >= maxTentativas;
        try {
            repository.registrarFalha(evento.getEventId(), estacionar ? Instant.now() : null);
        } catch (RuntimeException erroAoSalvar) {
            log.warn("⟳ OUTBOX não conseguiu registrar a tentativa do eventId={}: {}", evento.getEventId(),
                    erroAoSalvar.getMessage());
            return;
        }
        if (estacionar) {
            log.error("☠ OUTBOX evento estacionado eventId={} após {} tentativas: {}", evento.getEventId(),
                    tentativas, motivo(e));
            log.warn("⏸ OUTBOX chave={} bloqueada: os eventos seguintes desse usuário aguardam o eventId={} "
                    + "ser reprocessado (limpar falhou_em)", evento.getChave(), evento.getEventId());
        } else {
            log.warn("⟳ OUTBOX falha ao publicar eventId={} (tentativa {}/{}): {} — nova tentativa no próximo ciclo",
                    evento.getEventId(), tentativas, maxTentativas, motivo(e));
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

    private static void correlacionar(EventoOutbox evento) {
        if (evento.getCorrelationId() != null) {
            MDC.put(Correlacao.MDC_KEY, evento.getCorrelationId());
        }
    }

    /** Broker fora, sem líder, timeout: tende a passar sozinho e afeta todas as chaves igualmente. */
    static boolean transitoria(Throwable e) {
        for (Throwable causa = e; causa != null && causa != causa.getCause(); causa = causa.getCause()) {
            if (causa instanceof RetriableException || causa instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String motivo(Throwable e) {
        Throwable raiz = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        return raiz.getClass().getSimpleName() + (raiz.getMessage() != null ? ": " + raiz.getMessage() : "");
    }

    private record Envio(EventoOutbox evento, CompletableFuture<SendResult<String, String>> futuro) {
    }

    private static final class Rodada {
        final List<UUID> marcados = new ArrayList<>();
        boolean brokerIndisponivel;
    }
}
