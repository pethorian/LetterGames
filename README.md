# LetterGames — Backlog de Jogos

Projeto final do módulo **BE-JV-010 (Nível III)**. Três microsserviços Java/Spring que gerenciam o backlog de jogos de um jogador, com comunicação síncrona (REST + circuit breaker), assíncrona (Kafka + consumidor idempotente) e cache (Redis).

> 🚧 **Status:** planejamento concluído, implementação em andamento. Ver [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md).

## Problema

Quem joga acumula jogos comprados e nunca jogados e não sabe quantas horas tem pela frente. O LetterGames organiza o backlog (`QUERO_JOGAR`, `JOGANDO`, `ZERADO`, `ABANDONADO`) e calcula de forma assíncrona estatísticas e conquistas.

## Arquitetura

```
[backlog-service] ──REST + breaker──► [catalogo-service] ──► Redis (cache-aside)
      │ pg-backlog                          │ pg-catalogo
      │ Kafka: backlog.eventos (key = usuarioId)
      ▼
[estatisticas-service] (idempotente) ──► pg-estatisticas      falha permanente → backlog.eventos.DLT
```

Detalhes: [docs/arquitetura.md](docs/arquitetura.md) · Decisões: [docs/adr/](docs/adr/)

| Serviço | Porta | Responsabilidade |
|---|---|---|
| catalogo-service | 8081 | Jogos (CRUD) com cache Redis |
| backlog-service | 8082 | Backlog do jogador; publica eventos |
| estatisticas-service | 8083 | Estatísticas e conquistas a partir de eventos |
| Kafka UI | 8080 | Visualização de tópicos, groups e DLT |

## Garantias declaradas

- **Entrega:** at-least-once (producer `acks=all` + idempotente; commit manual do offset após o commit no banco).
- **Consumo:** idempotente por `eventId` (tabela `eventos_processados`) → efeito *effectively-once*.
- **Ordem:** por usuário (chave da partição = `usuarioId`).
- **Cache:** cache-aside, TTL 10 min, invalidação por evict em escrita, fallback para o banco.

## Perfil de execução

**Perfil A (Docker).** Testes rodam no **perfil B (pura JVM, sem Docker)**. Fallbacks usados: H2 no lugar de Postgres, EmbeddedKafka no lugar do broker e cache em memória no lugar do Redis, todos só nos testes. Ver [ADR-007](docs/adr/ADR-007-perfis-de-execucao.md).

## Como rodar

Pré-requisitos: JDK 21, Docker Desktop (com ~6 GB de RAM livres), `curl` e `jq` (Git Bash no Windows).

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps                      # tudo "healthy"
```

Testes (sem Docker):

```bash
./mvnw verify
```

Demo em terminais: ver [docs/APRESENTACAO.md](docs/APRESENTACAO.md).

```bash
pwsh ./scripts/abrir-terminais.ps1
./scripts/demo/01-cadastrar-jogos.sh
```

## Grupo

| Pessoa | Papel |
|---|---|
| _(nome)_ | A — Arquiteto + Infra/Demo |
| _(nome)_ | B — Dev Mensageria |
| _(nome)_ | C — Dev Dados/Cache + Testes/Contrato |

## Uso de IA

Ver [docs/ia/registro-uso-ia.md](docs/ia/registro-uso-ia.md) e o critério 8 do [AVALIACAO.md](AVALIACAO.md).
