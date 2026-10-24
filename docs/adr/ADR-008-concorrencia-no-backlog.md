# ADR-008 — Concorrência no backlog: lock pessimista com `@Version` de rede de segurança

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa A (Arquiteto), com Pessoa B (impacto nos eventos)
- **Critérios:** 3 — Consistência · 7 — Decisões arquiteturais

## Contexto

Cada mudança de status gera um evento com `statusAnterior` e `statusNovo`, e as estatísticas confiam nesse par. A revisão de código encontrou um *lost update*: dois `PATCH` concorrentes no mesmo item liam o mesmo estado e publicavam `QUERO_JOGAR → JOGANDO` **e** `QUERO_JOGAR → ABANDONADO`. Resultado: um item contava duas vezes nas estatísticas e desbloqueava uma conquista que não existia no estado final. Um `PATCH` esperando um `DELETE` terminava em 500.

## Decisão

- `alterarStatus` e `remover` leem o item com **lock pessimista** (`findByIdAndUsuarioIdParaAtualizar`, `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`). A segunda transação espera a primeira e enxerga o estado já commitado; o evento só é montado depois do lock.
- `@Version` em `ItemBacklog` (migration V3) como rede de segurança para qualquer caminho que escreva sem o lock.
- Conflito de lock ou de versão → **409 `CONFLITO_CONCORRENTE`**. `PATCH` em item removido no meio → 404.
- Adição duplicada (mesmo usuário e jogo) é resolvida pela unique `uk_itens_backlog_usuario_jogo` → 409 `JOGO_JA_NO_BACKLOG`; outras violações de integridade não são mascaradas.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Só lock otimista (`@Version`) | Correto, mas o segundo `PATCH` sempre falharia com 409, mesmo quando a transição seria válida a partir do novo estado. O pessimista serializa e dá a resposta certa. |
| `SERIALIZABLE` no Postgres | Resolve, mas com abortos e retries na aplicação inteira; desproporcional para uma linha. |
| Deixar o consumidor corrigir (clamp em zero) | Mascararia o defeito; o evento continuaria mentindo sobre o estado anterior. |

## Consequências

- ✅ Os eventos de um item formam uma sequência coerente (`AlteracaoConcorrenteTest`, determinístico: falha sem o lock).
- ✅ A ordem de `criado_em` no outbox acompanha a ordem de commit para o mesmo item.
- ⚠️ Lock de linha segura a requisição concorrente enquanto a primeira não commita (transações curtas; a chamada ao catálogo fica fora da transação).
- ⚠️ Dois eventos da mesma chave com `criado_em` idêntico (resolução de microssegundos) não têm desempate. Improvável com o lock; uma coluna de sequência resolveria.
