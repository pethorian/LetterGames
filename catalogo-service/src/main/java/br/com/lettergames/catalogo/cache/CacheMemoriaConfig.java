package br.com.lettergames.catalogo.cache;

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
    CacheManager cacheManager() {
        return new CacheManagerComLog(new ConcurrentMapCacheManager("jogos"));
    }
}
