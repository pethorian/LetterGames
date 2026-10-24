# CLAUDE.md — LetterGames (Backlog de Jogos)

Projeto final em grupo do módulo **BE-JV-010 (Nível III)**. O escopo oficial está em [brief-alunos.md](brief-alunos.md) e o plano de execução em [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md). **Banca: Aula 9, 26/10.**

## O que é

Um **backlog de jogos**: o jogador cadastra jogos do catálogo no seu backlog (`QUERO_JOGAR`, `JOGANDO`, `ZERADO`, `ABANDONADO`) e recebe estatísticas e conquistas calculadas de forma assíncrona. Sem front-end: a demo acontece **em vários terminais lado a lado**, mostrando a chamada saindo de um serviço e chegando no outro.

## Stack

- Java 21, Spring Boot 3.5.16, Maven multi-módulo (usar **`./mvnw`**: o Maven não está instalado na máquina)
- PostgreSQL 16 (**um container por serviço**), Redis 7.4, Apache Kafka 3.9.1 (KRaft, sem Zookeeper), Kafka UI v0.7.2
- Spring Kafka, Spring Data JPA + Flyway, Spring Cache, Resilience4j 2.4.0, Pact JVM 4.6.21
- Pacote base `br.com.lettergames.<servico>`; contratos em `br.com.lettergames.contratos`
- Configuração por ambiente via relaxed binding (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`, `CATALOGO_URL`). Os defaults em `application.yml` apontam para `localhost` com as portas do compose.
- Testes: JUnit 5, H2, `spring-kafka-test` (EmbeddedKafka), Pact. **Sem Docker no perfil padrão.**

## Módulos

| Módulo | Porta | Banco | Responsabilidade |
|---|---|---|---|
| `catalogo-service` | 8081 | `postgres-catalogo` (5433) + Redis | Jogos (título, plataforma, gênero, duração média). Cache-aside no Redis. |
| `backlog-service` | 8082 | `postgres-backlog` (5434) | Backlog do usuário. Consulta o catálogo por REST (timeout, retry, circuit breaker), grava item + evento no **outbox** na mesma transação e publica no Kafka. Lock pessimista em PATCH/DELETE. |
| `estatisticas-service` | 8083 | `postgres-estatisticas` (5435) | Dois consumer groups (`estatisticas`, `conquistas`), idempotentes por `(eventId, consumidor)`; retry classificado e DLT. |
| `shared-contracts` | — | — | DTOs, eventos (`BacklogEvento`) e arquivos Pact (`pacts/`). |

Infraestrutura: Kafka `9092`, Kafka UI `8080`, Redis `6379`, todas publicadas só em `127.0.0.1` (use `127.0.0.1`, não `localhost`, em scripts: o curl tentaria `::1` antes).

Tópicos: `backlog.eventos` (3 partições, chave = `usuarioId`) e `backlog.eventos.DLT`.

## Comandos

```bash
./mvnw verify                      # perfil B (padrão): testes pura-JVM, sem Docker
./mvnw verify -Pperfil-a           # inclui testes @Tag("docker") com Testcontainers (exige Docker)
docker compose up -d --build       # perfil A: sobe tudo
docker compose logs -f backlog-service
./scripts/demo/00-reset.sh         # limpa bases, cache e tópicos (mantém o seed do catálogo)
./scripts/demo/demo-completa.sh    # as 7 cenas em ordem (SEM_PAUSA=1 para rodar direto)
powershell -ExecutionPolicy Bypass -File scripts/abrir-terminais.ps1   # layout de terminais da banca
```

> Ordem dos módulos no `pom.xml` raiz: `shared-contracts`, `estatisticas-service`, `backlog-service`, `catalogo-service`. Cada consumidor gera o seu pact antes de o provedor verificá-lo: estatísticas → mensagem verificada pelo backlog; backlog → REST verificado pelo catálogo. O CI falha se o pact regenerado diferir do commitado (`.gitattributes` mantém `eol=lf`).

## Regras do projeto

1. **Nenhum serviço lê o banco de outro.** Dados de outro contexto entram por REST ou evento, e o backlog guarda um *snapshot* (título, duração) do jogo.
2. **Eventos vivem em `shared-contracts`.** Mudou o evento → incrementa `versao`, atualiza o pact e o ADR, se a mudança for de decisão.
3. **Toda decisão arquitetural vira ADR** em `docs/adr/ADR-NNN-titulo.md` (contexto, decisão, alternativas, trade-offs). ADR descreve *por quê*, não *o quê*.
4. **Toda evidência nova vai para o `AVALIACAO.md`** (critério → arquivo/classe/teste/commit). O que não estiver lá a banca não considera.
5. **Consumidor sempre idempotente:** registrar `(eventId, consumidor)` em `eventos_processados` na **mesma transação** da atualização (verifica e insere; a PK é a rede de segurança). **Produtor sempre via outbox:** nunca publicar no Kafka direto de um service.
6. **Logs de demo** usam o padrão `HH:mm:ss.SSS NIVEL [correlationId] Classe - mensagem` com marcadores visuais (`→ REST`, `⇢ KAFKA PUB`, `⇠ KAFKA SUB`, `✔ CACHE HIT`, `✘ CACHE MISS`, `🗑 CACHE EVICT`, `⟳ RETRY`, `☠ DLT`, `⚡ BREAKER`, `↺ DUPLICADO`). O `correlationId` vem do header HTTP `X-Correlation-Id` (validado por `[A-Za-z0-9._-]{1,64}`), passa pelo outbox e vai no header Kafka `correlationId`. Texto vindo de fora (título, motivo) é limpo de caracteres de controle antes de logar.
7. `./mvnw verify` precisa passar **sem Docker** antes de cada PR. Testes não podem alterar dados do seed (o H2 em memória é compartilhado entre contextos Spring): criem os próprios registros.
8. Mudou comportamento descrito num ADR, no `docs/arquitetura.md` ou no `docs/APRESENTACAO.md`? Atualize o documento no mesmo PR. A revisão já encontrou ADR contradizendo o código.
9. Registre todo uso relevante de IA em `docs/ia/registro-uso-ia.md` (o que foi pedido, o que veio, **o que validamos à mão**). Isso alimenta o critério 8.

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
