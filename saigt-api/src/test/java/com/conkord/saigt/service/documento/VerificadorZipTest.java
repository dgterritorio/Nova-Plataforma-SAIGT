package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ZipInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorZipTest {

    private static final String CAMINHO = "processos/2026-0001/PUBLICACAO/doc-plantas.zip";
    private static final UUID DOCUMENTO_ID = UUID.randomUUID();
    private static final byte[] DADOS = "conteúdo".getBytes(StandardCharsets.UTF_8);

    @Test
    void deveAceitarUmZipComFicheirosDeNomeValidoNaRaiz() throws Exception {
        assertThatCode(() -> verificar(zip("ordenamento.gpkg", "legenda.pdf"))).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarUmZipVazio() {
        byte[] vazio = new byte[22];
        ByteBuffer.wrap(vazio).order(ByteOrder.LITTLE_ENDIAN).putInt(0x06054b50);

        assertThatCode(() -> verificar(vazio)).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarUmZipComEntradasStoredEDescritorDeDados() {
        assertThatCode(() -> verificar(zipStoredComDescritor("planta.tif", DADOS))).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarUmaPastaVazia() throws Exception {
        assertThat(lista(zip("dados/", "legenda.pdf"))).isEqualTo("dados/");
    }

    @Test
    void deveRecusarAPastaEOFicheiroNumaSubpasta() throws Exception {
        assertThat(lista(zip("dados/", "dados/plano.gpkg"))).isEqualTo("dados/, dados/plano.gpkg");
    }

    @Test
    void deveRecusarUmFicheiroNumaSubpastaSemEntradaDePasta() throws Exception {
        assertThat(lista(zip("legenda.pdf", "dados/plano.gpkg"))).isEqualTo("dados/plano.gpkg");
    }

    @Test
    void deveRecusarUmNomeComSeparadorDePastaDoWindows() throws Exception {
        // Num ZIP criado em FAT, como os do java.util.zip, o Commons Compress normaliza `\` para `/`.
        assertThat(lista(zip("dados\\plano.gpkg"))).isEqualTo("dados/plano.gpkg");
    }

    @Test
    void deveRecusarUmNomeComEspaco() throws Exception {
        assertThat(lista(zip("planta final.tif"))).isEqualTo("planta final.tif");
    }

    @Test
    void deveRecusarUmNomeComDoisPontos() throws Exception {
        assertThat(lista(zip("planta.v2.tfw"))).isEqualTo("planta.v2.tfw");
    }

    @Test
    void deveJuntarTodasAsEntradasInvalidasNaMesmaListaPelaOrdemDoZip() throws Exception {
        assertThat(lista(zip("planta final.tif", "legenda.pdf", "planta.v2.tfw", "dados/", "x/y.pdf")))
                .isEqualTo("planta final.tif, planta.v2.tfw, dados/, x/y.pdf");
    }

    @Test
    void deveValidarONomeDeUmZipDentroDoZipSemOAbrir() throws Exception {
        byte[] interior = zip("sub pasta/lixo.pdf");

        assertThat(lista(zipCom("anexos final.zip", interior))).isEqualTo("anexos final.zip");
        assertThatCode(() -> verificar(zipCom("anexos.zip", interior))).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarComoIlegivelUmFicheiroQueNaoEZip() {
        byte[] pdf = "%PDF-1.4 não sou um zip".getBytes(StandardCharsets.UTF_8);

        assertThat(lista(pdf)).isEqualTo(VerificadorZip.MSG_ILEGIVEL);
    }

    @Test
    void deveRecusarComoIlegivelUmZipComODiretorioCentralCorrompido() throws Exception {
        byte[] zip = zip("legenda.pdf", "ordenamento.gpkg");
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putInt(zip.length - 22 + 16, 0x7f7f7f7f);

        assertThat(lista(zip)).isEqualTo(VerificadorZip.MSG_ILEGIVEL);
    }

    @Test
    void deveRecusarComoIlegivelQuandoODiretorioCentralUltrapassaOLimiteDeLeitura() throws Exception {
        String[] nomes = new String[6000];
        for (int i = 0; i < nomes.length; i++) {
            nomes[i] = "f" + i + ".txt";
        }
        byte[] grande = zip(nomes);
        ArmazenamentoEmMemoria armazenamento = new ArmazenamentoEmMemoria(grande);

        assertThatThrownBy(() -> new VerificadorZip(armazenamento, 128 * 1024)
                .verificar(DOCUMENTO_ID, CAMINHO, null, grande.length))
                .isInstanceOfSatisfying(ZipInvalidoException.class,
                        e -> assertThat(erro(e)).endsWith(": " + VerificadorZip.MSG_ILEGIVEL + "."));
        assertThat(armazenamento.bytesLidos).isLessThanOrEqualTo(128 * 1024);
        assertThatCode(() -> new VerificadorZip(new ArmazenamentoEmMemoria(grande), 1024 * 1024)
                .verificar(DOCUMENTO_ID, CAMINHO, null, grande.length)).doesNotThrowAnyException();
    }

    @Test
    void deveLerSoODiretorioCentralSemDescomprimirAsEntradas() throws Exception {
        byte[] aleatorio = new byte[5 * 1024 * 1024];
        new Random(3).nextBytes(aleatorio);
        byte[] zip = zipCom("ortofoto.tif", aleatorio);
        ArmazenamentoEmMemoria armazenamento = new ArmazenamentoEmMemoria(zip);

        new VerificadorZip(armazenamento).verificar(DOCUMENTO_ID, CAMINHO, null, zip.length);

        assertThat(zip.length).isGreaterThan(5 * 1024 * 1024);
        assertThat(armazenamento.bytesLidos).isLessThanOrEqualTo(2L * CanalObjetoArmazenado.TAMANHO_BLOCO);
    }

    @Test
    void deveLerAVersaoGravadaQuandoOBucketTemVersionamento() throws Exception {
        byte[] zip = zip("legenda.pdf");
        ArmazenamentoEmMemoria armazenamento = new ArmazenamentoEmMemoria(zip);

        new VerificadorZip(armazenamento).verificar(DOCUMENTO_ID, CAMINHO, "v-7", zip.length);

        assertThat(armazenamento.versoesPedidas).isNotEmpty().containsOnly("v-7");
        assertThat(armazenamento.caminhosPedidos).containsOnly(CAMINHO);
    }

    @Test
    void deveLerAVersaoCorrenteQuandoOBucketNaoTemVersionamento() throws Exception {
        byte[] zip = zip("legenda.pdf");
        ArmazenamentoEmMemoria armazenamento = new ArmazenamentoEmMemoria(zip);

        new VerificadorZip(armazenamento).verificar(DOCUMENTO_ID, CAMINHO, null, zip.length);

        assertThat(armazenamento.versoesPedidas).isNotEmpty().containsOnlyNulls();
    }

    @Test
    void deveDeixarSairAFalhaDoStorageEmVezDeDizerQueOZipEIlegivel() throws Exception {
        byte[] zip = zip("legenda.pdf");
        ArmazenamentoEmMemoria armazenamento = new ArmazenamentoEmMemoria(zip);
        armazenamento.indisponivel = true;

        assertThatThrownBy(() -> new VerificadorZip(armazenamento).verificar(DOCUMENTO_ID, CAMINHO, null, zip.length))
                .isInstanceOf(StorageIndisponivelException.class);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void naoDeveRegistarNomesDeEntradasNosLogs(CapturedOutput saida) throws Exception {
        assertThatThrownBy(() -> verificar(zip("planta final.tif", "planta.v2.tfw")))
                .isInstanceOf(ZipInvalidoException.class);

        assertThat(saida).contains("processoDocumentoId=" + DOCUMENTO_ID).contains("entradasRecusadas=2")
                .doesNotContain("planta final").doesNotContain("planta.v2");
    }

    @Test
    void aplicaSeSoAFicheirosComExtensaoZipSemDistinguirMaiusculas() {
        assertThat(VerificadorZip.aplicaSe("Plantas finais.ZIP")).isTrue();
        assertThat(VerificadorZip.aplicaSe("plantas.zip")).isTrue();
        assertThat(VerificadorZip.aplicaSe("plantas.pdf")).isFalse();
        assertThat(VerificadorZip.aplicaSe("zip")).isFalse();
    }

    private static void verificar(byte[] zip) {
        new VerificadorZip(new ArmazenamentoEmMemoria(zip)).verificar(DOCUMENTO_ID, CAMINHO, null, zip.length);
    }

    /** O `{lista}` da mensagem. */
    private static String lista(byte[] zip) {
        try {
            verificar(zip);
        } catch (ZipInvalidoException e) {
            String erro = erro(e);
            String prefixo = "O ZIP contém pastas ou ficheiros com nome inválido: ";
            assertThat(erro).startsWith(prefixo).endsWith(".");
            return erro.substring(prefixo.length(), erro.length() - 1);
        }
        throw new AssertionError("O ZIP devia ter sido recusado.");
    }

    private static String erro(ZipInvalidoException e) {
        assertThat(e.getCodigo()).isEqualTo("DOC_ZIP_INVALIDO");
        ValidacaoException.CampoErro campo = e.getCampos().get(0);
        assertThat(campo.path()).isEqualTo("ficheiro");
        return campo.erro();
    }

    private static byte[] zip(String... nomes) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (String nome : nomes) {
                zip.putNextEntry(new ZipEntry(nome));
                if (!nome.endsWith("/")) {
                    zip.write(DADOS);
                }
                zip.closeEntry();
            }
        }
        return saida.toByteArray();
    }

    private static byte[] zipCom(String nome, byte[] conteudo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry(nome));
            zip.write(conteudo);
            zip.closeEntry();
        }
        return saida.toByteArray();
    }

    /**
     * Entrada STORED com o bit 3: tamanhos e CRC a zero no cabeçalho local e só no descritor de dados, como gravam
     * alguns compressores em streaming. Um leitor dos cabeçalhos locais não sabe onde a entrada acaba.
     */
    private static byte[] zipStoredComDescritor(String nome, byte[] dados) {
        CRC32 crc = new CRC32();
        crc.update(dados);
        int crcValor = (int) crc.getValue();
        byte[] nomeBytes = nome.getBytes(StandardCharsets.UTF_8);
        short sinais = 0x0808;
        ByteBuffer b = ByteBuffer.allocate(30 + nomeBytes.length + dados.length + 16 + 46 + nomeBytes.length + 22)
                .order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x04034b50).putShort((short) 20).putShort(sinais).putShort((short) 0)
                .putShort((short) 0).putShort((short) 0x21).putInt(0).putInt(0).putInt(0)
                .putShort((short) nomeBytes.length).putShort((short) 0).put(nomeBytes).put(dados);
        b.putInt(0x08074b50).putInt(crcValor).putInt(dados.length).putInt(dados.length);
        int inicioCentral = b.position();
        b.putInt(0x02014b50).putShort((short) 20).putShort((short) 20).putShort(sinais).putShort((short) 0)
                .putShort((short) 0).putShort((short) 0x21).putInt(crcValor).putInt(dados.length).putInt(dados.length)
                .putShort((short) nomeBytes.length).putShort((short) 0).putShort((short) 0).putShort((short) 0)
                .putShort((short) 0).putInt(0).putInt(0).put(nomeBytes);
        int tamanhoCentral = b.position() - inicioCentral;
        b.putInt(0x06054b50).putShort((short) 0).putShort((short) 0).putShort((short) 1).putShort((short) 1)
                .putInt(tamanhoCentral).putInt(inicioCentral).putShort((short) 0);
        return b.array();
    }

    private static final class ArmazenamentoEmMemoria implements ArmazenamentoDocumentos {

        private final byte[] objeto;
        private final List<String> versoesPedidas = new ArrayList<>();
        private final List<String> caminhosPedidos = new ArrayList<>();
        private long bytesLidos;
        private boolean indisponivel;

        ArmazenamentoEmMemoria(byte[] objeto) {
            this.objeto = objeto;
        }

        @Override
        public InputStream abrir(String caminho, String versao, long inicio, long comprimento) {
            if (indisponivel) {
                throw new StorageIndisponivelException("indisponível", new IOException("sem ligação"));
            }
            assertThat(comprimento).isPositive();
            caminhosPedidos.add(caminho);
            versoesPedidas.add(versao);
            int fim = (int) Math.min(objeto.length, inicio + comprimento);
            bytesLidos += fim - inicio;
            return new ByteArrayInputStream(Arrays.copyOfRange(objeto, (int) inicio, fim));
        }

        @Override
        public String guardar(String caminho, InputStream conteudo, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String copiar(String origem, String versaoOrigem, String destino) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remover(String caminho) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removerTodasVersoes(String caminho) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void marcarEliminado(String caminho) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<VersaoObjeto> versoes(String caminho) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reter(String caminho, String versionId, Instant ate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean temRetencao(String caminho, String versionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Instant> retidoAte(String caminho, String versionId) {
            throw new UnsupportedOperationException();
        }
    }
}
