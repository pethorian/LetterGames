package br.com.lettergames.backlog.eventos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {

    List<EventoOutbox> findTop50ByPublicadoEmIsNullOrderByCriadoEmAsc();

    List<EventoOutbox> findByChaveOrderByCriadoEmAsc(String chave);
}
