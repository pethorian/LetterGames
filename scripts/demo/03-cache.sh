#!/usr/bin/env bash
# Cena 3 (narra: Pessoa C) — cache-aside com TTL e invalidação explícita por evict.
source "$(dirname "$0")/_comum.sh"
JOGO=${1:-3}
cena "CENA 3 · Cache do catálogo (jogo $JOGO)"

passo "1º GET: CACHE MISS → banco → SET no Redis com TTL de 10 min (terminais 1 e 4)"
chamar GET "$CATALOGO/jogos/$JOGO" demo-03a
passo "2º GET: CACHE HIT, nenhum SQL no terminal 1"
chamar GET "$CATALOGO/jogos/$JOGO" demo-03b
info "TTL restante da chave no Redis (segundos):"
dc exec -T redis redis-cli TTL "jogos::$JOGO" | sed 's/^/    /'
pausa

passo "PUT: atualiza o jogo e INVALIDA a chave (DEL no terminal 4). Evict, não put (ADR-004)"
novo=$(printf '%s' "$ULTIMA_RESPOSTA" | node -e '
  let s=""; process.stdin.on("data", d => s += d).on("end", () => {
    const j = JSON.parse(s); delete j.id; j.duracaoMediaHoras = (j.duracaoMediaHoras || 1) + 1;
    console.log(JSON.stringify(j));
  })')
chamar PUT "$CATALOGO/jogos/$JOGO" demo-03c "$novo"
passo "GET depois do PUT: MISS de novo, devolvendo o valor novo"
chamar GET "$CATALOGO/jogos/$JOGO" demo-03d
