#!/usr/bin/env bash
# Roda todas as cenas na ordem do roteiro (docs/APRESENTACAO.md §5), pausando entre elas.
# SEM_PAUSA=1 ./scripts/demo/demo-completa.sh roda direto (ensaio ou evidência de execução).
DIR="$(dirname "$0")"
source "$DIR/_comum.sh"

for cena in 01-cadastrar-jogos 02-adicionar-ao-backlog 03-cache 04-circuit-breaker \
            05-zerar-jogo 05b-duplicado 06-falha-transitoria 07-poison-pill; do
  "$DIR/$cena.sh"
  pausa
done
printf '\n\033[1;32m  ✔ Demo completa.\033[0m\n'
