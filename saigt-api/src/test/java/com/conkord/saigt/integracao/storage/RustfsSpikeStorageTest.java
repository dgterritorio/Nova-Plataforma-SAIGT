package com.conkord.saigt.integracao.storage;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetBucketVersioningArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectLockConfigurationArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetObjectRetentionArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spike ao RustFS 1.0.0 (nota armazenamento-e-acesso-documentos §10): os 7 comportamentos de que dependem a retenção
 * e a pasta SUBSTITUIDOS/. Sem código de produção: usa o cliente MinIO diretamente, como o adaptador S3.
 *
 * <p>Duas identidades: a de raiz do contentor (administrativa, com bypass) e a da aplicação, um utilizador do RustFS
 * com a política mínima, sem {@code s3:BypassGovernanceRetention}. A política e o utilizador criam-se pela API de
 * administração do RustFS, com o curl do próprio contentor. Credenciais só de teste.
 */
class RustfsSpikeStorageTest extends StorageTestcontainersBase {

    private static final Logger log = LoggerFactory.getLogger(RustfsSpikeStorageTest.class);

    private static final String BUCKET_SPIKE = "saigt-spike-rustfs";
    private static final String APP_ACCESS_KEY = "saigt-app-spike";
    private static final String APP_SECRET_KEY = "saigt-app-spike-segredo";
    private static final String POLITICA_APP = "saigt-app-minima";
    private static final long TAMANHO_GRANDE = 600L * 1024 * 1024;
    private static final long PARTE = 16L * 1024 * 1024;

    /**
     * Política mínima da aplicação: ler, gravar, apagar versões, listar e reter, mas nunca contornar a retenção. O
     * cliente MinIO pede a localização do bucket antes do primeiro pedido, por isso precisa de s3:GetBucketLocation.
     */
    private static final String POLITICA_JSON = """
            {"Version":"2012-10-17","Statement":[
              {"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket","s3:ListBucketVersions","s3:GetBucketVersioning",
                "s3:GetBucketObjectLockConfiguration"],"Resource":["arn:aws:s3:::%1$s"]},
              {"Effect":"Allow","Action":["s3:GetObject","s3:GetObjectVersion","s3:PutObject","s3:DeleteObject",
                "s3:DeleteObjectVersion","s3:GetObjectRetention","s3:PutObjectRetention"],
                "Resource":["arn:aws:s3:::%1$s/*"]}]}
            """.formatted(BUCKET_SPIKE);

    private static final String ENDPOINT = "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3);

    private static final MinioClient RAIZ = MinioClient.builder()
            .endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
    private static final MinioClient APP = MinioClient.builder()
            .endpoint(ENDPOINT).credentials(APP_ACCESS_KEY, APP_SECRET_KEY).build();

    @BeforeAll
    static void prepararBucketEIdentidadeDaAplicacao() throws Exception {
        RAIZ.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_SPIKE).objectLock(true).build());
        admin("add-canned-policy?name=" + POLITICA_APP, POLITICA_JSON);
        admin("add-user?accessKey=" + APP_ACCESS_KEY,
                "{\"secretKey\":\"" + APP_SECRET_KEY + "\",\"status\":\"enabled\"}");
        admin("set-user-or-group-policy?policyName=" + POLITICA_APP + "&userOrGroup=" + APP_ACCESS_KEY
                + "&isGroup=false", "");
    }

    @Test
    void spike1_deveTerOBucketComVersionamentoEObjectLockSemRetencaoPorOmissao() throws Exception {
        VersioningConfiguration versionamento = APP.getBucketVersioning(
                GetBucketVersioningArgs.builder().bucket(BUCKET_SPIKE).build());
        ObjectLockConfiguration lock = APP.getObjectLockConfiguration(
                GetObjectLockConfigurationArgs.builder().bucket(BUCKET_SPIKE).build());
        String chave = chave("sem-retencao");
        String versao = gravar(APP, chave, "%PDF-1.4 sem retenção");

        log.info("SPIKE 1: versionamento={}, modo por omissão={}, duração por omissão={}, retenção de um objeto novo={}",
                versionamento.status(), lock.mode(), lock.duration(), retencao(chave, versao));
        assertThat(versionamento.status()).isEqualTo(VersioningConfiguration.Status.ENABLED);
        assertThat(lock.mode()).isNull();
        assertThat(lock.duration()).isNull();
        assertThat(retencao(chave, versao)).isNull();
    }

    @Test
    void spike2_deveReterSoAVersaoIndicadaEmModoGovernance() throws Exception {
        String chave = chave("retencao-por-versao");
        String primeira = gravar(APP, chave, "%PDF-1.4 v1");
        String segunda = gravar(APP, chave, "%PDF-1.4 v2");
        ZonedDateTime ate = daquiAUmDia();

        APP.setObjectRetention(SetObjectRetentionArgs.builder().bucket(BUCKET_SPIKE).object(chave)
                .versionId(primeira).config(new Retention(RetentionMode.GOVERNANCE, ate)).build());

        Retention retidaPrimeira = retencao(chave, primeira);
        log.info("SPIKE 2: v1 retida={} até {}, v2 retida={}", retidaPrimeira.mode(),
                retidaPrimeira.retainUntilDate(), retencao(chave, segunda));
        assertThat(retidaPrimeira.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(retidaPrimeira.retainUntilDate().toInstant()).isEqualTo(ate.toInstant());
        assertThat(retencao(chave, segunda)).isNull();
    }

    @Test
    void spike3_deveRecusarAIdentidadeDaAplicacaoApagarOuContornarAVersaoRetida() throws Exception {
        String chave = chave("sem-bypass");
        String versao = gravar(APP, chave, "%PDF-1.4 retido");
        ZonedDateTime ate = daquiAUmDia();
        reter(APP, chave, versao, ate);

        String apagar = codigoDoErro(() -> APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE)
                .object(chave).versionId(versao).build()));
        String apagarComBypass = codigoDoErro(() -> APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE)
                .object(chave).versionId(versao).bypassGovernanceMode(true).build()));
        String encurtarComBypass = codigoDoErro(() -> APP.setObjectRetention(SetObjectRetentionArgs.builder()
                .bucket(BUCKET_SPIKE).object(chave).versionId(versao).bypassGovernanceMode(true)
                .config(new Retention(RetentionMode.GOVERNANCE, ZonedDateTime.now().plusMinutes(1))).build()));
        String encurtar = codigoDoErro(() -> APP.setObjectRetention(SetObjectRetentionArgs.builder()
                .bucket(BUCKET_SPIKE).object(chave).versionId(versao)
                .config(new Retention(RetentionMode.GOVERNANCE, ZonedDateTime.now().plusMinutes(1))).build()));

        log.info("SPIKE 3: aplicação — apagar a versão retida → {}; com bypass → {}; encurtar a retenção com bypass → {};"
                + " sem bypass → {}", apagar, apagarComBypass, encurtarComBypass, encurtar);
        assertThat(List.of(apagar, apagarComBypass, encurtarComBypass, encurtar)).doesNotContainNull();
        assertThat(versoes(chave)).extracting(Item::versionId).containsExactly(versao);
        assertThat(retencao(chave, versao).retainUntilDate().toInstant()).isEqualTo(ate.toInstant());

        // Controlo: a identidade administrativa contorna a retenção governance, por isso a recusa vem da política.
        RAIZ.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave).versionId(versao)
                .bypassGovernanceMode(true).build());
        log.info("SPIKE 3: identidade administrativa — apagar com bypass → OK");
        assertThat(versoes(chave)).isEmpty();
    }

    @Test
    void spike4_deveCriarUmMarcadorDeEliminacaoNumObjetoRetidoEManterAVersao() throws Exception {
        String chave = chave("marcador");
        String versao = gravar(APP, chave, "%PDF-1.4 retido com marcador");
        reter(APP, chave, versao, daquiAUmDia());

        APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave).build());

        List<Item> itens = versoes(chave);
        String semVersao = codigoDoErro(() -> APP.statObject(StatObjectArgs.builder().bucket(BUCKET_SPIKE)
                .object(chave).build()));
        log.info("SPIKE 4: depois do DELETE sem versionId — {} itens: {}; leitura sem versionId → {}", itens.size(),
                itens.stream().map(i -> (i.isDeleteMarker() ? "marcador" : "versão " + i.versionId())
                        + (i.isLatest() ? " (atual)" : "")).toList(), semVersao);
        assertThat(itens).hasSize(2);
        assertThat(itens).filteredOn(Item::isDeleteMarker).singleElement().extracting(Item::isLatest).isEqualTo(true);
        assertThat(itens).filteredOn(i -> !i.isDeleteMarker()).singleElement()
                .extracting(Item::versionId).isEqualTo(versao);
        assertThat(semVersao).isNotNull();
        assertThat(ler(chave, versao, null, null)).isEqualTo("%PDF-1.4 retido com marcador");
        assertThat(retencao(chave, versao).mode()).isEqualTo(RetentionMode.GOVERNANCE);
    }

    @Test
    void spike5_deveApagarTodasAsVersoesDeUmObjetoSemRetencao() throws Exception {
        String chave = chave("apagar-tudo");
        gravar(APP, chave, "%PDF-1.4 a");
        gravar(APP, chave, "%PDF-1.4 b");
        gravar(APP, chave, "%PDF-1.4 c");
        APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave).build());
        List<Item> antes = versoes(chave);

        for (Item item : antes) {
            APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave)
                    .versionId(item.versionId()).build());
        }

        log.info("SPIKE 5: {} entradas antes (3 versões e 1 marcador), {} depois", antes.size(), versoes(chave).size());
        assertThat(antes).hasSize(4);
        assertThat(versoes(chave)).isEmpty();
    }

    @Test
    void spike6_deveCopiarUmObjetoDe600MbPorCopyObject() throws Exception {
        String origem = chave("grande-origem");
        String destino = chave("grande-copia");
        String versaoOrigem;
        try (InputStream dados = new DadosDeterministicos(TAMANHO_GRANDE)) {
            versaoOrigem = APP.putObject(PutObjectArgs.builder().bucket(BUCKET_SPIKE).object(origem)
                    .stream(dados, TAMANHO_GRANDE, PARTE).contentType("application/zip").build()).versionId();
        }

        long inicio = System.nanoTime();
        ObjectWriteResponse copia = APP.copyObject(CopyObjectArgs.builder().bucket(BUCKET_SPIKE).object(destino)
                .source(CopySource.builder().bucket(BUCKET_SPIKE).object(origem).versionId(versaoOrigem).build())
                .build());
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        long tamanhoCopia = APP.statObject(StatObjectArgs.builder().bucket(BUCKET_SPIKE).object(destino)
                .versionId(copia.versionId()).build()).size();
        String shaCopia;
        try (GetObjectResponse lida = APP.getObject(GetObjectArgs.builder().bucket(BUCKET_SPIKE).object(destino)
                .versionId(copia.versionId()).build())) {
            shaCopia = sha256(lida);
        }
        String shaEsperado = sha256(new DadosDeterministicos(TAMANHO_GRANDE));
        log.info("SPIKE 6: CopyObject de {} bytes em {} ms; versionId da cópia {}; SHA-256 igual ao da origem: {}",
                tamanhoCopia, ms, copia.versionId(), shaCopia.equals(shaEsperado));
        assertThat(copia.versionId()).isNotBlank().isNotEqualTo(versaoOrigem);
        assertThat(tamanhoCopia).isEqualTo(TAMANHO_GRANDE);
        assertThat(shaCopia).isEqualTo(shaEsperado);
    }

    @Test
    void spike6_deveCopiarUmObjetoRetidoComACopiaSemRetencaoEPodendoSerRetida() throws Exception {
        String origem = chave("retido-origem");
        String destino = chave("SUBSTITUIDOS/retido-copia");
        String versaoOrigem = gravar(APP, origem, "%PDF-1.4 retido para copiar");
        ZonedDateTime ate = daquiAUmDia();
        reter(APP, origem, versaoOrigem, ate);

        String versaoCopia = APP.copyObject(CopyObjectArgs.builder().bucket(BUCKET_SPIKE).object(destino)
                .source(CopySource.builder().bucket(BUCKET_SPIKE).object(origem).versionId(versaoOrigem).build())
                .build()).versionId();
        Retention aoNascer = retencao(destino, versaoCopia);
        reter(APP, destino, versaoCopia, ate);
        Retention depois = retencao(destino, versaoCopia);
        String apagarCopiaRetida = codigoDoErro(() -> APP.removeObject(RemoveObjectArgs.builder().bucket(BUCKET_SPIKE)
                .object(destino).versionId(versaoCopia).build()));

        log.info("SPIKE 6: cópia de um objeto retido — retenção ao nascer={}; depois de reter={} até {};"
                + " apagar a cópia retida → {}", aoNascer, depois.mode(), depois.retainUntilDate(), apagarCopiaRetida);
        assertThat(aoNascer).isNull();
        assertThat(depois.mode()).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(depois.retainUntilDate().toInstant()).isEqualTo(ate.toInstant());
        assertThat(apagarCopiaRetida).isNotNull();
        assertThat(ler(destino, versaoCopia, null, null)).isEqualTo("%PDF-1.4 retido para copiar");
    }

    @Test
    void spike7_deveLerPorVersionIdEComIntervalo() throws Exception {
        String chave = chave("leitura");
        String primeira = gravar(APP, chave, "0123456789-versao-um");
        gravar(APP, chave, "ABCDEFGHIJ-versao-dois");

        String inteira = ler(chave, primeira, null, null);
        String intervaloDaPrimeira = ler(chave, primeira, 11L, 6L);
        String intervaloDaAtual = ler(chave, null, 0L, 10L);

        log.info("SPIKE 7: v1 inteira='{}'; v1 bytes 11–16='{}'; atual bytes 0–9='{}'", inteira, intervaloDaPrimeira,
                intervaloDaAtual);
        assertThat(inteira).isEqualTo("0123456789-versao-um");
        assertThat(intervaloDaPrimeira).isEqualTo("versao");
        assertThat(intervaloDaAtual).isEqualTo("ABCDEFGHIJ");
    }

    // --- auxiliares

    private static void admin(String caminho, String corpo) throws Exception {
        Container.ExecResult r = STORAGE.execInContainer("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                "--aws-sigv4", "aws:amz:us-east-1:s3", "--user", ACCESS_KEY + ":" + SECRET_KEY, "-X", "PUT",
                "-H", "Content-Type: application/json", "--data-binary", corpo,
                "http://localhost:" + PORTA_S3 + "/rustfs/admin/v3/" + caminho);
        assertThat(r.getStdout()).as("admin %s", caminho.replaceAll("\\?.*", "")).isEqualTo("200");
    }

    private static String chave(String nome) {
        return "processos/spike/" + UUID.randomUUID() + "-" + nome;
    }

    private static ZonedDateTime daquiAUmDia() {
        return ZonedDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String gravar(MinioClient cliente, String chave, String conteudo) throws Exception {
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        return cliente.putObject(PutObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType("application/pdf").build())
                .versionId();
    }

    private static void reter(MinioClient cliente, String chave, String versao, ZonedDateTime ate) throws Exception {
        cliente.setObjectRetention(SetObjectRetentionArgs.builder().bucket(BUCKET_SPIKE).object(chave)
                .versionId(versao).config(new Retention(RetentionMode.GOVERNANCE, ate)).build());
    }

    /** Nulo quando a versão não tem retenção. */
    private static Retention retencao(String chave, String versao) throws Exception {
        try {
            Retention r = APP.getObjectRetention(GetObjectRetentionArgs.builder().bucket(BUCKET_SPIKE).object(chave)
                    .versionId(versao).build());
            return r == null || r.mode() == null ? null : r;
        } catch (ErrorResponseException e) {
            if ("NoSuchObjectLockConfiguration".equals(e.errorResponse().code())) {
                return null;
            }
            throw e;
        }
    }

    private static List<Item> versoes(String chave) throws Exception {
        List<Item> itens = new ArrayList<>();
        for (Result<Item> r : APP.listObjects(ListObjectsArgs.builder().bucket(BUCKET_SPIKE).prefix(chave)
                .includeVersions(true).build())) {
            itens.add(r.get());
        }
        return itens;
    }

    private static String ler(String chave, String versao, Long inicio, Long tamanho) throws Exception {
        GetObjectArgs.Builder pedido = GetObjectArgs.builder().bucket(BUCKET_SPIKE).object(chave).versionId(versao);
        if (inicio != null) {
            pedido.offset(inicio).length(tamanho);
        }
        try (GetObjectResponse r = APP.getObject(pedido.build())) {
            return new String(r.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** O código S3 do erro, ou nulo se a operação passou. */
    private static String codigoDoErro(Operacao operacao) {
        try {
            operacao.executar();
            return null;
        } catch (ErrorResponseException e) {
            return e.errorResponse().code();
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    private static String sha256(InputStream in) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream d = new DigestInputStream(in, sha)) {
            d.transferTo(OutputStreamNulo.INSTANCIA);
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    @FunctionalInterface
    private interface Operacao {
        void executar() throws Exception;
    }

    private static final class OutputStreamNulo extends java.io.OutputStream {
        static final OutputStreamNulo INSTANCIA = new OutputStreamNulo();

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    }

    /** Bytes reproduzíveis, gerados sem os guardar em memória, para comparar a cópia grande com a origem. */
    private static final class DadosDeterministicos extends InputStream {
        private final long tamanho;
        private long posicao;

        DadosDeterministicos(long tamanho) {
            this.tamanho = tamanho;
        }

        @Override
        public int read() {
            return posicao < tamanho ? valor(posicao++) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (posicao >= tamanho) {
                return -1;
            }
            int n = (int) Math.min(len, tamanho - posicao);
            for (int i = 0; i < n; i++) {
                b[off + i] = (byte) valor(posicao++);
            }
            return n;
        }

        private static int valor(long i) {
            return (int) ((i * 31 + (i >>> 20)) & 0xFF);
        }
    }
}
