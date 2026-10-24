# Auto-avaliação — LetterGames (Backlog de Jogos)

Grupo: _(nome A)_ — Arquiteto + Infra/Demo · _(nome B)_ — Dev Mensageria · _(nome C)_ — Dev Dados/Cache + Testes/Contrato
Tema/domínio: backlog de jogos. O jogador organiza jogos do catálogo por status (quero jogar, jogando, zerado, abandonado) e recebe estatísticas e conquistas calculadas de forma assíncrona.
Perfil de execução: **A** (tudo em Docker) · Fallbacks usados: H2 (modo PostgreSQL), `@EmbeddedKafka`, cache em memória e servidor HTTP falso, **apenas nos testes do perfil B**. O perfil A também roda testes com Postgres e Redis reais (Testcontainers).

> Caminhos relativos à raiz do repositório. "Testes" = classes em `*/src/test/java`. Execução comprovada em [docs/evidencias/](docs/evidencias/).

## Evidências por critério

### 1. Decomposição de domínio — nível auto-atribuído: 3 · _dono: A_
- **3 bounded contexts, um módulo cada:** `catalogo-service/`, `backlog-service/`, `estatisticas-service/`. O que é compartilhado é só contrato: `shared-contracts/` (`JogoDTO`, `BacklogEvento`, `Topicos`, pacts).
- **Bases segregadas:**
  - `docker-compose.yml` (`postgres-catalogo`, `postgres-backlog`, `postgres-estatisticas`);
  - migrations próprias em `*/src/main/resources/db/migration/`;
  - nenhum serviço conhece a URL do banco de outro.
- **Sem acoplamento por dados:**
  - snapshot de `titulo` e `duracaoMediaHoras` em `backlog-service/.../dominio/ItemBacklog.java`;
  - as estatísticas só recebem eventos (`estatisticas-service/.../consumo/LeitorEventos.java`).
- **Documentação:** [docs/arquitetura.md](docs/arquitetura.md) (diagrama de contextos e fluxo de eventos) e [ADR-001](docs/adr/ADR-001-decomposicao-em-tres-servicos.md) (ADR de corte).

### 2. Comunicação assíncrona — 3 · _dono: B_
- **Producer:**
  - `backlog-service/.../eventos/OutboxRegistrador.java` grava o evento na mesma transação do item;
  - `OutboxPublicador.java` publica com key = `usuarioId` e headers `correlationId`/`tipo`;
  - `KafkaConfig.java` declara o `NewTopic` com 3 partições;
  - `application.yml` configura `acks: all` e `enable.idempotence: true`.
- **Consumer:** `estatisticas-service/.../consumo/EstatisticasListener.java` e `ConquistasListener.java`, **dois consumer groups** (`estatisticas`, `conquistas`).
  - `KafkaConfig.java` define as factories por group e o `DefaultErrorHandler`.
  - `application.yml` configura `enable-auto-commit: false` e `ack-mode: record`.
- **Mensagem × evento:** publicamos fatos no passado (`TipoEvento`: `JOGO_ADICIONADO`, `STATUS_ALTERADO`, `JOGO_REMOVIDO`). O produtor não conhece os consumidores; o group `conquistas` entrou sem mudar o backlog.
- **Garantia de entrega declarada:** at-least-once + consumidor idempotente = efeito exatamente uma vez. Está no [README](README.md#garantias-declaradas) e no [ADR-002](docs/adr/ADR-002-kafka-como-broker.md).
- **Testes:**
  - `OutboxPublicadorTest`: publica em ordem com key e headers, num EmbeddedKafka real;
  - `DoisConsumerGroupsTest`: o mesmo evento chega aos dois groups, com o correlationId do header no MDC.

### 3. Idempotência e consistência — 3 · _dono: B_
- **Chave de idempotência:** `eventId` (UUID) do `BacklogEvento`.
  - Tabela `eventos_processados (event_id, consumidor)` com PK composta: `estatisticas-service/src/main/resources/db/migration/V1__estatisticas_e_eventos_processados.sql`.
  - `consumo/RegistroIdempotencia.java` verifica e insere dentro da transação do efeito; a PK é a rede de segurança da corrida.
- **Teste que reprocessa sem duplicar** (`ReprocessamentoIdempotenteTest`):
  - o mesmo registro publicado 2×;
  - duplicata não adjacente (A, B, A);
  - duas threads processando o mesmo evento ao mesmo tempo (corrida real na PK).
  - Em todos: 1 efeito e 1 linha por consumidor.
- **Rollback correto:** `FalhaTransitoriaTest`. A falha acontece depois do registro de idempotência, e a nova tentativa processa normalmente.
- **Outbox (consistência banco ↔ broker):**
  - `backlog-service/.../eventos/` com as migrations V2, V4 e V5;
  - atomicidade: `BacklogServiceTest` (se o outbox falha, o item não é salvo);
  - at-least-once comprovado: `OutboxPublicadorTest#envioConfirmadoComFalhaAoMarcarRepublicaOMesmoEventIdNoCicloSeguinte`.
- **Concorrência:** lock pessimista + `@Version` em PATCH e DELETE (`ItemBacklogRepository#findByIdAndUsuarioIdParaAtualizar`, migration V3), coberto por `AlteracaoConcorrenteTest`. A ordem do outbox não depende do relógio (coluna `sequencia`).
- **ADRs:** [ADR-003](docs/adr/ADR-003-consumidor-idempotente.md) e [ADR-008](docs/adr/ADR-008-concorrencia-no-backlog.md).
- SAGA não se aplica: não há transação de negócio distribuída que precise de compensação. O único fluxo entre serviços com efeito é uma projeção derivada.

### 4. Cache — 3 · _dono: C_
- **Camada de cache:** `catalogo-service/.../cache/`.
  - `CacheRedisConfig`: TTL de 10 min, JSON, sem nulos.
  - `CacheConfig`: `@EnableCaching` por fora da transação e `CacheErrorHandler`.
  - `CacheManagerComLog` e `CacheComLog`: logs de HIT e EVICT e o segundo evict atrasado.
  - `@Cacheable` e `@CacheEvict` em `dominio/JogoService.java`.
- **Estratégia e invalidação:** cache-aside; evict depois do commit em PUT e DELETE, mais um segundo evict a 500 ms (`catalogo.cache.segundo-evict`).
- **Fallback:** Redis fora → WARN e leitura do banco.
- **Testes:**
  - `JogoCacheTest`: o 2º GET não vai ao banco, PUT e DELETE invalidam, criar não popula, 404 não é cacheado, listagem não é cacheada;
  - `CacheRedisConfigTest` (TTL e serialização);
  - `CacheIndisponivelTest` (fallback);
  - `SegundoEvictTest` e `SegundoEvictDesligadoTest`;
  - **perfil A:** `CatalogoInfraRealTest`, com Redis real: TTL expirando, evict e fallback com o container parado.
- **ADR:** [ADR-004](docs/adr/ADR-004-cache-aside-redis.md) (inclui a corrida que sobra e como o TTL limita o dano).

### 5. Resiliência — 3 · _donos: C (REST) e B (Kafka)_
- **REST:** `backlog-service/.../catalogo/CatalogoClient.java`, com `@Retry` + `@CircuitBreaker`.
  - Timeouts de 1 s e 2 s; config em `application.yml` (`resilience4j.*`); logs em `ResilienciaCatalogoLogs.java`.
  - 404 → 422 e outros 4xx → 502, ambos sem retry e fora do breaker.
- **Produtor:** isolamento por chave, estacionamento depois de 10 falhas e espera maior que `delivery.timeout.ms` (`OutboxPublicador.java`).
- **Consumidor:** `estatisticas-service/.../consumo/KafkaConfig.java` com `ClassificadorFalhas`, `LogDeRetry` e `RecuperadorDlt`.
  - Inválido → DLT sem retry; transitório → backoff até 10 min segurando a partição; processamento → 4 tentativas → DLT `backlog.eventos.DLT`.
- **Testes de falha transitória × permanente, nos dois lados:**
  - **REST** (`CatalogoClientResilienciaTest`):
    - 500, 500 e depois 200 → sucesso;
    - 404 e 400 sem retry;
    - breaker abre e recusa sem tocar o servidor;
    - HALF_OPEN → CLOSED;
    - timeout.
  - **Kafka:**
    - `FalhaTransitoriaTest`: banco fora 6× → nada na DLT; erro de processamento → DLT;
    - `FalhaPermanenteTest`: JSON inválido, sem eventId e com enum desconhecido → DLT sem retry, partição não trava;
    - `PoliticaRetryTest`.
  - **Produtor:** `OutboxPublicadorTest` (falha de uma chave não trava as outras; Kafka fora mantém pendente).
- **ADR:** [ADR-005](docs/adr/ADR-005-resiliencia.md).

### 6. Testabilidade — 3 · _dono: C_
- **Dois contratos executáveis (dois pares de contract test)**, com os pact files em `shared-contracts/pacts/`:
  - **REST:** `backlog-service-catalogo-service.json`, gerado por `CatalogoClientPactTest` (backlog) e verificado por `CatalogoPactProviderTest` (catálogo).
  - **Mensagem:** `estatisticas-service-backlog-service.json` (Pact V4), gerado por `BacklogEventoPactConsumerTest` (estatísticas) e verificado por `BacklogEventoPactProviderTest` (backlog, com o payload real gravado no outbox).
  - Ordem do reactor Maven: consumidor antes do provedor (`pom.xml`).
  - O CI falha se o pact regenerado diferir do commitado (`.github/workflows/ci.yml`).
- **`mvn verify` verde sem Docker (perfil B):** 158 testes. Usa H2, `@EmbeddedKafka`, cache em memória e `HttpServer` do JDK como catálogo falso.
- **Perfil A:** `./mvnw verify -Pperfil-a` soma os testes `@Tag("docker")` com Testcontainers, 163 no total.
- **CI:** jobs separados para os perfis B e A em todo PR (`.github/workflows/ci.yml`). Saída local em [docs/evidencias/mvnw-verify.txt](docs/evidencias/mvnw-verify.txt).
- **ADRs:** [ADR-006](docs/adr/ADR-006-contract-testing-pact.md) e [ADR-007](docs/adr/ADR-007-perfis-de-execucao.md).

### 7. Decisões arquiteturais — 3 → ver [docs/adr/](docs/adr/) · _dono: A (todos escreveram)_
- 8 ADRs, todos com contexto, decisão, **alternativas consideradas com o motivo da rejeição** e consequências (✅ ganhos / ⚠️ custos assumidos).
- Os ADRs 003, 005 e 008 registram decisões que **mudaram por causa da revisão**:
  - publicação após o commit → outbox;
  - retry único → retry classificado;
  - sem lock → lock pessimista.

### 8. Uso crítico de IA — 3 · _dono: A (todos contribuem)_
- Como usamos IA e o que validamos à mão: [docs/ia/registro-uso-ia.md](docs/ia/registro-uso-ia.md).
- Em resumo:
  - a IA (Claude Code) planejou, implementou por serviço a partir de especificações nossas e revisou;
  - a **revisão** (um agente revisor de código e um auditor de testes, ambos só leitura) encontrou defeitos reais que os testes da primeira versão não pegavam, como lost update, evento indo para a DLT com o banco fora e documentação contradizendo o código;
  - o CI e o perfil A pegaram dois testes dependentes de ordem e um bug real de ordenação no outbox.
- O registro diz também **o que não delegaríamos de novo** e o que conferimos manualmente: a demo contra infraestrutura real e a leitura dos ADRs contra o código.

### 9. Execução — 3 · _dono: A_
- Como rodar: [README](README.md#como-rodar) (`docker compose up -d --build`, `./scripts/demo/demo-completa.sh`, `./mvnw verify`). Perfil declarado: **A**.
- Evidências em [docs/evidencias/](docs/evidencias/):
  - `demo-completa-saida.txt`: as 7 cenas com as respostas HTTP;
  - `demo-completa-logs-servicos.txt`: os logs correlacionados dos 3 serviços;
  - `docker-compose-ps.txt`: containers saudáveis;
  - `mvnw-verify.txt`: resumo dos testes.
- Reprodutível do zero: `docker compose down -v && docker compose up -d --build` seguido da demo completa, executado antes da entrega.

## Opcionais entregues

O grupo tem 3 pessoas, então os opcionais não são obrigatórios. Entregamos:

- **Tópico Kafka com múltiplos consumer groups:** `estatisticas` e `conquistas` (`DoisConsumerGroupsTest`).
- **Resiliência avançada:** retry com backoff, circuit breaker e DLT, com classificação de falhas. Usamos retry bloqueante, e não `@RetryableTopic`, para preservar a ordem por usuário (ADR-005).
- **2º par de contract test:** Pact de mensagem estatísticas ↔ backlog.
- **Observabilidade básica:** logs com `correlationId` propagado HTTP → outbox → header Kafka → MDC nos consumidores (`CorrelationIdFilter`, `InterceptadorCorrelacao`, `CorrelacaoKafka`).
- **Uso documentado e crítico de IA:** [docs/ia/registro-uso-ia.md](docs/ia/registro-uso-ia.md).
- Extra fora da lista: **transactional outbox** (ADR-003).

## Observações honestas sobre processo

- O projeto foi concentrado em poucos dias antes da banca, não em semanas. Para deixar o processo auditável, cada demanda virou branch e PR com CI, e são 12 PRs para a `develop`:
  - #1 fundação;
  - #2 a #5 serviços e demo;
  - #6 CI do perfil A;
  - #7 a #9 correções da revisão;
  - #10 e #11 ajustes encontrados ao validar a demo e o perfil A;
  - #12 documentação final e evidências.
- Os commits são pequenos, em Conventional Commits, e os testes estão em commits `test(...)` próprios.
- Sem autenticação: `usuarioId` vem do path (limitação declarada no ADR-001).
