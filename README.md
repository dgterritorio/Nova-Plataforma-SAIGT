# Nova-Plataforma-SAIGT

Código da Nova Plataforma SAIGT (DGT).

| Pasta | O que é |
|---|---|
| `saigt-api/` | Backend: Java 21, Spring Boot, PostgreSQL com migrações Liquibase, armazenamento S3. Ver `saigt-api/README.md` |
| `saigt-ui/` | Frontend: Angular, servido por nginx, que encaminha `/api` e `/oauth2/token` para a `saigt-api`. Ver `saigt-ui/README.md` |
| `docker-compose.yml` | Orquestração de referência: PostgreSQL, RustFS (S3), `saigt-api` e `saigt-ui` |
| `docker/` | Script de arranque do PostgreSQL e pasta dos segredos (chave JWT) |

## Requisitos

Docker com Docker Compose v2. Não é preciso Java nem Node no servidor: a compilação corre dentro dos `Dockerfile`.

## Gerar as imagens

```bash
docker compose build
```

Gera `saigt-api:<SAIGT_VERSAO>` e `saigt-ui:<SAIGT_VERSAO>` (`local` por omissão; definir no `.env` ou na linha de
comandos, por exemplo `SAIGT_VERSAO=1.0.0 docker compose build`). Cada imagem também se gera isoladamente:

```bash
docker build -t saigt-api:1.0.0 saigt-api
docker build -t saigt-ui:1.0.0 saigt-ui
```

## Arrancar a plataforma

1. `cp .env.example .env` e preencher os valores de cada ambiente (palavras-passe, AD, integrações).
2. Gerar a chave JWT, como em `docker/secrets/README.md`.
3. `docker compose up -d`

No primeiro arranque a `saigt-api` aplica as migrações da base de dados e cria o bucket de documentos; a `saigt-ui`
só arranca depois de a `saigt-api` responder como saudável. A plataforma fica em `http://<servidor>:<SAIGT_UI_PORTA>`.
O primeiro utilizador MASTER é o username AD em `ADMIN_ARRANQUE_EMAIL`.

Estado e logs:

```bash
docker compose ps
docker compose logs -f saigt-api
```

## Notas para produção

- Pôr à frente da `saigt-ui` um reverse proxy com TLS; a `saigt-ui` serve HTTP na porta 8080.
- A `saigt-ui` encaminha para o host `saigt-api:8080`: noutra orquestração, o serviço da API tem de ter esse nome
  na mesma rede (ou ajustar `saigt-ui/nginx.conf`).
- Os volumes `postgres_data` e `rustfs_data` guardam os dados e os documentos: entram no plano de backups.
- O bucket é criado com versionamento e Object Lock; a retenção dos documentos é posta pela aplicação.
