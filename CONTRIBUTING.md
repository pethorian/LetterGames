# Fluxo de trabalho

## Branches

| Branch | Papel | Quem escreve nela |
|---|---|---|
| `main` | Código estável / releases | Só por PR de `develop`, **aprovado manualmente pelo dono** |
| `develop` | Integração do desenvolvimento | Só por PR de uma branch de trabalho, **aprovado e mergeado automaticamente pelo CI** |
| `<tipo>/<assunto>` | Uma demanda | Quem desenvolve |

**Ninguém commita direto em `main` nem em `develop`.**

Prefixos de branch (um por demanda): `feat/`, `fix/`, `refactor/`, `test/`, `docs/`, `chore/`, `ci/`. **Toda branch nova nasce da `develop`, com uma única exceção: `hotfix/` nasce da `main`** (veja "Correção urgente na main").
Exemplo: `feat/cache-catalogo`.

## Passo a passo de uma demanda

1. Atualize e crie a branch a partir da `develop`:

   ```bash
   git checkout develop && git pull
   git checkout -b feat/nome-da-demanda
   ```

2. Desenvolva em commits pequenos. **Cada feat/fix/refactor gera um commit.** Quando a mudança pede teste, **os testes também são commitados** (de preferência em commit próprio, `test: ...`).
3. Rode os testes localmente antes de subir (`./mvnw verify` na raiz, sem Docker).
4. Envie a branch e **abra um PR para `develop`** ao final:

   ```bash
   git push -u origin feat/nome-da-demanda
   gh pr create --base develop
   ```

5. O CI roda sozinho. Se tudo passar (testes, build, versões consistentes), o PR é aprovado e mergeado automaticamente. Se falhar, corrija na mesma branch com novos commits.
6. Para publicar: abra um PR `develop` → `main`. O CI roda, mas **a aprovação e o merge são manuais** (dono do repositório).

## Mensagens de commit

[Conventional Commits](https://www.conventionalcommits.org/pt-br/): `tipo(escopo opcional): descrição no imperativo`.

```
feat(catalogo): adiciona cache-aside no GET /jogos/{id}
fix(estatisticas): ignora evento duplicado
test(backlog): cobre circuit breaker aberto
refactor(backlog): extrai cliente do catálogo
docs: adiciona ADR de cache
ci: adiciona job de build do docker
chore: atualiza dependências
```

## CI/CD (GitHub Actions)

| Workflow | Quando | O que faz |
|---|---|---|
| `ci.yml` | PR para `develop` ou `main` | `./mvnw verify` (perfil B, sem Docker), build das imagens Docker, consistência de versão |
| `ci.yml` → job `auto-aprovar` | PR para `develop` com tudo verde | Aprova e faz o merge automaticamente |
| `ci.yml` → job `fluxo` | PR para `develop`/`main` | Confere a origem da branch (`scripts/check-branch-origin.sh`): fora `hotfix/*`, a branch deve ter nascido da `develop`; `hotfix/*` deve ter nascido da `main`, em histórico linear (use rebase, não merge, para atualizar). Para `main`: origem só `develop` ou `hotfix/*` e exige entrada no CHANGELOG |
| `release.yml` | Push na `main` | Cria a tag `vX.Y.Z-beta.N` e o GitHub Release a partir do `VERSION` e do CHANGELOG |
| `guarda-commits.yml` | Push em `main`/`develop` | Avisa (falha o job) se o commit não veio de um PR |

> **Limitação:** o plano gratuito do GitHub não oferece *branch protection* em repositório privado, então o "não commitar na `main`/`develop`" é uma regra de processo, vigiada pelo `guarda-commits.yml`, e não bloqueada pelo GitHub. Com GitHub Pro (ou repositório público) vale ativar proteção de branch exigindo o status `ci-ok`.

## Versionamento

- SemVer com pré-release: `MAJOR.MINOR.PATCH-beta.N`. Enquanto estivermos em beta, a versão é `0.x.y-beta.N`.
- **Fonte única:** o arquivo `VERSION`. Ele deve bater com o `pom.xml` raiz e o `<parent>` de cada módulo (o CI confere).
- Para trocar a versão, use o script (atualiza tudo de uma vez):

  ```bash
  scripts/bump-version.sh 0.1.0-beta.2
  ```

- Registre as mudanças em `CHANGELOG.md`, na seção `[Não lançado]`; ao lançar, renomeie para a versão e data.
- Quando usar cada incremento: `beta.N` para qualquer entrega em beta; `0.MINOR` para funcionalidade nova relevante; `0.x.PATCH` para correção. Ao sair do beta, `1.0.0`.
- O lançamento acontece quando o PR `develop` → `main` é aprovado: o `release.yml` cria a tag.

## Correção urgente na `main` (hotfix)

Se há um bug na `main` e a `develop` já tem coisas que ainda não podem ir para produção, **não** use a `develop` como base. Esta é a única exceção à regra "branch nasce da `develop`":

1. Crie a branch da `main`:

   ```bash
   git checkout main && git pull
   git checkout -b hotfix/nome-do-bug
   ```

2. Faça só a correção: um commit para o fix e outro para o teste. Suba a versão (`scripts/bump-version.sh`) e registre no `CHANGELOG.md`.
3. Abra PR `hotfix/...` → `main`. O CI roda e **a aprovação é manual**.
4. Depois de entrar na `main`, abra um segundo PR da **mesma branch** para a `develop` (`gh pr create --base develop`), para o bug não voltar no próximo release. Ele passa pelo CI e é mergeado automaticamente. Se a `develop` mudou na mesma área, resolva os conflitos na branch.
