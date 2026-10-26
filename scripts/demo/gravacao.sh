#!/usr/bin/env bash
# Conduz a demo para gravação em uma tela (use no painel 6 do tmux-demo.sh).
#
#   ./scripts/demo/gravacao.sh            # pausa entre cenas (para narrar)
#   ./scripts/demo/gravacao.sh --ensaio   # roda direto, sem pausas
#   ./scripts/demo/gravacao.sh --sem-reset  # pula o reset (stack já limpo)
#
# O script NÃO grava: ele prepara o ambiente, dá a contagem e conduz as cenas.
# Inicie a gravação antes da contagem (Spectacle, OBS, Kooha ou asciinema).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"

if [ ! -S /var/run/docker.sock ] && [ -n "${XDG_RUNTIME_DIR:-}" ] && [ -S "$XDG_RUNTIME_DIR/podman/podman.sock" ]; then
  export DOCKER_HOST="unix://$XDG_RUNTIME_DIR/podman/podman.sock"
fi

ENSAIO=0
RESET=1
for arg in "$@"; do
  case "$arg" in
    --ensaio) ENSAIO=1 ;;
    --sem-reset) RESET=0 ;;
    *) echo "Opção desconhecida: $arg" >&2; exit 2 ;;
  esac
done

if ! docker compose ps --status running --format '{{.Name}}' | grep -q catalogo-service; then
  echo "A stack não está no ar. Rode: docker compose up -d --build" >&2
  exit 1
fi

if [ "$RESET" = 1 ]; then
  ./scripts/demo/00-reset.sh
fi

if [ "$ENSAIO" = 1 ]; then
  SEM_PAUSA=1 ./scripts/demo/demo-completa.sh
  exit 0
fi

printf '\n\033[1;42;97m  GRAVE AGORA  \033[0m  comece a gravação e espere a contagem.\n\n'
for i in $(seq 10 -1 1); do
  printf '\r  começando em %2d... ' "$i"
  sleep 1
done
printf '\r  \033[1;32mvalendo!\033[0m            \n'

./scripts/demo/demo-completa.sh

printf '\n\033[1;42;97m  FIM  \033[0m  pare a gravação.\n'
