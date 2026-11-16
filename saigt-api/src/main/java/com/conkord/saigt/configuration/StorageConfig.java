package com.conkord.saigt.configuration;

import io.minio.MinioClient;
import io.minio.http.HttpUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Construir o cliente não abre ligação: o storage só é contactado no primeiro pedido. Os tempos limite são
 * explícitos: sem eles uma leitura parada prendia a descarga, e o seu lugar no limite de descargas, para sempre.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .httpClient(HttpUtils.newDefaultHttpClient(properties.timeoutLigacao().toMillis(),
                        properties.timeoutSemDados().toMillis(), properties.timeoutSemDados().toMillis()))
                .build();
    }
}
