package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.evento.Topicos;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Producer: o {@code KafkaTemplate<String, String>} vem da auto-configuração do Spring Boot com as
 * propriedades {@code spring.kafka.producer.*} do application.yml (acks=all, idempotência, timeouts curtos).
 */
@Configuration
public class KafkaConfig {

    /** 3 partições com chave = usuarioId: ordem por usuário e até 3 consumidores em paralelo (ADR-002). */
    @Bean
    NewTopic topicoBacklogEventos() {
        return TopicBuilder.name(Topicos.BACKLOG_EVENTOS).partitions(3).replicas(1).build();
    }
}
