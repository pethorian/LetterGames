package br.com.lettergames.backlog.eventos;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {

    /**
     * Cabeça de fila de cada chave: o evento pendente mais antigo (por {@code sequencia}) de cada usuário, se nenhum
     * anterior da mesma chave estiver pendente ou estacionado. Travado com {@code FOR UPDATE SKIP LOCKED} (lock
     * timeout -2 do Hibernate): outra instância do publicador pula a cabeça em uso e, como o 2º evento daquela chave não é
     * cabeça enquanto o 1º não for publicado, também não o publica fora de ordem. No H2 o dialeto não suporta
     * SKIP LOCKED e gera só {@code FOR UPDATE}, o que basta para os testes (uma instância).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = AvailableSettings.JAKARTA_LOCK_TIMEOUT, value = "-2"))
    @Query("""
            select e from EventoOutbox e
            where e.publicadoEm is null and e.falhouEm is null
              and not exists (select 1 from EventoOutbox anterior
                              where anterior.chave = e.chave and anterior.publicadoEm is null
                                and anterior.sequencia < e.sequencia)
            order by e.sequencia""")
    List<EventoOutbox> travarCabecasPendentes(Limit limite);

    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("update EventoOutbox e set e.publicadoEm = :quando where e.eventId = :eventId")
    int marcarPublicado(@Param("eventId") UUID eventId, @Param("quando") Instant quando);

    /** {@code falhouEm} não nulo estaciona o evento. */
    @Modifying(flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("update EventoOutbox e set e.tentativas = e.tentativas + 1, e.falhouEm = :falhouEm "
            + "where e.eventId = :eventId")
    int registrarFalha(@Param("eventId") UUID eventId, @Param("falhouEm") Instant falhouEm);
}
