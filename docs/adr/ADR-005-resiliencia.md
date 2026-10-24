# ADR-005 — Resiliência: timeout, retry com backoff, circuit breaker e DLT

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsáveis:** Pessoa C (lado REST) · Pessoa B (lado Kafka)
- **Critério:** 5 — Resiliência

## Contexto

Há três pontos de falha entre serviços:

1. a chamada síncrona backlog → catálogo;
2. a publicação do outbox no Kafka;
3. o processamento de eventos nos consumidores de estatísticas e conquistas.

As falhas podem ser **transitórias** (rede, banco reiniciando, lock) ou **permanentes** (JSON inválido, regra violada, bug).

## Decisão

### REST (backlog → catálogo), Resilience4j

- **Timeout:** connect 1 s, read 2 s (`JdkClientHttpRequestFactory`).
- **`@Retry`:** 3 tentativas, backoff exponencial a partir de 200 ms (×2). Retenta **só** `CatalogoIndisponivelException` (IO, timeout, 5xx).
- **`@CircuitBreaker`:** janela de 10 chamadas, mínimo de 5, abre com 50% de falha, fica aberto 15 s, 3 chamadas em half-open.
  - O Retry fica **por fora** do breaker: cada tentativa entra na janela, então uma requisição pode contar até 3 falhas.
  - Breaker aberto lança `CatalogoBreakerAbertoException`, que o Retry ignora: recusa em milissegundos, sem repetir a recusa.
- **Classificação de respostas:**
  - 404 → `JogoInexistenteNoCatalogoException`: 422, sem retry, não conta no breaker.
  - outros 4xx → `CatalogoRequisicaoInvalidaException`: 502, sem retry, não conta no breaker (é bug ou contrato quebrado nosso, não indisponibilidade).
  - IO/timeout/5xx esgotados ou breaker aberto → 503 `CATALOGO_INDISPONIVEL`. **Não gravamos item sem validar o jogo.**

### Produtor (outbox → Kafka)

- `acks=all`, `enable.idempotence=true`, `max.block.ms`/`request.timeout.ms` de 5 s, `delivery.timeout.ms` de 10 s. O publicador espera o envio por `delivery.timeout.ms` + 2 s, para não dar como falho um envio ainda em curso.
- Falha isola a chave e, depois de 10 falhas não transitórias, estaciona o evento (ADR-003). Falha transitória do Kafka (broker fora, timeout) e interrupção no shutdown não contam tentativa, então nunca estacionam.

### Consumidores (estatísticas e conquistas), Spring Kafka

`DefaultErrorHandler` com **política escolhida pela classe da falha** (`ClassificadorFalhas`, que segue a cadeia de causas):

| Classe | Exemplos | Política |
|---|---|---|
| **Inválida** | JSON inválido, `eventId`/`tipo` ausente, enum desconhecido (`EventoInvalidoException`) | Direto para a DLT, **sem retry** |
| **Transitória** | banco fora, conexão, lock (`CannotCreateTransactionException`, `TransientDataAccessException`...) | Backoff exponencial 1 s ×2 até 30 s, por até **10 min**, segurando a partição |
| **Processamento** | bug, `IllegalStateException` | 4 tentativas (1 s, 2 s, 4 s) → DLT |

- **DLT** `backlog.eventos.DLT`, mesma partição, value original e headers originais (inclusive `correlationId` e `kafka_dlt-original-consumer-group`). Como há dois groups, um poison pill gera duas cópias na DLT, uma por group (o log `☠ DLT` mostra o `group=`).
- Logs: `⟳ RETRY {n} ({classe}) consumidor={g} eventId=... próxima em {ms} ms: {motivo}` e `☠ DLT ... group={g} motivo=...`.
- O startup falha se o intervalo máximo do backoff não couber no `max.poll.interval.ms` (cada nova tentativa é um poll novo).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `@RetryableTopic` (retry não-bloqueante em tópicos de retry) | Mais vazão, mas a mensagem com falha sai da partição e as seguintes do **mesmo usuário** passam na frente. Quebra a ordem que o ADR-002 garante. |
| Mesma política (4 tentativas) para toda falha | Era a primeira versão. Na revisão vimos que o banco fora por ~15 s mandava o evento para a DLT e o evento seguinte do usuário era aplicado: projeção divergente para sempre. |
| Retry infinito para tudo | Um bug ou uma mensagem ruim travaria a partição para sempre. |
| Sem circuit breaker, só timeout | Com o catálogo fora, cada requisição gastaria timeout + retries, esgotando as threads do backlog. |
| Fallback que grava o item "pendente de validação" | Mais disponível, mas exigiria reconciliação depois. Preferimos recusar a gravar algo não validado. |

## Consequências

- ✅ Falha transitória se resolve sozinha nos dois lados: `CatalogoClientResilienciaTest` (500, 500, 200) e `FalhaTransitoriaTest` (banco fora por 6 tentativas, nada na DLT).
- ✅ Falha permanente é isolada na DLT sem bloquear o resto (`FalhaPermanenteTest`).
- ✅ Com o catálogo fora, o backlog responde 503 em ~10 ms depois que o breaker abre. As primeiras requisições ainda levam ~1–4 s (timeout + retries) até a janela atingir o mínimo.
- ⚠️ Retry bloqueante: uma falha transitória longa segura a partição (e os usuários dela) por até 10 min. Trocamos vazão por ordem e correção.
- ⚠️ O `DefaultErrorHandler` reinicia o backoff quando o tipo da exceção muda (padrão do spring-kafka). Uma falha que alterna de tipo enquanto o banco cai e volta pode passar dos 10 min. Aceito: o risco é atraso, não perda nem fora de ordem.
- ⚠️ A DLT exige operação manual (inspecionar no Kafka UI e republicar); não há alerta automático.
