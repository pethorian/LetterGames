package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Um container factory por consumer group, cada um com seu interceptor de correlação e seu error handler.
 *
 * <p>Retry bloqueante (e não {@code @RetryableTopic}) para preservar a ordem por usuário dentro da partição
 * (ADR-005). Cada falha é classificada por {@link ClassificadorFalhas}:
 * <ul>
 *   <li><b>inválida</b> ({@link EventoInvalidoException}): DLT direto, sem retry;</li>
 *   <li><b>transitória</b> (banco, conexão, lock): backoff exponencial longo. Melhor segurar a partição do que
 *       pular o evento: na DLT, o próximo evento do mesmo usuário seria aplicado sobre um estado incompleto e a
 *       projeção divergiria para sempre;</li>
 *   <li><b>processamento</b> (bug, regra): poucas tentativas e DLT.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(PropriedadesRetry.class)
public class KafkaConfig {

    public static final String FACTORY_ESTATISTICAS = "estatisticasContainerFactory";
    public static final String FACTORY_CONQUISTAS = "conquistasContainerFactory";

    static final int PARTICOES = 3;

    private static final long MAX_POLL_INTERVAL_PADRAO = 300_000;

    // O broker tem auto-create desligado; declarar aqui evita depender da ordem de subida dos serviços.
    @Bean
    NewTopic topicoBacklogEventos() {
        return TopicBuilder.name(Topicos.BACKLOG_EVENTOS).partitions(PARTICOES).replicas(1).build();
    }

    // Mesmo número de partições do tópico de origem: o recuperador publica na mesma partição.
    @Bean
    NewTopic topicoBacklogEventosDlt() {
        return TopicBuilder.name(Topicos.BACKLOG_EVENTOS_DLT).partitions(PARTICOES).replicas(1).build();
    }

    @Bean(FACTORY_ESTATISTICAS)
    ConcurrentKafkaListenerContainerFactory<Object, Object> estatisticasContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<?, ?> template, PropriedadesRetry retry, ObjectMapper objectMapper) {
        return criarFactory(Consumidores.ESTATISTICAS, configurer, consumerFactory, template, retry, objectMapper);
    }

    @Bean(FACTORY_CONQUISTAS)
    ConcurrentKafkaListenerContainerFactory<Object, Object> conquistasContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<?, ?> template, PropriedadesRetry retry, ObjectMapper objectMapper) {
        return criarFactory(Consumidores.CONQUISTAS, configurer, consumerFactory, template, retry, objectMapper);
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> criarFactory(
            String grupo, ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory, KafkaTemplate<?, ?> template, PropriedadesRetry retry,
            ObjectMapper objectMapper) {
        validarMaxPollInterval(consumerFactory, retry);
        // O configurer aplica spring.kafka.listener.* (ack-mode RECORD, concorrência) do application.yml.
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setRecordInterceptor(new InterceptadorCorrelacao(grupo, objectMapper));
        factory.setCommonErrorHandler(errorHandler(grupo, template, retry, objectMapper));
        return factory;
    }

    static DefaultErrorHandler errorHandler(String grupo, KafkaTemplate<?, ?> template, PropriedadesRetry retry,
                                            ObjectMapper objectMapper) {
        var logDeRetry = new LogDeRetry(grupo, objectMapper);
        var handler = new DefaultErrorHandler(new RecuperadorDlt(template), backOffProcessamento(retry));
        // Avaliada na 1ª falha de cada registro (e de novo se o tipo da exceção mudar).
        handler.setBackOffFunction((registro, excecao) -> {
            ClasseFalha classe = ClassificadorFalhas.classificar(excecao);
            BackOff backOff = switch (classe) {
                case INVALIDA -> new FixedBackOff(0, 0);
                case TRANSITORIA -> backOffTransitoria(retry);
                case PROCESSAMENTO -> backOffProcessamento(retry);
            };
            return logDeRetry.comLog(backOff, classe, registro, excecao);
        });
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        handler.setRetryListeners(logDeRetry);
        return handler;
    }

    static BackOff backOffTransitoria(PropriedadesRetry retry) {
        var cfg = retry.transitoria();
        var backOff = new ExponentialBackOff(cfg.intervaloInicial().toMillis(), cfg.multiplicador());
        backOff.setMaxInterval(cfg.intervaloMaximo().toMillis());
        // Conta a soma dos intervalos já concedidos; esgotado, o registro vai para a DLT.
        backOff.setMaxElapsedTime(cfg.tempoMaximo().toMillis());
        return backOff;
    }

    static BackOff backOffProcessamento(PropriedadesRetry retry) {
        var cfg = retry.processamento();
        var backOff = new ExponentialBackOffWithMaxRetries(cfg.maxTentativasExtras());
        backOff.setInitialInterval(cfg.intervaloInicial().toMillis());
        backOff.setMultiplier(cfg.multiplicador());
        return backOff;
    }

    /**
     * O DefaultErrorHandler dorme o intervalo na thread do consumidor, faz seek e volta ao poll: cada nova tentativa
     * é um poll novo, então só um intervalo (mais o processamento) precisa caber em {@code max.poll.interval.ms},
     * não o tempo total. Exigimos folga de 2x para não haver rebalanceamento no meio do backoff.
     */
    static void validarMaxPollInterval(ConsumerFactory<?, ?> consumerFactory, PropriedadesRetry retry) {
        Object valor = consumerFactory.getConfigurationProperties().get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG);
        long maxPoll = valor == null ? MAX_POLL_INTERVAL_PADRAO : Long.parseLong(valor.toString());
        long intervaloMaximo = retry.transitoria().intervaloMaximo().toMillis();
        if (intervaloMaximo * 2 > maxPoll) {
            throw new IllegalStateException("estatisticas.retry.transitoria.intervalo-maximo=" + intervaloMaximo
                    + " ms exige max.poll.interval.ms >= " + intervaloMaximo * 2 + " (atual: " + maxPoll + ")");
        }
    }
}
