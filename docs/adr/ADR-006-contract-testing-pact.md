# ADR-006 — Contrato executável com Pact (consumer-driven)

- **Status:** Proposto
- **Data:** 2026-10-23
- **Responsável:** Pessoa C (Testes/Contrato)
- **Critério:** 6 — Testabilidade

## Contexto

O backlog depende do formato de `GET /jogos/{id}` do catálogo. Uma renomeação de campo no catálogo quebraria o backlog só em execução.

## Decisão

- **Pact JVM, consumer-driven.** O teste do `backlog-service` (consumidor) declara a interação esperada e gera `shared-contracts/pacts/backlog-service-catalogo-service.json`.
- O `catalogo-service` (provedor) verifica esse arquivo contra a API real (`@Provider`, `@PactFolder`, `@State("jogo 42 existe")`).
- Ordem do reactor Maven: `backlog-service` antes de `catalogo-service`, e o pact é commitado.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Teste de integração E2E com os dois serviços de pé | Lento, exige Docker e não diz *qual* lado quebrou o contrato. |
| Spring Cloud Contract | Provider-driven e acoplado a Spring nos dois lados; o Pact é o padrão de mercado consumer-driven. |
| OpenAPI + validação de schema | Valida o schema do provedor, não o que o consumidor realmente usa. |
| Pact Broker | Ideal (versões, `can-i-deploy`), mas é mais um container sem ganho no escopo; usamos pasta compartilhada. |

## Consequências

- ✅ Mudar `duracaoMediaHoras` no catálogo quebra o build do catálogo, não a produção.
- ✅ Roda sem Docker e em segundos.
- ⚠️ Pasta compartilhada só funciona em monorepo; com repositórios separados seria preciso o Broker.
- ⚠️ Cobre só o contrato REST; o contrato do evento Kafka (message pact) é o extra S3.
