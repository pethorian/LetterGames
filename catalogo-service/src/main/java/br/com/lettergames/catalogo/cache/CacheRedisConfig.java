package br.com.lettergames.catalogo.cache;

import br.com.lettergames.contratos.catalogo.JogoDTO;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/** Cache em Redis (padrão). Nos testes usa-se {@code catalogo.cache.tipo=memoria}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "catalogo.cache.tipo", havingValue = "redis", matchIfMissing = true)
public class CacheRedisConfig {

    /** Pública e estática para o teste de TTL instanciar a configuração sem conectar no Redis. */
    public static RedisCacheConfiguration configuracao(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(JogoDTO.class)));
    }

    @Bean
    CacheManager cacheManager(RedisConnectionFactory conexao, @Value("${catalogo.cache.ttl}") Duration ttl,
            @Value("${catalogo.cache.segundo-evict:500ms}") Duration segundoEvict) {
        RedisCacheManager redis = RedisCacheManager.builder(conexao)
                .cacheDefaults(configuracao(ttl))
                .build();
        // Não é um bean, então o Spring não chama o afterPropertiesSet por nós.
        redis.afterPropertiesSet();
        return new CacheManagerComLog(redis, segundoEvict);
    }
}
