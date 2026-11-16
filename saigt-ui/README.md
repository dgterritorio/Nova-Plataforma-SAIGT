# saigt-ui

Frontend da Nova Plataforma SAIGT (PC5979 — DGT). Angular 22, standalone, signals. Os formulários são fixos, definidos na aplicação; o que ainda difere do LLD v13 está em `saigt-implementation/docs/analise-lacunas.md`.

## Desenvolvimento local
```bash
npm install
npm run start
```
O `proxy.conf.json` encaminha `/api` para o `saigt-api` local (porta 8081 quando corrido via `saigt-implementation`, 8080 quando corrido isoladamente — ajustar conforme o teu setup).

## Makefile
- `make dev` — `ng serve`
- `make build` — build de produção
- `make lint` — lint

## Integração contínua
O `.gitlab-ci.yml` corre em cada merge request e em cada push para `develop`, `staging` e `master`. Um push num ramo sem MR não corre pipeline, e um ramo com MR aberto corre só o pipeline do MR. Com "Pipelines must succeed" ativo no projeto, um MR só pode ser integrado com o pipeline verde. Todos os jobs são interrompíveis: com "Auto-cancel redundant pipelines" (ativo por omissão), um push novo no mesmo ramo cancela o pipeline anterior.

| Job | Stage | O que faz |
|---|---|---|
| `lint` | `verificacao` | `npm run lint`: ESLint e Stylelint (regras de tokens do `.stylelintrc.json`) |
| `testes` | `verificacao` | Testes unitários (`npm run test -- --watch=false`) e projeto `harness` do Playwright (`npm run e2e:harness`), em paralelo com o `lint`. Com `CI=true`, um `test.only` esquecido faz falhar. Se falhar, guarda durante 7 dias o relatório HTML do Playwright e os relatórios JUnit (`relatorios/junit/unitarios.xml`, `e2e/relatorios/junit/harness.xml`), que o MR mostra no separador dos testes |
| `build` | `build` | `npm run build` (produção), só depois de `lint` e `testes` passarem. Guarda `dist/saigt-ui/browser/` durante 7 dias |
| `imagem` | `imagem` | Só nos merge requests: `docker build` do `Dockerfile`, depois do `build`. Não faz login nem push |
| `publicar` | `imagem` | Só nos pipelines de ramo de `develop`, `staging` e `master`, depois do `build`: constrói e publica a imagem de produção no registry (ver «Publicação da imagem») |
| `deploy-desenvolvimento`, `deploy-qualidade`, `deploy-producao` | `deploy` | Manuais, só nos pipelines de ramo de ramos protegidos, depois do `publicar`: instalam no ambiente a imagem publicada por esse pipeline e confirmam que a página inicial responde com 200 (ver «Deploy por ambiente») |

Os jobs Node usam `node:22-alpine`, a imagem do `Dockerfile`, e guardam a cache do npm em `.npm/`, renovada quando muda o `package-lock.json`. Nenhum job usa as palavras-passe das personas E2E; só o `publicar` e os jobs de deploy usam credenciais (as do registry e, no deploy, a chave SSH do ambiente).

**Publicação da imagem.** Só nos pipelines de ramo de `develop`, `staging` e `master`, e só depois de `lint`, `testes` e `build` verdes, o job `publicar` faz `docker login` com a palavra-passe lida do stdin, constrói a imagem de produção (`--build-arg BUILD_CONFIG=production`) e publica-a como `$CI_REGISTRY_IMAGE:<sha curto do commit>` (por exemplo `registry.exemplo/delivery-iberia/saigt/saigt-ui:685920d`). É a única etiqueta: a do commit, que o deploy usa e que nunca se sobrescreve (se já existe, num pipeline repetido ou no mesmo commit promovido a `staging` ou `master`, o job termina sem reconstruir). Nos merge requests o `imagem` só constrói, sem login nem push. Variáveis de CI/CD de que depende: `CI_REGISTRY` (endereço do registry), `CI_REGISTRY_IMAGE` (nome da imagem, sem etiqueta), `CI_REGISTRY_USER` e `CI_REGISTRY_PASSWORD`. Com o Container Registry do GitLab são as predefinidas (a palavra-passe é o token do job, já mascarado). Com outro registry, definem-se no projeto (Settings → CI/CD → Variables) com os mesmos nomes, protegidas, para só os ramos protegidos as receberem, e a palavra-passe mascarada; o `.gitlab-ci.yml` não muda. `develop`, `staging` e `master` têm de ser ramos protegidos.

O `imagem` e o `publicar` usam Docker-in-Docker com TLS (`docker:29.9.2` e o serviço `docker:29.9.2-dind`). Precisa de um runner com executor docker em modo privilegiado, com o volume `/certs/client` partilhado entre o job e o serviço e sem o `/var/run/docker.sock` do servidor montado nos serviços.

**Deploy por ambiente.** Um job manual por ambiente, criado só nos pipelines de ramo e só quando o ramo é protegido (`CI_COMMIT_REF_PROTECTED`), nunca nos merge requests:

| Ramo | Job | Ambiente no GitLab (`environment:name`) | Tier |
|---|---|---|---|
| `develop` | `deploy-desenvolvimento` | `desenvolvimento` | `development` |
| `staging` | `deploy-qualidade` | `qualidade` | `staging` |
| `master` | `deploy-producao` | `producao` | `production` |

O mapa de ramos e ambientes e o gatilho manual são a proposta da story, ainda por confirmar pela DGT com a decisão de infraestrutura dos ambientes (OI-392); confirmá-los ou mudá-los só muda este ficheiro. Cada job tem um `resource_group` próprio e não é cancelado por um push mais recente.

*O que o job faz.* Cada ambiente tem, num servidor, uma pasta com o seu `docker-compose.yml` e o seu `.env`, geridos pela infraestrutura fora dos repositórios. O job liga-se por SSH a essa pasta, faz `docker login` no registry com a palavra-passe pelo stdin, `docker compose pull saigt-ui` e `docker logout`, e depois `docker compose up -d --no-deps --force-recreate saigt-ui` com `SAIGT_UI_IMAGEM=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA` só no ambiente desse comando: a imagem que o `publicar` do mesmo pipeline publicou. Não escreve no `.env` nem em nenhum ficheiro do ambiente e não toca nos outros serviços. Espera até `DEPLOY_TEMPO_LIMITE` segundos (120 por omissão) que todos os contentores do serviço corram a imagem nova e que a página inicial responda com 200 dentro de cada um (`http://127.0.0.1:8080/`, a porta do `nginx.conf`); falha logo se um contentor parar ou reiniciar, e falha ao fim do tempo limite. Em qualquer falha mostra as últimas `DEPLOY_LINHAS_LOG` linhas (100 por omissão) do log do `saigt-ui`.

*Repetir um deploy.* Correr outra vez o job (Retry), no mesmo pipeline.

*Voltar à versão anterior.* Novo deploy da etiqueta anterior: em Operate → Environments → ambiente, Rollback no deploy anterior (repete o job de deploy desse pipeline, com a imagem desse commit), ou Retry do job de deploy no pipeline anterior.

*Variáveis de CI/CD* (Settings → CI/CD → Variables), com o âmbito de cada ambiente (`desenvolvimento`, `qualidade`, `producao`), protegidas e, as que o permitem, mascaradas; nenhuma tem valor no `.gitlab-ci.yml`:

| Variável | Tipo | O que é |
|---|---|---|
| `DEPLOY_SSH_HOST` | Variable | Utilizador e servidor do ambiente para o SSH (`utilizador@servidor`) |
| `DEPLOY_SSH_PORT` | Variable | Porta SSH, se não for a 22 |
| `DEPLOY_SSH_KEY` | File | Chave privada SSH do utilizador de deploy, só para este fim |
| `DEPLOY_SSH_KNOWN_HOSTS` | File | Linha(s) `known_hosts` com a chave pública do servidor, obtida da infraestrutura por um canal fiável; o host é sempre verificado (`StrictHostKeyChecking=yes`) |
| `DEPLOY_DIR` | Variable | Pasta do ambiente no servidor |
| `DEPLOY_URL` | Variable | Endereço público do ambiente (`environment:url`) |
| `DEPLOY_TEMPO_LIMITE`, `DEPLOY_LINHAS_LOG` | Variable | Opcionais: sobrepõem-se aos valores por omissão do job |
| `CI_REGISTRY`, `CI_REGISTRY_IMAGE`, `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD` | — | As mesmas do `publicar` |

*Pré-requisitos do ambiente* (infraestrutura, fora do repositório): no `docker-compose.yml` do ambiente, o serviço `saigt-ui` com `image: ${SAIGT_UI_IMAGEM}` (com um valor por omissão) e sem `container_name`; o servidor com Docker e Docker Compose v2, SSH só por chave e um utilizador de deploy com acesso ao Docker e à pasta do ambiente, que confia no certificado do registry. O `saigt-api` tem de estar a correr no ambiente (o `nginx.conf` encaminha para ele). Os jobs de deploy não precisam de Docker-in-Docker: só do cliente SSH da imagem `docker`.

Para reproduzir os jobs localmente: `make lint`, `make test` e `make build`.

Fica fora do pipeline: os projetos E2E que precisam da stack completa (`setup`, `demo`, `visual`, `a11y`, `figma`, `fluxos`), o build do Storybook (`npm run catalogo:build`) e o SonarQube.

## Convenções
Standalone only, `inject()` only, signals para estado, `@if/@for/@switch`, rotas sempre lazy (`loadComponent`), sem `NgModule`, sem localStorage para tokens (tokens guardados em memória — BL-IAM-004 em `saigt-implementation/docs/lld/05-lld-identidade-acesso.md`).

## Contrato de API
Antes de implementar qualquer chamada API, ler `saigt-implementation/platform-contracts/openapi/saigt-api.yaml` — nunca inferir o contrato a partir do código do backend.

## Desenho
LLD v13 em `saigt-implementation/docs/lld/`; mockups de ecrã em `saigt-implementation/docs/design-to-build/mockups/`.
