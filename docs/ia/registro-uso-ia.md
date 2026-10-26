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

## Relato por integrante

> **A fazer por cada integrante antes da banca:** ler o código do seu papel e acrescentar aqui, em primeira pessoa, pelo menos um ponto que conferiu ou de que discordou. A banca vai perguntar a vocês, não à IA.

**João Pedro Guimarães dos Santos Santana (A — Arquiteto + Infra/Demo):** no geral, não tivemos problema com o uso da IA. Antes de pedir qualquer código, eu já tinha o escopo definido: como queria o sistema e como cada parte ia funcionar. Com isso, a IA trabalhou a partir de uma especificação nossa em vez de decidir o desenho sozinha, e a implementação andou rápido. Os erros da tabela acima apareceram no detalhe, não no desenho, e foram pegos pela revisão, pelo CI e pela demo.

**Marcos Emanuel Carvalho dos S D Farias (B — Mensageria):** fui o responsável por definir, com o grupo, o contrato de eventos, a regra de ordem por usuário e a política de retry/DLT. A parte contratual das mensagens, a semântica dos eventos e as regras de retry foram decisões tomadas à mão, discutidas e validadas antes de virar implementação. A IA foi útil para expandir alternativas, testar suposições e acelerar a codificação, mas não decidiu o desenho final.

Em vários momentos, por exemplo, a IA sugeriu caminhos diferentes para o retry: priorizar throughput em vez de ordem, ou permitir que uma partição do Kafka fosse liberada - mesmo quando um evento transitório exigisse espera longa. Como isso acarretaria mais complexidade na regra de negócio (e um risco maior de reordenação de eventos do mesmo usuário), nossa decisão foi manter a ordem estrita por usuário e aceitar o custo conhecido.

**Gabriel Lustosa Queiroz (C — Dados/Cache + Testes/Contrato):** nesta sessão final, fui eu quem deu as ordens e o escopo. A IA executou o setup, a demo e os artefatos; eu conferi rodando a demo no meu PC e gravando a tela. O que eu levo para a banca é o que os logs mostram, não o que o desenho promete: com o Redis fora, o `GET` continua 200 pelo banco; com o catálogo fora, o breaker responde 503 em ~1 ms e o backlog não pendura. Foi isso que me deu confiança para defender o cache como otimização, não como dependência.

## Reflexão

- **Onde a IA acelerou:** boilerplate de Spring, Kafka e Resilience4j; testes com EmbeddedKafka e Pact; scripts de demo; documentação. Três serviços com mais de 150 testes não caberiam no prazo sem isso.
- **Onde atrapalhou:** a primeira versão "passava em tudo" e mesmo assim tinha três defeitos de consistência sérios (itens 4, 5 e 6) e uma documentação que contradizia o código (item 10). Testes escritos pela mesma IA que escreveu o código tendem a confirmar o desenho, não a desafiá-lo.
- **O que não delegaríamos de novo sem revisão independente:** concorrência e garantias de entrega. A revisão de código e o perfil A no CI pegaram o que os testes iniciais não pegavam.
- **O que mantemos:** especificação escrita por nós antes do código, revisão por um agente separado e só de leitura, e a regra de que **toda afirmação de documento precisa de um teste ou de uma linha de log que a prove**.

---

## Adendo — sessão de 26/10/2026 (Gabriel Lustosa Queiroz, C)

> **Escopo e comando:** esta sessão foi pedida e dirigida por **Gabriel Lustosa Queiroz** (Pessoa C — Dados/Cache + Testes/Contrato). As ordens, o recorte e a decisão do que entraria no repositório foram dele; a IA executou a análise, o ambiente, a validação e os artefatos, reportando cada passo. Nenhuma linha de código dos três serviços foi alterada.

### O que foi feito

| # | Ordem de Gabriel | Execução | Evidência |
|---|---|---|---|
| 1 | Explicar o cache e as decisões arquiteturais | Leitura do ADR-004, de `catalogo-service/.../cache/`, de `JogoService` e dos testes de cache | Resumo conferido contra o código e os logs |
| 2 | Montar um roteiro de apresentação | Roteiro com tempos e falas de defesa, sobre a base de cenas da `docs/APRESENTACAO.md` | Texto entregue para revisão |
| 3 | Rodar a demo completa no PC | Setup em Bazzite com Podman rootless, build das 3 imagens, `compose up`, `00-reset.sh` e demo completa | `exit 0`; 8 cenas; logs dos 3 serviços |
| 4 | Provar o fallback do cache | `docker compose stop redis`, `GET /jogos/3`, `start redis` | HTTP 200 em ~1 s com `⚠ CACHE INDISPONÍVEL`; recuperação em ~20 s |
| 5 | Facilitar a gravação em uma tela | `scripts/demo/tmux-demo.sh` (grade 2×3) e `scripts/demo/gravacao.sh` (reset + contagem + cenas) | Layout e script validados na máquina |
| 6 | Roteiro sincronizado com os eventos | Leitura dos logs reais (correlation id, offsets, `eventId`) e escrita de `docs/ROTEIRO-DEMO-AO-VIVO.md` | Cenas 1 a 7 conferidas contra os logs da execução real |
| 7 | Entregar o vídeo em formato para Windows | `ffprobe` + `ffmpeg` (VP9 → H.264 High/5.1, `+faststart`) e cópia para o NTFS | MP4 de 2:07, 2560×1080, 3,3 MB, verificado com `ffprobe` |

### O que Gabriel conferiu à mão

- Rodou a demo no próprio PC e gravou a tela (vídeo de 2:07, 2560×1080).
- Conferiu o vídeo gravado e pediu a conversão para um formato que o Windows abrisse.
- Decidiu o que entraria neste registro e no PR.

### Erros e correções desta sessão

| # | O que a IA fez primeiro | Quem pegou | Correção |
|---|---|---|---|
| 1 | Assumiu o Docker disponível | `docker info` na máquina: daemon desligado e `sudo` com senha | Podman rootless via `DOCKER_HOST`, sem alterar o daemon |
| 2 | Primeiro `podman build` falhou com `short-name resolution enforced` | Log do build | `~/.config/containers/registries.conf` com `short-name-mode = "permissive"` |
| 3 | Imagens ficaram como `localhost/lettergames/...`, e o compose esperava `lettergames/...` | `podman images` antes do `compose up` | Tag adicional `docker.io/lettergames/...` |
| 4 | O vídeo gravado saiu sem áudio (padrão do Spectacle) | `ffprobe` na inspeção do arquivo | Avisado; conversão para MP4 e instruções de merge de narração |
| 5 | A primeira leitura foi feita no commit `ea2637c`; o `develop` já estava em `17f7e87` | `git log` antes de escrever | Releitura; as mudanças eram só de documentação |

> O vídeo não é versionado: é um artefato local (WebM de 2,1 MB convertido para MP4). O que fica no repositório são os scripts e o roteiro que o produzem.
