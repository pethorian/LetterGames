package br.com.lettergames.backlog.dominio;

import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.catalogo.CatalogoIndisponivelException;
import br.com.lettergames.backlog.eventos.EventoOutbox;
import br.com.lettergames.backlog.eventos.EventoOutboxRepository;
import br.com.lettergames.backlog.eventos.OutboxRegistrador;
import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Service + JPA + outbox reais sobre H2; só o catálogo é mockado. */
@SpringBootTest
@ActiveProfiles("test")
class BacklogServiceTest {

    private static final JogoDTO HOLLOW_KNIGHT = new JogoDTO(42L, "Hollow Knight", "PC", "Metroidvania", 27);

    @Autowired
    private BacklogService service;

    @Autowired
    private ItemBacklogRepository itens;

    @Autowired
    private EventoOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CatalogoClient catalogo;

    @MockitoSpyBean
    private OutboxRegistrador outbox;

    @BeforeEach
    void preparar() {
        outboxRepository.deleteAll();
        itens.deleteAll();
        when(catalogo.buscarJogo(42L)).thenReturn(HOLLOW_KNIGHT);
        MDC.put(Correlacao.MDC_KEY, "teste-01");
    }

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void adicionarGravaSnapshotDoCatalogoEEventoNoOutbox() throws Exception {
        ItemBacklog item = service.adicionar("joao", 42L);

        ItemBacklog salvo = itens.findById(item.getId()).orElseThrow();
        assertThat(salvo.getTitulo()).isEqualTo("Hollow Knight");
        assertThat(salvo.getDuracaoMediaHoras()).isEqualTo(27);
        assertThat(salvo.getStatus()).isEqualTo(StatusBacklog.QUERO_JOGAR);

        EventoOutbox linha = unicaLinhaDoOutbox();
        assertThat(linha.getTopico()).isEqualTo(Topicos.BACKLOG_EVENTOS);
        assertThat(linha.getChave()).isEqualTo("joao");
        assertThat(linha.getTipo()).isEqualTo(TipoEvento.JOGO_ADICIONADO);
        assertThat(linha.getCorrelationId()).isEqualTo("teste-01");
        assertThat(linha.getPublicadoEm()).isNull();

        BacklogEvento evento = objectMapper.readValue(linha.getPayload(), BacklogEvento.class);
        assertThat(evento.eventId()).isEqualTo(linha.getEventId());
        assertThat(evento.itemId()).isEqualTo(item.getId());
        assertThat(evento.jogoId()).isEqualTo(42L);
        assertThat(evento.titulo()).isEqualTo("Hollow Knight");
        assertThat(evento.duracaoMediaHoras()).isEqualTo(27);
        assertThat(evento.statusAnterior()).isNull();
        assertThat(evento.statusNovo()).isEqualTo(StatusBacklog.QUERO_JOGAR);
        assertThat(linha.getPayload()).contains("\"ocorridoEm\":\"" + evento.ocorridoEm() + "\"");
    }

    @Test
    void jogoDuplicadoNoBacklogEConflitoSemConsultarOCatalogoDeNovo() {
        service.adicionar("joao", 42L);

        assertThatThrownBy(() -> service.adicionar("joao", 42L)).isInstanceOf(JogoJaNoBacklogException.class);

        verify(catalogo, times(1)).buscarJogo(42L);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void adicaoConcorrenteDoMesmoJogoViraConflitoPelaUnique() {
        // Outra requisição grava o mesmo jogo enquanto esta consulta o catálogo (depois do exists).
        when(catalogo.buscarJogo(42L)).thenAnswer(invocacao -> {
            itens.saveAndFlush(ItemBacklog.novo("joao", 42L, "Hollow Knight", 27));
            return HOLLOW_KNIGHT;
        });

        assertThatThrownBy(() -> service.adicionar("joao", 42L)).isInstanceOf(JogoJaNoBacklogException.class);

        assertThat(itens.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void outraViolacaoDeIntegridadeNaoViraJogoJaNoBacklog() {
        when(catalogo.buscarJogo(7L)).thenReturn(new JogoDTO(7L, "x".repeat(300), "PC", "Plataforma", 8));

        assertThatThrownBy(() -> service.adicionar("joao", 7L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(JogoJaNoBacklogException.class);
        assertThat(itens.count()).isZero();
    }

    @Test
    void reconheceAUniquePeloNomeExtraidoPeloHibernate() {
        // Formato do Postgres: o nome vem limpo em getConstraintName(), sem aparecer na mensagem.
        assertThat(BacklogService.violouUnicidadeUsuarioJogo(violacao("uk_itens_backlog_usuario_jogo"))).isTrue();
        assertThat(BacklogService.violouUnicidadeUsuarioJogo(violacao("itens_backlog_pkey"))).isFalse();
        assertThat(BacklogService.violouUnicidadeUsuarioJogo(violacao(null))).isFalse();
    }

    private static DataIntegrityViolationException violacao(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("erro"), constraint));
    }

    @Test
    void mesmoJogoPodeEstarNoBacklogDeOutroUsuario() {
        service.adicionar("joao", 42L);
        service.adicionar("maria", 42L);

        assertThat(itens.count()).isEqualTo(2);
    }

    @Test
    void catalogoIndisponivelNaoGravaItemNemEvento() {
        when(catalogo.buscarJogo(7L)).thenThrow(new CatalogoIndisponivelException("fora"));

        assertThatThrownBy(() -> service.adicionar("joao", 7L)).isInstanceOf(CatalogoIndisponivelException.class);

        assertThat(itens.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void alterarStatusGeraStatusAlteradoComAnteriorENovo() throws Exception {
        ItemBacklog item = service.adicionar("joao", 42L);
        outboxRepository.deleteAll();

        ItemBacklog alterado = service.alterarStatus("joao", item.getId(), StatusBacklog.JOGANDO);

        assertThat(alterado.getStatus()).isEqualTo(StatusBacklog.JOGANDO);
        assertThat(itens.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(StatusBacklog.JOGANDO);
        BacklogEvento evento = objectMapper.readValue(unicaLinhaDoOutbox().getPayload(), BacklogEvento.class);
        assertThat(evento.tipo()).isEqualTo(TipoEvento.STATUS_ALTERADO);
        assertThat(evento.statusAnterior()).isEqualTo(StatusBacklog.QUERO_JOGAR);
        assertThat(evento.statusNovo()).isEqualTo(StatusBacklog.JOGANDO);
    }

    @Test
    void transicaoInvalidaNaoAlteraItemNemGeraEvento() {
        ItemBacklog item = service.adicionar("joao", 42L);

        assertThatThrownBy(() -> service.alterarStatus("joao", item.getId(), StatusBacklog.ZERADO))
                .isInstanceOf(TransicaoInvalidaException.class);

        assertThat(itens.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(StatusBacklog.QUERO_JOGAR);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void itemDeOutroUsuarioNaoEEncontrado() {
        ItemBacklog item = service.adicionar("joao", 42L);

        assertThatThrownBy(() -> service.alterarStatus("maria", item.getId(), StatusBacklog.JOGANDO))
                .isInstanceOf(ItemNaoEncontradoException.class);
        assertThatThrownBy(() -> service.remover("maria", item.getId()))
                .isInstanceOf(ItemNaoEncontradoException.class);
    }

    @Test
    void removerGeraJogoRemovidoComOStatusDoMomento() throws Exception {
        ItemBacklog item = service.adicionar("joao", 42L);
        service.alterarStatus("joao", item.getId(), StatusBacklog.JOGANDO);
        outboxRepository.deleteAll();

        service.remover("joao", item.getId());

        assertThat(itens.existsById(item.getId())).isFalse();
        BacklogEvento evento = objectMapper.readValue(unicaLinhaDoOutbox().getPayload(), BacklogEvento.class);
        assertThat(evento.tipo()).isEqualTo(TipoEvento.JOGO_REMOVIDO);
        assertThat(evento.itemId()).isEqualTo(item.getId());
        assertThat(evento.statusAnterior()).isEqualTo(StatusBacklog.JOGANDO);
        assertThat(evento.statusNovo()).isNull();
    }

    /** O spy fica atrás do proxy transacional: o stub vai no alvo, senão o MANDATORY dispara já no when(). */
    private void outboxFalha() {
        OutboxRegistrador spy = AopTestUtils.getUltimateTargetObject(outbox);
        doThrow(new IllegalStateException("outbox fora")).when(spy).registrar(any());
    }

    @Test
    void falhaAoGravarOOutboxDesfazOItem() {
        outboxFalha();

        assertThatThrownBy(() -> service.adicionar("joao", 42L)).hasMessage("outbox fora");

        assertThat(itens.count()).as("item não pode ficar sem o evento").isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void falhaAoGravarOOutboxDesfazAAlteracaoDeStatus() {
        ItemBacklog item = service.adicionar("joao", 42L);
        outboxFalha();

        assertThatThrownBy(() -> service.alterarStatus("joao", item.getId(), StatusBacklog.JOGANDO))
                .hasMessage("outbox fora");

        assertThat(itens.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(StatusBacklog.QUERO_JOGAR);
    }

    @Test
    void outboxSoAceitaGravarDentroDeUmaTransacao() {
        BacklogEvento evento = BacklogEvento.novo(TipoEvento.JOGO_ADICIONADO, "joao", 1L, 42L, "Hollow Knight",
                27, null, StatusBacklog.QUERO_JOGAR);

        assertThatThrownBy(() -> outbox.registrar(evento)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void listarFiltraPorStatus() {
        when(catalogo.buscarJogo(7L)).thenReturn(new JogoDTO(7L, "Celeste", "PC", "Plataforma", 8));
        ItemBacklog hollow = service.adicionar("joao", 42L);
        service.adicionar("joao", 7L);
        service.alterarStatus("joao", hollow.getId(), StatusBacklog.JOGANDO);

        assertThat(service.listar("joao", null)).hasSize(2);
        assertThat(service.listar("joao", StatusBacklog.JOGANDO)).extracting(ItemBacklog::getTitulo)
                .containsExactly("Hollow Knight");
        assertThat(service.listar("maria", null)).isEmpty();
    }

    private EventoOutbox unicaLinhaDoOutbox() {
        List<EventoOutbox> linhas = outboxRepository.findAll();
        assertThat(linhas).hasSize(1);
        return linhas.get(0);
    }
}
