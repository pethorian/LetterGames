# LetterGames — Backlog de Jogos

Projeto final do módulo **BE-JV-010 (Nível III)**. São três microsserviços Java/Spring que gerenciam o backlog de jogos de um jogador, combinando:

- comunicação síncrona (REST com timeout, retry e circuit breaker);
- comunicação assíncrona (transactional outbox + Kafka + consumidor idempotente com DLT);
- cache (Redis, cache-aside com TTL e invalidação);
- dois contratos executáveis (Pact REST e Pact de mensagem).

## Problema

Quem joga acumula jogos comprados e nunca jogados e não sabe quantas horas tem pela frente. O LetterGames organiza o backlog (`QUERO_JOGAR`, `JOGANDO`, `ZERADO`, `ABANDONADO`) e calcula de forma assíncrona as estatísticas (horas pendentes, % zerado) e as conquistas ("Primeiro zerado!", "Maratonista: 100h zeradas").

## Arquitetura

```
                 REST + timeout + retry + circuit breaker
[backlog-service] ─────────────────────────────────────► [catalogo-service] ──► Redis (cache-aside, TTL 10 min)
   │ pg-backlog (itens + outbox)                             │ pg-catalogo
   │ publicador do outbox → Kafka: backlog.eventos (key = usuarioId, 3 partições)
   ▼
[estatisticas-service]  group "estatisticas" ─┐
                        group "conquistas"  ──┴─► pg-estatisticas (idempotente: eventos_processados)
                        inválido / processamento esgotado → backlog.eventos.DLT
```

Detalhes: [docs/arquitetura.md](docs/arquitetura.md) · Decisões: [docs/adr/](docs/adr/) · Auto-avaliação: [AVALIACAO.md](AVALIACAO.md)

| Serviço | Porta | Responsabilidade |
|---|---|---|
| catalogo-service | 8081 | Jogos (CRUD), com cache Redis |
| backlog-service | 8082 | Backlog do jogador; valida no catálogo; publica eventos via outbox |
| estatisticas-service | 8083 | Estatísticas e conquistas a partir dos eventos (dois consumer groups) |
| Kafka UI | 8080 | Tópicos, consumer groups e DLT |

## Garantias declaradas

- **Entrega: at-least-once.**
  - Produtor: o evento é gravado no outbox na mesma transação do item e publicado depois, com `acks=all` e idempotência ligada.
  - Consumidor: o offset só é commitado depois do commit no banco (`AckMode.RECORD`, auto-commit desligado).
- **Consumo: idempotente** por `(eventId, consumidor)` em `eventos_processados`, na mesma transação do efeito. Resultado: efeito **exatamente uma vez**.
- **Ordem: por usuário.** A chave da partição é o `usuarioId`, o outbox publica só a cabeça de fila de cada usuário (ordem de gravação) e o retry do consumidor é bloqueante. Para o mesmo item, a ordem de gravação é a de commit (lock de linha, ADR-008); entre itens diferentes do mesmo usuário pode não ser, e as projeções não dependem disso (contadores comutativos).
- **Falhas no consumidor:**
  - inválida → DLT direto;
  - transitória (banco, lock) → retry com backoff até 10 min, segurando a partição;
  - de processamento → 4 tentativas → DLT.
- **Cache:** cache-aside, TTL 10 min, evict depois do commit mais um segundo evict atrasado, fallback para o banco se o Redis cair.

## Perfil de execução

**Perfil A (Docker): tudo roda em containers.**

Os testes rodam por padrão no **perfil B (pura JVM, sem Docker)**. Fallbacks usados **só nos testes**:

- H2 em modo PostgreSQL no lugar do Postgres;
- `@EmbeddedKafka` no lugar do broker;
- cache em memória no lugar do Redis;
- servidor HTTP falso no lugar do catálogo.

O perfil Maven `perfil-a` acrescenta testes com Testcontainers (Postgres e Redis reais). Ver [ADR-007](docs/adr/ADR-007-perfis-de-execucao.md).

## Como rodar

Pré-requisitos:

- JDK 21 (o Maven vem pelo wrapper `./mvnw`);
- Docker Desktop com ~6 GB de RAM livres;
- Git Bash no Windows, com `curl` e `node` (os scripts usam `jq` se houver, senão `node` para formatar o JSON).

### 1. Subir tudo (perfil A)

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

O `docker compose ps` deve mostrar todos os serviços como `healthy`. A primeira subida leva alguns minutos (build das imagens), e as portas ficam só em `127.0.0.1`.

### 2. Rodar a demo

```bash
./scripts/demo/00-reset.sh
```

```bash
./scripts/demo/demo-completa.sh
```

Para ver em vários terminais lado a lado (Windows Terminal), rode no PowerShell:

```bash
powershell -ExecutionPolicy Bypass -File scripts/abrir-terminais.ps1
```

As cenas, uma a uma, estão em `scripts/demo/01..07-*.sh`. Roteiro completo e falas: [docs/APRESENTACAO.md](docs/APRESENTACAO.md).

### 3. Testes

Perfil B, sem Docker:

```bash
./mvnw verify
```

Perfil A, com Testcontainers (exige Docker):

```bash
./mvnw verify -Pperfil-a
```

### 4. Explorar à mão

```bash
curl -s -X POST http://127.0.0.1:8082/usuarios/joao/backlog -H 'Content-Type: application/json' -H 'X-Correlation-Id: teste-1' -d '{"jogoId":1}'
```

```bash
curl -s http://127.0.0.1:8083/usuarios/joao/estatisticas
```

O catálogo vem com 10 jogos de seed (ids 1 a 10). Os logs mostram o `X-Correlation-Id` em todos os serviços:

```bash
docker compose logs -f backlog-service
```

### 5. Desligar

```bash
docker compose down -v
```

## Configuração

| Variável (compose / `.env`) | Padrão | Uso |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `lettergames` | credenciais dos 3 Postgres (**ambiente local**) |
| `CATALOGO_DB`, `BACKLOG_DB`, `ESTATISTICAS_DB` | `catalogo`, `backlog`, `estatisticas` | nome de cada base |
| `SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`, `CATALOGO_URL` | definidos no compose | endereços entre containers |

Propriedades principais:

- `catalogo.cache.ttl` (10m) e `catalogo.cache.segundo-evict` (500ms);
- `backlog.outbox.intervalo` (500ms) e `backlog.outbox.max-tentativas` (10);
- `estatisticas.retry.transitoria.*` e `estatisticas.retry.processamento.*`;
- `resilience4j.*` no `application.yml` do backlog.

## Grupo

| Pessoa | Papel |
|---|---|
| _(nome)_ | A — Arquiteto + Infra/Demo |
| _(nome)_ | B — Dev Mensageria |
| _(nome)_ | C — Dev Dados/Cache + Testes/Contrato |

## Uso de IA

Usamos IA (Claude Code) para planejar, implementar e, principalmente, **revisar**. Registro honesto, com o que validamos à mão e o que a revisão pegou: [docs/ia/registro-uso-ia.md](docs/ia/registro-uso-ia.md) (critério 8 do [AVALIACAO.md](AVALIACAO.md)).

## Fluxo de trabalho

Branch por demanda a partir da `develop`, PR com CI (perfis B e A, build Docker, versão e origem da branch) e merge automático com tudo verde. Ver [CONTRIBUTING.md](CONTRIBUTING.md).
