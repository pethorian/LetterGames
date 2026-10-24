package br.com.lettergames.catalogo.cache;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cache em memória para testes (perfil B, sem Docker). Sem TTL: não é usado em produção. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "catalogo.cache.tipo", havingValue = "memoria")
public class CacheMemoriaConfig {

    @Bean
    CacheManager cacheManager(@Value("${catalogo.cache.segundo-evict:0}") Duration segundoEvict) {
        return new CacheManagerComLog(new ConcurrentMapCacheManager("jogos"), segundoEvict);
    }
}
