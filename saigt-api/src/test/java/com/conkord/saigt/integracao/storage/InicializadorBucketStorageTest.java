package com.conkord.saigt.integracao.storage;

import com.conkord.saigt.configuration.StorageProperties;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class InicializadorBucketStorageTest extends StorageTestcontainersBase {

    private static final String ENDPOINT = "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3);
    private static final MinioClient MINIO = MinioClient.builder()
            .endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();

    @Test
    void deveCriarOBucketComVersionamentoEObjectLockSemRetencaoPorOmissao() throws Exception {
        String bucket = novoBucket();

        inicializador(bucket, true).run(null);

        assertThat(versionamento(bucket)).isEqualTo(VersioningConfiguration.Status.ENABLED);
        ObjectLockConfiguration lock = MINIO.getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(bucket).build());
        assertThat(lock).isNotNull();
        assertThat(lock.mode()).isNull();
        assertThat(lock.duration()).isNull();
    }

    @Test
    void deveFalharOArranqueComUmBucketExistenteSemObjectLockQuandoEObrigatorio() throws Exception {
        String bucket = novoBucket();
        MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());

        assertThatThrownBy(() -> inicializador(bucket, true).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tem de ser recriado com Object Lock")
                .hasMessageNotContaining(SECRET_KEY);
    }

    @Test
    void deveArrancarComAvisoELigarOVersionamentoNumBucketSemObjectLockQuandoNaoEObrigatorio(CapturedOutput saida)
            throws Exception {
        String bucket = novoBucket();
        MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());

        inicializador(bucket, false).run(null);

        assertThat(saida.getOut()).contains("Bucket sem Object Lock").contains("versionamento ligado");
        assertThat(versionamento(bucket)).isEqualTo(VersioningConfiguration.Status.ENABLED);
    }

    @Test
    void deveAceitarUmBucketExistenteComObjectLockSemAlterarNada(CapturedOutput saida) throws Exception {
        String bucket = novoBucket();
        MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());

        inicializador(bucket, true).run(null);

        assertThat(saida.getOut()).doesNotContain("Bucket sem Object Lock").doesNotContain("versionamento ligado");
        assertThat(versionamento(bucket)).isEqualTo(VersioningConfiguration.Status.ENABLED);
    }

    @Test
    void deveCriarOBucketSemPolitica() throws Exception {
        String bucket = novoBucket();

        inicializador(bucket, true).run(null);

        assertThat(politica(bucket)).isEmpty();
    }

    @Test
    void deveRemoverAPoliticaAnonimaDeUmBucketExistenteEAvisar(CapturedOutput saida) throws Exception {
        String bucket = novoBucket();
        MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
        MINIO.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config("""
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/publico/*"]}]}
                """.formatted(bucket)).build());
        assertThat(politica(bucket)).isNotEmpty();

        inicializador(bucket, true).run(null);

        assertThat(politica(bucket)).isEmpty();
        assertThat(saida.getOut()).contains("política removida").contains(bucket);
    }

    private static String politica(String bucket) throws Exception {
        return MINIO.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
    }

    private static InicializadorBucket inicializador(String bucket, boolean objectLockObrigatorio) {
        return new InicializadorBucket(MINIO, new StorageProperties(ENDPOINT, ACCESS_KEY, SECRET_KEY, bucket, true,
                objectLockObrigatorio, Duration.ofSeconds(10), Duration.ofSeconds(60)));
    }

    private static VersioningConfiguration.Status versionamento(String bucket) throws Exception {
        return MINIO.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(bucket).build()).status();
    }

    private static String novoBucket() {
        return "saigt-arranque-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
