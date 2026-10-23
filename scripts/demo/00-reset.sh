#!/usr/bin/env bash
# Deixa o ambiente pronto para a demo: limpa backlog, estatísticas, cache e tópicos, e mantém o seed do catálogo.
source "$(dirname "$0")/_comum.sh"
cena "RESET · preparando o ambiente da demo"

passo "Parando os serviços (consumidores saem dos groups)"
dc stop backlog-service estatisticas-service catalogo-service

passo "Limpando as bases do backlog e das estatísticas"
dc exec -T postgres-backlog psql -q -U "${POSTGRES_USER:-lettergames}" -d backlog \
  -c "TRUNCATE itens_backlog, outbox_eventos RESTART IDENTITY"
dc exec -T postgres-estatisticas psql -q -U "${POSTGRES_USER:-lettergames}" -d estatisticas \
  -c "TRUNCATE estatisticas_usuario, eventos_processados, progresso_conquistas, conquistas"

passo "Restaurando o catálogo para o seed original (10 jogos)"
dc exec -T postgres-catalogo psql -q -U "${POSTGRES_USER:-lettergames}" -d catalogo -c "
  DELETE FROM jogos WHERE id > 10;
  UPDATE jogos SET duracao_media_horas = 12 WHERE id = 3;
  SELECT setval(pg_get_serial_sequence('jogos', 'id'), 10);" >/dev/null

passo "Esvaziando o cache e recriando os tópicos"
dc exec -T redis redis-cli FLUSHALL >/dev/null
for t in backlog.eventos backlog.eventos.DLT; do
  kafka_bin kafka-topics.sh --bootstrap-server localhost:29092 --delete --topic "$t" >/dev/null 2>&1 || true
done
sleep 3

passo "Subindo os serviços de novo (eles recriam os tópicos com 3 partições)"
dc start catalogo-service backlog-service estatisticas-service
esperar_saudavel catalogo-service 8081
esperar_saudavel backlog-service 8082
esperar_saudavel estatisticas-service 8083
printf '\n\033[1;32m  ✔ Ambiente limpo. Abra os terminais e comece pela cena 1.\033[0m\n'
