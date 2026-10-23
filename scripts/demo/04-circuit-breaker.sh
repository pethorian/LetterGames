#!/usr/bin/env bash
# Cena 4 (narra: Pessoa C) — timeout + retry + circuit breaker no backlog → catálogo.
source "$(dirname "$0")/_comum.sh"
cena "CENA 4 · Catálogo fora do ar: circuit breaker"

passo "Derrubando o catalogo-service"
dc stop catalogo-service
pausa

passo "6 tentativas de adicionar jogos: primeiro RETRY (terminal 2), depois o breaker ABRE e responde 503 na hora"
for jogo in 4 5 6 7 8 9; do
  chamar POST "$BACKLOG/usuarios/$USUARIO/backlog" "demo-04-$jogo" "{\"jogoId\":$jogo}"
done
info "Estado do breaker no actuator:"
curl -s "$BACKLOG/actuator/circuitbreakers" | bonito | sed 's/^/    /'
pausa

passo "Religando o catálogo; depois de 15 s o breaker vai a HALF_OPEN e, com sucesso, volta a CLOSED"
dc start catalogo-service
esperar_saudavel catalogo-service 8081
info "aguardando a janela de 15 s do breaker aberto..."
sleep 15
info "HALF_OPEN libera 3 chamadas de teste; se derem certo, o breaker fecha (terminal 2: HALF_OPEN → CLOSED)"
for jogo in 4 5 6; do
  chamar POST "$BACKLOG/usuarios/$USUARIO/backlog" "demo-04-ok-$jogo" "{\"jogoId\":$jogo}"
done
