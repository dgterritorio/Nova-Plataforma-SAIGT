package com.conkord.saigt.configuration;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(StorageConfig.class);

    @Test
    void deveCriarClienteComCredenciaisCompletas() {
        contextRunner.withPropertyValues("app.storage.endpoint=http://localhost:9000", "app.storage.access-key=saigt",
                        "app.storage.secret-key=segredo", "app.storage.bucket=saigt-documentos")
                .run(context -> assertThat(context).hasSingleBean(MinioClient.class));
    }

    @Test
    void deveFalharArranqueQuandoFaltaCredencial() {
        contextRunner.withPropertyValues("app.storage.endpoint=http://localhost:9000", "app.storage.access-key=",
                        "app.storage.secret-key=segredo", "app.storage.bucket=saigt-documentos")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("accessKey");
                });
    }
}
