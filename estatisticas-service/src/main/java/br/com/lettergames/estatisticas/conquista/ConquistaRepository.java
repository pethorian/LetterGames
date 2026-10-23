package br.com.lettergames.estatisticas.conquista;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConquistaRepository extends JpaRepository<Conquista, Conquista.Id> {

    List<Conquista> findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc(String usuarioId);
}
