package br.com.lettergames.catalogo.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Habilita o Spring Cache. A ordem deixa o advice do cache por fora da transação: o
 * {@code @CacheEvict} roda depois do commit, o que fecha a janela "evict antes do commit".
 * A corrida clássica do cache-aside continua: o GET dá MISS e lê o valor antigo, o PUT commita
 * e faz o evict, e só então o GET grava o valor antigo no cache. Resultado: dado velho até o TTL,
 * que é o limite do dano. Mitigação: segundo evict atrasado ({@code catalogo.cache.segundo-evict}).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 10)
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Cache é opcional (ADR-004): qualquer falha vira WARN e a chamada segue pelo banco. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                avisar("get", cache, key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                avisar("put", cache, key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                avisar("evict", cache, key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                avisar("clear", cache, "*", e);
            }
        };
    }

    private static void avisar(String operacao, Cache cache, Object key, RuntimeException e) {
        log.warn("⚠ CACHE INDISPONÍVEL ({} {}::{}): {} → seguindo sem cache",
                operacao, cache.getName(), key, e.getMessage());
    }
}
