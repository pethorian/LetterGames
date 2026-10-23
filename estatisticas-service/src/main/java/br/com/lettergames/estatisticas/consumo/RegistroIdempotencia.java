package br.com.lettergames.estatisticas.consumo;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotência do consumidor (ADR-003). Precisa ser chamado <b>dentro</b> da transação do serviço que aplica o
 * efeito, para que o registro e o efeito sejam gravados (ou desfeitos) juntos.
 *
 * <p>Por que verificar e depois inserir, e não "inserir e capturar a violação de PK": no Postgres, um erro dentro
 * da transação a deixa abortada (todo comando seguinte falha), então capturar a exceção e seguir não funciona.
 * A verificação resolve o caso comum (reentrega depois do commit). A corrida rara (duas entregas simultâneas do
 * mesmo evento, ex.: durante um rebalanceamento) é resolvida pela PK: a segunda transação falha no INSERT, faz
 * rollback, o error handler reentrega o registro e, na nova tentativa, ele já aparece como processado.
 */
@Component
public class RegistroIdempotencia {

    private static final Logger log = LoggerFactory.getLogger(RegistroIdempotencia.class);

    private final EventoProcessadoRepository repositorio;
    private final EntityManager entityManager;

    public RegistroIdempotencia(EventoProcessadoRepository repositorio, EntityManager entityManager) {
        this.repositorio = repositorio;
        this.entityManager = entityManager;
    }

    /** @return {@code true} se o evento é novo para o consumidor e foi registrado; {@code false} se é duplicado. */
    public boolean registrarSeNovo(UUID eventId, String consumidor) {
        if (repositorio.existsById(new EventoProcessado.Id(eventId, consumidor))) {
            log.info("↺ DUPLICADO ignorado consumidor={} eventId={}", consumidor, eventId);
            return false;
        }
        entityManager.persist(new EventoProcessado(eventId, consumidor, Instant.now()));
        // flush antecipa a violação de PK (corrida) para antes de qualquer efeito ser calculado.
        entityManager.flush();
        return true;
    }
}
