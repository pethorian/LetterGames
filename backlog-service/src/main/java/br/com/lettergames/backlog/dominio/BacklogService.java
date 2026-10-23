package br.com.lettergames.backlog.dominio;

import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.eventos.OutboxRegistrador;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Casos de uso do backlog. Toda alteração grava o item e o evento no outbox na mesma transação:
 * ou os dois ficam, ou nenhum (sem dual write com o Kafka).
 */
@Service
public class BacklogService {

    private static final Logger log = LoggerFactory.getLogger(BacklogService.class);

    /** Unique de V1__cria_itens_backlog.sql. */
    static final String CONSTRAINT_USUARIO_JOGO = "uk_itens_backlog_usuario_jogo";

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
            // Qualquer outra violação (coluna nula, valor grande demais) é bug e propaga.
            if (violouUnicidadeUsuarioJogo(e)) {
                throw new JogoJaNoBacklogException(usuarioId, jogoId);
            }
            throw e;
        }
        log.info("✚ ITEM salvo usuario={} jogo=\"{}\" status={} · evento JOGO_ADICIONADO no outbox",
                usuarioId, item.getTitulo(), item.getStatus());
        return item;
    }

    @Transactional
    public ItemBacklog alterarStatus(String usuarioId, Long itemId, StatusBacklog novoStatus) {
        ItemBacklog item = buscarParaAtualizar(usuarioId, itemId);
        StatusBacklog anterior = item.alterarStatus(novoStatus);
        outbox.registrar(evento(TipoEvento.STATUS_ALTERADO, item, anterior, novoStatus));
        log.info("✎ STATUS usuario={} jogo=\"{}\" {} → {} · evento STATUS_ALTERADO no outbox",
                usuarioId, item.getTitulo(), anterior, novoStatus);
        return item;
    }

    @Transactional
    public void remover(String usuarioId, Long itemId) {
        ItemBacklog item = buscarParaAtualizar(usuarioId, itemId);
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

    // Com o lock, o evento (e o criado_em do outbox) só é montado depois do commit da transação anterior.
    private ItemBacklog buscarParaAtualizar(String usuarioId, Long itemId) {
        return repository.findByIdAndUsuarioIdParaAtualizar(itemId, usuarioId)
                .orElseThrow(() -> new ItemNaoEncontradoException(usuarioId, itemId));
    }

    /** O Hibernate extrai o nome da constraint (Postgres e H2); a mensagem do driver é o plano B. */
    static boolean violouUnicidadeUsuarioJogo(Throwable e) {
        for (Throwable causa = e; causa != null && causa != causa.getCause(); causa = causa.getCause()) {
            String nome = causa instanceof ConstraintViolationException violacao ? violacao.getConstraintName() : null;
            if (contemConstraint(nome) || contemConstraint(causa.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contemConstraint(String texto) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(CONSTRAINT_USUARIO_JOGO);
    }

    private static BacklogEvento evento(TipoEvento tipo, ItemBacklog item, StatusBacklog anterior,
                                        StatusBacklog novo) {
        return BacklogEvento.novo(tipo, item.getUsuarioId(), item.getId(), item.getJogoId(), item.getTitulo(),
                item.getDuracaoMediaHoras(), anterior, novo);
    }
}
