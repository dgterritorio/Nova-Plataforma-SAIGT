package com.conkord.saigt.controller.documento;

import com.conkord.saigt.service.documento.VerificadorZip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** ZIPs reais para os uploads dos testes: o conteúdo de um `.zip` é verificado ao carregar. */
final class ZipsDeTeste {

    private static final long DATA_FIXA = 1_767_225_600_000L;

    private ZipsDeTeste() {
    }

    /** Para um nome `.zip`, um ZIP válido com o conteúdo numa entrada; para os outros, o conteúdo tal como está. */
    static byte[] conteudo(String nomeFicheiro, byte[] conteudo) {
        return VerificadorZip.aplicaSe(nomeFicheiro) ? zip("documento.pdf", conteudo) : conteudo;
    }

    /** Entradas sem conteúdo; um nome terminado em `/` é uma pasta. Determinístico: o mesmo ZIP dá o mesmo hash. */
    static byte[] comEntradas(String... nomes) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (String nome : nomes) {
                ZipEntry entrada = new ZipEntry(nome);
                entrada.setTime(DATA_FIXA);
                zip.putNextEntry(entrada);
                if (!nome.endsWith("/")) {
                    zip.write(("conteúdo de " + nome).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }

    private static byte[] zip(String nome, byte[] conteudo) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            ZipEntry entrada = new ZipEntry(nome);
            entrada.setTime(DATA_FIXA);
            zip.putNextEntry(entrada);
            zip.write(conteudo);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }
}
