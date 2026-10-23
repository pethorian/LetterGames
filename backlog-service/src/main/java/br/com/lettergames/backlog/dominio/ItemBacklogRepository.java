package br.com.lettergames.backlog.dominio;

import br.com.lettergames.contratos.evento.StatusBacklog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemBacklogRepository extends JpaRepository<ItemBacklog, Long> {

    Optional<ItemBacklog> findByIdAndUsuarioId(Long id, String usuarioId);

    boolean existsByUsuarioIdAndJogoId(String usuarioId, Long jogoId);

    List<ItemBacklog> findByUsuarioIdOrderByIdAsc(String usuarioId);

    List<ItemBacklog> findByUsuarioIdAndStatusOrderByIdAsc(String usuarioId, StatusBacklog status);
}
