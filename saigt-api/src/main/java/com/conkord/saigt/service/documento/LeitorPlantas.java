package com.conkord.saigt.service.documento;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lê o TXT dos nomes das plantas («Tipo; Nome; Folha», EF v1.5 §3.1.2) e os nomes dos TIFF dentro de um ZIP.
 * Sem estado e sem armazenamento, para se testar só com bytes.
 */
public final class LeitorPlantas {

    static final int MAXIMO_BYTES_TXT = 1024 * 1024;
    static final int MAXIMO_LINHAS = 500;
    static final int MAXIMO_ENTRADAS_ZIP = 10_000;
    // O exemplo do Figma escreve a folha no fim do nome: «Planta de Ordenamento – Uso do Solo - Folha A».
    private static final Pattern FOLHA_NO_NOME = Pattern.compile("^(.*?)\\s*[-–]\\s*Folha\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);

    private LeitorPlantas() {
    }

    public record Linha(int ordem, String tipo, String nome, String folha) {
    }

    public record Txt(List<Linha> linhas, List<String> erros) {
    }

    public static Txt lerTxt(InputStream conteudo) throws IOException {
        byte[] bytes = conteudo.readNBytes(MAXIMO_BYTES_TXT + 1);
        List<String> erros = new ArrayList<>();
        if (bytes.length > MAXIMO_BYTES_TXT) {
            return new Txt(List.of(), List.of("O TXT tem mais de 1 MB."));
        }
        List<Linha> linhas = new ArrayList<>();
        String[] texto = descodificar(bytes).split("\\R");
        for (int i = 0; i < texto.length; i++) {
            String original = i == 0 ? texto[i].replace("﻿", "") : texto[i];
            if (original.isBlank()) {
                continue;
            }
            if (linhas.size() == MAXIMO_LINHAS) {
                erros.add("O TXT tem mais de " + MAXIMO_LINHAS + " linhas; as restantes foram ignoradas.");
                break;
            }
            Linha linha = lerLinha(linhas.size(), original);
            if (linha == null) {
                erros.add("Linha " + (i + 1) + ": use o formato «Tipo; Nome; Folha».");
            } else {
                linhas.add(linha);
            }
        }
        return new Txt(List.copyOf(linhas), List.copyOf(erros));
    }

    /** Nomes dos ficheiros `.tif`/`.tiff` do ZIP, sem pastas e por ordem alfabética. */
    public static List<String> tiffsDoZip(InputStream conteudo) throws IOException {
        TreeSet<String> nomes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (ZipInputStream zip = new ZipInputStream(conteudo)) {
            ZipEntry entrada;
            int lidas = 0;
            while ((entrada = zip.getNextEntry()) != null && lidas++ < MAXIMO_ENTRADAS_ZIP) {
                String nome = entrada.getName().substring(entrada.getName().lastIndexOf('/') + 1);
                String minusculas = nome.toLowerCase(Locale.ROOT);
                if (!entrada.isDirectory() && (minusculas.endsWith(".tif") || minusculas.endsWith(".tiff"))) {
                    nomes.add(nome);
                }
            }
        }
        return List.copyOf(nomes);
    }

    /** Sem acentos e em minúsculas, para comparar o tipo do TXT com o nome do ZIP. */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static Linha lerLinha(int ordem, String texto) {
        String[] partes = texto.split(";", -1);
        if (partes.length == 3) {
            String tipo = partes[0].strip();
            String nome = partes[1].strip();
            return tipo.isEmpty() || nome.isEmpty() ? null : new Linha(ordem, tipo, nome, vazioComoNulo(partes[2]));
        }
        if (partes.length == 2) {
            String tipo = partes[0].strip();
            Matcher folha = FOLHA_NO_NOME.matcher(partes[1].strip());
            if (tipo.isEmpty() || !folha.matches() || folha.group(1).isBlank()) {
                return null;
            }
            return new Linha(ordem, tipo, folha.group(1).strip(), folha.group(2).strip());
        }
        return null;
    }

    private static String vazioComoNulo(String texto) {
        return texto.isBlank() ? null : texto.strip();
    }

    /** UTF-8 e, se não for válido, Windows-1252, que é como o Bloco de Notas antigo grava. */
    private static String descodificar(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
