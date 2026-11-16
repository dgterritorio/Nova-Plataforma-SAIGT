package com.conkord.saigt.integracao.storage;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.service.documento.VersaoObjeto;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.SetObjectRetentionArgs;
import io.minio.messages.Item;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ArmazenamentoDocumentosStorageTest extends StorageTestcontainersBase {

    private static final String BUCKET_VERSIONADO = "saigt-versoes-teste";
    private static final String BUCKET_COM_LOCK = "saigt-retencao-teste";
    private static final String ENDPOINT = "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3);

    private static final MinioClient MINIO = MinioClient.builder()
            .endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();

    private final S3ArmazenamentoDocumentos armazenamento = new S3ArmazenamentoDocumentos(MINIO,
            new StorageProperties(ENDPOINT, ACCESS_KEY, SECRET_KEY, BUCKET_VERSIONADO, false, false, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(60)), DataSize.ofMegabytes(10));

    @BeforeAll
    static void prepararBucketVersionado() throws Exception {
        if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_VERSIONADO).build())) {
            MINIO.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_VERSIONADO).build());
        }
        MINIO.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_VERSIONADO)
                .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
    }

    @Test
    void deveApagarTodasAsVersoesDeUmObjetoVersionado() throws Exception {
        String caminho = novoCaminho();
        guardar(caminho);
        guardar(caminho);
        assertThat(versoes(caminho)).hasSize(2);

        armazenamento.remover(caminho);

        assertThat(versoes(caminho)).isEmpty();
    }

    @Test
    void deveApagarTresVersoesEOMarcadorDeEliminacaoSemTocarNaChaveQueComecaPeloMesmoTexto() throws Exception {
        String caminho = novoCaminho();
        String vizinho = caminho + "b";
        guardar(caminho);
        guardar(caminho);
        guardar(caminho);
        MINIO.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_VERSIONADO).object(caminho).build());
        guardar(vizinho);
        assertThat(versoes(caminho)).filteredOn(v -> v.startsWith(caminho + "@")).hasSize(4);

        armazenamento.removerTodasVersoes(caminho);

        assertThat(versoes(caminho)).extracting(v -> v.split("@")[0]).containsExactly(vizinho);
    }

    @Test
    void naoDeveFalharAoApagarAsVersoesDeUmaChaveInexistente() throws Exception {
        String caminho = novoCaminho();

        armazenamento.removerTodasVersoes(caminho);

        assertThat(versoes(caminho)).isEmpty();
    }

    @Test
    void deveApagarOObjetoNumBucketSemVersionamento() throws Exception {
        String bucketSemVersoes = "saigt-sem-versoes-teste";
        if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(bucketSemVersoes).build())) {
            MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucketSemVersoes).build());
        }
        S3ArmazenamentoDocumentos semVersoes = new S3ArmazenamentoDocumentos(MINIO, new StorageProperties(ENDPOINT,
                ACCESS_KEY, SECRET_KEY, bucketSemVersoes, false, false, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(60)), DataSize.ofMegabytes(10));
        String caminho = novoCaminho();
        semVersoes.guardar(caminho, new ByteArrayInputStream("%PDF-1.4 s".getBytes()), "application/pdf");

        semVersoes.removerTodasVersoes(caminho);

        assertThat(MINIO.listObjects(ListObjectsArgs.builder().bucket(bucketSemVersoes).prefix(caminho)
                .includeVersions(true).build())).isEmpty();
    }

    @Test
    void deveGravarOStreamCompletoEmPartesSemConhecerOTamanho() throws Exception {
        String caminho = novoCaminho();
        byte[] conteudo = new byte[12 * 1024 * 1024];
        new java.util.Random(7).nextBytes(conteudo);

        String versionId = armazenamento.guardar(caminho, new ByteArrayInputStream(conteudo), "application/pdf");

        assertThat(versionId).isNotBlank();
        try (var objeto = MINIO.getObject(io.minio.GetObjectArgs.builder().bucket(BUCKET_VERSIONADO).object(caminho).build())) {
            assertThat(objeto.readAllBytes()).isEqualTo(conteudo);
        }
    }

    @Test
    void deveDevolverExatamenteOsUltimos64KbDeUmObjetoNaVersaoPedida() throws Exception {
        String caminho = novoCaminho();
        byte[] conteudo = new byte[300 * 1024];
        new java.util.Random(11).nextBytes(conteudo);
        String versao = armazenamento.guardar(caminho, new ByteArrayInputStream(conteudo), "application/zip");
        armazenamento.guardar(caminho, new ByteArrayInputStream("outra versão".getBytes()), "application/zip");
        int comprimento = 64 * 1024;

        byte[] lido;
        try (var entrada = armazenamento.abrir(caminho, versao, conteudo.length - comprimento, comprimento)) {
            lido = entrada.readAllBytes();
        }

        assertThat(lido).isEqualTo(java.util.Arrays.copyOfRange(conteudo, conteudo.length - comprimento,
                conteudo.length));
    }

    @Test
    void deveListarAsVersoesSemRetencaoNumBucketSemObjectLock() {
        String caminho = novoCaminho();
        guardar(caminho);
        guardar(caminho);
        guardar(caminho + "x");

        assertThat(armazenamento.versoes(caminho)).hasSize(2).noneMatch(VersaoObjeto::retida);
    }

    @Test
    void deveDevolverListaVaziaParaUmObjetoInexistente() {
        assertThat(armazenamento.versoes(novoCaminho())).isEmpty();
    }

    @Test
    void deveMarcarComoRetidaAVersaoComRetencaoEmModoGovernance() throws Exception {
        String bucketComLock = "saigt-lock-teste";
        if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(bucketComLock).build())) {
            MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucketComLock).objectLock(true).build());
        }
        S3ArmazenamentoDocumentos comLock = new S3ArmazenamentoDocumentos(MINIO, new StorageProperties(ENDPOINT,
                ACCESS_KEY, SECRET_KEY, bucketComLock, false, false, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(60)), DataSize.ofMegabytes(10));
        String retido = novoCaminho();
        String livre = novoCaminho();
        String versaoRetida = comLock.guardar(retido, new ByteArrayInputStream("%PDF-1.4 r".getBytes()), "application/pdf");
        comLock.guardar(livre, new ByteArrayInputStream("%PDF-1.4 l".getBytes()), "application/pdf");
        MINIO.setObjectRetention(SetObjectRetentionArgs.builder().bucket(bucketComLock).object(retido)
                .versionId(versaoRetida).config(new Retention(RetentionMode.GOVERNANCE,
                        ZonedDateTime.now().plusDays(1))).build());

        assertThat(comLock.versoes(retido)).singleElement().extracting(VersaoObjeto::retida).isEqualTo(true);
        assertThat(comLock.versoes(livre)).singleElement().extracting(VersaoObjeto::retida).isEqualTo(false);
    }

    @Test
    void deveCopiarAVersaoPedidaEDevolverOVersionIdDaCopia() throws Exception {
        String origem = novoCaminho();
        String primeira = armazenamento.guardar(origem, new ByteArrayInputStream("%PDF-1.4 primeira".getBytes()),
                "application/pdf");
        armazenamento.guardar(origem, new ByteArrayInputStream("%PDF-1.4 segunda".getBytes()), "application/pdf");
        String destino = "privado/" + UUID.randomUUID() + "/SUBSTITUIDOS/doc.pdf";

        String versaoCopia = armazenamento.copiar(origem, primeira, destino);

        assertThat(versoes(destino)).containsExactly(destino + "@" + versaoCopia);
        try (var copia = armazenamento.abrir(destino, versaoCopia, 0, 0)) {
            assertThat(new String(copia.readAllBytes())).isEqualTo("%PDF-1.4 primeira");
        }
        assertThat(versoes(origem)).hasSize(2);
    }

    @Test
    void deveCriarSoUmMarcadorDeEliminacaoEManterAVersaoRetida() throws Exception {
        String bucketComLock = "saigt-lock-teste";
        if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(bucketComLock).build())) {
            MINIO.makeBucket(MakeBucketArgs.builder().bucket(bucketComLock).objectLock(true).build());
        }
        S3ArmazenamentoDocumentos comLock = new S3ArmazenamentoDocumentos(MINIO, new StorageProperties(ENDPOINT,
                ACCESS_KEY, SECRET_KEY, bucketComLock, false, false, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(60)), DataSize.ofMegabytes(10));
        String retido = novoCaminho();
        String versaoRetida = comLock.guardar(retido, new ByteArrayInputStream("%PDF-1.4 r".getBytes()), "application/pdf");
        MINIO.setObjectRetention(SetObjectRetentionArgs.builder().bucket(bucketComLock).object(retido)
                .versionId(versaoRetida).config(new Retention(RetentionMode.GOVERNANCE,
                        ZonedDateTime.now().plusDays(1))).build());

        comLock.marcarEliminado(retido);

        List<String> marcadores = new ArrayList<>();
        List<String> versoesComConteudo = new ArrayList<>();
        for (Result<Item> resposta : MINIO.listObjects(ListObjectsArgs.builder()
                .bucket(bucketComLock).prefix(retido).includeVersions(true).build())) {
            Item item = resposta.get();
            (item.isDeleteMarker() ? marcadores : versoesComConteudo).add(item.versionId());
        }
        assertThat(versoesComConteudo).containsExactly(versaoRetida);
        assertThat(marcadores).hasSize(1);
    }

    @Test
    void deveReterEmGovernanceSoAVersaoPedida() throws Exception {
        S3ArmazenamentoDocumentos comLock = armazenamentoComLock();
        String caminho = novoCaminho();
        String v1 = comLock.guardar(caminho, new ByteArrayInputStream("%PDF-1.4 1".getBytes()), "application/pdf");
        String v2 = comLock.guardar(caminho, new ByteArrayInputStream("%PDF-1.4 2".getBytes()), "application/pdf");
        Instant ate = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        comLock.reter(caminho, v1, ate);

        Retention retencao = MINIO.getObjectRetention(GetObjectRetentionArgs.builder().bucket(BUCKET_COM_LOCK)
                .object(caminho).versionId(v1).build());
        assertThat(retencao.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(retencao.retainUntilDate().toInstant()).isEqualTo(ate);
        assertThat(comLock.temRetencao(caminho, v1)).isTrue();
        assertThat(comLock.temRetencao(caminho, v2)).isFalse();
        assertThat(comLock.retidoAte(caminho, v1)).contains(ate);
        assertThat(comLock.retidoAte(caminho, v2)).isEmpty();
    }

    @Test
    void deveRecusarRemoverUmObjetoComUmaVersaoRetidaEMantela() {
        S3ArmazenamentoDocumentos comLock = armazenamentoComLock();
        String caminho = novoCaminho();
        String versao = comLock.guardar(caminho, new ByteArrayInputStream("%PDF-1.4 r".getBytes()), "application/pdf");
        comLock.reter(caminho, versao, Instant.now().plus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> comLock.remover(caminho)).isInstanceOf(StorageIndisponivelException.class);

        assertThat(comLock.versoes(caminho)).extracting(VersaoObjeto::versionId).contains(versao);
    }

    @Test
    void naoDeveTerRetencaoNumBucketSemObjectLock() {
        String caminho = novoCaminho();
        String versao = armazenamento.guardar(caminho, new ByteArrayInputStream("%PDF-1.4 l".getBytes()),
                "application/pdf");

        assertThat(armazenamento.temRetencao(caminho, versao)).isFalse();
        assertThat(armazenamento.retidoAte(caminho, versao)).isEmpty();
    }

    private static S3ArmazenamentoDocumentos armazenamentoComLock() {
        try {
            if (!MINIO.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_COM_LOCK).build())) {
                MINIO.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_COM_LOCK).objectLock(true).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new S3ArmazenamentoDocumentos(MINIO, new StorageProperties(ENDPOINT, ACCESS_KEY, SECRET_KEY,
                BUCKET_COM_LOCK, false, false, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(60)),
                DataSize.ofMegabytes(10));
    }

    private static String novoCaminho() {
        return "privado/" + UUID.randomUUID() + "/PUBLICACAO/doc.pdf";
    }

    private void guardar(String caminho) {
        byte[] conteudo = ("%PDF-1.4 " + UUID.randomUUID()).getBytes();
        armazenamento.guardar(caminho, new ByteArrayInputStream(conteudo), "application/pdf");
    }

    private static List<String> versoes(String prefixo) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : MINIO.listObjects(ListObjectsArgs.builder()
                .bucket(BUCKET_VERSIONADO).prefix(prefixo).includeVersions(true).build())) {
            Item item = resposta.get();
            resultado.add(item.objectName() + "@" + item.versionId());
        }
        return resultado;
    }
}
