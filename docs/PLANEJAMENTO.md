# Planejamento de implementação — LetterGames (Backlog de Jogos)

> Fonte do escopo: [brief-alunos.md](../brief-alunos.md). Este documento diz **o que** construir, **quem** constrói, **em que ordem** e **como cada entrega vira nota**.

---

## 0. Alerta de prazo

A banca é na **segunda, 26/10**. Hoje é **sexta, 23/10**: sobram **3 dias de trabalho + o dia da banca**. Consequências:

- Fazemos **só o CORE + resiliência básica + correlation id**. Resiliência é critério da rubrica (12 pts), e o correlation id é o que deixa a demo em vários terminais legível.
- Tudo o que está em **§7 Extras** só entra se o CORE estiver verde e ensaiado.
- O brief pede commits **ao longo das semanas**. Não dá para recuperar isso, então vamos **commitar pequeno e com frequência** e assumir o prazo com honestidade no `AVALIACAO.md`.

---

## 1. Produto

**Problema:** quem joga acumula dezenas de jogos comprados e nunca jogados (o "backlog de vergonha") e não sabe quantas horas tem pela frente.

**Solução:** três microsserviços.

| # | História | Serviço |
|---|---|---|
| H1 | Como admin, cadastro jogos no catálogo (título, plataforma, gênero, duração média em horas). | catalogo |
| H2 | Como jogador, consulto um jogo rapidamente (cache). | catalogo |
| H3 | Como jogador, adiciono um jogo do catálogo ao meu backlog. | backlog → catalogo (REST) |
| H4 | Como jogador, mudo o status: `QUERO_JOGAR → JOGANDO → ZERADO` ou `ABANDONADO`. | backlog |
| H5 | Como jogador, vejo minhas estatísticas: total por status, horas pendentes e % zerado. | estatisticas (via Kafka) |
| H6 | Como jogador, ganho conquistas ("Primeiro zerado", "5 zerados", "Maratonista: 100h zeradas"). | estatisticas |

Não há serviço de usuário: `usuarioId` é uma string no path (`/usuarios/joao/...`). Essa decisão fica registrada no ADR-001.

---

## 2. Arquitetura em uma tela

```
            REST (timeout + retry + circuit breaker)
 [backlog-service] ──────────────────────────────► [catalogo-service] ──► Redis (cache-aside, TTL 10min)
       │  pg-backlog                                     │  pg-catalogo
       │
       │ ⇢ Kafka: backlog.eventos  (key = usuarioId, at-least-once)
       ▼
 [estatisticas-service] ── idempotente (eventos_processados) ── pg-estatisticas
       │ falha permanente
       ▼
 backlog.eventos.DLT
```

Detalhes em [arquitetura.md](arquitetura.md).

### Contratos

**REST:** catalogo expõe `GET /jogos/{id}` → `{id, titulo, plataforma, genero, duracaoMediaHoras}`. É o contrato coberto pelo Pact (consumidor: backlog; provedor: catalogo).

**Evento** `BacklogEvento` (em `shared-contracts`):

```json
{
  "eventId": "6f1c...-uuid",
  "tipo": "JOGO_ADICIONADO | STATUS_ALTERADO | JOGO_REMOVIDO",
  "versao": 1,
  "ocorridoEm": "2026-10-24T14:03:11Z",
  "usuarioId": "joao",
  "itemId": 17,
  "jogoId": 42,
  "titulo": "Hollow Knight",
  "duracaoMediaHoras": 27,
  "statusAnterior": "JOGANDO",
  "statusNovo": "ZERADO"
}
```

Headers Kafka: `correlationId`, `tipo`.

### Endpoints

| Serviço | Método e rota |
|---|---|
| catalogo | `POST /jogos` · `GET /jogos/{id}` · `GET /jogos?plataforma=` · `PUT /jogos/{id}` · `DELETE /jogos/{id}` |
| backlog | `POST /usuarios/{u}/backlog` `{jogoId}` · `PATCH /usuarios/{u}/backlog/{itemId}` `{status}` · `GET /usuarios/{u}/backlog` · `DELETE /usuarios/{u}/backlog/{itemId}` |
| estatisticas | `GET /usuarios/{u}/estatisticas` · `GET /usuarios/{u}/conquistas` |

---

## 3. Papéis (3 pessoas)

O brief sugere 4 papéis (arquiteto, mensageria, cache/dados, testes/contrato). Com 3 pessoas, juntamos **cache/dados com testes/contrato**: os dois tocam a borda catálogo ↔ backlog. A divisão segue o peso dos critérios.

| Pessoa | Papel | Critérios (peso) | Código sob responsabilidade |
|---|---|---|---|
| **A** | Arquiteto + Infra/Demo | 1 (15) · 7 (13) · 8 (5) · 9 (5) = **38** | pom raiz, docker-compose, Dockerfiles, CI, `backlog-service` (CRUD), scripts de demo, ADRs, README |
| **B** | Dev Mensageria | 2 (15) · 3 (12) · 5-Kafka (≈6) = **≈33** | `shared-contracts` (eventos), producer do backlog, `estatisticas-service` inteiro, retry/DLT |
| **C** | Dev Dados/Cache + Testes/Contrato | 4 (10) · 6 (13) · 5-REST (≈6) = **≈29** + qualidade dos testes de todos | `catalogo-service` inteiro, cliente REST + circuit breaker no backlog, Pact consumidor e provedor, perfis Maven |

> **Regra de defesa:** cada pessoa precisa explicar **as decisões das outras duas** em nível básico. A banca pode perguntar qualquer coisa a qualquer um. Ver §6 do [APRESENTACAO.md](APRESENTACAO.md).

---

## 4. Tarefas por fase

Legenda: **Dono** · **Crit.** = critério da rubrica · **Pronto quando** = critério de aceite.
Cada tarefa vira **uma branch, um PR e pelo menos um commit** (testes em commit `test:` separado).

### F0 — Fundação (Dia 1, manhã) · bloqueia todo o resto

| ID | Tarefa | Dono | Branch | Pronto quando |
|---|---|---|---|---|
| F0.1 | Criar repositório privado no GitHub, `main` + `develop`, `.gitignore`, `VERSION`, `CHANGELOG.md` | A | `chore/estrutura-inicial` | repo criado e `develop` publicada |
| F0.2 | `pom.xml` raiz multi-módulo (Spring Boot 3.3 parent, Java 21) + Maven Wrapper + perfis `perfil-a`/`perfil-b`/`perfil-c` + 4 módulos vazios que sobem | A | `chore/maven-multimodulo` | `./mvnw verify` verde com 3 apps "hello" |
| F0.3 | `docker-compose.yml`: 3 Postgres, Redis, Kafka KRaft, Kafka UI, 3 serviços com healthcheck e `depends_on: service_healthy`, `JAVA_TOOL_OPTIONS=-Xmx256m` | A | `chore/docker-compose` | `docker compose up -d` deixa tudo `healthy` |
| F0.4 | Dockerfile multi-stage por serviço (build com `maven:3.9-eclipse-temurin-21`, runtime `eclipse-temurin:21-jre`) | A | (junto com F0.3) | imagens com menos de 300 MB |
| F0.5 | CI no GitHub Actions copiando `pethorian/GestaFinanceiraBox`: `ci.yml` (verify, build, versão, `ci-ok`, `auto-aprovar`), `release.yml`, `guarda-commits.yml`, PR template, `CONTRIBUTING.md` | A | `ci/pipeline-inicial` | PR de teste fica verde e é mergeado sozinho |
| F0.6 | `BacklogEvento` + enums (`TipoEvento`, `StatusBacklog`) + `JogoDTO` em `shared-contracts` | B | `feat/contratos-eventos` | módulo compila e é dependência dos 3 serviços |
| F0.7 | Logback compartilhado: padrão com `%X{correlationId}`, cores, nome do serviço + filtro HTTP que lê/gera `X-Correlation-Id` | C | `feat/correlation-id` | log mostra o mesmo id nos 3 serviços |

### F1 — Catálogo + Cache (Dia 1, tarde) · Crit. 1, 4

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F1.1 | CRUD de `Jogo` (JPA + Postgres + Flyway `V1__jogos.sql`) + seed com ~10 jogos | C | `curl /jogos/1` responde |
| F1.2 | Cache-aside com `@Cacheable("jogos")` no `GET /jogos/{id}`, TTL 10 min via `RedisCacheConfiguration`, serialização JSON | C | 2º GET não loga SQL e loga `✔ CACHE HIT` |
| F1.3 | Invalidação explícita: `@CacheEvict` em PUT/DELETE (`@CachePut` foi avaliado e descartado no ADR-004) | C | GET → PUT → GET devolve o valor novo |
| F1.4 | Fallback: `CacheErrorHandler` que loga `WARN` e segue para o banco se o Redis cair | C | `docker stop redis` → catálogo continua respondendo |
| F1.5 | Testes: cache hit/miss e invalidação com `ConcurrentMapCacheManager` no perfil B; teste do `CacheErrorHandler` | C | `./mvnw verify` verde sem Docker |

### F2 — Backlog + chamada REST + Contrato (Dia 1 tarde → Dia 2 manhã) · Crit. 1, 5, 6

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F2.1 | Entidade `ItemBacklog` (snapshot de `titulo` e `duracaoMediaHoras`), regras de transição de status, endpoints | A | adicionar/alterar/listar funcionam com o catálogo mockado |
| F2.2 | `CatalogoClient` com `RestClient`: timeout de connect 1 s e read 2 s, propagação de `X-Correlation-Id` | C | logs `→ REST GET catalogo/jogos/42` com o mesmo id dos dois lados |
| F2.3 | Resilience4j: `@Retry` (3 tentativas, backoff exponencial 200 ms, só para IO/5xx) + `@CircuitBreaker` (janela 10, 50% de falha, aberto por 15 s) + fallback que devolve **503** "catálogo indisponível" | C | `docker stop catalogo-service` → depois de N falhas, log `⚡ BREAKER OPEN` e resposta imediata |
| F2.4 | **Pact consumidor** no backlog: interação `GET /jogos/42` → grava `shared-contracts/pacts/backlog-service-catalogo-service.json` | C | arquivo pact gerado no `verify` |
| F2.5 | **Pact provedor** no catálogo: `@Provider("catalogo-service")` + `@PactFolder` + `@State("jogo 42 existe")` | C | verificação passa; quebrar um campo derruba o build |
| F2.6 | Testes do circuit breaker (WireMock ou `MockRestServiceServer`): transitório (falha 2×, depois sucesso) × permanente (abre o breaker) | C | dois testes verdes |

### F3 — Mensageria + Idempotência (Dia 2) · Crit. 2, 3

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F3.1 | Producer no backlog: `KafkaTemplate<String, BacklogEvento>`, chave `usuarioId`, `acks=all`, `enable.idempotence=true`, header `correlationId`; publica **depois do commit** (`@TransactionalEventListener(AFTER_COMMIT)`) | B | log `⇢ KAFKA PUB backlog.eventos key=joao` |
| F3.2 | Criação de tópicos via `NewTopic` (3 partições) + `backlog.eventos.DLT` | B | tópicos aparecem no Kafka UI |
| F3.3 | `estatisticas-service`: `@KafkaListener` (group `estatisticas`), `enable-auto-commit=false`, `AckMode.RECORD` | B | log `⇠ KAFKA SUB` com o mesmo `correlationId` |
| F3.4 | **Idempotência**: tabela `eventos_processados(event_id PK, processado_em)`. Na **mesma transação**, insere o `eventId` e atualiza `estatisticas_usuario`. Se o id já existe → log `↺ DUPLICADO ignorado` e ack | B | reenviar o mesmo evento não altera os contadores |
| F3.5 | Regras de estatística (contadores por status, horas pendentes, % zerado) + conquistas (dispara uma única vez por usuário) | B | `GET /usuarios/joao/estatisticas` correto |
| F3.6 | **Teste de reprocessamento**: EmbeddedKafka + H2, publica o mesmo evento 2× → contadores iguais a 1× | B | teste verde no perfil B |

### F4 — Resiliência no consumidor (Dia 2 tarde) · Crit. 5

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F4.1 | `DefaultErrorHandler` com `ExponentialBackOff` (1 s, ×2, 4 tentativas) + `DeadLetterPublishingRecoverer` → `backlog.eventos.DLT` | B | log `⟳ RETRY 1/4 ...` e depois `☠ DLT` |
| F4.2 | Exceções não-retentáveis (`DeserializationException`, `EventoInvalidoException`) vão direto para a DLT; `ErrorHandlingDeserializer` contra *poison pill* | B | mensagem lixo cai na DLT sem travar a partição |
| F4.3 | Testes: falha transitória (repositório falha 2×, depois sucesso → processa 1×) × permanente (vai para a DLT) | B | dois testes verdes |

### F5 — Demo em terminais (Dia 3 manhã) · Crit. 9 + banca

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F5.1 | `scripts/abrir-terminais.ps1`: Windows Terminal (`wt`) em grade 2×3 (ver [APRESENTACAO.md §2](APRESENTACAO.md)) | A | um comando abre o layout inteiro |
| F5.2 | Scripts `scripts/demo/01..07-*.sh` (curl + `jq`), com `X-Correlation-Id` legível (`demo-01`, `demo-02`...) | A | cada cena da demo roda com 1 comando |
| F5.3 | Script de evento duplicado (publica o mesmo JSON 2× via `kafka-console-producer`) e de *poison pill* | B | cena 5 e 7 reproduzíveis |
| F5.4 | Revisão dos marcadores visuais de log nos 3 serviços (padrão em CLAUDE.md, regra 6) | C | logs limpos, sem ruído de framework (`logging.level.org.apache.kafka=WARN`) |

### F6 — Documentação e evidência (Dia 3) · Crit. 7, 8, 9

| ID | Tarefa | Dono | Pronto quando |
|---|---|---|---|
| F6.1 | Revisar os 7 ADRs: trocar `Proposto` → `Aceito` e ajustar números reais (TTL, backoff) | A (revisão: todos) | cada ADR tem alternativas e trade-offs |
| F6.2 | `docs/arquitetura.md` final + README "como rodar" testado do zero (`git clone` → `docker compose up`) | A | outra pessoa do grupo sobe seguindo só o README |
| F6.3 | `AVALIACAO.md` preenchido: cada critério → arquivo/classe/teste/commit | **cada um preenche os seus critérios** | sem nenhum "TODO" |
| F6.4 | `docs/ia/registro-uso-ia.md`: cada pessoa registra pelo menos 3 usos de IA, com o que foi validado ou corrigido à mão | todos | reflexão honesta, com erros da IA que pegamos |
| F6.5 | Evidência de execução: prints/asciinema da demo em `docs/evidencias/` + saída do `./mvnw verify` | A | arquivos commitados |
| F6.6 | `CHANGELOG` + bump para `0.1.0-beta.1` + PR `develop → main` | A | release criada |

### F7 — Ensaio (Dia 3 noite + Dia 4 manhã)

- 2 ensaios cronometrados (meta: **17 min**, sobrando 3 para arguição).
- 1 rodada de "arguição cruzada": um faz perguntas do §6 do APRESENTACAO ao outro, **fora da sua área**.
- Ensaio do **Plano B** (Docker falhou): rodar `./mvnw verify` e mostrar os testes de reprocessamento, DLT e pact.

---

## 5. Cronograma

| Dia | Pessoa A (Arquiteto) | Pessoa B (Mensageria) | Pessoa C (Cache/Contrato) |
|---|---|---|---|
| **Sex 23** | F0.1–F0.5 (repo, Maven, compose, CI) · ADR-001 | F0.6 eventos · ADR-002 | F0.7 correlation id · F1 catálogo + cache · ADR-004 |
| **Sáb 24** | F2.1 backlog CRUD · ADR-007 | F3 producer, consumer, idempotência · ADR-003 | F2.2–F2.6 REST, breaker, Pact · ADR-005 (REST) e 006 |
| **Dom 25** | F5.1–F5.2 terminais e demo · F6.1, F6.2, F6.5 | F4 retry/DLT · F5.3 · ADR-005 (Kafka) | F5.4 logs · revisão de testes · F6.3 |
| **Dom 25 (noite)** | **Ensaio 1** (todos) · F6.4 · F6.6 | | |
| **Seg 26** | **Ensaio 2** · checklist pré-banca · **BANCA** | | |

**Pontos de sincronização** (15 min, todos):

1. Sex, 12h: contratos `BacklogEvento` e `JogoDTO` congelados (B e C dependem disso).
2. Sáb, 12h: fluxo ponta a ponta (catalogo → backlog → kafka → estatisticas) funcionando no compose.
3. Dom, 14h: *feature freeze*. Daí em diante só bug, log, doc e ensaio.

---

## 6. Matriz critério → evidência → dono

| # | Critério | Peso | Evidência planejada | Dono |
|---|---|:---:|---|---|
| 1 | Decomposição | 15 | 3 módulos, 3 Postgres no compose, snapshot no `ItemBacklog`, `arquitetura.md`, ADR-001 | A |
| 2 | Assíncrono | 15 | `KafkaProducerConfig`, `BacklogEventoListener`, garantia **at-least-once** declarada no README, ADR-002 | B |
| 3 | Idempotência | 12 | `eventos_processados`, `ReprocessamentoIdempotenteTest`, ADR-003 (+ outbox se der tempo) | B |
| 4 | Cache | 10 | `CacheConfig` (TTL), `@CacheEvict`, `CacheErrorHandler`, `CacheInvalidacaoTest`, ADR-004 | C |
| 5 | Resiliência | 12 | Resilience4j no `CatalogoClient`, `DefaultErrorHandler` + DLT, testes transitório × permanente nos dois lados, ADR-005 | B + C |
| 6 | Testabilidade | 13 | pact file + verificação no provedor, `./mvnw verify` verde **sem Docker**, EmbeddedKafka/H2, ADR-006 e ADR-007 | C |
| 7 | ADRs | 13 | `docs/adr/ADR-001..007` com alternativas e trade-offs | A (todos escrevem) |
| 8 | Uso de IA | 5 | `docs/ia/registro-uso-ia.md` + seção no README/AVALIACAO | A (todos registram) |
| 9 | Execução | 5 | README "como rodar", perfil **A** declarado, `docs/evidencias/` | A |

---

## 7. Extras (só depois do CORE verde)

Em ordem de custo × retorno na nota:

| # | Extra | Dono | Ganho |
|---|---|---|---|
| S1 | **Transactional outbox** no backlog (tabela `outbox` + `@Scheduled` publicador) em vez de `AFTER_COMMIT` | B | Crit. 3 → nível 3; elimina a perda de evento no *dual write* |
| S2 | **2º consumer group** `conquistas` (listener separado, idempotência por `(event_id, consumidor)`) | B | demo de *fan-out* em terminal; opcional "Kafka com múltiplos consumer groups" |
| S3 | **Message Pact** do `BacklogEvento` (backlog → estatisticas) | C | opcional "2º par de contract test"; Crit. 6 → nível 3 |
| S4 | Testcontainers no `perfil-a` (Postgres/Redis/Kafka reais) | C | reforça Crit. 6 |
| S5 | Front mínimo (página estática consumindo as APIs) | qualquer um | só se sobrar tempo, como o grupo combinou |

---

## 8. Riscos

| Risco | Prob. | Mitigação |
|---|---|---|
| Notebook sem RAM para 3 JVMs + Kafka + 3 Postgres | Média | `-Xmx256m`, Postgres `alpine`, Kafka com `KAFKA_HEAP_OPTS=-Xmx512m`; ensaiar na máquina da banca |
| Docker falhar na hora | Baixa/alta dor | Plano B: `./mvnw verify` + gravação da demo em `docs/evidencias/` |
| Pact: ordem dos módulos (provedor roda antes do pact existir) | Média | ordem no `pom.xml` raiz (ver CLAUDE.md) + pact commitado em `shared-contracts/pacts` |
| Três pessoas no `backlog-service` ao mesmo tempo | Alta | pacotes separados: `api/` e `dominio/` (A), `catalogo/` (C), `eventos/` (B); PRs pequenos |
| Maven não instalado | Certa | Maven Wrapper (`mvnw`) commitado em F0.2 |
| Prazo | Alta | *feature freeze* domingo 14h; extras só depois do CORE |

---

## 9. Definição de pronto (vale para todo PR)

- [ ] `./mvnw verify` verde **sem Docker**
- [ ] Teste novo para comportamento novo (em commit `test:`)
- [ ] Log com o marcador visual correto para a demo
- [ ] Se for decisão de arquitetura → ADR criado ou atualizado
- [ ] Linha correspondente no `AVALIACAO.md` atualizada
- [ ] Uso de IA relevante registrado em `docs/ia/registro-uso-ia.md`
