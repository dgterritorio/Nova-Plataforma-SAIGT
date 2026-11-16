-- Sequências Postgres-only criadas via Liquibase <createSequence> (não pelo Hibernate,
-- já que não há @GeneratedValue/@SequenceGenerator a associá-las a nenhuma entidade) e por
-- isso invisíveis ao ddl-auto=create-drop usado nos testes (spring.liquibase.enabled=false).
-- Ver 2026-08-10-7-create-nucleo-operacional.xml.
CREATE SEQUENCE IF NOT EXISTS seq_instrumento_numero_sequencial START WITH 1 INCREMENT BY 1;

-- O H2 não tem pg_advisory_xact_lock, que o gerador dos contadores do IDUS chama: sem concorrência nos testes, não faz nada.
CREATE ALIAS IF NOT EXISTS pg_advisory_xact_lock FOR "com.conkord.saigt.persistence.operacional.FuncoesH2.pgAdvisoryXactLock";

-- Tabela do ShedLock (2026-10-28-1-create-shedlock.xml): sem entidade JPA, o ddl-auto não a cria.
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
