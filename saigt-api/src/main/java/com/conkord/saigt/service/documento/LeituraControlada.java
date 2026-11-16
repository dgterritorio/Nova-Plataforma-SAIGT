package com.conkord.saigt.service.documento;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Lê o corpo uma só vez: conta os bytes, calcula o SHA-256 e interrompe a leitura ao passar o limite, para nunca
 * se gravar mais do que o permitido. As falhas ficam registadas porque o cliente do storage as embrulha.
 */
public final class LeituraControlada extends FilterInputStream {

    private final MessageDigest digest;
    private final long limiteBytes;
    private long lidos;
    private boolean limiteExcedido;
    private IOException falhaLeitura;

    public LeituraControlada(InputStream origem, long limiteBytes) {
        super(origem);
        this.limiteBytes = limiteBytes;
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    @Override
    public int read() throws IOException {
        byte[] um = new byte[1];
        int n = read(um, 0, 1);
        return n < 0 ? -1 : um[0] & 0xFF;
    }

    @Override
    public int read(byte[] destino, int inicio, int tamanho) throws IOException {
        int n;
        try {
            n = in.read(destino, inicio, tamanho);
        } catch (IOException e) {
            falhaLeitura = e;
            throw e;
        }
        if (n > 0) {
            lidos += n;
            if (lidos > limiteBytes) {
                limiteExcedido = true;
                throw new IOException("Limite de tamanho excedido.");
            }
            digest.update(destino, inicio, n);
        }
        return n;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    public long bytesLidos() {
        return lidos;
    }

    public boolean limiteExcedido() {
        return limiteExcedido;
    }

    public IOException falhaLeitura() {
        return falhaLeitura;
    }

    String sha256() {
        return HexFormat.of().formatHex(digest.digest());
    }
}
