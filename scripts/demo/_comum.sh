#!/usr/bin/env bash
# Funções compartilhadas pelos scripts de demo. Uso: source "$(dirname "$0")/_comum.sh"
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CATALOGO="${CATALOGO_URL:-http://localhost:8081}"
BACKLOG="${BACKLOG_URL:-http://localhost:8082}"
ESTATISTICAS="${ESTATISTICAS_URL:-http://localhost:8083}"
USUARIO="${USUARIO:-joao}"

# No Git Bash do Windows, evita que /opt/... vire C:/Program Files/Git/opt/...
export MSYS_NO_PATHCONV=1

dc() { docker compose --project-directory "$RAIZ" "$@"; }

cena() { printf '\n\033[1;45;97m  %s  \033[0m\n' "$*"; }
passo() { printf '\n\033[1;35m▶ %s\033[0m\n' "$*"; }
info() { printf '\033[2m  %s\033[0m\n' "$*"; }
pausa() { [ -n "${SEM_PAUSA:-}" ] && return 0; printf '\033[2m  [Enter para continuar]\033[0m'; read -r _; }

# Formata JSON com jq, ou node, ou devolve como veio.
bonito() {
  if command -v jq >/dev/null 2>&1; then
    jq . 2>/dev/null || cat
  elif command -v node >/dev/null 2>&1; then
    node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{console.log(JSON.stringify(JSON.parse(s),null,2))}catch(e){if(s.trim())console.log(s)}})'
  else
    cat
  fi
}

# Extrai um campo de um JSON (caminho JS, ex.: '.id' ou '.find(i=>i.jogoId==1).id').
campo() {
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const v=eval("JSON.parse(s)"+process.argv[1]);console.log(v===undefined?"":v)})' "$1"
}

# chamar MÉTODO URL CORRELATION_ID [CORPO_JSON]
# Imprime o comando, o status HTTP, o tempo e o corpo formatado. O corpo cru fica em $ULTIMA_RESPOSTA.
chamar() {
  local metodo=$1 url=$2 cid=$3 corpo=${4:-}
  local args=(-s -o /tmp/lettergames-resp.$$ -w '%{http_code} %{time_total}' -X "$metodo" "$url"
              -H "X-Correlation-Id: $cid" -H 'Accept: application/json')
  [ -n "$corpo" ] && args+=(-H 'Content-Type: application/json' -d "$corpo")
  printf '\033[1;36m$ %s %s\033[0m \033[33m[X-Correlation-Id: %s]\033[0m\n' "$metodo" "$url" "$cid"
  [ -n "$corpo" ] && info "corpo: $corpo"
  local saida
  saida=$(curl "${args[@]}" || echo "000 0")
  ULTIMO_STATUS=${saida%% *}
  local tempo=${saida##* }
  ULTIMA_RESPOSTA=$(cat /tmp/lettergames-resp.$$ 2>/dev/null || true)
  rm -f /tmp/lettergames-resp.$$
  local cor=32; [ "${ULTIMO_STATUS:0:1}" = "4" ] && cor=33; [ "${ULTIMO_STATUS:0:1}" = "5" ] || [ "$ULTIMO_STATUS" = "000" ] && cor=31
  printf '\033[1;%sm  ← HTTP %s\033[0m \033[2m(%ss)\033[0m\n' "$cor" "$ULTIMO_STATUS" "$tempo"
  [ -n "$ULTIMA_RESPOSTA" ] && printf '%s' "$ULTIMA_RESPOSTA" | bonito | sed 's/^/    /'
  return 0
}

# Id do item do backlog do $USUARIO para um jogoId (vazio se não existir).
item_do_jogo() {
  curl -s "$BACKLOG/usuarios/$USUARIO/backlog" -H 'X-Correlation-Id: demo-busca' | campo ".find(i=>i.jogoId==$1)?.id"
}

# Garante que o jogo está no backlog e devolve o id do item.
garantir_item() {
  local jogo=$1 id
  id=$(item_do_jogo "$jogo")
  if [ -z "$id" ]; then
    curl -s -X POST "$BACKLOG/usuarios/$USUARIO/backlog" -H 'Content-Type: application/json' \
      -H 'X-Correlation-Id: demo-prep' -d "{\"jogoId\":$jogo}" >/dev/null
    id=$(item_do_jogo "$jogo")
  fi
  echo "$id"
}

esperar_saudavel() {
  local servico=$1 porta=$2
  info "aguardando $servico ficar saudável..."
  for _ in $(seq 1 60); do
    curl -sf "http://localhost:$porta/actuator/health" >/dev/null 2>&1 && { info "$servico UP"; return 0; }
    sleep 2
  done
  echo "  $servico não subiu a tempo" >&2
  return 1
}

kafka_bin() { dc exec -T kafka /opt/kafka/bin/"$@"; }
