#!/usr/bin/env bash
# Layout da banca em uma tela só (equivalente Linux do scripts/abrir-terminais.ps1).
#
#   1 CATÁLOGO (logs)  | 2 BACKLOG (logs)        | 3 ESTATÍSTICAS (logs)
#   4 REDIS MONITOR    | 5 KAFKA backlog.eventos | 6 COMANDOS (shell)
#
# Uso:   ./scripts/demo/tmux-demo.sh          # abre (ou reusa) a sessão
#        ./scripts/demo/tmux-demo.sh --reset  # recria a sessão do zero
#        tmux attach -t lettergames           # entrar depois
#
# Pré-requisito: stack no ar (docker compose up -d). Funciona com Docker ou Podman rootless.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SESSAO=lettergames

# Docker daemon padrão; se não existir, usa o socket do Podman rootless (Bazzite/Fedora Atomic).
if [ ! -S /var/run/docker.sock ] && [ -n "${XDG_RUNTIME_DIR:-}" ] && [ -S "$XDG_RUNTIME_DIR/podman/podman.sock" ]; then
  export DOCKER_HOST="unix://$XDG_RUNTIME_DIR/podman/podman.sock"
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker/Podman não está acessível. Suba o daemon antes." >&2
  exit 1
fi

if [ "${1:-}" = "--reset" ]; then
  tmux kill-session -t "$SESSAO" 2>/dev/null || true
fi

if tmux has-session -t "$SESSAO" 2>/dev/null; then
  echo "Sessão '$SESSAO' já existe. Use --reset para recriar."
  exec tmux attach -t "$SESSAO"
fi

LOG='docker compose logs -f --tail 5 --no-log-prefix'
KAFKA='docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic backlog.eventos --property print.key=true --property print.headers=true --property print.partition=true'

tmux new-session -d -s "$SESSAO" -n demo -c "$RAIZ"
tmux split-window -h -t "$SESSAO:demo"
tmux split-window -h -t "$SESSAO:demo"
tmux split-window -v -t "$SESSAO:demo.0"
tmux split-window -v -t "$SESSAO:demo.2"
tmux split-window -v -t "$SESSAO:demo.4"
tmux select-layout -t "$SESSAO:demo" tiled
tmux set-option -t "$SESSAO" pane-border-status top
tmux set-option -t "$SESSAO" pane-border-format ' #{pane_title} '
tmux set-option -t "$SESSAO" mouse on

titulo() { tmux select-pane -t "$SESSAO:demo.$1" -T "$2"; }
titulo 0 '1 CATÁLOGO';          tmux send-keys -t "$SESSAO:demo.0" "clear; $LOG catalogo-service" C-m
titulo 1 '2 BACKLOG';           tmux send-keys -t "$SESSAO:demo.1" "clear; $LOG backlog-service" C-m
titulo 2 '3 ESTATÍSTICAS';      tmux send-keys -t "$SESSAO:demo.2" "clear; $LOG estatisticas-service" C-m
titulo 3 '4 REDIS MONITOR';     tmux send-keys -t "$SESSAO:demo.3" "clear; docker compose exec redis redis-cli MONITOR" C-m
titulo 4 '5 KAFKA';             tmux send-keys -t "$SESSAO:demo.4" "clear; $KAFKA" C-m
titulo 5 '6 COMANDOS';          tmux send-keys -t "$SESSAO:demo.5" "clear; cd '$RAIZ'; printf '\n  COMANDOS · comece por ./scripts/demo/00-reset.sh\n\n'" C-m
tmux select-pane -t "$SESSAO:demo.5"

echo "Layout aberto na sessão '$SESSAO'."
echo "  Aumente a fonte no terminal (Ctrl + =) antes de gravar."
echo "  Para sair sem matar: Ctrl+b d   ·   voltar: tmux attach -t $SESSAO"
exec tmux attach -t "$SESSAO"
