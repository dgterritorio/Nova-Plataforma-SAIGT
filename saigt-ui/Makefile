.PHONY: dev build lint test e2e e2e-demo catalogo catalogo-build

dev:
	npm run start

build:
	npm run build

lint:
	npm run lint

test:
	npm run test -- --watch=false
	npm run e2e:harness

e2e:
	npm run e2e

e2e-demo:
	npm run e2e:demo

catalogo:
	npm run catalogo

catalogo-build:
	npm run catalogo:build
