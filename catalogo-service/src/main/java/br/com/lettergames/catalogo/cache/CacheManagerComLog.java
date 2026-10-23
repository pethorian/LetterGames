package br.com.lettergames.catalogo.cache;

import java.util.Collection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Envolve qualquer CacheManager (Redis ou memória) para que seus caches loguem HIT/EVICT. */
public class CacheManagerComLog implements CacheManager {

    private final CacheManager delegado;

    public CacheManagerComLog(CacheManager delegado) {
        this.delegado = delegado;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegado.getCache(name);
        return cache == null ? null : new CacheComLog(cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegado.getCacheNames();
    }
}
