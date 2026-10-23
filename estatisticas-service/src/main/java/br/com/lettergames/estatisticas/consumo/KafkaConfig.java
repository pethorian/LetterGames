package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
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

/**
 * Um container factory por consumer group, cada um com seu interceptor de correlação e seu error handler.
 *
 * <p>Retry bloqueante (e não {@code @RetryableTopic}) para preservar a ordem por usuário dentro da partição
 * (ADR-005). Falhas transitórias (banco fora, timeout) são retentadas com backoff exponencial; falhas permanentes
 * vão direto para a DLT.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesRetry.class)
public class KafkaConfig {

    public static final String FACTORY_ESTATISTICAS = "estatisticasContainerFactory";
    public static final String FACTORY_CONQUISTAS = "conquistasContainerFactory";

    static final int PARTICOES = 3;

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
        // O configurer aplica spring.kafka.listener.* (ack-mode RECORD, concorrência) do application.yml.
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setRecordInterceptor(new InterceptadorCorrelacao(grupo, objectMapper));
        factory.setCommonErrorHandler(errorHandler(grupo, template, retry, objectMapper));
        return factory;
    }

    static DefaultErrorHandler errorHandler(String grupo, KafkaTemplate<?, ?> template, PropriedadesRetry retry,
                                            ObjectMapper objectMapper) {
        var backOff = new ExponentialBackOffWithMaxRetries(retry.maxTentativasExtras());
        backOff.setInitialInterval(retry.intervaloInicial().toMillis());
        backOff.setMultiplier(retry.multiplicador());

        var handler = new DefaultErrorHandler(new RecuperadorDlt(template), backOff);
        // Tudo o mais (DataAccessException, CannotCreateTransactionException, violação de PK na corrida de
        // idempotência...) é tratado como transitório e retentado.
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        handler.setRetryListeners(new LogDeRetry(grupo, retry.totalTentativas(), objectMapper));
        return handler;
    }
}
