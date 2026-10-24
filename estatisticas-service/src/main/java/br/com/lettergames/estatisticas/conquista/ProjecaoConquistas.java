package br.com.lettergames.estatisticas.conquista;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.estatisticas.consumo.Consumidores;
import br.com.lettergames.estatisticas.consumo.RegistroIdempotencia;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Serviço de aplicação do consumer group {@code conquistas}. */
@Service
public class ProjecaoConquistas {

    private static final Logger log = LoggerFactory.getLogger(ProjecaoConquistas.class);

    private final RegistroIdempotencia idempotencia;
    private final ProgressoConquistasRepository progressos;
    private final ConquistaRepository conquistas;
    private final EntityManager entityManager;

    public ProjecaoConquistas(RegistroIdempotencia idempotencia, ProgressoConquistasRepository progressos,
                              ConquistaRepository conquistas, EntityManager entityManager) {
        this.idempotencia = idempotencia;
        this.progressos = progressos;
        this.conquistas = conquistas;
        this.entityManager = entityManager;
    }

    /**
     * Registra o evento como processado, atualiza o progresso e desbloqueia o que foi atingido, tudo na mesma
     * transação.
     *
     * @return conquistas desbloqueadas por este evento (vazia se duplicado ou se nada novo foi atingido)
     */
    @Transactional
    public List<TipoConquista> processar(BacklogEvento evento) {
        if (!idempotencia.registrarSeNovo(evento.eventId(), Consumidores.CONQUISTAS)) {
            return List.of();
        }
        ProgressoConquistas progresso = progressos.findById(evento.usuarioId())
                .orElseGet(() -> new ProgressoConquistas(evento.usuarioId()));
        if (!progresso.registrar(evento)) {
            return List.of();
        }
        progressos.save(progresso);

        List<TipoConquista> novas = new ArrayList<>();
        Instant agora = Instant.now();
        for (TipoConquista tipo : TipoConquista.values()) {
            if (tipo.atingida(progresso)
                    && !conquistas.existsById(new Conquista.Id(evento.usuarioId(), tipo))) {
                // persist (e não save/merge): a PK garante o "uma vez só" mesmo numa corrida.
                entityManager.persist(new Conquista(evento.usuarioId(), tipo, agora));
                novas.add(tipo);
                log.info("🏆 CONQUISTA DESBLOQUEADA {}: {}", evento.usuarioId(), tipo.descricao());
            }
        }
        return novas;
    }
}
