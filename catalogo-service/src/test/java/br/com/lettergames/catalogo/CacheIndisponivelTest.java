package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * Simula o Redis fora do ar: o cache lança exceção em get/put/evict e o serviço
 * precisa continuar respondendo pelo banco (CacheErrorHandler integrado ao fluxo real).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CacheIndisponivelTest.CacheQuebradoConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class CacheIndisponivelTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getContinuaRespondendoPeloBancoERegistraWarn(CapturedOutput saida) throws Exception {
        mvc.perform(get("/jogos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")));

        assertThat(saida.getOut()).contains("WARN").contains("get jogos::1").contains("seguindo sem cache");
        assertThat(saida.getOut()).contains("put jogos::1");
    }

    @Test
    void escritasTambemSobrevivemAoCacheForaDoAr() throws Exception {
        String corpo = """
                {"titulo":"Disco Elysium","plataforma":"Xbox","genero":"RPG","duracaoMediaHoras":30}""";
        String criado = mvc.perform(post("/jogos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = criado.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(put("/jogos/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        mvc.perform(delete("/jogos/" + id)).andExpect(status().isNoContent());
    }

    @TestConfiguration
    static class CacheQuebradoConfig {

        @Bean
        @Primary
        CacheManager cacheManagerQuebrado() {
            Cache quebrado = new CacheQuebrado();
            return new CacheManager() {
                @Override
                public Cache getCache(String name) {
                    return quebrado;
                }

                @Override
                public java.util.Collection<String> getCacheNames() {
                    return List.of("jogos");
                }
            };
        }
    }

    static class CacheQuebrado implements Cache {

        private static RuntimeException erro() {
            return new IllegalStateException("Redis fora do ar");
        }

        @Override
        public String getName() {
            return "jogos";
        }

        @Override
        public Object getNativeCache() {
            throw erro();
        }

        @Override
        public ValueWrapper get(Object key) {
            throw erro();
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            throw erro();
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw erro();
        }

        @Override
        public void put(Object key, Object value) {
            throw erro();
        }

        @Override
        public void evict(Object key) {
            throw erro();
        }

        @Override
        public void clear() {
            throw erro();
        }
    }
}
