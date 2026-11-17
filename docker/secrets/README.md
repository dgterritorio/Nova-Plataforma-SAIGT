# Segredos (nunca no repositório)

Esta pasta é montada só de leitura em `/secrets` no contentor `saigt-api` (ver `docker-compose.yml`). Os ficheiros
`.pem` estão no `.gitignore`.

## Chave privada JWT (RS256)

A `saigt-api` assina os tokens com esta chave e não arranca sem ela. Cada ambiente tem a sua chave, gerada no
próprio servidor:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out docker/secrets/jwt-private-key.pem
chmod 644 docker/secrets/jwt-private-key.pem
```

O `chmod` deixa o utilizador sem privilégios do contentor (`conkord`) ler a chave. Para restringir mais, dar a
chave ao UID desse utilizador (`docker compose run --rm --entrypoint id saigt-api`) com permissões `600`.

Trocar a chave invalida as sessões abertas: os utilizadores voltam a autenticar-se.
