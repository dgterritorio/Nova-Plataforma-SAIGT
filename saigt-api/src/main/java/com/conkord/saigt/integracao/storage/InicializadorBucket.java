package com.conkord.saigt.integracao.storage;

import com.conkord.saigt.configuration.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketPolicyArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketVersioningArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.VersioningConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fora do compose de desenvolvimento não há `rustfs-init`. Cria o bucket com versionamento e Object Lock, sem regra
 * de retenção por omissão: a retenção é posta pela aplicação, versão a versão (BL-DOC-009). O Object Lock só se liga
 * na criação do bucket, por isso um bucket existente sem ele tem de ser recriado. Desligado nos testes em H2.
 */
@Component
@ConditionalOnProperty(name = "app.storage.inicializar-bucket", havingValue = "true", matchIfMissing = true)
public class InicializadorBucket implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorBucket.class);
    private static final Set<String> SEM_OBJECT_LOCK = Set.of("ObjectLockConfigurationNotFoundError",
            "NoSuchObjectLockConfiguration");

    private final MinioClient minioClient;
    private final String bucket;
    private final boolean objectLockObrigatorio;

    public InicializadorBucket(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.bucket();
        this.objectLockObrigatorio = properties.objectLockObrigatorio();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
            log.info("Bucket criado com versionamento e Object Lock — bucket={}", bucket);
            return;
        }
        verificarObjectLock();
        ligarVersionamento();
        removerPolitica();
    }

    /** Só a saigt-api acede ao bucket: uma política herdada de um volume antigo (leitura anónima) não pode ficar. */
    private void removerPolitica() throws Exception {
        String politica = minioClient.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
        if (politica == null || politica.isBlank()) {
            return;
        }
        minioClient.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
        log.warn("Bucket com política de acesso: política removida, o acesso é só pela aplicação — bucket={}", bucket);
    }

    private void verificarObjectLock() throws Exception {
        ObjectLockConfiguration configuracao;
        try {
            configuracao = minioClient.getObjectLockConfiguration(
                    GetObjectLockConfigurationArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException e) {
            if (!SEM_OBJECT_LOCK.contains(e.errorResponse().code())) {
                throw e;
            }
            configuracao = null;
        }
        if (configuracao == null) {
            if (objectLockObrigatorio) {
                throw new IllegalStateException("O bucket " + bucket + " não tem Object Lock e tem de ser recriado "
                        + "com Object Lock (app.storage.object-lock-obrigatorio=true).");
            }
            log.warn("Bucket sem Object Lock: os ficheiros validados e publicados não ficam retidos — bucket={}", bucket);
        } else if (configuracao.mode() != null || configuracao.duration() != null) {
            log.warn("Bucket com regra de retenção por omissão; a retenção devia ser só a da aplicação — bucket={} "
                    + "modo={}", bucket, configuracao.mode());
        }
    }

    private void ligarVersionamento() throws Exception {
        VersioningConfiguration versionamento = minioClient.getBucketVersioning(
                GetBucketVersioningArgs.builder().bucket(bucket).build());
        if (versionamento.status() == VersioningConfiguration.Status.ENABLED) {
            return;
        }
        minioClient.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(bucket)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
        log.warn("Bucket existente sem versionamento: versionamento ligado — bucket={}", bucket);
    }
}
