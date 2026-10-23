#!/usr/bin/env bash
# Cena 2 (narra: Pessoa A na parte REST, Pessoa B na parte Kafka) — o fluxo ponta a ponta.
source "$(dirname "$0")/_comum.sh"
JOGO=${1:-1}
cena "CENA 2 · $USUARIO adiciona o jogo $JOGO ao backlog"
info "Siga o id demo-02 em todos os terminais: 6 → 2 → 1 (REST) → 4 (Redis) → 2 → 5 (Kafka) → 3"

passo "POST no backlog: valida o jogo no catálogo (REST) e grava item + evento no outbox (mesma transação)"
chamar POST "$BACKLOG/usuarios/$USUARIO/backlog" demo-02 "{\"jogoId\":$JOGO}"

passo "O publicador do outbox envia o evento ao Kafka e o estatisticas-service consome (aguardando 2s)"
sleep 2
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-02b
