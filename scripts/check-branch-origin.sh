#!/usr/bin/env bash
# Confere de onde a branch do PR saiu.
# Uso: scripts/check-branch-origin.sh <base-do-pr> <branch-do-pr> [ref-da-branch=HEAD]
#  - PR para develop: a branch não pode conter commits que só existem na main
#    (ou seja, tem que ter nascido da develop).
#  - PR para main vindo de hotfix/*: a branch tem que ter nascido da main, sem
#    arrastar o que só está na develop. Como a develop só recebe código por commits
#    de merge de PR, qualquer merge entre main e a branch indica contaminação.
# Requer histórico completo (fetch-depth: 0) e origin/main e origin/develop.
set -euo pipefail

base="${1:?base}"; head_ref="${2:?branch}"; ref="${3:-HEAD}"

case "$base" in
  develop)
    case "$head_ref" in hotfix/*) echo "hotfix/* -> develop: back-merge permitido."; exit 0 ;; esac
    so_main=$(comm -12 \
      <(git rev-list "$ref" "^origin/develop" | sort) \
      <(git rev-list origin/main "^origin/develop" | sort))
    if [ -n "$so_main" ]; then
      echo "::error::'$head_ref' contém commits que só existem na main; toda branch (exceto hotfix/*) deve nascer da develop."
      echo "$so_main" | head -5
      exit 1
    fi
    echo "OK: '$head_ref' nasceu da develop."
    ;;
  main)
    case "$head_ref" in
      hotfix/*)
        n=$(git rev-list --merges --count "origin/main..$ref")
        if [ "$n" -gt 0 ]; then
          echo "::error::'$head_ref' contém $n commit(s) de merge em relação à main, indício de que arrasta código da develop. Crie o hotfix a partir da main e use rebase (não merge) para atualizar."
          git rev-list --merges "origin/main..$ref" | head -5
          exit 1
        fi
        echo "OK: '$head_ref' nasceu da main (histórico linear sobre a main)."
        ;;
      *) echo "Sem checagem de origem para '$head_ref' -> main." ;;
    esac
    ;;
  *) echo "Base '$base' sem checagem de origem." ;;
esac
