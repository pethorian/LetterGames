# Roteiro ao vivo — narração sincronizada com os logs

> Use com o layout de 6 painéis (`scripts/demo/tmux-demo.sh`).
> **Painéis:** 1 catálogo · 2 backlog · 3 estatísticas · 4 Redis · 5 Kafka · 6 comandos.
> O `correlationId` (`demo-02`, `demo-03a`…) aparece em **todos** os painéis: é o fio condutor.
> As falas abaixo cabem nos tempos reais da execução (a demo sem pausas dura ~1min40; com narração, ~6min).

---

## Cola de 1 página

| # | Cena | O que olhar | Frase-âncora |
|---|---|---|---|
| 1 | Cadastro | T1 `Jogo 11 criado` · T4 vazio | "Escrita não aquece cache; listagem não é cacheada." |
| 2 | Adiciona ao backlog | T2 `demo-02` → T1 `MISS` → T4 `SET` → T2 `✚ ITEM` → T5 evento → T3 dois groups | "Um POST, dois mundos: síncrono para validar, assíncrono para propagar." |
| 3 | Cache | T1 `MISS` → `HIT` → `EVICT` → `EVICT (2º)` → `MISS` | "Evict, não put; depois do commit; e o TTL é o teto do dano." |
| 4 | Breaker | T2 `RETRY` → `CLOSED → OPEN` → `503` em ~1ms → `OPEN → HALF_OPEN` → `CLOSED` | "Falha rápida protege o backlog; não gravamos item sem validar." |
| 5 | Zera o jogo | T2 `✎ STATUS` → T3 `⇠ KAFKA SUB` nos dois groups → `🏆` | "Um evento, duas projeções independentes." |
| 5b | Duplicata | T3 `↺ DUPLICADO ignorado` nos dois groups → `✔ Nada mudou` | "At-least-once + idempotência = efeito exatamente uma vez." |
| 6 | Falha transitória | T3 `⟳ RETRY 1/2/3` com 1000/2000/4000 ms → `📊` | "Falha de infraestrutura: segura a partição e espera; não perde nem pula a fila." |
| 7 | Poison pill | T3 `☠ DLT` sem `RETRY` → evento válido passa | "Falha permanente não adianta repetir; a partição não trava." |

---

## Antes de começar (20 s)

**FALA:**
> "Vou rodar a demo completa no ambiente real: três serviços, três Postgres, Redis e Kafka.
> Reparem no `correlationId` no canto de cada log — é o mesmo id atravessando HTTP, banco, outbox, Kafka e consumidor.
> São sete cenas; eu vou narrando o que aparece em cada painel."

Pressione **Enter** no painel 6 para começar.

---

## Cena 1 · Cadastro no catálogo (≈5 s)

**No painel 6:** `01-cadastrar-jogos.sh` — dois `POST /jogos` e um `GET /jogos?plataforma=PC`.

**Vai aparecer, nesta ordem:**
- T1: `Jogo 11 criado: Outer Wilds`
- T1: `Jogo 12 criado: Hollow Knight: Silksong`
- T4 (Redis): **nada**
- Respostas `201` e `200` no painel 6.

**FALA (enquanto roda):**
> "Cadastrar um jogo é escrita. Reparem no painel 4, o Redis: **nada acontece**.
> Cache-aside só é preenchido na leitura — escrita não aquece cache.
> E a listagem também não é cacheada, de propósito: invalidar listas é combinatorialmente chato e não é o caminho quente. O caminho quente é `GET /jogos/{id}`."

**Se perguntarem:** “Por que não cachear a lista?” → “Invalidar qualquer lista a cada escrita custa mais do que economiza; o ganho está na leitura por id.”

---

## Cena 2 · `joao` adiciona o jogo 1 ao backlog (≈12 s)

**No painel 6:** `02-adicionar-ao-backlog.sh`.

**Vai aparecer, nesta ordem (siga o `[demo-02]`):**
1. T2: `→ REST GET catalogo-service /jogos/1`
2. T1: `✘ CACHE MISS jogos::1 → consultando postgres-catalogo`
3. T4: `GET jogos::1` e depois `SET jogos::1` (com `PX`/TTL)
4. T2: `← REST 200 catalogo-service /jogos/1 (≈700 ms)` — a primeira é mais lenta (JIT/conexão)
5. T2: `✚ ITEM salvo usuario=joao jogo="Hollow Knight" status=QUERO_JOGAR · evento JOGO_ADICIONADO no outbox`
6. T2: `⇢ KAFKA PUB backlog.eventos key=joao tipo=JOGO_ADICIONADO eventId=... partição=2 offset=0`
7. T5: o JSON do evento com headers, chave e partição
8. T3: `⇠ KAFKA SUB ... group=conquistas` **e** `group=estatisticas` (a ordem pode variar)
9. T3: `📊 ESTATÍSTICAS joao: quero=1 jogando=0 zerados=0 abandonados=0 · 27h pendentes · 0h zeradas`
10. Painel 6: `HTTP 201` com o item criado.

**FALA (acompanhando):**
> "Um POST, dois mundos. Primeiro o **síncrono**: o backlog não confia no `jogoId` — ele chama o catálogo e valida. Esse `GET` é o que dá `MISS` no cache e popula o Redis.
> Depois o **assíncrono**: item e evento entram na **mesma transação** no outbox. O publicador pega a cabeça de fila e publica.
> Reparem no painel 5: a chave é o `usuarioId`, então tudo desse usuário cai na **mesma partição** — é o que dá ordem por usuário.
> E o evento é um **fato no passado**: `JOGO_ADICIONADO`. Quem projeta é o estatísticas, em dois consumer groups independentes."

**Se perguntarem:** “Por que outbox e não publicar direto?” → “Se o banco commita e o Kafka falha, o evento se perde. O outbox garante que o evento só existe se o item existir; a publicação vira at-least-once, e o consumidor deduplica.”

> **Atenção:** se rodar sem `00-reset.sh`, o passo 2 pode aparecer como `✔ CACHE HIT`. Nesse caso diga: “já estava aquecido da execução anterior; o cache é compartilhado entre réplicas.”

---

## Cena 3 · Cache do catálogo (≈10 s)

**No painel 6:** `03-cache.sh`.

**Vai aparecer, nesta ordem (siga `demo-03a..d`):**
1. T1: `✘ CACHE MISS jogos::3 → consultando postgres-catalogo` (`demo-03a`)
2. T4: `GET jogos::3` + `SET jogos::3` (TTL)
3. Painel 6: `TTL jogos::3` ≈ `600`
4. T1: `✔ CACHE HIT jogos::3` (`demo-03b`) — **sem SQL**
5. T1: `🗑 CACHE EVICT jogos::3` (`demo-03c`) + T4: `DEL jogos::3`
6. ≈500 ms depois, T1: `🗑 CACHE EVICT (2º, atrasado) jogos::3`
7. T1: `✘ CACHE MISS jogos::3` (`demo-03d`) e a resposta com a duração nova.

**FALA:**
> "Cache-aside. A primeira leitura paga o banco e grava com TTL de 10 minutos.
> A segunda é `HIT`: o painel 1 não mostra SQL, e como o advice de cache está **fora da transação**, nem abre conexão.
> No `PUT` a gente **apaga**, não atualiza. `Evict`, não `CachePut`: com duas escritas concorrentes, o put pode deixar a versão mais velha por último.
> E o evict roda **depois do commit** — senão um GET concorrente leria o valor antigo e regravaria no cache.
> Mesmo assim sobra a corrida clássica do cache-aside; por isso o **segundo evict**, 500 ms depois. No pior caso, o TTL de 10 minutos é o limite do dano."

**Se perguntarem:** “Isso é consistência forte?” → “Não. É consistência eventual com teto declarado: 10 minutos. Está no ADR-004, com a corrida desenhada.”

---

## Cena 4 · Catálogo fora do ar: circuit breaker (≈35 s)

**No painel 6:** `04-circuit-breaker.sh`.

**Vai aparecer, nesta ordem:**
1. T1: `Graceful shutdown` (o catálogo para).
2. Primeiros POSTs: T2 `→ REST GET` → `← REST FALHA ... (≈1000 ms): ConnectException: HTTP connect timed out` → `⟳ RETRY catalogo tentativa 2/3` → `3/3` → `✘ 503`.
3. Quando a janela fecha: T2 `⚡ BREAKER catalogo CLOSED → OPEN`.
4. POSTs seguintes: T2 `⚡ BREAKER catalogo ABERTO: chamada recusada sem tocar no catálogo` e `503` em **~1 ms**.
5. Catálogo volta; após 15 s: T2 `⚡ BREAKER catalogo OPEN → HALF_OPEN`.
6. Três POSTs de teste: `← REST 200` em ~8 ms e T2 `⚡ BREAKER catalogo HALF_OPEN → CLOSED`.

**FALA:**
> "Agora o catálogo cai. Reparem: cada tentativa gasta **1 segundo** de timeout de conexão, e o retry só repete erro transitório — rede, timeout, 5xx. 404 não se repete.
> Quando a taxa de falha passa de 50% numa janela de 10 chamadas, o breaker **abre**. A partir daí as chamadas nem tocam no catálogo: respondem 503 em cerca de **1 milissegundo**, sem pendurar thread.
> Depois de 15 segundos ele vai a **half-open**, libera três chamadas de teste e, se elas passarem, fecha.
> O importante: o backlog **não cai junto**. Ele só recusa a adição — a gente **não grava item sem validar**. Preferimos recusar a gravar lixo."

**Se perguntarem:** “Por que não aceitar e validar depois?” → “Aí o backlog teria jogo inexistente e a estatística quebraria. Validação é pré-condição; disponibilidade se resolve com breaker, não com dado sujo.”

---

## Cena 5 · `joao` zera o jogo (≈10 s)

**No painel 6:** `05-zerar-jogo.sh` — `QUERO_JOGAR → JOGANDO → ZERADO`.

**Vai aparecer, nesta ordem (siga `demo-05a` e `demo-05b`):**
1. T2: `✎ STATUS usuario=joao jogo="Hollow Knight" QUERO_JOGAR → JOGANDO · evento STATUS_ALTERADO no outbox`
2. T2: `⇢ KAFKA PUB ... tipo=STATUS_ALTERADO ... partição=2 offset=4`
3. T3: `⇠ KAFKA SUB ... group=estatisticas` e `group=conquistas`
4. T3: `📊 ESTATÍSTICAS ... quero=3 jogando=1 zerados=0 ...`
5. T2: `✎ STATUS ... JOGANDO → ZERADO` e `⇢ KAFKA PUB ... offset=5`
6. T3: `📊 ESTATÍSTICAS ... zerados=1 · 236h pendentes · 27h zeradas`
7. T3: `🏆 CONQUISTA DESBLOQUEADA joao: Primeiro zerado!`

**FALA:**
> "Agora um evento de **mudança de estado**. O mesmo `STATUS_ALTERADO` chega a **dois consumer groups independentes**.
> O group `estatisticas` recalcula contadores e horas; o `conquistas` avalia regras de conquista. Um evento, duas projeções.
> Nenhum dos dois valida transição de status — quem é dono dessa regra é o backlog. Os consumidores só reagem ao fato."

**Se perguntarem:** “Por que dois groups e não um?” → “Cada projeção tem seu offset e seu ritmo. Se conquistas quebra, estatísticas não para; e dá para reprocessar uma sem a outra.”

---

## Cena 5b · Reentrega do MESMO evento (≈20 s)

**No painel 6:** `05b-duplicado.sh` — lê o último `ZERADO` do tópico e republica idêntico.

**Vai aparecer, nesta ordem:**
1. Painel 6 mostra o JSON do evento e as estatísticas **antes**.
2. `kafka-console-producer` publica o mesmo evento (com header `correlationId:demo-05-dup`).
3. T3: `⇠ KAFKA SUB ... eventId=<o mesmo>` nos dois groups.
4. T3: `↺ DUPLICADO ignorado consumidor=estatisticas eventId=...`
5. T3: `↺ DUPLICADO ignorado consumidor=conquistas eventId=...`
6. Painel 6: estatísticas **depois**, idênticas, e `✔ Nada mudou: o consumidor é idempotente.`

**FALA:**
> "Isso não é artificial: é o cenário real de at-least-once. O consumidor processa, cai antes de commitar o offset, e a mensagem volta.
> A idempotência está no **banco**: o par `eventId` + `consumidor` é gravado na **mesma transação** do efeito. Se já existe, ignora.
> Reparem: o `eventId` é o mesmo; o offset é novo. Entrega pode repetir; **efeito acontece uma vez**."

**Se perguntarem:** “Por que não exactly-once do Kafka?” → “O EOS do Kafka cobre Kafka → Kafka. Nosso efeito é num Postgres, então a idempotência tem que estar no consumidor de qualquer jeito.”

---

## Cena 6 · Banco de estatísticas cai por alguns segundos (≈20 s)

**No painel 6:** `06-falha-transitoria.sh` — para o `postgres-estatisticas`, altera um status e religa.

**Vai aparecer, nesta ordem (siga `demo-06`):**
1. T3: `⇠ KAFKA SUB ... eventId=e995...`
2. T3: `⟳ RETRY 1 (transitória) consumidor=estatisticas ... próxima em 1000 ms: CannotCreateTransactionException`
3. T3: `⟳ RETRY 2 ... próxima em 2000 ms`
4. T3: `⟳ RETRY 3 ... próxima em 4000 ms`
5. Banco volta; T3: `⇠ KAFKA SUB ...` de novo e `📊 ESTATÍSTICAS ...`
6. Painel 6: estatísticas com o novo valor; **nada na DLT**.

**FALA:**
> "Aqui a falha é de **infraestrutura**, não do evento. O classificador distingue: falha transitória ganha retry com backoff — 1, 2, 4 segundos, até 10 minutos — e **segura a partição**.
> O evento não é descartado e não pula a fila. Quando o banco volta, a próxima tentativa processa **uma única vez** e segue.
> O custo está assumido: retry bloqueante troca vazão por ordem. Um banco fora por minutos para aquela partição."

**Se perguntarem:** “E se fosse falha permanente?” → “Aí não retenta: vai direto para a DLT. É a próxima cena.”

---

## Cena 7 · Poison pill (≈15 s)

**No painel 6:** `07-poison-pill.sh` — publica JSON inválido e um evento sem `eventId`, depois um evento válido.

**Vai aparecer, nesta ordem:**
1. T3: `⇠ KAFKA SUB ... tipo=null eventId=?` (JSON inválido)
2. T3: `☠ DLT backlog.eventos.DLT ← group=estatisticas ... motivo=EventoInvalidoException: JSON inválido`
3. T3: `☠ DLT ... group=conquistas ...` (mesma mensagem)
4. T3: o mesmo para `eventId ausente`, nos dois groups.
5. Evento válido (`Hades → JOGANDO`): T3 `⇠ KAFKA SUB` + `📊 ESTATÍSTICAS ... quero=2 jogando=2 ...`
6. Painel 6 imprime o conteúdo da DLT: **duas cópias de cada** mensagem (uma por group).

**FALA:**
> "Falha **permanente** não adianta repetir. JSON inválido e evento sem `eventId` vão direto para a **DLT**, sem retry, nos dois groups.
> E o próximo evento válido do mesmo usuário **passa normalmente** — a partição não travou.
> A DLT tem uma cópia por consumer group; por isso aparecem duas linhas de cada. Hoje a operação é inspeção no Kafka UI e republicação após a correção; o próximo passo é alerta."

**Se perguntarem:** “Quem consome a DLT?” → “Ninguém automático. É operação manual hoje: inspecionar, corrigir, republicar. A idempotência protege contra reprocessar.”

---

## Encerramento (20 s)

**FALA:**
> "Fechando: o caminho de escrita é síncrono só para validar; o resto é assíncrono e idempotente.
> A garantia é **at-least-once** — entrega pode repetir, efeito acontece uma vez.
> A ordem é **por usuário**, porque a chave da partição é o `usuarioId` e o outbox publica só a cabeça de fila.
> E as falhas são classificadas: transitória espera, permanente vai para a DLT.
> O cache fica no dono do dado, é opcional e tem o TTL como limite de dano.
> Nenhuma dessas trocas está escondida: estão nos oito ADRs e nos testes."

---

## Se uma cena falhar ao vivo

Não depure na frente da banca. Diga: **“essa cena está registrada em `docs/evidencias/`, e o teste que cobre esse comportamento é o X”**, e siga.

| Cena | Teste equivalente |
|---|---|
| 3 | `JogoCacheTest`, `CatalogoInfraRealTest` |
| 4 | `CatalogoClientResilienciaTest` |
| 5 e 5b | `DoisConsumerGroupsTest`, `ReprocessamentoIdempotenteTest` |
| 6 | `FalhaTransitoriaTest` |
| 7 | `FalhaPermanenteTest` |

---

## Números para não errar

| Item | Valor |
|---|---|
| Cache | `jogos::{id}`, TTL 10 min, sem nulos, evict pós-commit + 2º evict 500 ms |
| Fallback do cache | `CacheErrorHandler`; Redis fora → 200 pelo banco; timeouts 500 ms |
| REST backlog→catálogo | connect 1 s / read 2 s; 3 tentativas; backoff 200 ms; breaker 50% em 10, aberto 15 s, 3 em half-open |
| Kafka | `backlog.eventos`, 3 partições, chave `usuarioId`; DLT própria |
| Garantia | at-least-once + idempotência `(eventId, consumidor)` = efeito 1× |
| Retry do consumidor | transitória: 1 s ×2 até 30 s, por até 10 min; processamento: 4 tentativas (1, 2, 4 s) → DLT |
| Outbox | publica a cada 500 ms; `acks=all`; idempotência ligada; estaciona após 10 falhas não transitórias |
| Testes | 160 no perfil B; 165 no perfil A |
