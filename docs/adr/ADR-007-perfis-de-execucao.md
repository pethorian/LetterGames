# ADR-007 — Perfis de execução: A (Docker) para rodar, B (pura JVM) para testar

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsáveis:** Pessoa A (Arquiteto) · Pessoa C (testes)
- **Critérios:** 6 — Testabilidade · 9 — Execução

## Contexto

O brief aceita três perfis (A: Docker; B: restrito/JVM; C: conceitual) e exige testes que rodem sem Docker. Nem todo integrante, nem todo ambiente de avaliação, tem Docker funcionando.

## Decisão

- **Execução declarada: perfil A.** `docker compose up -d --build` sobe 3 serviços, 3 Postgres, Redis, Kafka e Kafka UI, com healthchecks e portas só em `127.0.0.1`.
- **Testes no perfil B por padrão** (`./mvnw verify`): H2 em modo PostgreSQL, `@EmbeddedKafka`, cache em memória, servidor HTTP falso do JDK e Pact. Exclui os testes `@Tag("docker")`.
- **Perfil Maven `perfil-a`** (`./mvnw verify -Pperfil-a`): inclui os testes `@Tag("docker")` com Testcontainers. Hoje: `CatalogoInfraRealTest`, com Postgres 16 e Redis 7.4 reais (migrations, TTL expirando, evict e fallback com o Redis parado).
- **Perfil Maven `perfil-c`:** só compila (fallback conceitual).
- O CI roda os perfis B e A em jobs separados; os dois entram no `ci-ok`.
- Maven Wrapper (`mvnw`) para não depender de Maven instalado.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Testcontainers como padrão | Testes mais fiéis, mas `mvn verify` quebraria sem Docker e o brief pede pura-JVM. |
| Só perfil B (sem compose) | A demo em terminais com Kafka e Redis reais é o ponto forte da apresentação. |
| Serviços fora do Docker e infra no Docker | Mais rápido para desenvolver (permitido no dia a dia), mas não reproduzível para a banca. |

## Consequências

- ✅ Qualquer pessoa roda os testes só com JDK 21.
- ✅ Demo reproduzível com um comando (`docker compose up -d --build` + `scripts/demo/demo-completa.sh`).
- ⚠️ H2 não é Postgres: SQL específico pode passar no teste e falhar no real. Mitigação: SQL padrão nas migrations, `CatalogoInfraRealTest` no Postgres real e a demo em Postgres real. O `FOR UPDATE SKIP LOCKED` do outbox, por exemplo, só existe no Postgres; no H2 o Hibernate gera `FOR UPDATE`.
- ⚠️ EmbeddedKafka não reproduz rede, latência e rebalanceamento reais; a demo cobre isso manualmente.
