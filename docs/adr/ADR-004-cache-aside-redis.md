# ADR-004 — Cache-aside no catálogo com Redis, TTL e invalidação por evict

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa C (Dados/Cache)
- **Critério:** 4 — Cache

## Contexto

`GET /jogos/{id}` é chamado em toda adição ao backlog e em consultas, enquanto jogos quase nunca mudam. A leitura domina a escrita.

## Decisão

- **Cache-aside** no `catalogo-service` com Spring Cache + Redis: `@Cacheable("jogos")` no `GET /jogos/{id}`.
- **TTL de 10 minutos**, com valores serializados em JSON.
- **Invalidação explícita** com `@CacheEvict` em `PUT` e `DELETE`.
- **Fallback:** `CacheErrorHandler` loga `WARN` e segue para o banco se o Redis falhar. O cache nunca é dependência obrigatória.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `@CachePut` no update (write-through) | Duas escritas concorrentes podem deixar a versão antiga por último no cache. Evict força a releitura da fonte da verdade. |
| Só TTL, sem invalidação | Até 10 min de dado velho após cada edição, sem necessidade. |
| Cache local (Caffeine) | Com mais de uma réplica, cada uma teria uma versão; a invalidação não se propaga. |
| Cache no backlog | Dois caches para invalidar; o backlog já tem snapshot (ADR-001). |
| Write-behind | Risco de perder escrita; complexidade sem ganho aqui. |

## Consequências

- ✅ 2ª leitura não toca o banco (visível no `redis-cli MONITOR` na demo).
- ✅ Redis fora não derruba o catálogo.
- ⚠️ TTL de 10 min é número de bom senso, **não medido**.
- ⚠️ Sem proteção contra *stampede*; em escala: `@Cacheable(sync = true)` ou TTL com jitter.
- ⚠️ Listagem (`GET /jogos?plataforma=`) não é cacheada: invalidar listas é mais difícil e não é o caminho quente.
