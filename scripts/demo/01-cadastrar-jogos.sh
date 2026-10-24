#!/usr/bin/env bash
# Cena 1 (narra: Pessoa C) — escrita no catálogo não popula o cache (cache-aside só preenche na leitura).
source "$(dirname "$0")/_comum.sh"
cena "CENA 1 · Cadastro no catálogo"

passo "Cadastrando dois jogos novos (terminal 1: INSERT; terminal 4: nada no Redis)"
chamar POST "$CATALOGO/jogos" demo-01a '{"titulo":"Outer Wilds","plataforma":"PC","genero":"Aventura","duracaoMediaHoras":22}'
chamar POST "$CATALOGO/jogos" demo-01b '{"titulo":"Hollow Knight: Silksong","plataforma":"Switch","genero":"Metroidvania","duracaoMediaHoras":40}'

passo "Listando os jogos de PC (listagem não é cacheada — ADR-004)"
chamar GET "$CATALOGO/jogos?plataforma=PC" demo-01c
