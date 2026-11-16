package com.conkord.saigt.service.documento;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EscritorZipSemCompressaoTest {

    private static final Instant INSTANTE = Instant.parse("2026-11-10T12:30:00Z");
    private static final byte[] A = "primeiro ficheiro".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B = new byte[200_000];

    @Test
    void deveEscreverEntradasSemCompressaoComDiretorioCentralValido() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        EscritorZipSemCompressao escritor = new EscritorZipSemCompressao(saida, INSTANTE);
        escritor.adicionar("DEPOSITO/a.pdf", new ByteArrayInputStream(A), A.length, sha256(A));
        escritor.adicionar("DEPOSITO/GEOCONFORM/b.zip", new ByteArrayInputStream(B), B.length, sha256(B));
        escritor.concluir();

        Path ficheiro = Files.createTempFile("escritor-zip", ".zip");
        try {
            Files.write(ficheiro, saida.toByteArray());
            try (ZipFile zip = new ZipFile(ficheiro.toFile())) {
                List<? extends ZipEntry> entradas = Collections.list(zip.entries());
                assertThat(entradas).extracting(ZipEntry::getName)
                        .containsExactly("DEPOSITO/a.pdf", "DEPOSITO/GEOCONFORM/b.zip");
                assertThat(entradas).extracting(ZipEntry::getMethod).containsOnly(ZipEntry.STORED);
                assertThat(zip.getInputStream(entradas.get(0)).readAllBytes()).isEqualTo(A);
                assertThat(zip.getInputStream(entradas.get(1)).readAllBytes()).isEqualTo(B);
                assertThat(entradas.get(1).getSize()).isEqualTo(B.length);
            }
        } finally {
            Files.deleteIfExists(ficheiro);
        }
    }

    @Test
    void deveEscreverUmZipVazioValido() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        EscritorZipSemCompressao escritor = new EscritorZipSemCompressao(saida, INSTANTE);
        escritor.concluir();

        assertThat(saida.size()).isEqualTo(22);
    }

    @Test
    void deveLancarESemDiretorioCentralQuandoOSha256NaoBate() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        EscritorZipSemCompressao escritor = new EscritorZipSemCompressao(saida, INSTANTE);

        assertThatThrownBy(() -> escritor.adicionar("a.pdf", new ByteArrayInputStream(A), A.length, sha256(B)))
                .isInstanceOf(IOException.class);
        assertThat(contem(saida.toByteArray(), new byte[]{'P', 'K', 5, 6})).isFalse();
    }

    @Test
    void deveLancarQuandoOTamanhoNaoBate() {
        EscritorZipSemCompressao escritor = new EscritorZipSemCompressao(new ByteArrayOutputStream(), INSTANTE);

        assertThatThrownBy(() -> escritor.adicionar("a.pdf", new ByteArrayInputStream(A), A.length + 1, sha256(A)))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> escritor.adicionar("a.pdf", new ByteArrayInputStream(A), A.length - 1, sha256(A)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void deveRecusarOQueNaoCabeSemZip64() {
        assertThat(EscritorZipSemCompressao.cabe(List.of(1_000L, 2_000L), 40)).isTrue();
        assertThat(EscritorZipSemCompressao.cabe(List.of(3_000_000_000L, 2_000_000_000L), 40)).isFalse();
        assertThat(EscritorZipSemCompressao.cabe(Collections.nCopies(70_000, 1L), 40)).isFalse();
    }

    private static boolean contem(byte[] bytes, byte[] padrao) {
        for (int i = 0; i <= bytes.length - padrao.length; i++) {
            boolean igual = true;
            for (int j = 0; j < padrao.length && igual; j++) {
                igual = bytes[i + j] == padrao[j];
            }
            if (igual) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
