# Roteiro da banca — LetterGames (Aula 9 · 26/10 · ~20 min)

> A banca avalia **"por que assim"**, não só "o que faz". Cada fala abaixo tem: o que mostrar, o que dizer e a frase de defesa (o trade-off).
> **Pessoa A** = Arquiteto + Infra/Demo · **Pessoa B** = Mensageria · **Pessoa C** = Cache/Dados + Testes/Contrato.

---

## 1. Visão geral do tempo

| Min | Bloco | Quem fala | Quem pilota o teclado |
|---|---|---|---|
| 0:00–2:00 | Problema, tema, perfil de execução | A | — |
| 2:00–4:30 | Arquitetura e decomposição | A | — |
| 4:30–12:30 | **Demo ao vivo em 6 terminais** (7 cenas) | A, B e C alternam | **A** (os outros narram) |
| 12:30–15:30 | Decisões e trade-offs (ADRs) | B, C, A | — |
| 15:30–17:00 | Testes e contrato | C | — |
| 17:00–18:00 | Uso crítico de IA | A (com 1 exemplo de B e 1 de C) | — |
| 18:00–20:00+ | Arguição | quem for perguntado; o dono do tema complementa | — |

> **Por que A pilota:** quem narra não digita. A pessoa que fala aponta para o terminal enquanto A roda o próximo script, e a demo não trava esperando alguém achar o comando.

---

## 2. Layout dos terminais

Aberto com `pwsh ./scripts/abrir-terminais.ps1` (Windows Terminal, grade 2×3, fonte 16+ para o projetor):

```
┌─────────────────────────┬─────────────────────────┬─────────────────────────┐
│ 1. CATÁLOGO (logs)      │ 2. BACKLOG (logs)       │ 3. ESTATÍSTICAS (logs)  │
│ docker compose logs -f  │ docker compose logs -f  │ docker compose logs -f  │
│   catalogo-service      │   backlog-service       │   estatisticas-service  │
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ 4. REDIS MONITOR        │ 5. KAFKA (tópico ao     │ 6. COMANDOS (demo)      │
│ docker compose exec     │    vivo, com chave e    │ ./scripts/demo/0X-*.sh  │
│   redis redis-cli       │    headers)             │                         │
│   MONITOR               │ kafka-console-consumer  │                         │
│                         │ backlog.eventos         │                         │
└─────────────────────────┴─────────────────────────┴─────────────────────────┘
```

A narrativa visual é ler **da esquerda para a direita e de cima para baixo**: o comando sai no 6, aparece no 2, vai para o 1 (REST) e para o 4 (cache), volta, cai no 5 (Kafka) e termina no 3. **O mesmo `correlationId` (`demo-02`) aparece em todos.**

Aba extra (fora da grade): Kafka UI em `http://localhost:8080`, para mostrar a DLT e os consumer groups, se perguntarem.

---

## 3. Checklist pré-banca (30 min antes)

- [ ] `docker compose down -v && docker compose up -d --build` → todos `healthy`
- [ ] `./scripts/demo/00-reset.sh` (limpa backlog e estatísticas, mantém o seed do catálogo)
- [ ] Rodar a demo inteira **uma vez** (aquece JVM e cache) e resetar de novo
- [ ] Layout de terminais aberto, fonte grande, tema escuro, notificações do Windows desligadas
- [ ] Plano B pronto: terminal com `./mvnw verify` já rodado (saída verde visível) + vídeo em `docs/evidencias/`
- [ ] Cada pessoa com este roteiro aberto no celular

---

## 4. Falas por pessoa

### 🅐 Pessoa A — Arquiteto (abertura, arquitetura, condução da demo, IA)

**[0:00] Abertura (2 min)**
> "Todo mundo que joga tem uma pilha de jogos comprados e nunca jogados. O LetterGames é um backlog de jogos: você adiciona jogos do catálogo, marca como jogando, zerado ou abandonado, e o sistema calcula de forma assíncrona quantas horas você tem pela frente e quais conquistas desbloqueou.
> Rodamos no **perfil A, tudo em Docker**: são 3 microsserviços, 3 Postgres, Redis e Kafka. Os testes rodam sem Docker, no perfil B, com `mvnw verify`. Sem front-end, de propósito: queremos que vocês vejam a mensagem andando entre os serviços."

**[2:00] Arquitetura e decomposição (2,5 min)** · mostrar `docs/arquitetura.md`
- 3 bounded contexts: **Catálogo** (o que é um jogo), **Backlog** (a relação do jogador com o jogo) e **Estatísticas** (leitura derivada, calculada a partir de eventos).
- **Cada serviço tem seu próprio Postgres.** Ninguém faz join entre bancos.
- Frase de defesa:
> "O backlog guarda um **snapshot** do título e da duração do jogo no momento em que foi adicionado. Isso é duplicação proposital: se o catálogo cair, o backlog continua listando. O preço é que, se o catálogo corrigir um título, o backlog mostra o antigo até o usuário readicionar. Aceitamos, porque título quase não muda e disponibilidade importa mais. Está no ADR-001."
- Por que REST entre backlog e catálogo e não evento: "adicionar ao backlog exige **validar agora** que o jogo existe. É consulta síncrona por natureza. Já as estatísticas não precisam estar prontas na hora: eventual é aceitável, então vão por fila."

**[4:30] Demo:** A roda os scripts; cada cena tem um narrador (ver §5).

**[17:00] Uso de IA (1 min)** · mostrar `docs/ia/registro-uso-ia.md`
- Onde usamos: rascunho de ADRs, esqueleto de configuração Kafka/Resilience4j, geração de casos de teste.
- **O que pegamos errado na IA** (citar casos reais do registro). Exemplos a confirmar durante o desenvolvimento:
  - sugeriu `@RetryableTopic`, mas isso quebra a ordem por usuário (B explica);
  - sugeriu `@CachePut` no update, mas preferimos *evict* (C explica);
  - sugeriu auto-commit de offset, o que perde a garantia.
- Frase:
> "A IA acelerou o boilerplate, mas toda decisão que está num ADR foi discutida por nós, e cada config foi validada com um teste que falha se a config estiver errada."

---

### 🅑 Pessoa B — Mensageria (fluxo assíncrono, idempotência, retry/DLT)

**Narra as cenas 2 (parte Kafka), 5, 6 e 7** (ver §5).

**[12:30] Decisões de mensageria (1,5 min)**
- **Por que Kafka:** "Precisávamos de ordem por usuário: um `STATUS_ALTERADO` para ZERADO não pode ser processado antes do `JOGO_ADICIONADO`. Usamos `usuarioId` como chave, então todos os eventos de um usuário caem na mesma partição, em ordem. RabbitMQ resolveria a fila, mas a ordem por chave e a releitura do log são nativas no Kafka." (ADR-002)
- **Mensagem × evento:** "Publicamos **eventos**, fatos no passado (`JOGO_ADICIONADO`), e não comandos (`CALCULE_ESTATISTICA`). O backlog não sabe quem consome. Se amanhã entrar um serviço de recomendação, ele só assina o tópico."
- **Garantia:** "**At-least-once.** Producer com `acks=all` e idempotência ligada. O consumidor só confirma o offset **depois** de commitar no banco. Se cair no meio, a mensagem volta. Por isso o consumidor **tem** que ser idempotente."
- **Idempotência:** "Tabela `eventos_processados` com o `eventId` como chave primária, inserida **na mesma transação** que atualiza as estatísticas. Ou as duas coisas acontecem, ou nenhuma. Duplicata vira violação de PK, e a gente ignora." (ADR-003)
- **Retry e DLT:** "Retry **bloqueante** com backoff exponencial: 1 s, 2 s, 4 s. Esgotou, vai para a DLT. Escolhemos bloqueante e não o `@RetryableTopic` porque o não-bloqueante joga a mensagem para outro tópico e as seguintes do mesmo usuário passam na frente, quebrando a ordem. O custo é que uma mensagem ruim segura a partição por ~7 s. Aceitamos, e erro de formato vai **direto** para a DLT sem retry." (ADR-005)
- **Ponto fraco assumido** (diga antes que perguntem):
> "Publicamos depois do commit do banco. Se o Kafka estiver fora nesse instante, perdemos o evento: é o problema do *dual write*. A solução é o **transactional outbox**, que está no ADR-003 como próximo passo [ou: que implementamos — se S1 entrar]."

---

### 🅒 Pessoa C — Cache, contrato e testes

**Narra as cenas 1, 3 e 4** (ver §5).

**[13:30] Decisões de cache e resiliência REST (1 min)**
- **Cache-aside no catálogo:** "Leitura de jogo é muito mais frequente que escrita. `GET` olha o Redis; no *miss* vai ao banco e grava com **TTL de 10 minutos**. Em `PUT` e `DELETE` a gente **invalida** (*evict*), não atualiza. Evict é mais simples e evita a corrida em que duas escritas concorrentes deixam o cache com o valor velho." (ADR-004)
- **TTL como rede de segurança:** "Se alguma invalidação falhar, o pior caso é 10 minutos de dado velho. Para catálogo de jogos isso é aceitável."
- **Fallback:** "Se o Redis cair, o `CacheErrorHandler` loga e vai direto ao banco. O cache é otimização, nunca dependência."
- **Circuit breaker no backlog → catálogo:** "Timeout de 2 s, 3 retries com backoff só para erro de rede ou 5xx (404 não se repete). Se 50% de 10 chamadas falharem, o breaker abre por 15 s e respondemos 503 na hora, sem pendurar threads. **Não** adicionamos o jogo sem validar, porque preferimos recusar a gravar lixo." (ADR-005)

**[15:30] Testes e contrato (1,5 min)** · mostrar o pact JSON e a saída do `mvnw verify`
- "O backlog é o **consumidor** do catálogo. O teste do backlog descreve o que ele espera de `GET /jogos/{id}` e gera um arquivo pact. O catálogo, o **provedor**, roda esse arquivo contra a API real dele. Se alguém renomear `duracaoMediaHoras` no catálogo, **o build do catálogo quebra**, antes de quebrar em produção." (ADR-006)
- "Por que não só teste de integração? Teste de integração exige subir os dois serviços juntos. O pact testa cada lado isolado e roda em segundos."
- "**Tudo roda sem Docker**: H2 no lugar do Postgres, EmbeddedKafka no lugar do broker e cache em memória no lugar do Redis. Temos teste de reprocessamento (evento 2× = efeito 1×), de falha transitória × permanente nos dois lados e de invalidação de cache." (ADR-007)
- Limitação assumida: "H2 não é Postgres. Por isso o perfil A tem testes com Testcontainers [se S4 entrar] e a demo roda em Postgres real."

---

## 5. Roteiro da demo (8 min, 7 cenas)

Cada cena = **1 script** no terminal 6. O narrador fala enquanto A executa.

| # | Script | Narrador | O que aparece | Frase-chave |
|---|---|---|---|---|
| 1 | `01-cadastrar-jogos.sh` | C | T1: `INSERT` · T4: nada (escrita não popula o cache) | "Escrita não aquece cache; cache-aside só preenche na leitura." |
| 2 | `02-adicionar-ao-backlog.sh` (`joao` adiciona Hollow Knight) | A (REST) → B (Kafka) | T2 `→ REST GET` · T1 `✘ CACHE MISS` · T4 `SET jogos::42` · T2 `⇢ KAFKA PUB key=joao` · T5 evento JSON · T3 `⇠ KAFKA SUB` + estatística | "Mesmo `correlationId demo-02` nos 4 terminais: dá para seguir a requisição." |
| 3 | `03-cache.sh` (GET 2×, PUT, GET) | C | T4 `GET` hit, sem SQL no T1 · PUT → T4 `DEL` · GET seguinte → miss | "Invalidação explícita: o PUT apaga a chave; a próxima leitura busca o valor novo." |
| 4 | `04-circuit-breaker.sh` (`docker stop catalogo-service` + 6 POSTs) | C | T2 `⟳ RETRY` → `⚡ BREAKER OPEN` → 503 imediato · `docker start` → `HALF_OPEN` → `CLOSED` | "Falha rápida protege o backlog; ele não morre junto com o catálogo." |
| 5 | `05-zerar-jogo.sh` + `05b-duplicado.sh` (republica o mesmo evento) | B | T3 conquista `🏆 Primeiro zerado!` · reenvio → `↺ DUPLICADO ignorado eventId=...` · GET estatísticas inalterado | "At-least-once + consumidor idempotente = efeito exatamente uma vez." |
| 6 | `06-falha-transitoria.sh` (`docker stop postgres-estatisticas`, alterar status, `docker start` em 3 s) | B | T3 `⟳ RETRY 1/4 (1s)`, `2/4 (2s)` → banco volta → processa | "Falha transitória: o retry com backoff resolve sozinho, sem perder evento." |
| 7 | `07-poison-pill.sh` (JSON inválido no tópico) | B | T3 `☠ DLT` **sem retry** · próximo evento válido passa normal · Kafka UI mostra a DLT | "Falha permanente não adianta repetir: vai para a DLT e não trava a fila." |

**Se uma cena falhar:** não depurar ao vivo. A diz: "Essa cena está gravada em `docs/evidencias/`, e o teste que cobre esse comportamento é o `X`", e segue para a próxima.

---

## 6. Arguição: perguntas prováveis e respostas

> Cada pessoa estuda **todas**. A coluna "Dono" é quem responde primeiro; os outros complementam.

### Arquitetura (A)
| Pergunta | Resposta curta |
|---|---|
| Por que 3 serviços e não 2? | Estatísticas é um modelo de **leitura** com escala e consistência diferentes (eventual). Separar permite reprocessar o tópico do zero para recalcular tudo sem tocar no backlog. |
| Por que não um banco só com 3 schemas? | Schema separado ainda permite join e acoplamento escondido. Container separado deixa a fronteira **física**, e dá para derrubar um banco na demo (cena 6) sem afetar os outros. |
| Snapshot não é dado duplicado? | É, e de propósito: troca consistência forte por disponibilidade. O dono do dado continua sendo o catálogo. |
| E o serviço de usuário? | Fora do escopo; `usuarioId` é uma string. Em produção viria de um token JWT. Está no ADR-001 como limitação. |
| Como escalaria? | Catálogo: réplicas + Redis (stateless). Estatísticas: até 3 instâncias no mesmo group (3 partições). Mais que isso exige mais partições. |

### Mensageria (B)
| Pergunta | Resposta curta |
|---|---|
| O consumidor cai depois de gravar no banco e antes de commitar o offset. E aí? | A mensagem é reentregue, o `eventId` já está em `eventos_processados`, então é ignorada. É exatamente o cenário que a idempotência cobre. |
| E se cair antes de gravar? | A transação não commitou, então nada foi gravado. A reentrega processa normalmente. |
| Por que não exactly-once do Kafka (transações)? | O EOS do Kafka cobre só Kafka → Kafka. Nosso efeito é num Postgres, então a idempotência tem que estar no consumidor de qualquer jeito. |
| Como garante a ordem? | Chave = `usuarioId` → mesma partição; 1 consumidor por partição no group; retry bloqueante. |
| O que faz com a DLT? | Hoje: inspeção manual no Kafka UI e republicação após a correção. Próximo passo: alerta quando a DLT tiver mensagens. |
| E o dual write no producer? | Ponto fraco conhecido; solução é o outbox (ADR-003). |
| Evento com `versao`, para quê? | Evolução do schema: o consumidor sabe tratar v1 e v2 em convivência durante a migração. |

### Cache e testes (C)
| Pergunta | Resposta curta |
|---|---|
| Por que TTL de 10 min? | Catálogo muda pouco. 10 min limita o dano de uma invalidação perdida e ainda dá taxa de acerto alta. Número de bom senso, não medido. Dizemos isso no ADR. |
| E cache stampede? | Com o nosso volume, não é problema. Em escala: `@Cacheable(sync = true)` (um único carregamento por chave por instância) ou TTL com jitter. |
| Por que não cachear no backlog também? | Seriam dois caches para invalidar e o backlog já tem o snapshot. Um cache só, no dono do dado. |
| Por que evict e não `@CachePut`? | Com duas escritas concorrentes, o `put` pode gravar a versão mais velha por último. O evict força a releitura do banco, que é a fonte da verdade. |
| O pact substitui teste de integração? | Não. O pact garante o **formato** do contrato; não testa a regra de negócio nem a infra. Complementa. |
| Quem garante que o provedor roda o pact mais novo? | Hoje: arquivo commitado em `shared-contracts/pacts` e ordem do reactor Maven. Em produção: Pact Broker + `can-i-deploy`. |
| H2 esconde bug de Postgres? | Pode esconder (tipos, SQL específico). Mitigação: Flyway com SQL padrão e a demo/perfil A em Postgres real. |
| Retry no REST não piora uma queda? | Por isso só 3 tentativas com backoff, só em erro transitório, e o breaker corta tudo quando a taxa de falha passa de 50%. |

### Para todos
| Pergunta | Resposta curta |
|---|---|
| O que fariam diferente com mais tempo? | Outbox, Pact Broker, Testcontainers em tudo, observabilidade com tracing (OpenTelemetry) em vez de só correlation id. |
| Qual foi a decisão mais difícil? | Retry bloqueante × não-bloqueante: ordem vs vazão (B detalha). |
| Onde a IA errou? | Ver registro de uso de IA (casos reais, cada um conta o seu). |

---

## 7. Regras de ouro na banca

1. **Responda primeiro o trade-off**: "escolhemos X porque Y, custou Z".
2. Não sabe? "Não implementamos isso; faríamos assim: ..." vale mais do que inventar.
3. Ninguém interrompe quem está respondendo; complementa no final com "só somando...".
4. Não depurar ao vivo. Plano B: gravação + teste.
5. Fechar com o critério: "isso está evidenciado no `AVALIACAO.md`, critério N".
