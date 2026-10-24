# ADR-003 — Transactional outbox no produtor e consumidor idempotente

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa B (Mensageria)
- **Critério:** 3 — Idempotência e consistência

## Contexto

Dois problemas clássicos de consistência entre banco e broker:

1. **Dual write no produtor.** Gravar o item no Postgres e publicar no Kafka são duas operações. Se o Kafka estiver fora entre uma e outra, o evento se perde e as estatísticas divergem para sempre.
2. **Reentrega no consumidor.** Com at-least-once (ADR-002), o mesmo evento pode chegar mais de uma vez: reentrega depois de uma queda, rebalanceamento do group ou republicação manual da DLT. Contar o mesmo "zerado" duas vezes corrompe as estatísticas e desbloqueia conquista duplicada. O próprio outbox também pode publicar duas vezes (ver abaixo).

## Decisão

### Produtor: transactional outbox (`backlog-service`)

- `BacklogService` grava o item e a linha em `outbox_eventos` **na mesma transação** (`OutboxRegistrador` exige `Propagation.MANDATORY`). Ou os dois existem, ou nenhum.
- `OutboxPublicador` roda a cada 500 ms. Trava só a **cabeça de fila de cada chave**, ordenada pela coluna `sequencia` gerada no INSERT (`FOR UPDATE SKIP LOCKED` no Postgres), envia ao Kafka com key = `usuarioId` e headers `correlationId`/`tipo`, e só então marca `publicado_em`. A ordem de gravação por usuário se mantém, inclusive com mais de uma instância. Para o mesmo item ela é a ordem de commit (ADR-008); entre itens diferentes do mesmo usuário pode não ser, o que as projeções toleram (contadores comutativos).
- Falha num evento bloqueia só aquela chave no ciclo; os outros usuários seguem. Depois de `backlog.outbox.max-tentativas` (10) falhas não transitórias, o evento é **estacionado** (`falhou_em`), com log `☠ OUTBOX evento estacionado`, e a chave fica retida (ordem vale mais que disponibilidade). Reprocessar é manual (zerar `falhou_em` e `tentativas`). Queda do broker e interrupção no shutdown **não contam** tentativa: só falhas atribuíveis ao evento levam ao estacionamento. Erro de banco durante a rodada condena a transação inteira: a rodada para e o que foi enviado nela sai de novo no próximo ciclo.
- Se o processo cair depois do envio e antes de marcar `publicado_em`, o evento é publicado de novo: **at-least-once**, coberto por `OutboxPublicadorTest`.

### Consumidor: tabela de eventos processados (`estatisticas-service`)

- Tabela `eventos_processados (event_id, consumidor)` com **PK composta**: cada consumer group tem a sua idempotência.
- `RegistroIdempotencia.registrarSeNovo`, **dentro da transação do efeito**:
  1. verifica se `(eventId, consumidor)` já existe → se sim, log `↺ DUPLICADO ignorado` e retorna sem efeito (o offset é commitado normalmente);
  2. se não, faz `persist` + `flush` antes de aplicar o efeito.
- A **PK é a rede de segurança** da corrida. Se duas entregas do mesmo evento passarem juntas pela verificação, a segunda falha no INSERT, a transação inteira faz rollback, a falha é retentada (ADR-005) e, na nova tentativa, o evento aparece como duplicado.
- Falha **depois** do registro (ex.: ao aplicar a regra) desfaz também o registro: a nova tentativa processa normalmente. Coberto por `FalhaTransitoriaTest`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Publicar depois do commit (`@TransactionalEventListener(AFTER_COMMIT)`) | Era o plano inicial. Mais simples, mas mantém o dual write: Kafka fora = evento perdido. O outbox elimina isso. |
| Transação Kafka + JPA (ChainedTransactionManager) | Não é atômico de verdade (best effort 1PC) e é mais complexo que o outbox. |
| CDC (Debezium) lendo o outbox | Correto e sem poller, mas é mais um componente pesado para o escopo. |
| Operações naturalmente idempotentes (recalcular tudo a partir do estado) | Exigiria o consumidor conhecer o backlog inteiro do usuário; acopla os contextos. |
| Deduplicar em memória ou Redis | Perde o estado ao reiniciar e não é atômico com a escrita no Postgres. |
| `INSERT` direto e capturar a violação de PK como "duplicado" | No Postgres a transação fica abortada depois do erro, então não dá para continuar e dar ack na mesma transação. Por isso: verificar e inserir, com a PK só como rede de segurança. |

## Consequências

- ✅ Nenhum evento se perde com o Kafka fora: ele fica pendente no outbox e sai quando o broker volta.
- ✅ Reprocessar N vezes tem efeito de 1 vez: `ReprocessamentoIdempotenteTest` (mesmo registro 2×, duplicata não adjacente A-B-A e duas threads simultâneas).
- ✅ Atomicidade garantida pelo Postgres, sem coordenação distribuída.
- ⚠️ Latência extra de até ~500 ms (intervalo do poller) entre o commit e a publicação.
- ⚠️ `outbox_eventos` e `eventos_processados` crescem sem limite. Em produção: expurgo de publicados e de processados mais velhos que a retenção do tópico.
- ⚠️ Evento estacionado exige intervenção manual e segura os eventos seguintes daquele usuário.
