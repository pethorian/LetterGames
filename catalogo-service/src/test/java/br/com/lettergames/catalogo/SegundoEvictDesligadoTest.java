package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Com {@code catalogo.cache.segundo-evict=0} o double delete fica desligado. */
@SpringBootTest(properties = "catalogo.cache.segundo-evict=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegundoEvictDesligadoTest {

    private static final String CORPO = """
            {"titulo":"Jogo sem segundo evict","plataforma":"PC","genero":"Teste","duracaoMediaHoras":5}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    CacheManager cacheManager;

    @Test
    void semSegundoEvictOValorRegravadoPermanece() throws Exception {
        long id = JogosDeTeste.criar(mvc, CORPO);
        Cache cache = cacheManager.getCache("jogos");
        mvc.perform(put("/jogos/" + id).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
        cache.put(id, "valor velho");

        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(cache.get(id)).isNotNull());
    }
}
