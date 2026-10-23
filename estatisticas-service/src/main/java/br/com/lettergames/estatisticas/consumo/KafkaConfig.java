package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class KafkaConfig {

    public static final String FACTORY_ESTATISTICAS = "estatisticasContainerFactory";

    static final int PARTICOES = 3;

    // O broker tem auto-create desligado; declarar aqui evita depender da ordem de subida dos serviços.
    @Bean
    NewTopic topicoBacklogEventos() {
        return TopicBuilder.name(Topicos.BACKLOG_EVENTOS).partitions(PARTICOES).replicas(1).build();
    }

    @Bean(FACTORY_ESTATISTICAS)
    ConcurrentKafkaListenerContainerFactory<Object, Object> estatisticasContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory, ObjectMapper objectMapper) {
        return criarFactory(Consumidores.ESTATISTICAS, configurer, consumerFactory, objectMapper);
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> criarFactory(
            String grupo, ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory, ObjectMapper objectMapper) {
        // O configurer aplica spring.kafka.listener.* (ack-mode RECORD, concorrência) do application.yml.
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setRecordInterceptor(new InterceptadorCorrelacao(grupo, objectMapper));
        return factory;
    }
}
