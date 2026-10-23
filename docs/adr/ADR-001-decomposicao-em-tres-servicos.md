# ADR-001 — Decomposição em três serviços com bases segregadas

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa A (Arquiteto)
- **Critério:** 1 — Decomposição de domínio

## Contexto

O domínio tem três responsabilidades com ritmos diferentes: o catálogo de jogos (muita leitura, pouca escrita), o backlog do jogador (escrita frequente, precisa de consistência imediata) e as estatísticas (dado derivado, pode atrasar alguns segundos).

## Decisão

Três serviços (`catalogo`, `backlog`, `estatisticas`), **cada um com seu próprio container Postgres**. O backlog valida o jogo por REST no catálogo e guarda um **snapshot** (`titulo`, `duracaoMediaHoras`) no item. Estatísticas só conhece eventos. Não existe serviço de usuário: `usuarioId` é uma string no path.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Monólito modular | Não exercita os padrões do módulo (fila, contrato entre serviços). |
| 2 serviços (estatísticas dentro do backlog) | Mistura modelo de escrita com modelo de leitura e impede reprocessar o histórico de forma independente. |
| 1 Postgres com 3 schemas | Mais leve, mas permite join entre schemas e acoplamento escondido. Container separado deixa a fronteira física e permite derrubar um banco na demo. |
| Backlog só guarda `jogoId` e consulta o catálogo a cada listagem | Acoplamento temporal: catálogo fora = backlog fora. |
| Serviço de usuários | Fora do escopo do prazo; não acrescenta padrão novo. |

## Consequências

- ✅ Cada serviço sobe, cai e escala sozinho; o backlog lista itens mesmo com o catálogo fora.
- ✅ Estatísticas pode ser recalculada do zero relendo o tópico.
- ⚠️ Snapshot pode ficar desatualizado se o catálogo corrigir um título. Aceito: título quase nunca muda.
- ⚠️ 3 Postgres pesam no notebook. Mitigação: imagens `alpine`, poucos recursos.
- ⚠️ Sem autenticação: limitação declarada, não uma decisão de produção.
