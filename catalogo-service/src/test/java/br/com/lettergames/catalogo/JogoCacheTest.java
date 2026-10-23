package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lettergames.catalogo.dominio.JogoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Prova o cache-aside: contamos as idas ao repositório (o "banco") em vez de confiar no log. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JogoCacheTest {

    private static final String CORPO = """
            {"titulo":"Disco Elysium","plataforma":"Xbox","genero":"RPG","duracaoMediaHoras":30}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CacheManager cacheManager;

    @MockitoSpyBean
    JogoRepository repositorio;

    @BeforeEach
    void limpa() {
        cacheManager.getCache("jogos").clear();
        clearInvocations(repositorio);
    }

    private long criar() throws Exception {
        String corpo = mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    @Test
    void segundoGetNaoConsultaORepositorio() throws Exception {
        mvc.perform(get("/jogos/1")).andExpect(status().isOk());
        mvc.perform(get("/jogos/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")));

        verify(repositorio, times(1)).findById(1L);
        assertThat(cacheManager.getCache("jogos").get(1L)).isNotNull();
    }

    @Test
    void criarNaoPopulaOCache() throws Exception {
        long id = criar();

        assertThat(cacheManager.getCache("jogos").get(id)).isNull();
        mvc.perform(get("/jogos/" + id)).andExpect(status().isOk());
        verify(repositorio, times(1)).findById(id);
    }

    @Test
    void putInvalidaOCacheEOProximoGetVeOValorNovo() throws Exception {
        long id = criar();
        mvc.perform(get("/jogos/" + id)).andExpect(jsonPath("$.titulo", is("Disco Elysium")));
        assertThat(cacheManager.getCache("jogos").get(id)).isNotNull();

        String novo = """
                {"titulo":"Disco Elysium - Final Cut","plataforma":"PC","genero":"RPG","duracaoMediaHoras":35}""";
        mvc.perform(put("/jogos/" + id).contentType(MediaType.APPLICATION_JSON).content(novo))
                .andExpect(status().isOk());
        assertThat(cacheManager.getCache("jogos").get(id)).isNull();

        clearInvocations(repositorio);
        mvc.perform(get("/jogos/" + id)).andExpect(jsonPath("$.titulo", is("Disco Elysium - Final Cut")));
        mvc.perform(get("/jogos/" + id)).andExpect(jsonPath("$.titulo", is("Disco Elysium - Final Cut")));
        verify(repositorio, times(1)).findById(id);
    }

    @Test
    void deleteInvalidaOCache() throws Exception {
        long id = criar();
        mvc.perform(get("/jogos/" + id)).andExpect(status().isOk());
        assertThat(cacheManager.getCache("jogos").get(id)).isNotNull();

        mvc.perform(delete("/jogos/" + id)).andExpect(status().isNoContent());

        assertThat(cacheManager.getCache("jogos").get(id)).isNull();
        mvc.perform(get("/jogos/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void listagemNaoEhCacheada() throws Exception {
        mvc.perform(get("/jogos").param("plataforma", "PC")).andExpect(status().isOk());
        mvc.perform(get("/jogos").param("plataforma", "PC")).andExpect(status().isOk());

        verify(repositorio, times(2)).findByPlataformaIgnoreCaseOrderById("PC");
    }
}
