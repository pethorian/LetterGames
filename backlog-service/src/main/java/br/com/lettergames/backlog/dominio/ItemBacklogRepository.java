package br.com.lettergames.backlog.dominio;

import br.com.lettergames.contratos.evento.StatusBacklog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemBacklogRepository extends JpaRepository<ItemBacklog, Long> {

    /**
     * {@code SELECT ... FOR UPDATE}: PATCH/DELETE concorrentes no mesmo item esperam o commit do anterior
     * e leem o estado já gravado, então o {@code statusAnterior} do evento é sempre o real.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ItemBacklog i where i.id = :id and i.usuarioId = :usuarioId")
    Optional<ItemBacklog> findByIdAndUsuarioIdParaAtualizar(@Param("id") Long id,
                                                            @Param("usuarioId") String usuarioId);

    boolean existsByUsuarioIdAndJogoId(String usuarioId, Long jogoId);

    List<ItemBacklog> findByUsuarioIdOrderByIdAsc(String usuarioId);

    List<ItemBacklog> findByUsuarioIdAndStatusOrderByIdAsc(String usuarioId, StatusBacklog status);
}
