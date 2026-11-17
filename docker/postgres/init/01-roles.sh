#!/bin/sh
# Só infraestrutura: cria o role de runtime da saigt-api (saigt_app). As tabelas, os dados de referência e os
# grants sobre as tabelas vêm das migrações Liquibase da saigt-api, que correm com o dono da base de dados.
# Corre uma vez, quando o volume do PostgreSQL é criado. Sem `set -u`: o entrypoint do PostgreSQL pode fazer source
# deste ficheiro em vez de o executar.
set -e

: "${SAIGT_APP_PASSWORD:?SAIGT_APP_PASSWORD em falta}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_password="$SAIGT_APP_PASSWORD" -v db="$POSTGRES_DB" <<'EOSQL'
SELECT format('CREATE ROLE saigt_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'saigt_app')
\gexec
SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO saigt_app', :'db')
\gexec
EOSQL
