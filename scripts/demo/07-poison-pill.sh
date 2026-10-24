#!/usr/bin/env bash
# Cena 7 (narra: Pessoa B) — falha permanente: vai direto para a DLT, sem retry, sem travar a partição.
source "$(dirname "$0")/_comum.sh"
cena "CENA 7 · Mensagem envenenada (poison pill)"

publicar_cru() {
  kafka_bin kafka-console-producer.sh --bootstrap-server localhost:29092 --topic backlog.eventos \
    --property parse.key=true --property parse.headers=true
}

passo "Publicando no tópico um JSON inválido e um evento sem eventId, com a chave de $USUARIO"
printf 'correlationId:demo-07-lixo\t%s\t%s\n' "$USUARIO" '{isto nao e json' | publicar_cru
printf 'correlationId:demo-07-sem-id\t%s\t{"tipo":"JOGO_ADICIONADO","usuarioId":"%s","statusNovo":"QUERO_JOGAR"}\n' \
  "$USUARIO" "$USUARIO" | publicar_cru
info "Terminal 3: '☠ DLT ... group=estatisticas' e 'group=conquistas' imediatamente, sem ⟳ RETRY"
sleep 2

passo "Um evento válido do mesmo usuário passa normalmente (a partição não travou)"
ITEM=$(garantir_item 5)
chamar PATCH "$BACKLOG/usuarios/$USUARIO/backlog/$ITEM" demo-07-ok '{"status":"JOGANDO"}'
sleep 2

passo "Conteúdo da DLT (também no Kafka UI: http://localhost:8080)"
kafka_bin kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic backlog.eventos.DLT \
  --from-beginning --timeout-ms 5000 --property print.key=true 2>/dev/null | tr -d '\r' | sed 's/^/    /' || true
