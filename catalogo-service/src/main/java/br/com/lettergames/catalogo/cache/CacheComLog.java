package br.com.lettergames.catalogo.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Decorador que loga HIT e EVICT para a demo e delega todo o resto.
 * O MISS é logado dentro do método cacheado, que só executa quando não há valor.
 * Com agendador, todo evict é repetido após o atraso (double delete).
 */
class CacheComLog implements Cache {

    private static final Logger log = LoggerFactory.getLogger(CacheComLog.class);

    private final Cache delegado;
    private final ScheduledExecutorService agendador;
    private final long atrasoMs;

    /** Sem agendador (null) não há segundo evict. */
    CacheComLog(Cache delegado, ScheduledExecutorService agendador, long atrasoMs) {
        this.delegado = delegado;
        this.agendador = agendador;
        this.atrasoMs = atrasoMs;
    }

    /** Double delete: repete o evict depois de um atraso, varrendo valor antigo regravado por um GET concorrente. */
    private void agendarSegundoEvict(Object key) {
        if (agendador == null) {
            return;
        }
        try {
            agendador.schedule(() -> {
                try {
                    delegado.evict(key);
                    log.info("🗑 CACHE EVICT (2º, atrasado) {}", chave(key));
                } catch (RuntimeException e) {
                    log.warn("⚠ CACHE INDISPONÍVEL (2º evict {}): {}", chave(key), e.getMessage());
                }
            }, atrasoMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.warn("⚠ 2º evict não agendado {}: {}", chave(key), e.getMessage());
        }
    }

    private String chave(Object key) {
        return getName() + "::" + key;
    }

    @Override
    public String getName() {
        return delegado.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegado.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper valor = delegado.get(key);
        if (valor != null) {
            log.info("✔ CACHE HIT {}", chave(key));
        }
        return valor;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T valor = delegado.get(key, type);
        if (valor != null) {
            log.info("✔ CACHE HIT {}", chave(key));
        }
        return valor;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return delegado.get(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        delegado.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        return delegado.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        delegado.evict(key);
        log.info("🗑 CACHE EVICT {}", chave(key));
        agendarSegundoEvict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean removido = delegado.evictIfPresent(key);
        log.info("🗑 CACHE EVICT {}", chave(key));
        agendarSegundoEvict(key);
        return removido;
    }

    @Override
    public void clear() {
        delegado.clear();
    }

    @Override
    public boolean invalidate() {
        return delegado.invalidate();
    }
}
