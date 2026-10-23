package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * Double delete: simula a corrida do cache-aside regravando o valor antigo logo após o PUT
 * e verifica que o segundo evict atrasado o remove.
 */
@SpringBootTest(properties = "catalogo.cache.segundo-evict=200ms")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegundoEvictTest {

    private static final String CORPO = """
            {"titulo":"Hollow Knight","plataforma":"PC","genero":"Metroidvania","duracaoMediaHoras":27}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    CacheManager cacheManager;

    @Test
    void segundoEvictVarreOValorAntigoRegravadoDepoisDoPut() throws Exception {
        Cache cache = cacheManager.getCache("jogos");
        mvc.perform(get("/jogos/2")).andExpect(status().isOk());
        mvc.perform(put("/jogos/2").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
        assertThat(cache.get(2L)).isNull();

        // GET concorrente que leu antes do commit grava o valor velho depois do 1º evict.
        cache.put(2L, "valor velho");
        assertThat(cache.get(2L)).isNotNull();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(cache.get(2L)).isNull());
    }
}
