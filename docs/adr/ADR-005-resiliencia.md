# ADR-005 — Resiliência: timeout, retry com backoff, circuit breaker e DLT

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsáveis:** Pessoa C (lado REST) · Pessoa B (lado Kafka)
- **Critério:** 5 — Resiliência

## Contexto

Há dois pontos de falha entre serviços: a chamada síncrona backlog → catálogo e o processamento de eventos no consumidor de estatísticas. As falhas podem ser **transitórias** (rede, banco reiniciando) ou **permanentes** (JSON inválido, regra violada).

## Decisão

### REST (backlog → catálogo), Resilience4j

- Timeout: connect 1 s, read 2 s.
- `@Retry`: 3 tentativas, backoff exponencial a partir de 200 ms, **só** para IO e 5xx (404 e 400 não são repetidos).
- `@CircuitBreaker`: janela de 10 chamadas, abre com 50% de falha, fica aberto 15 s, 3 chamadas em half-open.
- Fallback: **503** "catálogo indisponível". Não gravamos item sem validar o jogo.

### Kafka (consumidor de estatísticas), Spring Kafka

- `DefaultErrorHandler` com `ExponentialBackOff` (1 s, ×2, 4 tentativas) → `DeadLetterPublishingRecoverer` para `backlog.eventos.DLT`.
- Exceções **não-retentáveis** (`DeserializationException`, `EventoInvalidoException`) vão direto para a DLT.
- `ErrorHandlingDeserializer` impede que um *poison pill* trave a partição.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `@RetryableTopic` (retry não-bloqueante em tópicos de retry) | Mais vazão, mas a mensagem com falha sai da partição e as seguintes do **mesmo usuário** passam na frente: quebra a ordem que o ADR-002 garante. |
| Retry infinito | Uma mensagem ruim trava a partição para sempre. |
| Sem circuit breaker, só timeout | Com o catálogo fora, cada requisição gasta o timeout inteiro e as threads do backlog se esgotam. |
| Fallback que grava o item "pendente de validação" | Mais disponível, mas exigiria reconciliação depois. Preferimos recusar a gravar algo não validado. |

## Consequências

- ✅ Falha transitória se resolve sozinha (testes `FalhaTransitoria*Test`).
- ✅ Falha permanente é isolada na DLT sem bloquear o resto (`FalhaPermanente*Test`).
- ✅ Catálogo fora → backlog responde 503 em milissegundos.
- ⚠️ Retry bloqueante segura a partição por até ~7 s numa falha transitória.
- ⚠️ DLT exige operação manual (inspecionar e republicar); não há alerta automático.
