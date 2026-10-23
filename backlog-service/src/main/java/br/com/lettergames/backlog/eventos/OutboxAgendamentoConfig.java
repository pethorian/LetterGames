package br.com.lettergames.backlog.eventos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Dispara o {@link OutboxPublicador} periodicamente. Desligado nos testes, que o chamam diretamente. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "backlog.outbox.publicador-habilitado", havingValue = "true", matchIfMissing = true)
public class OutboxAgendamentoConfig {

    private final OutboxPublicador publicador;

    public OutboxAgendamentoConfig(OutboxPublicador publicador) {
        this.publicador = publicador;
    }

    @Scheduled(fixedDelayString = "${backlog.outbox.intervalo:500ms}")
    public void publicarPendentes() {
        publicador.publicarPendentes();
    }
}
