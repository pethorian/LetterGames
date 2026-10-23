#!/usr/bin/env bash
# Cena 5 (narra: Pessoa B) — STATUS_ALTERADO chegando aos dois consumer groups (estatísticas e conquistas).
source "$(dirname "$0")/_comum.sh"
JOGO=${1:-1}
cena "CENA 5 · $USUARIO zera o jogo $JOGO"
ITEM=$(garantir_item "$JOGO")
info "item do backlog: $ITEM"

passo "QUERO_JOGAR → JOGANDO"
chamar PATCH "$BACKLOG/usuarios/$USUARIO/backlog/$ITEM" demo-05a '{"status":"JOGANDO"}'
sleep 1
passo "JOGANDO → ZERADO (terminal 3: os groups 'estatisticas' e 'conquistas' recebem o mesmo evento)"
chamar PATCH "$BACKLOG/usuarios/$USUARIO/backlog/$ITEM" demo-05b '{"status":"ZERADO"}'
sleep 2
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-05c
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/conquistas" demo-05d
