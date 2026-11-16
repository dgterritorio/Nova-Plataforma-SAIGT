package com.conkord.saigt.integracao.storage;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base dos testes que tocam no storage: RustFS (API S3) real, levantado uma vez por execução da suite
 * (singleton estático, parado pelo Ryuk no fim da JVM). Só as subclasses exigem Docker; os restantes testes
 * correm em H2.
 */
@Tag("docker")
public abstract class StorageTestcontainersBase {

    protected static final String BUCKET = "saigt-documentos-teste";

    protected static final String ACCESS_KEY = "saigt-teste";
    protected static final String SECRET_KEY = "saigt-teste-segredo";
    protected static final int PORTA_S3 = 9000;

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> STORAGE = new GenericContainer<>("rustfs/rustfs:1.0.0")
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(PORTA_S3)
            .waitingFor(Wait.forHttp("/health").forPort(PORTA_S3).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        STORAGE.start();
    }

    @DynamicPropertySource
    static void propriedadesStorage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint",
                () -> "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3));
        registry.add("app.storage.access-key", () -> ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> SECRET_KEY);
        registry.add("app.storage.bucket", () -> "${saigt.teste.bucket:" + BUCKET + "}");
        registry.add("app.storage.inicializar-bucket", () -> "true");
    }
}
