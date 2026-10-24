package br.com.lettergames.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lettergames.catalogo.cache.CacheRedisConfig;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.test.context.ActiveProfiles;

/** Instancia a configuração do Redis sem conectar: só inspeciona TTL, nulos e serialização. */
@SpringBootTest
@ActiveProfiles("test")
class CacheRedisConfigTest {

    @Value("${catalogo.cache.ttl}")
    Duration ttlConfigurado;

    @Test
    void ttlPadraoEhDeDezMinutos() {
        assertThat(ttlConfigurado).isEqualTo(Duration.ofMinutes(10));

        RedisCacheConfiguration config = CacheRedisConfig.configuracao(ttlConfigurado);

        assertThat(config.getTtlFunction().getTimeToLive("1", null)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void naoCacheiaNulosEUsaChaveJogosDoisPontos() {
        RedisCacheConfiguration config = CacheRedisConfig.configuracao(Duration.ofMinutes(10));

        assertThat(config.getAllowCacheNullValues()).isFalse();
        assertThat(config.getKeyPrefixFor("jogos")).isEqualTo("jogos::");
    }

    @Test
    void valorEhSerializadoComoJson() {
        RedisCacheConfiguration config = CacheRedisConfig.configuracao(Duration.ofMinutes(10));
        JogoDTO dto = new JogoDTO(42L, "Hades", "PC", "Roguelike", 43);

        ByteBuffer bytes = config.getValueSerializationPair().write(dto);
        String texto = StandardCharsets.UTF_8.decode(bytes).toString();

        assertThat(texto).contains("\"id\":42", "\"titulo\":\"Hades\"", "\"duracaoMediaHoras\":43");
        assertThat(config.getValueSerializationPair().read(ByteBuffer.wrap(texto.getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(dto);
    }
}
