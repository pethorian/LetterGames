# ADR-001 — Decomposição em três serviços com bases segregadas

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa A (Arquiteto)
- **Critério:** 1 — Decomposição de domínio

## Contexto

O domínio tem três responsabilidades com ritmos diferentes:

- o **catálogo** de jogos: muita leitura, pouca escrita;
- o **backlog** do jogador: escrita frequente, precisa de consistência imediata (regras de transição de status);
- as **estatísticas e conquistas**: dado derivado, que pode atrasar alguns segundos.

## Decisão

- Três serviços (`catalogo-service`, `backlog-service`, `estatisticas-service`), **cada um com o seu próprio container Postgres** (`postgres-catalogo`, `postgres-backlog`, `postgres-estatisticas`) e as suas próprias migrations Flyway. Nenhum serviço conhece a URL do banco de outro.
- O backlog valida o jogo por REST no catálogo e guarda um **snapshot** (`titulo`, `duracaoMediaHoras`) em `itens_backlog` (`ItemBacklog`).
- Estatísticas só conhece **eventos** (`BacklogEvento`); não chama nenhum serviço.
- O que é compartilhado é só o **contrato** (`shared-contracts`: `JogoDTO`, `BacklogEvento`, nomes de tópicos e os pacts), nunca entidade nem tabela.
- Não existe serviço de usuário: `usuarioId` é uma string no path, validada por `[A-Za-z0-9._-]{1,64}`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Monólito modular | Não exercita os padrões do módulo (fila, contrato entre serviços). |
| 2 serviços (estatísticas dentro do backlog) | Mistura modelo de escrita com modelo de leitura e impede reprocessar o histórico de forma independente. |
| 1 Postgres com 3 schemas | Mais leve, mas permite join entre schemas e acoplamento escondido. Container separado deixa a fronteira física e permite derrubar um banco na demo (cena 6) sem afetar os outros. |
| Backlog só guarda `jogoId` e consulta o catálogo a cada listagem | Acoplamento temporal: catálogo fora = backlog fora. |
| Catálogo publicar eventos e o backlog manter réplica local do catálogo | Resolveria o snapshot desatualizado, mas é mais um fluxo assíncrono sem ganho no escopo. Fica como evolução. |
| Serviço de usuários com autenticação | Fora do escopo; não acrescenta padrão novo do módulo. |

## Consequências

- ✅ Cada serviço sobe, cai e escala sozinho. Com o catálogo fora, o backlog continua listando e alterando itens; só a **adição** responde 503.
- ✅ Estatísticas pode ser recalculada do zero relendo o tópico com um consumer group novo.
- ⚠️ O snapshot fica desatualizado se o catálogo corrigir um título depois. Aceito: título quase nunca muda, e disponibilidade importa mais.
- ⚠️ 3 Postgres pesam no notebook. Mitigação: imagens `alpine` e JVMs com `-Xmx256m`.
- ⚠️ **Sem autenticação:** qualquer um lê e altera o backlog de qualquer `usuarioId`. É uma limitação declarada de escopo, não uma decisão de produção; em produção o `usuarioId` viria de um token (JWT) e não do path.
