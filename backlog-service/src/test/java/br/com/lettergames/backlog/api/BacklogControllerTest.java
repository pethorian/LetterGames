package br.com.lettergames.backlog.api;

import br.com.lettergames.backlog.catalogo.CatalogoBreakerAbertoException;
import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.catalogo.CatalogoIndisponivelException;
import br.com.lettergames.backlog.catalogo.CatalogoRequisicaoInvalidaException;
import br.com.lettergames.backlog.catalogo.JogoInexistenteNoCatalogoException;
import br.com.lettergames.backlog.dominio.ItemBacklogRepository;
import br.com.lettergames.backlog.eventos.EventoOutboxRepository;
import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API ponta a ponta (filtro, controller, advice, service, H2) com o catálogo mockado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BacklogControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ItemBacklogRepository itens;

    @Autowired
    private EventoOutboxRepository outbox;

    @MockitoBean
    private CatalogoClient catalogo;

    @BeforeEach
    void preparar() {
        outbox.deleteAll();
        itens.deleteAll();
        when(catalogo.buscarJogo(42L)).thenReturn(new JogoDTO(42L, "Hollow Knight", "PC", "Metroidvania", 27));
    }

    @Test
    void adicionaJogoERespondeCriadoComLocationEOCorrelationId() throws Exception {
        mvc.perform(post("/usuarios/joao/backlog").header(Correlacao.HEADER_HTTP, "demo-02")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 42}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/usuarios/joao/backlog/\\d+")))
                .andExpect(header().string(Correlacao.HEADER_HTTP, "demo-02"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.usuarioId").value("joao"))
                .andExpect(jsonPath("$.jogoId").value(42))
                .andExpect(jsonPath("$.titulo").value("Hollow Knight"))
                .andExpect(jsonPath("$.duracaoMediaHoras").value(27))
                .andExpect(jsonPath("$.status").value("QUERO_JOGAR"))
                .andExpect(jsonPath("$.criadoEm").isString())
                .andExpect(jsonPath("$.atualizadoEm").isString());
    }

    @Test
    void geraCorrelationIdCurtoQuandoAusente() throws Exception {
        mvc.perform(get("/usuarios/joao/backlog"))
                .andExpect(status().isOk())
                .andExpect(header().string(Correlacao.HEADER_HTTP, matchesPattern("[0-9a-f-]{8}")));
    }

    @Test
    void jogoInexistenteNoCatalogoE422() throws Exception {
        when(catalogo.buscarJogo(999L)).thenThrow(new JogoInexistenteNoCatalogoException(999L));

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 999}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("JOGO_INEXISTENTE"))
                .andExpect(jsonPath("$.mensagem").value("Jogo 999 não existe no catálogo"));
    }

    @Test
    void jogoJaNoBacklogE409() throws Exception {
        adicionar(42);

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 42}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("JOGO_JA_NO_BACKLOG"));
    }

    @Test
    void catalogoIndisponivelE503ENaoGravaOItem() throws Exception {
        when(catalogo.buscarJogo(7L)).thenThrow(new CatalogoIndisponivelException("catálogo respondeu 500"));

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 7}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("CATALOGO_INDISPONIVEL"))
                .andExpect(jsonPath("$.mensagem")
                        .value("Catálogo indisponível no momento, tente novamente em instantes"));

        assertThat(itens.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void catalogoRecusandoARequisicaoE502() throws Exception {
        when(catalogo.buscarJogo(7L)).thenThrow(new CatalogoRequisicaoInvalidaException(400));

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 7}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("CATALOGO_REQUISICAO_INVALIDA"));

        assertThat(itens.count()).isZero();
    }

    @Test
    void breakerAbertoTambemE503() throws Exception {
        when(catalogo.buscarJogo(7L)).thenThrow(new CatalogoBreakerAbertoException(null));

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": 7}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("CATALOGO_INDISPONIVEL"));
    }

    @Test
    void corpoInvalidoE400() throws Exception {
        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(startsWith("jogoId: "))); // texto do validador depende do locale

        mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON).content("{\"jogoId\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO"));

        mvc.perform(patch("/usuarios/joao/backlog/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INVENTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO"));

        mvc.perform(get("/usuarios/joao/backlog").param("status", "INVENTADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO"));
    }

    @Test
    void alteraStatusE200() throws Exception {
        long itemId = adicionar(42);

        mvc.perform(patch("/usuarios/joao/backlog/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"JOGANDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.status").value("JOGANDO"));
    }

    @Test
    void transicaoInvalidaE422() throws Exception {
        long itemId = adicionar(42);

        mvc.perform(patch("/usuarios/joao/backlog/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ZERADO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"))
                .andExpect(jsonPath("$.mensagem").value(
                        "Transição inválida: QUERO_JOGAR → ZERADO. A partir de QUERO_JOGAR só é permitido: "
                                + "[JOGANDO, ABANDONADO]"));
    }

    @Test
    void itemInexistenteOuDeOutroUsuarioE404() throws Exception {
        long itemId = adicionar(42);

        mvc.perform(patch("/usuarios/maria/backlog/" + itemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"JOGANDO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("ITEM_NAO_ENCONTRADO"));
        mvc.perform(delete("/usuarios/joao/backlog/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listaComFiltroDeStatus() throws Exception {
        when(catalogo.buscarJogo(7L)).thenReturn(new JogoDTO(7L, "Celeste", "PC", "Plataforma", 8));
        long hollow = adicionar(42);
        adicionar(7);
        mvc.perform(patch("/usuarios/joao/backlog/" + hollow).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"JOGANDO\"}")).andExpect(status().isOk());

        mvc.perform(get("/usuarios/joao/backlog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/usuarios/joao/backlog").param("status", "JOGANDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Hollow Knight"));
    }

    @Test
    void removeE204() throws Exception {
        long itemId = adicionar(42);

        mvc.perform(delete("/usuarios/joao/backlog/" + itemId)).andExpect(status().isNoContent());

        mvc.perform(get("/usuarios/joao/backlog")).andExpect(jsonPath("$", hasSize(0)));
    }

    private long adicionar(long jogoId) throws Exception {
        String corpo = mvc.perform(post("/usuarios/joao/backlog").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jogoId\": " + jogoId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }
}
