# ADR-002 — Kafka como broker e eventos em vez de comandos

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa B (Mensageria)
- **Critério:** 2 — Comunicação assíncrona

## Contexto

Mudanças no backlog precisam chegar ao serviço de estatísticas sem que o backlog dependa dele estar no ar. A **ordem importa por usuário**: processar `STATUS_ALTERADO → ZERADO` antes de `JOGO_ADICIONADO` gera estatística errada.

## Decisão

- Apache Kafka (KRaft, nó único no compose), tópico `backlog.eventos` com 3 partições e **chave = `usuarioId`**.
- Publicamos **eventos** (fatos no passado: `JOGO_ADICIONADO`, `STATUS_ALTERADO`, `JOGO_REMOVIDO`), não comandos. O produtor não sabe quem consome.
- Garantia declarada: **at-least-once** (producer `acks=all` + `enable.idempotence=true`; consumidor com commit manual do offset depois do commit no banco).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| RabbitMQ | Ótimo para fila de trabalho, mas ordem por chave com vários consumidores e releitura do histórico não são nativas. |
| Comando (`CalcularEstatistica`) em fila ponto a ponto | Acopla o backlog ao consumidor; um novo consumidor exigiria mudar o produtor. |
| Chamada REST síncrona backlog → estatísticas | Estatísticas fora derrubaria o backlog. |
| At-most-once (auto-commit) | Perde eventos se o consumidor cair. |
| Exactly-once transacional do Kafka | Só cobre Kafka → Kafka; nosso efeito é num Postgres. |

## Consequências

- ✅ Ordem por usuário garantida pela partição.
- ✅ Novos consumidores (ex.: recomendações) só assinam o tópico.
- ✅ É possível reprocessar do offset 0 para recalcular estatísticas.
- ⚠️ At-least-once **obriga** idempotência no consumidor (ADR-003).
- ⚠️ Paralelismo limitado a 3 consumidores por group (3 partições).
- ⚠️ Kafka é mais pesado que RabbitMQ para rodar local.
