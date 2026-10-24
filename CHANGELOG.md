# Changelog

Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

## [0.1.0-beta.1] - 2026-10-23

Primeira versão entregável para a banca (perfil A).

### Adicionado
- `catalogo-service`: CRUD de jogos com seed, cache-aside no Redis (TTL 10 min, evict após o commit, segundo evict atrasado, fallback com o Redis fora).
- `backlog-service`: backlog do jogador com regras de transição, snapshot do catálogo, cliente REST com timeout, retry e circuit breaker, transactional outbox com publicação ordenada por usuário, lock pessimista em PATCH/DELETE.
- `estatisticas-service`: estatísticas e conquistas em dois consumer groups, consumidor idempotente, retry classificado e DLT.
- Contratos executáveis: Pact REST (backlog → catálogo) e Pact de mensagem (estatísticas → backlog).
- `docker-compose.yml` com 3 Postgres, Redis, Kafka (KRaft) e Kafka UI; portas só em `127.0.0.1`.
- Scripts da demo em terminais (`scripts/demo/`, `scripts/abrir-terminais.ps1`).
- CI com perfis B (sem Docker) e A (Testcontainers), build das imagens, checagem de versão, origem da branch e estabilidade dos pacts; merge automático na `develop`; release a partir da `main`.
- Documentação: 8 ADRs, arquitetura, roteiro da banca, auto-avaliação e registro de uso de IA.
