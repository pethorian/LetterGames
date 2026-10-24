#!/usr/bin/env bash
# Cena 6 (narra: Pessoa B) — falha transitória: retry com backoff exponencial resolve sozinho, sem perder o evento.
source "$(dirname "$0")/_comum.sh"
JOGO=${1:-3}
cena "CENA 6 · Banco de estatísticas cai por alguns segundos"
ITEM=$(garantir_item "$JOGO")
sleep 2
jogando_antes=$(curl -s "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" | campo '.porStatus.JOGANDO')

passo "Derrubando o postgres-estatisticas"
dc stop postgres-estatisticas

passo "Alterando um status no backlog (o backlog não percebe nada: só publica)"
chamar PATCH "$BACKLOG/usuarios/$USUARIO/backlog/$ITEM" demo-06 '{"status":"JOGANDO"}'
info "Terminal 3: ⟳ RETRY 1 (transitória) próxima em 1000 ms, RETRY 2 ... 2000 ms (segura a partição, não descarta)"
sleep 3

passo "Religando o banco: o retry de falha transitória espera até 10 min, então o evento não é perdido"
dc start postgres-estatisticas
info "aguardando a próxima tentativa do consumidor processar o evento..."
for _ in $(seq 1 30); do
  agora=$(curl -s "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" | campo '.porStatus.JOGANDO' 2>/dev/null || true)
  [ -n "$agora" ] && [ "$agora" != "$jogando_antes" ] && break
  sleep 1
done
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-06b
info "O evento foi processado uma única vez, sem ir para a DLT."
