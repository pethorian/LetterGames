package br.com.lettergames.backlog.dominio;

import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.eventos.OutboxRegistrador;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Casos de uso do backlog. Toda alteração grava o item e o evento no outbox na mesma transação:
 * ou os dois ficam, ou nenhum (sem dual write com o Kafka).
 */
@Service
public class BacklogService {

    private static final Logger log = LoggerFactory.getLogger(BacklogService.class);

    private final ItemBacklogRepository repository;
    private final CatalogoClient catalogo;
    private final OutboxRegistrador outbox;
    private final TransactionTemplate transacao;

    public BacklogService(ItemBacklogRepository repository, CatalogoClient catalogo, OutboxRegistrador outbox,
                          TransactionTemplate transacao) {
        this.repository = repository;
        this.catalogo = catalogo;
        this.outbox = outbox;
        this.transacao = transacao;
    }

    /**
     * Valida o jogo no catálogo e só então abre a transação: a chamada REST (com retries) não deve
     * segurar uma conexão do banco. Por isso {@link TransactionTemplate} em vez de {@code @Transactional}.
     */
    public ItemBacklog adicionar(String usuarioId, Long jogoId) {
        if (repository.existsByUsuarioIdAndJogoId(usuarioId, jogoId)) {
            throw new JogoJaNoBacklogException(usuarioId, jogoId);
        }
        JogoDTO jogo = catalogo.buscarJogo(jogoId);
        ItemBacklog item;
        try {
            item = transacao.execute(status -> {
                ItemBacklog salvo = repository.saveAndFlush(
                        ItemBacklog.novo(usuarioId, jogoId, jogo.titulo(), jogo.duracaoMediaHoras()));
                outbox.registrar(evento(TipoEvento.JOGO_ADICIONADO, salvo, null, salvo.getStatus()));
                return salvo;
            });
        } catch (DataIntegrityViolationException e) {
            // Duas adições simultâneas do mesmo jogo: a unique (usuario_id, jogo_id) decide.
            throw new JogoJaNoBacklogException(usuarioId, jogoId);
        }
        log.info("✚ ITEM salvo usuario={} jogo=\"{}\" status={} · evento JOGO_ADICIONADO no outbox",
                usuarioId, item.getTitulo(), item.getStatus());
        return item;
    }

    @Transactional
    public ItemBacklog alterarStatus(String usuarioId, Long itemId, StatusBacklog novoStatus) {
        ItemBacklog item = buscar(usuarioId, itemId);
        StatusBacklog anterior = item.alterarStatus(novoStatus);
        outbox.registrar(evento(TipoEvento.STATUS_ALTERADO, item, anterior, novoStatus));
        log.info("✎ STATUS usuario={} jogo=\"{}\" {} → {} · evento STATUS_ALTERADO no outbox",
                usuarioId, item.getTitulo(), anterior, novoStatus);
        return item;
    }

    @Transactional
    public void remover(String usuarioId, Long itemId) {
        ItemBacklog item = buscar(usuarioId, itemId);
        repository.delete(item);
        outbox.registrar(evento(TipoEvento.JOGO_REMOVIDO, item, item.getStatus(), null));
        log.info("✖ ITEM removido usuario={} jogo=\"{}\" · evento JOGO_REMOVIDO no outbox",
                usuarioId, item.getTitulo());
    }

    @Transactional(readOnly = true)
    public List<ItemBacklog> listar(String usuarioId, StatusBacklog status) {
        return status == null
                ? repository.findByUsuarioIdOrderByIdAsc(usuarioId)
                : repository.findByUsuarioIdAndStatusOrderByIdAsc(usuarioId, status);
    }

    private ItemBacklog buscar(String usuarioId, Long itemId) {
        return repository.findByIdAndUsuarioId(itemId, usuarioId)
                .orElseThrow(() -> new ItemNaoEncontradoException(usuarioId, itemId));
    }

    private static BacklogEvento evento(TipoEvento tipo, ItemBacklog item, StatusBacklog anterior,
                                        StatusBacklog novo) {
        return BacklogEvento.novo(tipo, item.getUsuarioId(), item.getId(), item.getJogoId(), item.getTitulo(),
                item.getDuracaoMediaHoras(), anterior, novo);
    }
}
