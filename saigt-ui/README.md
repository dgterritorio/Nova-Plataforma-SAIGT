# saigt-ui

Frontend da Nova Plataforma SAIGT (PC5979 — DGT). Angular 22, standalone, signals. Os formulários são fixos, definidos na aplicação; o que ainda difere do LLD v13 está em `saigt-implementation/docs/analise-lacunas.md`.

## Desenvolvimento local
```bash
npm install
npm run start
```
O `proxy.conf.json` encaminha `/api` para o `saigt-api` local (porta 8081 quando corrido com o `docker-compose.yml` da raiz do repositório, 8080 quando corrido isoladamente — ajustar conforme o teu setup).

## Makefile
- `make dev` — `ng serve`
- `make build` — build de produção
- `make lint` — lint
- `make test` — testes unitários

## Convenções
Standalone only, `inject()` only, signals para estado, `@if/@for/@switch`, rotas sempre lazy (`loadComponent`), sem `NgModule`, sem localStorage para tokens (tokens guardados em memória — BL-IAM-004 em `saigt-implementation/docs/lld/05-lld-identidade-acesso.md`).

## Contrato de API
Antes de implementar qualquer chamada API, ler `saigt-implementation/platform-contracts/openapi/saigt-api.yaml` — nunca inferir o contrato a partir do código do backend.

## Desenho
LLD v13 em `saigt-implementation/docs/lld/`; mockups de ecrã em `saigt-implementation/docs/design-to-build/mockups/`.
