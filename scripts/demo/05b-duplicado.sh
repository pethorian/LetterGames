#!/usr/bin/env bash
# Cena 5b (narra: Pessoa B) — at-least-once + consumidor idempotente = efeito exatamente uma vez.
source "$(dirname "$0")/_comum.sh"
cena "CENA 5b · Reentrega do MESMO evento (mesmo eventId)"

passo "Lendo do tópico o último evento ZERADO de $USUARIO"
evento=$(kafka_bin kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic backlog.eventos \
  --from-beginning --timeout-ms 6000 --property print.key=true 2>/dev/null | tr -d '\r' \
  | awk -F'\t' -v u="$USUARIO" '$1 == u && $2 ~ /"statusNovo":"ZERADO"/ { ultimo = $2 } END { print ultimo }')
if [ -z "$evento" ]; then
  echo "  Nenhum evento ZERADO encontrado; rode antes o 05-zerar-jogo.sh"
  exit 1
fi
printf '%s' "$evento" | bonito | sed 's/^/    /'

passo "Estatísticas ANTES da reentrega"
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-05e
antes=$ULTIMA_RESPOSTA
pausa

passo "Republicando o evento idêntico no tópico (simula reentrega após queda do consumidor)"
printf 'correlationId:demo-05-dup,tipo:STATUS_ALTERADO\t%s\t%s\n' "$USUARIO" "$evento" \
  | kafka_bin kafka-console-producer.sh --bootstrap-server localhost:29092 --topic backlog.eventos \
      --property parse.key=true --property parse.headers=true
info "Terminal 3: '↺ DUPLICADO ignorado' nos dois consumer groups"
sleep 2

passo "Estatísticas DEPOIS da reentrega"
chamar GET "$ESTATISTICAS/usuarios/$USUARIO/estatisticas" demo-05f
if [ "$antes" = "$ULTIMA_RESPOSTA" ]; then
  printf '\033[1;32m  ✔ Nada mudou: o consumidor é idempotente.\033[0m\n'
else
  printf '\033[1;31m  ✘ As estatísticas mudaram!\033[0m\n'
fi
