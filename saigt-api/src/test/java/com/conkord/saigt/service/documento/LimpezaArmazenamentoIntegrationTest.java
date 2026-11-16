package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.integracao.storage.S3ArmazenamentoDocumentos;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.documento.LimpezaArmazenamentoService.ResultadoLimpeza;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Limpeza contra o RustFS real, com versionamento: o objeto apagado fica sem nenhuma versão. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LimpezaArmazenamentoIntegrationTest extends StorageTestcontainersBase {

    private static final String BUCKET_LIMPEZA = "saigt-limpeza-teste";
    private static final String ENDPOINT = "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3);
    private static final MinioClient MINIO = MinioClient.builder()
            .endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();

    @Autowired
    private ArmazenamentoIntencaoRepository intencoes;

    @Autowired
    private ProcessoDocumentoRepository documentos;

    @Autowired
    private NotificacaoDocumentoRepository anexos;

    private S3ArmazenamentoDocumentos armazenamento;
    private LimpezaArmazenamentoService servico;

    @BeforeAll
    static void prepararBucket() throws Exception {
        if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_LIMPEZA).build())) {
            MINIO.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_LIMPEZA).build());
        }
        MINIO.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_LIMPEZA)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
    }

    @BeforeEach
    void criarServico() {
        armazenamento = new S3ArmazenamentoDocumentos(MINIO, new StorageProperties(ENDPOINT, ACCESS_KEY, SECRET_KEY,
                BUCKET_LIMPEZA, false, false, Duration.ofSeconds(10), Duration.ofSeconds(60)), DataSize.ofMegabytes(10));
        servico = new LimpezaArmazenamentoService(intencoes, documentos, anexos, armazenamento, new SimpleMeterRegistry(),
                Duration.ofHours(24));
    }

    @AfterEach
    void limparIntencoes() {
        intencoes.deleteAll();
    }

    @Test
    void deveApagarTodasAsVersoesDoObjetoSemLinhaEDescartarAIntencao() {
        String chave = novaChave();
        guardar(chave);
        guardar(chave);
        intencoes.save(new ArmazenamentoIntencao(chave, OperacaoArmazenamento.GRAVACAO));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(Instant.now().plus(Duration.ofHours(25)));

        assertThat(resultado).containsEntry(ResultadoLimpeza.APAGADA, 1);
        assertThat(armazenamento.versoes(chave)).isEmpty();
        assertThat(intencoes.findByChaveObjeto(chave)).isEmpty();
    }

    @Test
    void deveApagarOsFicheirosComIntencaoDeApagamento() {
        String chave = novaChave();
        guardar(chave);
        intencoes.save(new ArmazenamentoIntencao(chave, OperacaoArmazenamento.APAGAMENTO));

        servico.limpar(Instant.now().plus(Duration.ofHours(25)));

        assertThat(armazenamento.versoes(chave)).isEmpty();
        assertThat(intencoes.findByChaveObjeto(chave)).isEmpty();
    }

    @Test
    void naoDeveTratarIntencoesComMenosDe24h() {
        String chave = novaChave();
        guardar(chave);
        intencoes.save(new ArmazenamentoIntencao(chave, OperacaoArmazenamento.GRAVACAO));

        Map<ResultadoLimpeza, Integer> resultado = servico.limpar(Instant.now().plus(Duration.ofHours(23)));

        assertThat(resultado).isEmpty();
        assertThat(armazenamento.versoes(chave)).hasSize(1);
        assertThat(intencoes.findByChaveObjeto(chave)).hasSize(1);
    }

    private static String novaChave() {
        return "processos/" + UUID.randomUUID() + "/PUBLICACAO/" + UUID.randomUUID() + "-regulamento.pdf";
    }

    private void guardar(String chave) {
        armazenamento.guardar(chave, new ByteArrayInputStream(("%PDF-1.4 " + UUID.randomUUID()).getBytes()),
                "application/pdf");
    }
}
