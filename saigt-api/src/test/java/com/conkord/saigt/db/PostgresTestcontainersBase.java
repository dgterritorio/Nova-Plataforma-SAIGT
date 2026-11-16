package com.conkord.saigt.db;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base dos testes que precisam de PostgreSQL (SQL nativo, jsonb): um servidor por execução da suite (singleton
 * estático, parado pelo Ryuk no fim da JVM), com o schema criado pelo Hibernate. Só as subclasses exigem Docker;
 * os restantes testes correm em H2.
 */
@Tag("docker")
public abstract class PostgresTestcontainersBase {

    private static final int PORTA_POSTGRES = 5432;
    private static final String BASE = "saigt";
    private static final String PASSWORD = "teste";

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:15-alpine")
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withEnv("POSTGRES_DB", BASE)
            .withExposedPorts(PORTA_POSTGRES)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\n", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propriedadesPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PORTA_POSTGRES) + "/" + BASE);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // O servidor é partilhado por todos os contextos destas subclasses. Com o create-drop do perfil de teste, um
        // contexto despejado da cache do Spring apagava o schema por baixo dos contextos ainda em cache (relation
        // "utilizador" does not exist). O create recria-o ao arrancar e não o apaga ao fechar.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }
}
