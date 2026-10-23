# ADR-003 — Consumidor idempotente com tabela de eventos processados

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa B (Mensageria)
- **Critério:** 3 — Idempotência e consistência

## Contexto

Com at-least-once (ADR-002), o mesmo evento pode chegar mais de uma vez: reentrega após queda, rebalanceamento do group ou republicação manual da DLT. Contar o mesmo "zerado" duas vezes corrompe as estatísticas e dispara conquista duplicada.

## Decisão

- Todo evento tem `eventId` (UUID) gerado no produtor.
- O consumidor, **na mesma transação**, faz `INSERT INTO eventos_processados(event_id)` (PK) e atualiza `estatisticas_usuario`.
- Violação de PK → evento já processado → log `↺ DUPLICADO ignorado` e ack.
- Produtor publica **após o commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Operações naturalmente idempotentes (sempre recalcular a partir do estado) | Exigiria o consumidor conhecer o backlog inteiro do usuário; acopla os contextos. |
| Deduplicar em memória/Redis | Perde o estado ao reiniciar e não é atômico com a escrita no Postgres. |
| Checar "já existe?" antes do INSERT | Condição de corrida entre a checagem e a escrita; a PK resolve no banco. |
| **Transactional outbox** no produtor | É a solução correta para o *dual write* (ver consequências). Fica como próximo passo (S1 do planejamento) pelo prazo. |

## Consequências

- ✅ Reprocessar N vezes tem efeito de 1 vez. Coberto por `ReprocessamentoIdempotenteTest`.
- ✅ Atomicidade garantida pelo Postgres, sem coordenação distribuída.
- ⚠️ `eventos_processados` cresce sem limite. Em produção: expurgo de registros mais velhos que a retenção do tópico.
- ⚠️ **Dual write no produtor:** se o Kafka estiver fora entre o commit do banco e o envio, o evento se perde. Risco aceito e declarado; mitigação planejada: outbox.
