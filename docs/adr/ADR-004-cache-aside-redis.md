# ADR-004 — Cache-aside no catálogo com Redis, TTL e invalidação por evict

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa C (Dados/Cache)
- **Critério:** 4 — Cache

## Contexto

`GET /jogos/{id}` é chamado em toda adição ao backlog e em consultas, enquanto jogos quase nunca mudam. A leitura domina a escrita.

## Decisão

- **Cache-aside** no `catalogo-service` com Spring Cache + Redis: `@Cacheable("jogos")` em `JogoService.buscar`. Só a leitura popula o cache; criar um jogo não.
- **TTL de 10 minutos** (`catalogo.cache.ttl`), valores em JSON (`Jackson2JsonRedisSerializer<JogoDTO>`), chave `jogos::{id}`, **sem cachear nulos** (404 não fica em cache).
- **Invalidação explícita** com `@CacheEvict` em `PUT` e `DELETE`. O cache roda **por fora da transação** (`@EnableCaching(order = LOWEST_PRECEDENCE - 10)`), então o evict acontece **depois do commit**.
- **Segundo evict atrasado** (double delete): 500 ms depois do primeiro, a mesma chave é apagada de novo (`catalogo.cache.segundo-evict`; 0 desliga).
- **Fallback:** `CacheErrorHandler` loga `⚠ CACHE INDISPONÍVEL` e segue sem cache. Redis com timeouts de 500 ms e fora do health check. O cache é otimização, nunca dependência.
- Logs de demo: `✘ CACHE MISS`, `✔ CACHE HIT`, `🗑 CACHE EVICT` (decorador `CacheComLog`).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `@CachePut` no update (write-through) | Duas escritas concorrentes podem deixar a versão antiga por último no cache. O evict força a releitura da fonte da verdade. |
| Só TTL, sem invalidação | Até 10 min de dado velho depois de cada edição, sem necessidade. |
| Cache local (Caffeine) | Com mais de uma réplica, cada uma teria uma versão; a invalidação não se propaga. |
| Cache também no backlog | Dois caches para invalidar; o backlog já tem snapshot (ADR-001). |
| Write-behind | Risco de perder escrita; complexidade sem ganho aqui. |
| Versionar o valor no cache e recusar gravação mais velha | Elimina a corrida de verdade, mas exige script Lua/comparação no Redis. Desproporcional para o domínio. |

## A corrida que sobra (e como limitamos)

O evict depois do commit fecha a janela "evict antes do commit". Mas a corrida clássica do cache-aside continua possível:

1. GET dá MISS e lê do banco o valor **antigo**;
2. PUT commita e faz o evict;
3. o GET, atrasado, grava o valor antigo no cache.

O **segundo evict atrasado** apaga esse valor na grande maioria dos casos (`SegundoEvictTest`). No pior caso, o **TTL de 10 min é o limite do dano**.

## Consequências

- ✅ A 2ª leitura não toca o banco (visível no `redis-cli MONITOR` na demo e provado em `JogoCacheTest` e `CatalogoInfraRealTest` com Redis real).
- ✅ Redis fora não derruba o catálogo (`CacheIndisponivelTest` e `CatalogoInfraRealTest`, com o container parado).
- ⚠️ TTL de 10 min é um número de bom senso, **não medido**.
- ⚠️ Sem proteção contra *stampede*. Em escala: `@Cacheable(sync = true)` ou TTL com jitter.
- ⚠️ A listagem (`GET /jogos?plataforma=`) não é cacheada: invalidar listas é mais difícil e não é o caminho quente.
