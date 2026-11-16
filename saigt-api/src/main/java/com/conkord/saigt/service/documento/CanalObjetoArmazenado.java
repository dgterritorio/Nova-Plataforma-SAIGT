package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.StorageIndisponivelException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Canal só de leitura sobre um objeto já gravado, por leituras parciais do storage em blocos fixos: nada vai para
 * disco local. Lê no máximo `leituraMaxima` bytes; acima disso falha com {@link LeituraExcedidaException}.
 */
final class CanalObjetoArmazenado implements SeekableByteChannel {

    static final int TAMANHO_BLOCO = 64 * 1024;
    private static final String MSG_INDISPONIVEL = "Armazenamento de documentos temporariamente indisponível.";

    private final ArmazenamentoDocumentos armazenamento;
    private final String caminho;
    private final String versao;
    private final long tamanho;
    private final long leituraMaxima;

    private long posicao;
    private long bytesLidos;
    private long inicioBloco = -1;
    private byte[] bloco = new byte[0];
    private boolean aberto = true;

    /** `versao` é o `versionId` devolvido pela gravação, nulo num bucket sem versionamento. */
    CanalObjetoArmazenado(ArmazenamentoDocumentos armazenamento, String caminho, String versao, long tamanho,
                          long leituraMaxima) {
        this.armazenamento = armazenamento;
        this.caminho = caminho;
        this.versao = versao;
        this.tamanho = tamanho;
        this.leituraMaxima = leituraMaxima;
    }

    @Override
    public int read(ByteBuffer destino) throws IOException {
        garantirAberto();
        if (posicao >= tamanho) {
            return -1;
        }
        if (!destino.hasRemaining()) {
            return 0;
        }
        if (posicao < inicioBloco || posicao >= inicioBloco + bloco.length) {
            carregarBloco(posicao - posicao % TAMANHO_BLOCO);
        }
        int deslocamento = (int) (posicao - inicioBloco);
        int quantidade = Math.min(destino.remaining(), bloco.length - deslocamento);
        destino.put(bloco, deslocamento, quantidade);
        posicao += quantidade;
        return quantidade;
    }

    private void carregarBloco(long inicio) throws IOException {
        int comprimento = (int) Math.min(TAMANHO_BLOCO, tamanho - inicio);
        if (bytesLidos + comprimento > leituraMaxima) {
            throw new LeituraExcedidaException();
        }
        byte[] lido;
        try (InputStream entrada = armazenamento.abrir(caminho, versao, inicio, comprimento)) {
            lido = entrada.readNBytes(comprimento);
        } catch (IOException e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
        if (lido.length != comprimento) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL,
                    new IOException("Objeto mais curto do que o tamanho gravado."));
        }
        bytesLidos += comprimento;
        inicioBloco = inicio;
        bloco = lido;
    }

    long bytesLidos() {
        return bytesLidos;
    }

    @Override
    public long position() throws IOException {
        garantirAberto();
        return posicao;
    }

    @Override
    public SeekableByteChannel position(long novaPosicao) throws IOException {
        garantirAberto();
        if (novaPosicao < 0) {
            throw new IllegalArgumentException("Posição negativa.");
        }
        posicao = novaPosicao;
        return this;
    }

    @Override
    public long size() throws IOException {
        garantirAberto();
        return tamanho;
    }

    @Override
    public int write(ByteBuffer origem) {
        throw new NonWritableChannelException();
    }

    @Override
    public SeekableByteChannel truncate(long tamanhoNovo) {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return aberto;
    }

    @Override
    public void close() {
        aberto = false;
        bloco = new byte[0];
    }

    private void garantirAberto() throws ClosedChannelException {
        if (!aberto) {
            throw new ClosedChannelException();
        }
    }

    /** O ZIP pede mais bytes do que o limite de leitura: tratado como ilegível. */
    static final class LeituraExcedidaException extends IOException {
        private static final long serialVersionUID = 1L;

        LeituraExcedidaException() {
            super("Limite de leitura do ZIP excedido.");
        }
    }
}
