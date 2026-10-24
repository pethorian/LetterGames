# Arquitetura — LetterGames

## Contextos (bounded contexts)

```mermaid
flowchart LR
    subgraph Catalogo["Contexto Catálogo"]
        CS[catalogo-service :8081]
        PGC[(postgres-catalogo)]
        R[(Redis)]
        CS --- PGC
        CS -. cache-aside .- R
    end

    subgraph Backlog["Contexto Backlog"]
        BS[backlog-service :8082]
        PGB[(postgres-backlog<br/>itens_backlog + outbox_eventos)]
        BS --- PGB
    end

    subgraph Estatisticas["Contexto Estatísticas"]
        EL[group: estatisticas]
        CL[group: conquistas]
        ES[estatisticas-service :8083]
        PGE[(postgres-estatisticas)]
        EL --- ES
        CL --- ES
        ES --- PGE
    end

    K{{Kafka: backlog.eventos<br/>3 partições, key = usuarioId}}
    DLT{{backlog.eventos.DLT}}

    BS -- "REST GET /jogos/{id}<br/>timeout + retry + circuit breaker" --> CS
    BS -- "outbox → evento" --> K
    K --> EL
    K --> CL
    EL -- "inválido / processamento esgotado" --> DLT
    CL -- "inválido / processamento esgotado" --> DLT
```

| Contexto | Dono de | Não sabe de |
|---|---|---|
| Catálogo | o que **é** um jogo (título, plataforma, gênero, duração média) | quem tem o jogo |
| Backlog | a **relação** jogador ↔ jogo, o status e as regras de transição; snapshot de título e duração | estatísticas e conquistas |
| Estatísticas | dados **derivados**: contadores, horas, conquistas (duas projeções, dois groups) | regras de transição de status |

Regra: **nenhum serviço acessa o banco de outro** (ADR-001). O que é compartilhado é só contrato (`shared-contracts`).

## Fluxo principal: adicionar um jogo

```mermaid
sequenceDiagram
    autonumber
    actor U as Terminal (curl)
    participant B as backlog-service
    participant C as catalogo-service
    participant R as Redis
    participant O as outbox (postgres-backlog)
    participant K as Kafka
    participant E as estatisticas-service

    U->>B: POST /usuarios/joao/backlog {jogoId:1}<br/>X-Correlation-Id: demo-02
    B->>C: GET /jogos/1 (X-Correlation-Id)
    C->>R: GET jogos::1
    alt cache miss
        C->>C: SELECT no postgres-catalogo
        C->>R: SET jogos::1 (TTL 10 min)
    end
    C-->>B: 200 {titulo, duracaoMediaHoras}
    B->>O: BEGIN · INSERT item (snapshot) · INSERT outbox · COMMIT
    B-->>U: 201 Created
    loop a cada 500 ms
        O->>K: publicador: cabeça de fila por usuário (key=joao, header correlationId)
        O->>O: marca publicado_em
    end
    par group estatisticas
        K->>E: entrega (at-least-once)
        E->>E: BEGIN · registra (eventId, estatisticas) · atualiza contadores · COMMIT · ack
    and group conquistas
        K->>E: entrega (at-least-once)
        E->>E: BEGIN · registra (eventId, conquistas) · progresso + 🏆 · COMMIT · ack
    end
```

## API

| Serviço | Rota | Respostas |
|---|---|---|
| catálogo | `POST /jogos` · `GET /jogos/{id}` · `GET /jogos?plataforma=` · `PUT /jogos/{id}` · `DELETE /jogos/{id}` | 201, 200, 204, 400 `VALIDACAO`, 404 `JOGO_NAO_ENCONTRADO` |
| backlog | `POST /usuarios/{u}/backlog` `{jogoId}` | 201, 400, 409 `JOGO_JA_NO_BACKLOG`, 422 `JOGO_INEXISTENTE`, 502 `CATALOGO_REQUISICAO_INVALIDA`, 503 `CATALOGO_INDISPONIVEL` |
| backlog | `PATCH /usuarios/{u}/backlog/{itemId}` `{status}` · `GET ...?status=` · `DELETE ...` | 200, 204, 404 `ITEM_NAO_ENCONTRADO`, 409 `CONFLITO_CONCORRENTE`, 422 `TRANSICAO_INVALIDA` |
| estatísticas | `GET /usuarios/{u}/estatisticas` · `GET /usuarios/{u}/conquistas` | 200, 400, 503 `BANCO_INDISPONIVEL` |

`usuarioId` e `X-Correlation-Id` seguem `[A-Za-z0-9._-]{1,64}`.

## Mensageria

| Item | Valor |
|---|---|
| Tópico | `backlog.eventos` (3 partições, chave `usuarioId`) |
| DLT | `backlog.eventos.DLT` (3 partições, mesma partição do original) |
| Tipos | `JOGO_ADICIONADO`, `STATUS_ALTERADO`, `JOGO_REMOVIDO` (eventos, fatos no passado), `versao = 1` |
| Produtor | **transactional outbox** (item + evento na mesma transação) + publicador a cada 500 ms; `acks=all`, `enable.idempotence=true`; falha isola a chave; estaciona após 10 falhas não transitórias (queda do broker não conta); erro de banco encerra a rodada |
| Consumidores | groups `estatisticas` e `conquistas`; `enable-auto-commit=false`, `AckMode.RECORD` (offset depois do commit no banco) |
| **Garantia** | **at-least-once**, com consumidor idempotente (`eventos_processados (event_id, consumidor)`) → efeito exatamente uma vez |
| Retry | inválido → DLT direto; transitório → 1 s ×2 até 30 s, por até 10 min; processamento → 4 tentativas (1, 2, 4 s) → DLT |
| Ordem | por usuário: mesma partição, cabeça de fila no outbox (ordem de gravação, coluna `sequencia`) e retry bloqueante no consumidor. Por item, gravação = commit (lock de linha) |

## Cache

| Item | Valor |
|---|---|
| Onde | `catalogo-service`, `GET /jogos/{id}` |
| Estratégia | cache-aside (`@Cacheable("jogos")`); só a leitura popula |
| TTL | 10 min; nulos não são cacheados |
| Invalidação | `@CacheEvict` em `PUT` e `DELETE`, depois do commit, mais um segundo evict 500 ms depois |
| Fallback | `CacheErrorHandler`: Redis fora → loga `WARN` e lê do banco |

## Resiliência REST (backlog → catálogo)

| Item | Valor |
|---|---|
| Timeout | connect 1 s · read 2 s |
| Retry | 3 tentativas, backoff exponencial a partir de 200 ms, só IO/timeout/5xx |
| Circuit breaker | janela 10, mínimo 5, 50% de falha, aberto 15 s, 3 chamadas em half-open |
| 404 / outro 4xx | 422 / 502, sem retry e fora do breaker |
| Fallback | 503 `CATALOGO_INDISPONIVEL`; o item **não** é gravado sem validação |

## Concorrência

`PATCH` e `DELETE` do mesmo item são serializados com `SELECT ... FOR UPDATE`, e `@Version` funciona como rede de segurança (ADR-008).

## Observabilidade

- `X-Correlation-Id` (HTTP) → MDC `correlationId` → coluna `correlation_id` do outbox → header Kafka `correlationId` → MDC no consumidor.
- Padrão de log: `HH:mm:ss.SSS NIVEL [correlationId] Classe - mensagem`, com marcadores: `→ REST`, `← REST`, `✚ ITEM`, `⇢ KAFKA PUB`, `⇠ KAFKA SUB`, `📊 ESTATÍSTICAS`, `🏆 CONQUISTA`, `✔ CACHE HIT`, `✘ CACHE MISS`, `🗑 CACHE EVICT`, `⟳ RETRY`, `⚡ BREAKER`, `↺ DUPLICADO`, `☠ DLT`, `☠ OUTBOX`.

## Contratos

| Contrato | Arquivo | Gera | Verifica |
|---|---|---|---|
| REST `GET /jogos/{id}` | `shared-contracts/pacts/backlog-service-catalogo-service.json` | `CatalogoClientPactTest` (backlog) | `CatalogoPactProviderTest` (catálogo) |
| Mensagem `BacklogEvento` | `shared-contracts/pacts/estatisticas-service-backlog-service.json` | `BacklogEventoPactConsumerTest` (estatísticas) | `BacklogEventoPactProviderTest` (backlog) |
