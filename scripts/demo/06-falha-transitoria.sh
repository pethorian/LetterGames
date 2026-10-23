#!/usr/bin/env bash
# Cena 6 (narra: Pessoa B) — falha transitória: retry com backoff exponencial resolve sozinho.
source "$(dirname "$0")/_comum.sh"
JOGO=${1:-3}
cena "CENA 6 · Banco de estatísticas cai por alguns segundos"
ITEM=$(garantir_item "$JOGO")
sleep 2

passo "Derrubando o postgres-estatisticas"
dc stop postgres-estatisticas

passo "Alterando um status no backlog (o backlog não percebe nada: só publica)"
chamar PATCH "$BACKLOG/usuarios/$USUARIO/backlog/$ITEM" demo-06 '{"status":"JOGANDO"}'
info "Terminal 3: ⟳ RETRY 1/4 (1s), 2/4 (2s)..."
sleep 3

passo "Religando o banco antes de esgotar as 4 tentativas (~1 s + 2 s + 4 s)"
dc start postgres-estatisticas
sleep 8
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-06b
info "O evento foi processado uma única vez, sem ir para a DLT."
