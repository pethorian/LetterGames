# ADR-007 — Perfis de execução: A (Docker) para rodar, B (pura JVM) para testar

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa A (Arquiteto) · Pessoa C (testes)
- **Critérios:** 6 — Testabilidade · 9 — Execução

## Contexto

O brief aceita três perfis (A: Docker; B: restrito/JVM; C: conceitual) e exige testes que rodem sem Docker. Nem todo integrante e nem todo ambiente de avaliação tem Docker funcionando.

## Decisão

- **Execução declarada: perfil A.** `docker compose up -d --build` sobe 3 serviços, 3 Postgres, Redis, Kafka e Kafka UI.
- **Testes no perfil B por padrão** (`./mvnw verify`): H2 (modo PostgreSQL), `@EmbeddedKafka`, `ConcurrentMapCacheManager`, MockRestServiceServer/WireMock e Pact.
- Perfil Maven `perfil-a`: adiciona testes com Testcontainers (extra S4).
- Perfil Maven `perfil-c`: só compila e valida a documentação (fallback conceitual).
- Maven Wrapper (`mvnw`) para não depender de Maven instalado.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Testcontainers como padrão | Testes mais fiéis, mas `mvn verify` quebra sem Docker e o brief pede pura-JVM. |
| Só perfil B (sem compose) | A demo em terminais com Kafka/Redis reais é o ponto forte da apresentação. |
| Rodar os serviços fora do Docker com infra no Docker | Mais rápido para desenvolver (permitido no dia a dia), mas não reproduzível para a banca. |

## Consequências

- ✅ Qualquer pessoa roda os testes só com JDK 21.
- ✅ Demo reproduzível com um comando.
- ⚠️ H2 não é Postgres: SQL específico pode passar no teste e falhar no real. Mitigação: Flyway com SQL padrão e smoke test na demo.
- ⚠️ EmbeddedKafka não reproduz rede, latência e rebalanceamento reais.
