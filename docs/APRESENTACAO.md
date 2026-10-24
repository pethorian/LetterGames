# Roteiro da banca — LetterGames (Aula 9 · 26/10 · ~20 min)

> A banca avalia **"por que assim"**, não só "o que faz". Cada fala abaixo tem: o que mostrar, o que dizer e a frase de defesa (o trade-off).
> **Pessoa A** = Arquiteto + Infra/Demo · **Pessoa B** = Mensageria · **Pessoa C** = Cache/Dados + Testes/Contrato.

---

## 1. Visão geral do tempo

| Min | Bloco | Quem fala | Quem pilota o teclado |
|---|---|---|---|
| 0:00–2:00 | Problema, tema, perfil de execução | A | — |
| 2:00–4:30 | Arquitetura e decomposição | A | — |
| 4:30–12:30 | **Demo ao vivo em 6 terminais** (7 cenas + 5b) | A, B e C alternam | **A** (os outros narram) |
| 12:30–15:30 | Decisões e trade-offs (ADRs) | B, C, A | — |
| 15:30–17:00 | Testes e contratos | C | — |
| 17:00–18:00 | Uso crítico de IA | A (com 1 exemplo de B e 1 de C) | — |
| 18:00–20:00+ | Arguição | quem for perguntado; o dono do tema complementa | — |

> **Por que A pilota:** quem narra não digita. A pessoa que fala aponta para o terminal enquanto A roda o próximo script, e a demo não trava esperando alguém achar o comando.

---

## 2. Layout dos terminais

Aberto com `powershell -ExecutionPolicy Bypass -File scripts\abrir-terminais.ps1` (Windows Terminal, grade 2×3). Aumente a fonte com `Ctrl` + `=` para o projetor.

```
┌─────────────────────────┬─────────────────────────┬─────────────────────────┐
│ 1. CATÁLOGO (logs)      │ 2. BACKLOG (logs)       │ 3. ESTATÍSTICAS (logs)  │
│ docker compose logs -f  │ docker compose logs -f  │ docker compose logs -f  │
│   catalogo-service      │   backlog-service       │   estatisticas-service  │
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ 4. REDIS MONITOR        │ 5. KAFKA (tópico ao     │ 6. COMANDOS (Git Bash)  │
│ redis-cli MONITOR       │    vivo, com chave,     │ ./scripts/demo/0X-*.sh  │
│                         │    headers e partição)  │                         │
└─────────────────────────┴─────────────────────────┴─────────────────────────┘
```

A narrativa visual é ler **da esquerda para a direita e de cima para baixo**:

1. o comando sai no 6 e aparece no 2;
2. vai ao 1 (REST) e ao 4 (cache) e volta;
3. sai pelo outbox, cai no 5 (Kafka) e termina no 3, nos dois consumer groups.

**O mesmo `correlationId` (`demo-02`) aparece em todos os terminais.**

Aba extra, fora da grade: Kafka UI em `http://localhost:8080`, para mostrar a DLT e os dois consumer groups, se perguntarem.

---

## 3. Checklist pré-banca (30 min antes)

- [ ] `docker compose up -d --build` → `docker compose ps` com tudo `healthy`
- [ ] `./scripts/demo/00-reset.sh` (limpa backlog, estatísticas, cache e tópicos; mantém o seed do catálogo)
- [ ] Ensaio rápido: `SEM_PAUSA=1 ./scripts/demo/demo-completa.sh` (aquece JVM e cache), depois `00-reset.sh` de novo
- [ ] Layout de terminais aberto, fonte grande, tema escuro, notificações do Windows desligadas
- [ ] Plano B pronto: `docs/evidencias/` aberto (saída da demo e logs) + terminal com `./mvnw verify` já rodado (verde)
- [ ] Cada pessoa com este roteiro aberto no celular

---

## 4. Falas por pessoa

### 🅐 Pessoa A — Arquiteto (abertura, arquitetura, condução da demo, IA)

**[0:00] Abertura (2 min)**
> "Todo mundo que joga tem uma pilha de jogos comprados e nunca jogados. O LetterGames é um backlog de jogos: você adiciona jogos do catálogo, marca como jogando, zerado ou abandonado, e o sistema calcula de forma assíncrona quantas horas você tem pela frente e quais conquistas desbloqueou.
> Rodamos no **perfil A, tudo em Docker**: são 3 microsserviços, 3 Postgres, Redis e Kafka. Os testes rodam sem Docker, no perfil B, com `mvnw verify`; o perfil A acrescenta testes com Postgres e Redis reais via Testcontainers. Sem front-end, de propósito: queremos que vocês vejam a mensagem andando entre os serviços."

**[2:00] Arquitetura e decomposição (2,5 min)** · mostrar `docs/arquitetura.md`
- 3 bounded contexts:
  - **Catálogo:** o que é um jogo;
  - **Backlog:** a relação do jogador com o jogo;
  - **Estatísticas:** leitura derivada, com duas projeções (estatísticas e conquistas) em dois consumer groups.
- **Cada serviço tem o seu próprio Postgres.** Ninguém faz join entre bancos; o que é compartilhado é só contrato.
- Frase de defesa:
> "O backlog guarda um **snapshot** do título e da duração do jogo no momento em que foi adicionado. É duplicação proposital: se o catálogo cair, o backlog continua listando e alterando itens. O preço é que, se o catálogo corrigir um título, o backlog mostra o antigo. Aceitamos, porque título quase não muda e disponibilidade importa mais. Está no ADR-001."
- Por que REST entre backlog e catálogo e não evento:
> "Adicionar ao backlog exige **validar agora** que o jogo existe. É consulta síncrona por natureza. Já as estatísticas não precisam estar prontas na hora: eventual é aceitável, então vão por fila."

**[4:30] Demo:** A roda os scripts; cada cena tem um narrador (ver §5).

**[14:30] Concorrência (30 s), se sobrar tempo ou se perguntarem** (ADR-008)
> "A revisão de código achou um *lost update*: dois PATCH ao mesmo tempo no mesmo item publicavam dois eventos partindo do mesmo status anterior, e um item contava duas vezes. Resolvemos com `SELECT ... FOR UPDATE` no PATCH e no DELETE, mais `@Version` de rede de segurança. Temos um teste determinístico que falha sem o lock."

**[17:00] Uso de IA (1 min)** · mostrar `docs/ia/registro-uso-ia.md`
- **Onde usamos:** planejamento e rascunho dos ADRs; implementação orientada por especificação (subagentes por serviço); e principalmente **revisão** (um revisor de código e um auditor de testes).
- **O que a revisão pegou** (casos reais do registro):
  - o retry igual para toda falha mandava o banco fora para a DLT e corrompia a projeção (B explica);
  - o lost update no PATCH (A);
  - um teste dependente de ordem que só falhava no CI Linux (C).
- Frase:
> "A IA acelerou o código, mas não decidiu sozinha. Toda decisão num ADR foi discutida por nós, cada comportamento tem um teste que falha se estiver errado, e a demo roda contra infraestrutura real."

---

### 🅑 Pessoa B — Mensageria (fluxo assíncrono, idempotência, retry/DLT)

**Narra a parte Kafka da cena 2 e as cenas 5, 5b, 6 e 7** (ver §5).

**[12:30] Decisões de mensageria (1,5 min)**
- **Por que Kafka** (ADR-002):
> "Precisávamos de ordem por usuário: um `STATUS_ALTERADO` para ZERADO não pode ser processado antes do `JOGO_ADICIONADO`. Usamos `usuarioId` como chave, então todos os eventos de um usuário caem na mesma partição, em ordem. RabbitMQ resolveria a fila, mas a ordem por chave e a releitura do log são nativas no Kafka."
- **Mensagem × evento:**
> "Publicamos **eventos**, fatos no passado (`JOGO_ADICIONADO`), e não comandos (`CALCULE_ESTATISTICA`). O backlog não sabe quem consome. A prova é o group `conquistas`: entrou depois, só assinando o tópico, sem mudar uma linha do backlog."
- **Outbox** (ADR-003):
> "Gravar no banco e publicar no Kafka são duas operações: se o Kafka cair no meio, o evento some. Por isso o backlog grava o item **e** o evento na mesma transação, numa tabela de outbox, e um publicador envia depois. Kafka fora? O evento espera no banco."
- **Garantia:**
> "**At-least-once.** O outbox pode publicar duas vezes se cair entre o envio e a marcação, e o consumidor só confirma o offset **depois** de commitar no banco. Por isso o consumidor **tem** que ser idempotente."
- **Idempotência:**
> "Tabela `eventos_processados` com chave (eventId, consumidor), gravada **na mesma transação** que atualiza as estatísticas. Já processado? Ignoramos e confirmamos. Se duas entregas correrem juntas, a chave primária barra a segunda, ela faz rollback, é retentada e aí aparece como duplicada."
- **Retry e DLT** (ADR-005):
> "Classificamos a falha. Mensagem inválida vai **direto** para a DLT, porque repetir não adianta. Falha de infraestrutura, como banco fora, faz retry com backoff até 10 minutos, **segurando a partição**. Bug de processamento: 4 tentativas e DLT. Não usamos o `@RetryableTopic` porque ele tira a mensagem da partição e as seguintes do mesmo usuário passam na frente, quebrando a ordem."
- **Ponto fraco assumido** (diga antes que perguntem):
> "Retry bloqueante troca vazão por ordem: um banco fora por minutos para a partição inteira. E a DLT exige operação manual; não temos alerta."

---

### 🅒 Pessoa C — Cache, contratos e testes

**Narra as cenas 1, 3 e 4** (ver §5).

**[13:30] Decisões de cache e resiliência REST (1 min)**
- **Cache-aside no catálogo** (ADR-004):
> "Leitura de jogo é muito mais frequente que escrita. O `GET` olha o Redis; no *miss* vai ao banco e grava com **TTL de 10 minutos**. Em `PUT` e `DELETE` a gente **invalida**, não atualiza, e depois do commit."
- **A corrida que sobra:**
> "Mesmo assim, um GET lento pode ler o valor antigo e gravá-lo depois do evict. Por isso apagamos a chave de novo 500 ms depois, o *double delete*. No pior caso, o TTL limita o dado velho a 10 minutos."
- **Fallback:**
> "Se o Redis cair, o `CacheErrorHandler` loga e vai direto ao banco. O cache é otimização, nunca dependência. Temos teste disso com o container do Redis parado de verdade."
- **Circuit breaker no backlog → catálogo** (ADR-005):
> "Timeout de 2 segundos e 3 retries com backoff, só para erro de rede ou 5xx; 404 e outros 4xx não se repetem. Com 50% de falha em 10 chamadas, o breaker abre por 15 segundos e respondemos 503 em milissegundos, sem pendurar threads. **Não** adicionamos o jogo sem validar: preferimos recusar a gravar lixo."

**[15:30] Testes e contratos (1,5 min)** · mostrar os dois pacts em `shared-contracts/pacts/` e a saída do `mvnw verify`
> "Temos **dois contratos executáveis**. No REST, o backlog é o consumidor: o teste dele descreve o que espera de `GET /jogos/{id}` e gera o pact, e o catálogo roda esse arquivo contra a API real. No evento Kafka é o contrário: as estatísticas são o consumidor e o backlog prova que o JSON que ele grava no outbox bate com o contrato. Renomeou um campo de qualquer lado, o build quebra antes da produção. O CI ainda confere se o pact gerado é igual ao commitado." (ADR-006)

> "**Tudo roda sem Docker** no perfil B: H2, Kafka embutido e cache em memória. São 150+ testes: reprocessamento com o mesmo eventId, duplicata fora de ordem, duas threads processando o mesmo evento, falha transitória contra permanente nos dois lados, concorrência no backlog, at-least-once do outbox. No perfil A, o Testcontainers sobe Postgres e Redis reais e prova o TTL expirando." (ADR-007)

- Limitação assumida:
> "H2 não é Postgres. Por isso existe o teste do perfil A, e a demo roda em Postgres real."

---

## 5. Roteiro da demo (8 min)

Cada cena é **1 script** no terminal 6 (ou `./scripts/demo/demo-completa.sh`, que pausa entre as cenas). O narrador fala enquanto A executa.

| # | Script | Narrador | O que aparece | Frase-chave |
|---|---|---|---|---|
| 1 | `01-cadastrar-jogos.sh` | C | T1: `Jogo 11 criado` · T4: nada (escrita não popula o cache) | "Escrita não aquece cache: cache-aside só preenche na leitura." |
| 2 | `02-adicionar-ao-backlog.sh` (`joao` adiciona Hollow Knight) | A (REST) → B (Kafka) | T2 `→ REST GET` · T1 `✘ CACHE MISS` · T4 `SET jogos::1` · T2 `✚ ITEM salvo ... no outbox` → `⇢ KAFKA PUB key=joao` · T5 evento JSON · T3 `⇠ KAFKA SUB` nos 2 groups + `📊 ESTATÍSTICAS` | "Mesmo `demo-02` em todos os terminais: dá para seguir a requisição." |
| 3 | `03-cache.sh` (GET 2×, TTL, PUT, GET) | C | T1 `✘ MISS` e depois `✔ CACHE HIT` sem SQL · TTL ~600 s · PUT → `🗑 CACHE EVICT` e o 2º evict atrasado · GET seguinte → MISS | "Invalidação explícita: o PUT apaga a chave; a próxima leitura busca o valor novo." |
| 4 | `04-circuit-breaker.sh` (para o catálogo + 6 POSTs) | C | T2 `⟳ RETRY catalogo 2/3, 3/3` → `⚡ BREAKER CLOSED → OPEN` → 503 em ~10 ms · religa → `OPEN → HALF_OPEN` → 3 POSTs → `HALF_OPEN → CLOSED` | "Falha rápida protege o backlog; ele não cai junto com o catálogo." |
| 5 | `05-zerar-jogo.sh` | B | T3: os dois groups recebem `STATUS_ALTERADO` · `🏆 CONQUISTA DESBLOQUEADA joao: Primeiro zerado!` | "Um evento, dois consumidores independentes." |
| 5b | `05b-duplicado.sh` (republica o mesmo evento) | B | T3 `↺ DUPLICADO ignorado` nos dois groups · estatísticas iguais antes e depois (`✔ Nada mudou`) | "At-least-once + consumidor idempotente = efeito exatamente uma vez." |
| 6 | `06-falha-transitoria.sh` (para o `postgres-estatisticas`, PATCH, religa) | B | T3 `⟳ RETRY 1 (transitória) ... próxima em 1000 ms`, `RETRY 2 ... 2000 ms`... → banco volta → `📊 ESTATÍSTICAS` | "Falha de infraestrutura: seguramos a partição e esperamos; o evento não é perdido nem pula a fila." |
| 7 | `07-poison-pill.sh` (JSON inválido + evento sem eventId) | B | T3 `☠ DLT ... group=estatisticas` e `group=conquistas` **sem retry** · o próximo evento válido passa · conteúdo da DLT impresso | "Falha permanente não adianta repetir: vai para a DLT e não trava a fila." |

**Se uma cena falhar:** não depurar ao vivo. A diz: "Essa cena está registrada em `docs/evidencias/`, e o teste que cobre esse comportamento é o `X`", e segue para a próxima.

| Cena | Teste equivalente (plano B) |
|---|---|
| 3 | `JogoCacheTest`, `CatalogoInfraRealTest` |
| 4 | `CatalogoClientResilienciaTest` |
| 5 e 5b | `DoisConsumerGroupsTest`, `ReprocessamentoIdempotenteTest` |
| 6 | `FalhaTransitoriaTest` |
| 7 | `FalhaPermanenteTest` |

---

## 6. Arguição: perguntas prováveis e respostas

> Cada pessoa estuda **todas**. A coluna "Dono" é quem responde primeiro; os outros complementam.

### Arquitetura (A)
| Pergunta | Resposta curta |
|---|---|
| Por que 3 serviços e não 2? | Estatísticas é um modelo de **leitura** com consistência eventual. Separar permite reprocessar o tópico do zero para recalcular tudo sem tocar no backlog. |
| Por que não um banco só com 3 schemas? | Schema separado ainda permite join e acoplamento escondido. Container separado deixa a fronteira **física**, e dá para derrubar um banco na demo (cena 6) sem afetar os outros. |
| Snapshot não é dado duplicado? | É, de propósito: troca consistência forte por disponibilidade. O dono do dado continua sendo o catálogo. |
| E o serviço de usuário? Qualquer um mexe no backlog de qualquer um? | Sim, é limitação de escopo declarada no ADR-001. Em produção o `usuarioId` viria do token (JWT), não do path. |
| Dois PATCH ao mesmo tempo? | Lock pessimista na linha: o segundo espera e parte do estado já commitado; conflito de versão vira 409 (ADR-008). |
| Como escalaria? | Catálogo: réplicas + Redis (stateless). Estatísticas: até 3 instâncias por group (3 partições). Backlog: várias instâncias, porque o outbox trava só a cabeça de fila de cada usuário (`SKIP LOCKED`). |

### Mensageria (B)
| Pergunta | Resposta curta |
|---|---|
| O consumidor cai depois de gravar no banco e antes de commitar o offset. E aí? | A mensagem é reentregue; o `eventId` já está em `eventos_processados`, então é ignorada. É exatamente o cenário que a idempotência cobre. |
| E se cair antes de gravar? | A transação não commitou, nada foi gravado. A reentrega processa normalmente. |
| E se o backlog cair depois de publicar e antes de marcar no outbox? | Publica de novo no próximo ciclo: at-least-once. O consumidor deduplica. Temos teste que mostra o mesmo eventId duas vezes no tópico. |
| Por que não exactly-once do Kafka (transações)? | O EOS do Kafka cobre só Kafka → Kafka. Nosso efeito é num Postgres, então a idempotência tem que estar no consumidor de qualquer jeito. |
| Como garante a ordem? | Chave = `usuarioId` → mesma partição; o outbox publica só a cabeça de fila de cada usuário; 1 consumidor por partição no group; retry bloqueante. |
| Um evento que nunca publica trava tudo? | Não: trava só aquele usuário. Depois de 10 falhas o evento é estacionado e logado como `☠ OUTBOX`. Os outros usuários seguem. |
| O que faz com a DLT? | Hoje: inspeção no Kafka UI e republicação após a correção (a idempotência protege contra reprocessar). Próximo passo: alerta quando a DLT tiver mensagens. |
| Por que a mesma mensagem aparece duas vezes na DLT? | Uma cópia por consumer group; o header `kafka_dlt-original-consumer-group` e o log dizem qual. |
| Evento com `versao`, para quê? | Evolução do schema: o consumidor sabe tratar v1 e v2 juntos durante a migração. O contrato de mensagem quebra se o formato mudar sem combinar. |

### Cache e testes (C)
| Pergunta | Resposta curta |
|---|---|
| Por que TTL de 10 min? | Catálogo muda pouco. 10 min limita o dano de uma invalidação perdida e ainda dá taxa de acerto alta. Número de bom senso, não medido; o ADR diz isso. |
| O evict garante que nunca há dado velho? | Não. Um GET lento pode regravar o valor antigo; o segundo evict atrasado reduz muito a chance e o TTL limita o pior caso. |
| E cache stampede? | Com o nosso volume, não é problema. Em escala: `@Cacheable(sync = true)` ou TTL com jitter. |
| Por que não cachear no backlog também? | Seriam dois caches para invalidar, e o backlog já tem o snapshot. Um cache só, no dono do dado. |
| Por que evict e não `@CachePut`? | Com duas escritas concorrentes, o `put` pode gravar a versão mais velha por último. O evict força a releitura do banco, que é a fonte da verdade. |
| O pact substitui teste de integração? | Não. O pact garante o **formato** do contrato; não testa regra de negócio nem infra. Complementa. |
| Quem garante que o provedor roda o pact mais novo? | A ordem do reactor Maven (consumidor antes do provedor) e o CI, que falha se o pact gerado diferir do commitado. Em produção: Pact Broker + `can-i-deploy`. |
| H2 esconde bug de Postgres? | Pode esconder. Mitigação: SQL padrão nas migrations, `CatalogoInfraRealTest` com Postgres real no perfil A e a demo em Postgres real. |
| Retry no REST não piora uma queda? | Por isso são só 3 tentativas com backoff, só em erro transitório, e o breaker corta tudo quando a taxa de falha passa de 50%. |

### Para todos
| Pergunta | Resposta curta |
|---|---|
| O que fariam diferente com mais tempo? | Autenticação, Pact Broker, alerta na DLT, tracing com OpenTelemetry em vez de só correlation id, expurgo do outbox e dos eventos processados. |
| Qual foi a decisão mais difícil? | Retry bloqueante × não-bloqueante: ordem contra vazão (B detalha). |
| Onde a IA errou? | Ver o registro de uso de IA (casos reais, cada um conta o seu). |

---

## 7. Regras de ouro na banca

1. Responda primeiro o trade-off: "escolhemos X porque Y, custou Z".
2. Não sabe? "Não implementamos isso; faríamos assim: ..." vale mais do que inventar.
3. Ninguém interrompe quem está respondendo; complementa no final com "só somando...".
4. Não depurar ao vivo. Plano B: evidências + teste.
5. Fechar com o critério: "isso está evidenciado no `AVALIACAO.md`, critério N".
