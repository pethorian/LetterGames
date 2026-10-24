package br.com.lettergames.catalogo.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Envolve qualquer CacheManager (Redis ou memória) para que seus caches loguem HIT/EVICT.
 * Com {@code segundoEvict} positivo, cada evict é repetido após esse atraso; zero ou nulo desliga.
 */
public class CacheManagerComLog implements CacheManager, AutoCloseable {

    private final CacheManager delegado;
    private final ScheduledExecutorService agendador;
    private final long atrasoMs;

    public CacheManagerComLog(CacheManager delegado) {
        this(delegado, Duration.ZERO);
    }

    public CacheManagerComLog(CacheManager delegado, Duration segundoEvict) {
        this.delegado = delegado;
        this.atrasoMs = segundoEvict == null ? 0 : segundoEvict.toMillis();
        this.agendador = atrasoMs > 0
                ? Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "cache-segundo-evict");
                    t.setDaemon(true);
                    return t;
                })
                : null;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegado.getCache(name);
        return cache == null ? null : new CacheComLog(cache, agendador, atrasoMs);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegado.getCacheNames();
    }

    @Override
    public void close() {
        if (agendador != null) {
            agendador.shutdownNow();
        }
    }
}
