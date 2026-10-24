package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lettergames.catalogo.dominio.JogoRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Perfil A: prova o cache-aside com infraestrutura REAL (Postgres 16 + Redis 7.4 em Testcontainers),
 * sem H2 nem cache em memória. Excluído do perfil B pela tag {@code docker}.
 * O último teste derruba o Redis, por isso a ordem é fixa.
 */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "catalogo.cache.tipo=redis",
        "catalogo.cache.ttl=2s",
        "catalogo.cache.segundo-evict=0"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogoInfraRealTest {

    private static final String CHAVE = "jogos::1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    @MockitoSpyBean
    JogoRepository repositorio;

    @BeforeEach
    void limpa() {
        if (REDIS.isRunning()) {
            redis.delete(CHAVE);
        }
        clearInvocations(repositorio);
    }

    @Test
    @Order(1)
    void migrationsESeedRodamNoPostgresReal() throws Exception {
        mvc.perform(get("/jogos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")));
    }

    @Test
    @Order(2)
    void getGravaChaveNoRedisComTtlEOSegundoGetNaoVaiAoBanco() throws Exception {
        mvc.perform(get("/jogos/1")).andExpect(status().isOk());

        assertThat(redis.hasKey(CHAVE)).isTrue();
        Long ttl = redis.getExpire(CHAVE);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(2L);
        assertThat(redis.opsForValue().get(CHAVE)).contains("Hollow Knight");

        mvc.perform(get("/jogos/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")));
        verify(repositorio, times(1)).findById(1L);
    }

    @Test
    @Order(3)
    void aposOTtlAChaveSomeEOProximoGetVoltaAoBanco() throws Exception {
        mvc.perform(get("/jogos/1")).andExpect(status().isOk());
        assertThat(redis.hasKey(CHAVE)).isTrue();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertThat(redis.hasKey(CHAVE)).isFalse());

        clearInvocations(repositorio);
        mvc.perform(get("/jogos/1")).andExpect(status().isOk());
        verify(repositorio, times(1)).findById(1L);
        assertThat(redis.hasKey(CHAVE)).isTrue();
    }

    @Test
    @Order(4)
    void putRemoveAChaveDoRedis() throws Exception {
        mvc.perform(get("/jogos/1")).andExpect(status().isOk());
        assertThat(redis.hasKey(CHAVE)).isTrue();

        String corpo = """
                {"titulo":"Hollow Knight","plataforma":"PC","genero":"Metroidvania","duracaoMediaHoras":28}""";
        mvc.perform(put("/jogos/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        assertThat(redis.hasKey(CHAVE)).isFalse();
        mvc.perform(get("/jogos/1")).andExpect(jsonPath("$.duracaoMediaHoras", is(28)));
    }

    @Test
    @Order(5)
    void redisParadoGetContinua200PeloBanco() throws Exception {
        REDIS.stop();

        mvc.perform(get("/jogos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Hollow Knight")));
        verify(repositorio, times(1)).findById(1L);
    }
}
