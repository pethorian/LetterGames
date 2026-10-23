package br.com.lettergames.catalogo.cache;

import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Decorador que loga HIT e EVICT para a demo e delega todo o resto.
 * O MISS é logado dentro do método cacheado, que só executa quando não há valor.
 */
class CacheComLog implements Cache {

    private static final Logger log = LoggerFactory.getLogger(CacheComLog.class);

    private final Cache delegado;

    CacheComLog(Cache delegado) {
        this.delegado = delegado;
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
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean removido = delegado.evictIfPresent(key);
        log.info("🗑 CACHE EVICT {}", chave(key));
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
