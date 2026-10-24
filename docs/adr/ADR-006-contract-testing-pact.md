# ADR-006 — Contratos executáveis com Pact (REST e mensagens)

- **Status:** Aceito
- **Data:** 2026-10-23
- **Responsável:** Pessoa C (Testes/Contrato), com Pessoa B no contrato de mensagem
- **Critério:** 6 — Testabilidade

## Contexto

Há dois contratos entre serviços:

1. **REST:** o backlog depende do formato de `GET /jogos/{id}` do catálogo;
2. **Mensagem:** as estatísticas dependem do formato do `BacklogEvento` publicado pelo backlog.

Uma renomeação de campo de qualquer lado quebraria o outro só em execução.

## Decisão

**Pact JVM 4.6, consumer-driven, sem broker: os pacts ficam commitados em `shared-contracts/pacts/`.**

| Par | Consumidor (gera) | Provedor (verifica) | Arquivo |
|---|---|---|---|
| REST | `backlog-service` · `CatalogoClientPactTest` | `catalogo-service` · `CatalogoPactProviderTest` | `backlog-service-catalogo-service.json` |
| Mensagem | `estatisticas-service` · `BacklogEventoPactConsumerTest` | `backlog-service` · `BacklogEventoPactProviderTest` | `estatisticas-service-backlog-service.json` |

- **REST:** estados `jogo 42 existe` (200, campos por tipo) e `jogo 999 não existe` (404). O consumidor testa o `CatalogoClient` real contra o mock server; o provedor sobe a API real (`RANDOM_PORT`) com o service mockado.
- **Mensagem (Pact V4 assíncrono):** três mensagens (`um evento JOGO_ADICIONADO`, `... STATUS_ALTERADO`, `... JOGO_REMOVIDO`).
  - O consumidor passa cada mensagem pelo código real (`LeitorEventos`, `RegraEstatisticas`, conquistas) e confere o efeito.
  - O provedor gera a mensagem pelo `BacklogService` real e lê o payload que ficou gravado no outbox. É exatamente o que vai para o Kafka.
- **Ordem do reactor Maven** (`pom.xml`): `shared-contracts` → `estatisticas-service` → `backlog-service` → `catalogo-service`. Cada consumidor gera o seu pact antes de o provedor verificá-lo.
- O CI roda `git diff --exit-code -- shared-contracts/pacts` depois do build: pact regenerado diferente do commitado quebra o PR. Exemplos fixos nos matchers e `.gitattributes` com `eol=lf` mantêm o arquivo estável.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Teste E2E com os serviços de pé | Lento, exige Docker e não diz *qual* lado quebrou o contrato. (A demo cumpre esse papel de forma manual.) |
| Spring Cloud Contract | Provider-driven e acoplado a Spring dos dois lados; o Pact é o padrão de mercado consumer-driven. |
| OpenAPI + validação de schema | Valida o schema do provedor, não o que o consumidor realmente usa. |
| JSON "golden" do evento compartilhado | Mais simples para o evento, mas não verifica o provedor de forma independente e não segue o mesmo modelo do REST. |
| Pact Broker + `can-i-deploy` | Ideal (versões por ambiente), mas é mais um container sem ganho num monorepo; usamos a pasta compartilhada. |

## Consequências

- ✅ Renomear `duracaoMediaHoras` no catálogo quebra o build do catálogo; parar de serializar `statusAnterior` como `null` no evento quebra o build do backlog. Os dois quebram antes de chegar à produção.
- ✅ Roda sem Docker e em segundos.
- ⚠️ Pasta compartilhada só funciona em monorepo; com repositórios separados seria preciso o Broker.
- ⚠️ Matchers por tipo: o contrato não verifica valores nem regra de negócio, só formato. Isso fica para os testes de cada serviço.
