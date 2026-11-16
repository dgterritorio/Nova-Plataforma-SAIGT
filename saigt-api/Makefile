.PHONY: test test-rapido analyse migrate build run clean help

help:
	@echo "test        - run all tests (needs Docker)"
	@echo "test-rapido - run tests without Docker containers"
	@echo "analyse     - static analysis (sonar)"
	@echo "migrate     - liquibase update"
	@echo "build       - build docker image"
	@echo "run         - run app locally"

test:
	./mvnw test

test-rapido:
	./mvnw test -Prapido

analyse:
	./mvnw sonar:sonar

migrate:
	./mvnw liquibase:update

build:
	docker build -t saigt-api:local .

run:
	./mvnw spring-boot:run

clean:
	./mvnw clean
