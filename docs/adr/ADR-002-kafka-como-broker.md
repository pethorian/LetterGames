# ADR-002 — Kafka como broker, eventos em vez de comandos e dois consumer groups

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa B (Mensageria)
- **Critério:** 2 — Comunicação assíncrona

## Contexto

As mudanças no backlog precisam chegar às estatísticas e às conquistas sem que o backlog dependa delas estarem no ar. A **ordem importa por usuário**: processar `STATUS_ALTERADO → ZERADO` antes de `JOGO_ADICIONADO` gera estatística errada.

## Decisão

- Apache Kafka 3.9 (KRaft, nó único no compose). Tópico `backlog.eventos` com **3 partições** e **chave = `usuarioId`**: todos os eventos de um usuário caem na mesma partição, em ordem. Tópicos criados pelos próprios serviços (`NewTopic`); o broker tem auto-create desligado.
- Publicamos **eventos** (fatos no passado: `JOGO_ADICIONADO`, `STATUS_ALTERADO`, `JOGO_REMOVIDO`), não comandos. O produtor não sabe quem consome. O evento carrega `eventId` (chave de idempotência) e `versao` (evolução do schema).
- **Dois consumer groups** independentes no mesmo tópico: `estatisticas` (contadores e horas) e `conquistas` (progresso e conquistas). Cada um recebe todos os eventos, tem o seu offset, a sua idempotência e a sua política de erro.
- Garantia declarada: **at-least-once** de ponta a ponta.
  - Produtor: transactional outbox (ADR-003) + `acks=all` + `enable.idempotence=true`.
  - Consumidor: `enable-auto-commit=false` e `AckMode.RECORD`. O container só commita o offset depois que o listener retorna, e o listener só retorna depois do commit da transação no banco.
  - Com o consumidor idempotente (ADR-003), o efeito é **exatamente uma vez**.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| RabbitMQ | Ótimo para fila de trabalho, mas ordem por chave com vários consumidores e releitura do histórico não são nativas. |
| Comando (`CalcularEstatistica`) em fila ponto a ponto | Acopla o backlog ao consumidor; um novo consumidor exigiria mudar o produtor. |
| Chamada REST síncrona backlog → estatísticas | Estatísticas fora derrubaria o backlog. |
| Um consumer group só, fazendo estatísticas e conquistas | Uma falha nas conquistas atrasaria as estatísticas; dois groups mostram o fan-out e isolam falhas. |
| At-most-once (auto-commit) | Perde eventos se o consumidor cair entre o commit do offset e o do banco. |
| Exactly-once transacional do Kafka | Só cobre Kafka → Kafka; o nosso efeito é num Postgres, então a idempotência tem que estar no consumidor de qualquer jeito. |

## Consequências

- ✅ Ordem por usuário garantida pela partição + retry bloqueante (ADR-005).
- ✅ Novos consumidores (ex.: recomendações) só assinam o tópico; `conquistas` entrou assim, sem mudar o backlog.
- ✅ É possível reprocessar do offset 0 para recalcular as projeções.
- ⚠️ At-least-once **obriga** idempotência no consumidor.
- ⚠️ Paralelismo limitado a 3 consumidores por group (3 partições).
- ⚠️ Kafka é mais pesado que RabbitMQ para rodar local (heap limitado a 512 MB no compose).
