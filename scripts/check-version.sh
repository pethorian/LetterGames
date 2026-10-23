#!/usr/bin/env bash
# Confere se VERSION, o pom.xml raiz e o <parent> de cada módulo estão alinhados.
# Com --changelog, exige também a seção "## [VERSION]" no CHANGELOG.md.
set -euo pipefail
cd "$(dirname "$0")/.."

v=$(tr -d '[:space:]' < VERSION)
erro=0
if ! [[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+)?$ ]]; then
  echo "::error::VERSION '$v' fora do padrão MAJOR.MINOR.PATCH[-beta.N]"; erro=1
fi

raiz=$(sed -n '/<artifactId>lettergames<\/artifactId>/{n;s#.*<version>\(.*\)</version>.*#\1#p;q}' pom.xml | tr -d '\r')
[ "$raiz" = "$v" ] || { echo "::error::pom.xml tem '$raiz', VERSION tem '$v'"; erro=1; }

for pom in */pom.xml; do
  mod=$(sed -n '/<artifactId>lettergames<\/artifactId>/{n;s#.*<version>\(.*\)</version>.*#\1#p;q}' "$pom" | tr -d '\r')
  [ "$mod" = "$v" ] || { echo "::error::$pom aponta para o parent '$mod', VERSION tem '$v'"; erro=1; }
done

if [ "${1:-}" = "--changelog" ]; then
  grep -qF "## [$v]" CHANGELOG.md || { echo "::error::CHANGELOG.md sem a seção '## [$v]'"; erro=1; }
fi

[ "$erro" -eq 0 ] && echo "Versão $v consistente."
exit "$erro"
