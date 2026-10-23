# Registro de uso de IA

Evidência do critério 8. **Registre na hora**, não no fim: o valor está nos casos em que a IA errou e a gente percebeu.

## Como registrar

| Data | Quem | Ferramenta | O que pedimos | O que veio | O que validamos/corrigimos à mão | Resultado |
|---|---|---|---|---|---|---|
| 2026-10-23 | A | Claude Code | Estruturar o planejamento, CLAUDE.md, ADRs iniciais e roteiro da banca a partir do brief | Plano completo, 7 ADRs em rascunho, roteiro por pessoa | TODO: revisar números (TTL, backoff, janela do breaker) e trade-offs de cada ADR; confirmar que cada decisão é nossa | Aceito com revisão |

## Casos para observar (candidatos a "a IA errou")

- Sugestão de `@RetryableTopic` sem considerar a ordem por chave.
- `enable-auto-commit=true` ou commit do offset antes da transação.
- `@CachePut` no update em vez de evict.
- Teste de idempotência que não reenvia de fato o **mesmo** `eventId`.
- Versões de dependência inexistentes ou incompatíveis (Pact × Spring Boot 3).

## Reflexão final (preencher antes da banca)

TODO: onde a IA acelerou, onde atrapalhou, o que não delegaríamos de novo.
