package br.com.lettergames.estatisticas.consumo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcessadoRepository extends JpaRepository<EventoProcessado, EventoProcessado.Id> {
}
