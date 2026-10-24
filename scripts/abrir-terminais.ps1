# Abre o layout da banca no Windows Terminal: grade 2x3 (ver docs/APRESENTACAO.md, seção 2).
#
#  1 CATÁLOGO (logs)  | 2 BACKLOG (logs)        | 3 ESTATÍSTICAS (logs)
#  4 REDIS MONITOR    | 5 KAFKA backlog.eventos | 6 COMANDOS (Git Bash)
#
# Uso: powershell -ExecutionPolicy Bypass -File scripts\abrir-terminais.ps1
# Pré-requisito: docker compose up -d (os painéis só leem logs e tópicos).

$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bash = 'C:\Program Files\Git\bin\bash.exe'

function Painel([string]$titulo, [string]$comando) {
    # -NoExit mantém o painel aberto se o comando terminar (ex.: serviço reiniciado).
    return "--title `"$titulo`" -d `"$raiz`" powershell -NoExit -Command `"$comando`""
}

$logs = 'docker compose logs -f --tail 5 --no-log-prefix'
$kafka = 'docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 ' +
         '--topic backlog.eventos --property print.key=true --property print.headers=true --property print.partition=true'

$p1 = Painel '1 CATALOGO' "$logs catalogo-service"
$p2 = Painel '2 BACKLOG' "$logs backlog-service"
$p3 = Painel '3 ESTATISTICAS' "$logs estatisticas-service"
$p4 = Painel '4 REDIS' 'docker compose exec redis redis-cli MONITOR'
$p5 = Painel '5 KAFKA' $kafka
$p6 = "--title `"6 COMANDOS`" -d `"$raiz`" `"$bash`" --login -i"

# Ordem de divisão: 1 | 2 (66%) | 3 (50% do 2); depois divide 3→6, 2→5, 1→4 na horizontal.
$argumentos = @(
    "-w lettergames new-tab $p1",
    "split-pane -V -s 0.66 $p2",
    "split-pane -V -s 0.5 $p3",
    "split-pane -H $p6",
    "move-focus left",
    "split-pane -H $p5",
    "move-focus left",
    "split-pane -H $p4",
    "move-focus right",
    "move-focus right"
) -join ' ; '

Start-Process wt -ArgumentList $argumentos
Write-Host 'Layout aberto. Aumente a fonte com Ctrl + = (projetor) e rode os scripts no painel 6: ./scripts/demo/01-cadastrar-jogos.sh'
