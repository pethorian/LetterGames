# Auto-avaliação — LetterGames (Backlog de Jogos)

Grupo: _(nome A)_ — Arquiteto + Infra/Demo · _(nome B)_ — Dev Mensageria · _(nome C)_ — Dev Dados/Cache + Testes/Contrato
Tema/domínio: backlog de jogos. O jogador organiza jogos do catálogo por status e recebe estatísticas e conquistas calculadas de forma assíncrona.
Perfil de execução: **A** · Fallbacks usados: H2, EmbeddedKafka e cache em memória **apenas nos testes** (perfil B)

> ⚠️ Preencher cada **TODO** com a evidência real (caminho de arquivo, classe, teste ou hash de commit) à medida que for implementado. Cada pessoa preenche os critérios que defende.

## Evidências por critério

1. **Decomposição de domínio** — nível auto-atribuído: TODO · _dono: A_
   Evidência: módulos `catalogo-service/`, `backlog-service/`, `estatisticas-service/`; 3 Postgres em `docker-compose.yml`; snapshot em `ItemBacklog` (TODO caminho); [docs/arquitetura.md](docs/arquitetura.md); [ADR-001](docs/adr/ADR-001-decomposicao-em-tres-servicos.md)

2. **Comunicação assíncrona** — TODO · _dono: B_
   Evidência: TODO `KafkaProducerConfig`, `BacklogEventoPublisher`, `BacklogEventoListener`; garantia **at-least-once** declarada no README; [ADR-002](docs/adr/ADR-002-kafka-como-broker.md)

3. **Idempotência e consistência** — TODO · _dono: B_
   Evidência: tabela `eventos_processados` (TODO migration); `ReprocessamentoIdempotenteTest` (TODO); [ADR-003](docs/adr/ADR-003-consumidor-idempotente.md)

4. **Cache** — TODO · _dono: C_
   Evidência: TODO `CacheConfig` (TTL 10 min), `@CacheEvict` em `JogoService`, `CacheErrorHandler`, `CacheInvalidacaoTest`; [ADR-004](docs/adr/ADR-004-cache-aside-redis.md)

5. **Resiliência** — TODO · _donos: C (REST) e B (Kafka)_
   Evidência: TODO `CatalogoClient` (Resilience4j), `application.yml` (resilience4j.*), `KafkaConsumerConfig` (`DefaultErrorHandler` + DLT); testes transitório × permanente; [ADR-005](docs/adr/ADR-005-resiliencia.md)

6. **Testabilidade** — TODO · _dono: C_
   Evidência: `shared-contracts/pacts/backlog-service-catalogo-service.json`; TODO `CatalogoClientPactTest` (consumidor) e `CatalogoProviderPactTest` (provedor); `./mvnw verify` verde sem Docker (TODO print em `docs/evidencias/`); [ADR-006](docs/adr/ADR-006-contract-testing-pact.md), [ADR-007](docs/adr/ADR-007-perfis-de-execucao.md)

7. **Decisões arquiteturais** — TODO → ver [docs/adr/](docs/adr/) · _dono: A_

8. **Uso crítico de IA** — TODO · _dono: A (todos contribuem)_
   Como usamos IA e o que validamos manualmente: TODO resumo de [docs/ia/registro-uso-ia.md](docs/ia/registro-uso-ia.md), incluindo os casos em que a sugestão da IA foi rejeitada e por quê.

9. **Execução** — TODO · _dono: A_
   Como rodar: `docker compose up -d --build`; testes: `./mvnw verify`; perfil declarado: **A**. Evidência: TODO `docs/evidencias/`.

## Opcionais entregues

Grupo de 3 pessoas: opcionais não são obrigatórios. Se entrarem (ver PLANEJAMENTO §7):
- TODO (ex.: Observabilidade básica: correlation id entre serviços, `CorrelationIdFilter`)

## Observações honestas sobre processo

TODO: o projeto foi concentrado em poucos dias antes da banca; os commits foram mantidos pequenos e por tarefa para evidenciar o processo.
