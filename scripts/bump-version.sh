#!/usr/bin/env bash
# Uso: scripts/bump-version.sh 0.1.0-beta.2
# Atualiza VERSION, o pom.xml raiz e o <parent> de cada módulo.
set -euo pipefail
cd "$(dirname "$0")/.."

v="${1:?uso: $0 MAJOR.MINOR.PATCH[-beta.N]}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+)?$ ]] || { echo "Versão inválida: $v" >&2; exit 1; }

printf '%s\n' "$v" > VERSION
for pom in pom.xml */pom.xml; do
  sed -i '/<artifactId>lettergames<\/artifactId>/{n;s#<version>[^<]*</version>#<version>'"$v"'</version>#}' "$pom"
done
echo "Versão atualizada para $v. Registre no CHANGELOG.md."
