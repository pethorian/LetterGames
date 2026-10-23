package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Grava o evento na tabela de outbox, sempre dentro da transação de quem alterou o backlog. */
@Component
public class OutboxRegistrador {

    private final EventoOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxRegistrador(EventoOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // MANDATORY: gravar o outbox fora da transação do item reabriria o problema do dual write.
    @Transactional(propagation = Propagation.MANDATORY)
    public EventoOutbox registrar(BacklogEvento evento) {
        EventoOutbox linha = new EventoOutbox(evento.eventId(), Topicos.BACKLOG_EVENTOS, evento.usuarioId(),
                evento.tipo(), serializar(evento), MDC.get(Correlacao.MDC_KEY), Instant.now());
        return repository.save(linha);
    }

    private String serializar(BacklogEvento evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o evento " + evento.eventId(), e);
        }
    }
}
