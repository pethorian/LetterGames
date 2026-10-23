# CLAUDE.md — LetterGames (Backlog de Jogos)

Projeto final em grupo do módulo **BE-JV-010 (Nível III)**. O escopo oficial está em [brief-alunos.md](brief-alunos.md) e o plano de execução em [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md). **Banca: Aula 9, 26/10.**

## O que é

Um **backlog de jogos**: o jogador cadastra jogos do catálogo no seu backlog (`QUERO_JOGAR`, `JOGANDO`, `ZERADO`, `ABANDONADO`) e recebe estatísticas e conquistas calculadas de forma assíncrona. Sem front-end: a demo acontece **em vários terminais lado a lado**, mostrando a chamada saindo de um serviço e chegando no outro.

## Stack

- Java 21, Spring Boot 3.3.x, Maven multi-módulo (usar **`./mvnw`**: o Maven não está instalado na máquina)
- PostgreSQL 16 (**um container por serviço**), Redis 7, Apache Kafka 3.8 (KRaft, sem Zookeeper), Kafka UI
- Spring Kafka, Spring Data JPA, Spring Cache, Resilience4j, Pact JVM
- Testes: JUnit 5, H2, `spring-kafka-test` (EmbeddedKafka), Pact. **Sem Docker no perfil padrão.**

## Módulos

| Módulo | Porta | Banco | Responsabilidade |
|---|---|---|---|
| `catalogo-service` | 8081 | `postgres-catalogo` (5433) + Redis | Jogos (título, plataforma, gênero, duração média). Cache-aside no Redis. |
| `backlog-service` | 8082 | `postgres-backlog` (5434) | Backlog do usuário. Consulta o catálogo por REST (timeout, retry, circuit breaker) e publica eventos no Kafka. |
| `estatisticas-service` | 8083 | `postgres-estatisticas` (5435) | Consome eventos de forma idempotente, calcula estatísticas e conquistas. |
| `shared-contracts` | — | — | DTOs, eventos (`BacklogEvento`) e arquivos Pact (`pacts/`). |

Infraestrutura: Kafka `9092`, Kafka UI `8080`, Redis `6379`.

Tópicos: `backlog.eventos` (chave = `usuarioId`) e `backlog.eventos.DLT`.

## Comandos

```bash
./mvnw verify                      # perfil B (padrão): testes pura-JVM, sem Docker
./mvnw verify -Pperfil-a           # inclui testes com Testcontainers (exige Docker)
docker compose up -d --build       # perfil A: sobe tudo
docker compose logs -f backlog-service
./scripts/demo/01-cadastrar-jogos.sh   # roteiro de demo (ver docs/APRESENTACAO.md)
pwsh ./scripts/abrir-terminais.ps1     # abre o layout de terminais da banca (Windows Terminal)
```

> Ordem dos módulos no `pom.xml` raiz: `shared-contracts`, `backlog-service`, `catalogo-service`, `estatisticas-service`. O `backlog-service` gera o pact (consumidor) **antes** de o `catalogo-service` verificá-lo (provedor).

## Regras do projeto

1. **Nenhum serviço lê o banco de outro.** Dados de outro contexto entram por REST ou evento, e o backlog guarda um *snapshot* (título, duração) do jogo.
2. **Eventos vivem em `shared-contracts`.** Mudou o evento → incrementa `versao`, atualiza o pact e o ADR, se a mudança for de decisão.
3. **Toda decisão arquitetural vira ADR** em `docs/adr/ADR-NNN-titulo.md` (contexto, decisão, alternativas, trade-offs). ADR descreve *por quê*, não *o quê*.
4. **Toda evidência nova vai para o `AVALIACAO.md`** (critério → arquivo/classe/teste/commit). O que não estiver lá a banca não considera.
5. **Consumidor sempre idempotente:** registrar `eventId` em `eventos_processados` na **mesma transação** da atualização.
6. **Logs de demo** usam o padrão `HH:mm:ss.SSS [correlationId] NIVEL Classe - mensagem` com marcadores visuais (`→ REST`, `⇢ KAFKA PUB`, `⇠ KAFKA SUB`, `✔ CACHE HIT`, `✘ CACHE MISS`, `⟳ RETRY`, `☠ DLT`, `⚡ BREAKER`). O `correlationId` vem do header HTTP `X-Correlation-Id` e é repassado no header Kafka `correlationId`.
7. `./mvnw verify` precisa passar **sem Docker** antes de cada PR.
8. Registre todo uso relevante de IA em `docs/ia/registro-uso-ia.md` (o que foi pedido, o que veio, **o que validamos à mão**). Isso alimenta o critério 8.

## Fluxo Git (resumo das preferências globais)

- Nunca commitar em `main`/`develop`. Branch nasce da `develop` atualizada: `feat/`, `fix/`, `test/`, `docs/`, `chore/`, `ci/`.
- Conventional Commits em português: `feat(backlog): publica evento ao alterar status`. Testes de preferência em commit próprio (`test: ...`).
- PR para `develop` com CI verde é aprovado e mergeado automaticamente. `develop` → `main` só com aprovação manual do dono.
- **Commits pequenos e frequentes**: a banca avalia processo pelo histórico.

## Papéis

| Pessoa | Papel | Critérios que defende |
|---|---|---|
| **A** | Arquiteto + Infra/Demo | 1 Decomposição, 7 ADRs, 8 Uso de IA, 9 Execução |
| **B** | Dev Mensageria | 2 Assíncrono, 3 Idempotência, 5 Resiliência (lado Kafka) |
| **C** | Dev Dados/Cache + Testes/Contrato | 4 Cache, 6 Testabilidade, 5 Resiliência (lado REST) |

## Documentos

- [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md): tarefas, donos, cronograma e riscos
- [docs/APRESENTACAO.md](docs/APRESENTACAO.md): roteiro da banca, falas por pessoa e perguntas esperadas
- [docs/arquitetura.md](docs/arquitetura.md): contextos, fluxos e contratos
- [docs/adr/](docs/adr/): decisões
- [AVALIACAO.md](AVALIACAO.md): auto-avaliação (obrigatória)
