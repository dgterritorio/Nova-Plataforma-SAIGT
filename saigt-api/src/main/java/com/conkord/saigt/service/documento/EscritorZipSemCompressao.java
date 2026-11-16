package com.conkord.saigt.service.documento;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;

/**
 * ZIP em streaming, de uma só passagem, com as entradas sem compressão (método 0) e o diretório central no fim.
 * O `java.util.zip` não serve: exige o CRC-32 de cada entrada STORED antes de a escrever, e o manifesto só guarda o
 * SHA-256. Aqui o CRC e o tamanho seguem no descritor de dados depois dos bytes. Cada entrada é conferida contra o
 * manifesto (tamanho e SHA-256) ao terminar: se não bater, lança e o ZIP fica cortado, sem diretório central, em vez
 * de entregar conteúdo diferente do comunicado. Sem ZIP64: {@link #cabe} diz se o manifesto cabe.
 */
final class EscritorZipSemCompressao {

    static final int MAXIMO_ENTRADAS = 0xFFFF - 1;
    static final long MAXIMO_BYTES = 0xFFFFFFFFL - 1;

    private static final int TAMANHO_BLOCO = 64 * 1024;
    private static final int CABECALHO_LOCAL = 0x04034b50;
    private static final int DESCRITOR_DADOS = 0x08074b50;
    private static final int ENTRADA_CENTRAL = 0x02014b50;
    private static final int FIM_CENTRAL = 0x06054b50;
    private static final short VERSAO = 20;
    // Bit 3: tamanho e CRC no descritor depois dos dados. Bit 11: nomes em UTF-8.
    private static final short MARCAS = 0x0008 | 0x0800;
    private static final int TAMANHO_CABECALHO_LOCAL = 30;
    private static final int TAMANHO_DESCRITOR = 16;
    private static final int TAMANHO_ENTRADA_CENTRAL = 46;
    private static final int TAMANHO_FIM_CENTRAL = 22;

    private final OutputStream destino;
    private final short horaDos;
    private final short dataDos;
    private final List<Entrada> entradas = new ArrayList<>();
    private long posicao;

    private record Entrada(byte[] nome, long crc, long tamanho, long deslocamento) {
    }

    EscritorZipSemCompressao(OutputStream destino, Instant instante) {
        this.destino = destino;
        LocalDateTime data = LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
        int ano = Math.max(data.getYear(), 1980);
        this.horaDos = (short) (data.getHour() << 11 | data.getMinute() << 5 | data.getSecond() / 2);
        this.dataDos = (short) ((ano - 1980) << 9 | data.getMonthValue() << 5 | data.getDayOfMonth());
    }

    /** Se o ZIP com estas entradas cabe no formato sem ZIP64: o total dos ficheiros e dos nomes, e o número de entradas. */
    static boolean cabe(List<Long> tamanhos, long bytesDosNomes) {
        long total = TAMANHO_FIM_CENTRAL + bytesDosNomes * 2;
        for (long tamanho : tamanhos) {
            total += tamanho + TAMANHO_CABECALHO_LOCAL + TAMANHO_DESCRITOR + TAMANHO_ENTRADA_CENTRAL;
        }
        return tamanhos.size() <= MAXIMO_ENTRADAS && total <= MAXIMO_BYTES;
    }

    /** Escreve a entrada lendo `conteudo` até ao fim; lança se o tamanho ou o SHA-256 não forem os do manifesto. */
    void adicionar(String nome, InputStream conteudo, long tamanho, String sha256) throws IOException {
        byte[] nomeBytes = nome.getBytes(StandardCharsets.UTF_8);
        long deslocamento = posicao;
        escrever(cabecalhoLocal(nomeBytes));

        CRC32 crc = new CRC32();
        MessageDigest digest = novoDigest();
        byte[] bloco = new byte[TAMANHO_BLOCO];
        long lidos = 0;
        int n;
        while ((n = conteudo.read(bloco)) != -1) {
            lidos += n;
            if (lidos > tamanho) {
                throw new IOException("O objeto é maior do que o tamanho do manifesto.");
            }
            crc.update(bloco, 0, n);
            digest.update(bloco, 0, n);
            destino.write(bloco, 0, n);
            posicao += n;
        }
        if (lidos != tamanho || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha256)) {
            throw new IOException("O objeto lido não é o do manifesto.");
        }
        escrever(descritor(crc.getValue(), lidos));
        entradas.add(new Entrada(nomeBytes, crc.getValue(), lidos, deslocamento));
    }

    /** Diretório central e fim do arquivo: só depois disto o ZIP é válido. */
    void concluir() throws IOException {
        long inicioCentral = posicao;
        for (Entrada entrada : entradas) {
            escrever(entradaCentral(entrada));
        }
        long tamanhoCentral = posicao - inicioCentral;
        ByteBuffer fim = buffer(TAMANHO_FIM_CENTRAL);
        fim.putInt(FIM_CENTRAL).putShort((short) 0).putShort((short) 0)
                .putShort((short) entradas.size()).putShort((short) entradas.size())
                .putInt((int) tamanhoCentral).putInt((int) inicioCentral).putShort((short) 0);
        escrever(fim);
        destino.flush();
    }

    private ByteBuffer cabecalhoLocal(byte[] nome) {
        ByteBuffer b = buffer(TAMANHO_CABECALHO_LOCAL + nome.length);
        b.putInt(CABECALHO_LOCAL).putShort(VERSAO).putShort(MARCAS).putShort((short) 0)
                .putShort(horaDos).putShort(dataDos).putInt(0).putInt(0).putInt(0)
                .putShort((short) nome.length).putShort((short) 0).put(nome);
        return b;
    }

    private static ByteBuffer descritor(long crc, long tamanho) {
        ByteBuffer b = buffer(TAMANHO_DESCRITOR);
        b.putInt(DESCRITOR_DADOS).putInt((int) crc).putInt((int) tamanho).putInt((int) tamanho);
        return b;
    }

    private ByteBuffer entradaCentral(Entrada e) {
        ByteBuffer b = buffer(TAMANHO_ENTRADA_CENTRAL + e.nome().length);
        b.putInt(ENTRADA_CENTRAL).putShort(VERSAO).putShort(VERSAO).putShort(MARCAS).putShort((short) 0)
                .putShort(horaDos).putShort(dataDos).putInt((int) e.crc()).putInt((int) e.tamanho())
                .putInt((int) e.tamanho()).putShort((short) e.nome().length).putShort((short) 0).putShort((short) 0)
                .putShort((short) 0).putShort((short) 0).putInt(0).putInt((int) e.deslocamento()).put(e.nome());
        return b;
    }

    private void escrever(ByteBuffer bytes) throws IOException {
        destino.write(bytes.array(), 0, bytes.position());
        posicao += bytes.position();
    }

    private static ByteBuffer buffer(int tamanho) {
        return ByteBuffer.allocate(tamanho).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static MessageDigest novoDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
