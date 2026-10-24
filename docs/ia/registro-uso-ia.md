# Registro de uso de IA

Evidência do critério 8. O valor deste registro está nos casos em que **a IA errou e alguém percebeu**: uma revisão, um teste, o CI ou a demo contra infraestrutura real.

## Como usamos

| Etapa | Ferramenta | Como |
|---|---|---|
| Planejamento | Claude Code | A partir do `brief-alunos.md`, gerou o plano (`docs/PLANEJAMENTO.md`), o roteiro da banca e os ADRs em rascunho. |
| Implementação | Claude Code com subagentes | A fundação (Maven, contratos, compose, CI) foi feita direto. Depois, um agente por serviço, em worktrees isolados e em paralelo, cada um com uma especificação nossa: contratos congelados, valores de timeout e backoff, logs exatos, testes obrigatórios. Modelo maior para backlog e estatísticas (concorrência, Kafka); modelo menor para o catálogo. |
| Revisão | Agentes só de leitura | Um **revisor de código** (correção, transações, concorrência, divergência doc × código) e um **auditor de testes** (os testes provam o que a rubrica pede?). Os achados viraram 3 PRs de correção. |
| Validação | CI + demo real | CI com perfis B e A em todo PR; demo completa rodada várias vezes contra o `docker compose`. |

## O que a IA errou e como pegamos

| # | Onde | O que a IA fez primeiro | Quem pegou | Correção |
|---|---|---|---|---|
| 1 | Plano e ADR-003 | Publicar o evento "depois do commit" (`@TransactionalEventListener`) e deixar o outbox para depois | Discussão do ADR: dual write perde evento com o Kafka fora | Transactional outbox implementado (PR #2) |
| 2 | Retry do backlog | O fallback do breaker aberto lançava a mesma exceção que o Retry repete, então a recusa era repetida 3 vezes | O próprio agente, ao analisar a ordem dos aspectos | `CatalogoBreakerAbertoException`, ignorada pelo Retry; teste mede que nenhuma requisição chega ao servidor |
| 3 | Cache | O `@CacheEvict` rodava antes do commit (mesma precedência da transação) | Agente do catálogo | `@EnableCaching(order = ...)` para o evict sair depois do commit |
| 4 | Consumidor | **Mesma política de retry para toda falha:** o banco fora por ~15 s mandava o evento para a DLT e o evento seguinte do usuário era aplicado, deixando a projeção divergente para sempre | **Revisor de código** (achado ALTO), confirmado nas evidências da demo (o retry chegou a 4/4) | Classificação de falhas: transitória segura a partição por até 10 min; só inválido e processamento vão para a DLT (PR #8) |
| 5 | Backlog | **Lost update:** dois PATCH concorrentes publicavam `QJ→JOGANDO` e `QJ→ABANDONADO` | **Revisor de código** (achado ALTO) | Lock pessimista + `@Version`; teste determinístico que falha sem o lock (PR #9, ADR-008) |
| 6 | Outbox | Um evento com falha permanente travava a publicação de **todos** os usuários (`break` no lote) | Revisor de código | Isolamento por chave e estacionamento após 10 falhas (PR #9) |
| 7 | Outbox | Primeira ideia: `SKIP LOCKED` sobre o lote inteiro, o que deixaria outra instância publicar o 2º evento do usuário antes do 1º | O próprio agente, ao projetar a correção | Trava só a cabeça de fila de cada chave |
| 8 | Outbox | Contar toda falha para estacionar faria uma queda do Kafka estacionar a cabeça de todos os usuários | O próprio agente | Falha transitória conta tentativa mas não estaciona |
| 9 | Outbox | Ordenar por `criado_em`: dois eventos do mesmo usuário com o mesmo instante viravam cabeça de fila juntos | **Job do perfil A no CI** (falha intermitente) | Coluna `sequencia` gerada no INSERT (PR #11) |
| 10 | Documentação | O ADR-003 dizia "INSERT e trata violação de PK como duplicado", o **oposto** do código (verifica e insere; a PK é a rede de segurança). O AVALIACAO citava classes que não existiam | **Revisor de código** | ADRs e AVALIACAO reescritos a partir do código final |
| 11 | Testes | O CLAUDE.md prometia Testcontainers no perfil A, mas não havia nenhum teste com `@Tag("docker")`. Também não havia contrato do evento Kafka | **Auditor de testes** | `CatalogoInfraRealTest` (Postgres e Redis reais) e Pact de mensagem (PRs #7, #8 e #9) |
| 12 | Testes | Estado estático compartilhado no servidor HTTP falso (um teste lento roubava respostas do seguinte) e asserção de tempo `< 200 ms` frágil em CI | Auditor de testes | Cenário por teste e asserção por "nenhuma requisição nova" |
| 13 | Testes | Testes do catálogo alteravam jogos do seed num H2 compartilhado entre contextos, o que **só falhava no CI Linux** (outra ordem de execução) | **CI** | Cada teste cria o seu próprio jogo |
| 14 | Pact | O pact gerado no Windows saía com CRLF e o CI (Linux) acusava diferença; os matchers sem exemplo geravam valores aleatórios a cada build | CI (`git diff --exit-code` no pact) | `.gitattributes` com `eol=lf` e exemplos fixos nos matchers |
| 15 | Demo | Scripts usando `/tmp` (o curl nativo do Windows não enxerga o `/tmp` do Git Bash), caminho `/c/...` passado ao `docker.exe` e `localhost` (~200 ms por chamada depois que as portas foram para `127.0.0.1`) | Rodando a demo de verdade | Corpo lido do stdout, caminho Windows no compose, `127.0.0.1` |
| 16 | Demo | Com o banco fora, o GET de estatísticas respondia 500 com stack trace no terminal da banca | Rodando a demo de verdade | 503 `BANCO_INDISPONIVEL` em uma linha (PR #10) |
| 17 | Outbox | O contador de tentativas somava falhas transitórias: depois de uma queda longa do Kafka, **uma** falha permanente estacionava o evento; a interrupção no shutdown também contava como falha do evento | **Revisão final** (segunda revisão independente, depois das correções) | Só falha atribuível ao evento conta; interrupção encerra a rodada sem registrar; erro de banco encerra a rodada; chaves bloqueadas saem da busca (PR #13) |
| 18 | Catálogo | `\p{Cntrl}` em Java só cobre controles ASCII: o NEL (`\u0085`) passava pela validação | Revisão final | `\p{Cc}`; o teste com NEL falha sem a correção |

## O que validamos à mão

- A **demo completa** contra o `docker compose` real, do zero (`down -v`), várias vezes, lendo os logs dos três serviços para confirmar o correlation id ponta a ponta, o `↺ DUPLICADO`, o `⚡ BREAKER`, o `⟳ RETRY` e o `☠ DLT`.
- Os **números** dos ADRs contra o `application.yml` (TTL, backoff, janela do breaker, tentativas).
- **Cada ADR contra o código** depois das correções. Foi aí que achamos o ADR-003 contradizendo a implementação.
- Que os testes novos **falham sem a correção** (lock pessimista, empate do outbox), e não só que passam com ela.

> **A fazer por cada integrante antes da banca:** ler o código do seu papel e acrescentar aqui, em primeira pessoa, pelo menos um ponto que conferiu ou de que discordou. A banca vai perguntar a vocês, não à IA.

## Reflexão

- **Onde a IA acelerou:** boilerplate de Spring, Kafka e Resilience4j; testes com EmbeddedKafka e Pact; scripts de demo; documentação. Três serviços com mais de 150 testes não caberiam no prazo sem isso.
- **Onde atrapalhou:** a primeira versão "passava em tudo" e mesmo assim tinha três defeitos de consistência sérios (itens 4, 5 e 6) e uma documentação que contradizia o código (item 10). Testes escritos pela mesma IA que escreveu o código tendem a confirmar o desenho, não a desafiá-lo.
- **O que não delegaríamos de novo sem revisão independente:** concorrência e garantias de entrega. A revisão de código e o perfil A no CI pegaram o que os testes iniciais não pegavam.
- **O que mantemos:** especificação escrita por nós antes do código, revisão por um agente separado e só de leitura, e a regra de que **toda afirmação de documento precisa de um teste ou de uma linha de log que a prove**.
