package br.com.lettergames.catalogo;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lettergames.contratos.Correlacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JogoControllerTest {

    private static final String CORPO_VALIDO = """
            {"titulo":"Disco Elysium","plataforma":"Xbox","genero":"RPG","duracaoMediaHoras":30}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private long criarJogo() throws Exception {
        String resposta = mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode no = json.readTree(resposta);
        return no.get("id").asLong();
    }

    @Test
    void seedDevolveHollowKnightNoId1() throws Exception {
        mvc.perform(get("/jogos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")))
                .andExpect(jsonPath("$.plataforma", is("PC")))
                .andExpect(jsonPath("$.genero", is("Metroidvania")))
                .andExpect(jsonPath("$.duracaoMediaHoras", is(27)));
    }

    @Test
    void criarDevolve201ComLocationEIdentityContinuaDoSeed() throws Exception {
        mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/jogos/\\d+")))
                .andExpect(jsonPath("$.id", greaterThanOrEqualTo(11)))
                .andExpect(jsonPath("$.titulo", is("Disco Elysium")));
    }

    @Test
    void criarComCamposInvalidosDevolve400() throws Exception {
        String invalido = """
                {"titulo":" ","plataforma":"","genero":"RPG","duracaoMediaHoras":0}""";
        mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", is("VALIDACAO")));
    }

    @Test
    void buscarInexistenteDevolve404ComCorpoPadronizado() throws Exception {
        mvc.perform(get("/jogos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro", is("JOGO_NAO_ENCONTRADO")))
                .andExpect(jsonPath("$.mensagem", is("Jogo 999999 não encontrado")));
    }

    @Test
    void atualizarDevolve200ComNovosValores() throws Exception {
        long id = criarJogo();
        String novo = """
                {"titulo":"Disco Elysium - Final Cut","plataforma":"PC","genero":"RPG","duracaoMediaHoras":35}""";
        mvc.perform(put("/jogos/" + id).contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Disco Elysium - Final Cut")))
                .andExpect(jsonPath("$.duracaoMediaHoras", is(35)));
    }

    @Test
    void atualizarInexistenteDevolve404() throws Exception {
        mvc.perform(put("/jogos/999999").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro", is("JOGO_NAO_ENCONTRADO")));
    }

    @Test
    void removerDevolve204EDepoisOJogoNaoExiste() throws Exception {
        long id = criarJogo();
        mvc.perform(delete("/jogos/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/jogos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void removerInexistenteDevolve404() throws Exception {
        mvc.perform(delete("/jogos/999999")).andExpect(status().isNotFound());
    }

    @Test
    void listarFiltraPorPlataforma() throws Exception {
        mvc.perform(get("/jogos").param("plataforma", "Switch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].titulo", hasItems("Celeste", "Zelda: Breath of the Wild")))
                .andExpect(jsonPath("$[*].plataforma", everyItem(is("Switch"))));
    }

    @Test
    void listarSemFiltroDevolveTodosOsJogosDoSeed() throws Exception {
        mvc.perform(get("/jogos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(greaterThanOrEqualTo(10))));
    }

    @Test
    void correlationIdEhGeradoQuandoAusenteEEcoadoQuandoPresente() throws Exception {
        mvc.perform(get("/jogos/1"))
                .andExpect(header().string(Correlacao.HEADER_HTTP, org.hamcrest.Matchers.matchesPattern("[0-9a-f]{8}")));
        mvc.perform(get("/jogos/1").header(Correlacao.HEADER_HTTP, "demo-02"))
                .andExpect(header().string(Correlacao.HEADER_HTTP, "demo-02"));
    }

    @Test
    void correlationIdInvalidoEhSubstituidoPorUmNovo() throws Exception {
        for (String invalido : new String[] {"abc\nINFO falso", "com espaco", "<script>", "x".repeat(65)}) {
            mvc.perform(get("/jogos/1").header(Correlacao.HEADER_HTTP, invalido))
                    .andExpect(header().string(Correlacao.HEADER_HTTP,
                            org.hamcrest.Matchers.matchesPattern("[0-9a-f]{8}")));
        }
        mvc.perform(get("/jogos/1").header(Correlacao.HEADER_HTTP, "x".repeat(64)))
                .andExpect(header().string(Correlacao.HEADER_HTTP, "x".repeat(64)));
    }

    @Test
    void stringsLongasDevolvem400EmVezDe500() throws Exception {
        String titulo201 = """
                {"titulo":"%s","plataforma":"PC","genero":"RPG","duracaoMediaHoras":30}""".formatted("t".repeat(201));
        String plataforma51 = """
                {"titulo":"X","plataforma":"%s","genero":"RPG","duracaoMediaHoras":30}""".formatted("p".repeat(51));
        String genero51 = """
                {"titulo":"X","plataforma":"PC","genero":"%s","duracaoMediaHoras":30}""".formatted("g".repeat(51));
        for (String corpo : new String[] {titulo201, plataforma51, genero51}) {
            mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro", is("VALIDACAO")));
        }
        String noLimite = """
                {"titulo":"%s","plataforma":"%s","genero":"%s","duracaoMediaHoras":30}"""
                .formatted("t".repeat(200), "p".repeat(50), "g".repeat(50));
        mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(noLimite))
                .andExpect(status().isCreated());
    }

    @Test
    void caracteresDeControleDevolvem400() throws Exception {
        String comQuebra = """
                {"titulo":"Jogo\\nINFO falso","plataforma":"PC","genero":"RPG","duracaoMediaHoras":30}""";
        String comTab = """
                {"titulo":"X","plataforma":"P\\tC","genero":"RPG","duracaoMediaHoras":30}""";
        String comCr = """
                {"titulo":"X","plataforma":"PC","genero":"RP\\rG","duracaoMediaHoras":30}""";
        for (String corpo : new String[] {comQuebra, comTab, comCr}) {
            mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro", is("VALIDACAO")));
            mvc.perform(put("/jogos/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro", is("VALIDACAO")));
        }
    }
}
