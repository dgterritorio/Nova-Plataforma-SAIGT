package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ResultadoDescarga;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

/**
 * A pasta de depósito já aceite: tem lugar no limite, linha `INICIADA` e o primeiro objeto aberto, para uma falha do
 * storage ainda dar 503. {@link #fechar} regista o resultado, fecha a leitura e liberta o lugar, aconteça o que
 * acontecer ao envio. Só um ZIP completo, com o diretório central, conta como concluído.
 */
public final class DescargaPastaEmCurso {

    private final List<EntradaDepositoSnit> entradas;
    private final ArmazenamentoDocumentos armazenamento;
    private final Instant instante;
    private final Long descargaId;
    private final AuditoriaDescargaService auditoria;
    private final Runnable libertarLugar;
    private InputStream aberto;
    private long enviados;
    private boolean concluida;
    private boolean fechada;

    DescargaPastaEmCurso(List<EntradaDepositoSnit> entradas, InputStream primeiro, ArmazenamentoDocumentos armazenamento,
                         Instant instante, Long descargaId, AuditoriaDescargaService auditoria,
                         Runnable libertarLugar) {
        this.entradas = entradas;
        this.aberto = primeiro;
        this.armazenamento = armazenamento;
        this.instante = instante;
        this.descargaId = descargaId;
        this.auditoria = auditoria;
        this.libertarLugar = libertarLugar;
    }

    public void transferir(OutputStream destino) throws IOException {
        EscritorZipSemCompressao escritor = new EscritorZipSemCompressao(new Contador(destino), instante);
        for (EntradaDepositoSnit entrada : entradas) {
            if (aberto == null) {
                aberto = armazenamento.abrir(entrada.chave(), entrada.versao(), 0, entrada.tamanhoBytes());
            }
            try {
                escritor.adicionar(entrada.caminhoNoZip(), aberto, entrada.tamanhoBytes(), entrada.hashSha256());
            } finally {
                fecharLeitura();
            }
        }
        escritor.concluir();
        concluida = true;
    }

    public long bytesEnviados() {
        return enviados;
    }

    public ResultadoDescarga fechar() {
        ResultadoDescarga resultado = concluida ? ResultadoDescarga.CONCLUIDA : ResultadoDescarga.INTERROMPIDA;
        if (fechada) {
            return resultado;
        }
        fechada = true;
        try {
            fecharLeitura();
        } finally {
            try {
                auditoria.concluir(descargaId, resultado, enviados);
            } finally {
                libertarLugar.run();
            }
        }
        return resultado;
    }

    private void fecharLeitura() {
        InputStream leitura = aberto;
        aberto = null;
        if (leitura != null) {
            try {
                leitura.close();
            } catch (IOException e) {
                // A leitura já terminou ou foi abandonada; o resultado não depende de a fechar bem.
            }
        }
    }

    private final class Contador extends OutputStream {

        private final OutputStream destino;

        private Contador(OutputStream destino) {
            this.destino = destino;
        }

        @Override
        public void write(int b) throws IOException {
            destino.write(b);
            enviados++;
        }

        @Override
        public void write(byte[] bytes, int inicio, int tamanho) throws IOException {
            destino.write(bytes, inicio, tamanho);
            enviados += tamanho;
        }

        @Override
        public void flush() throws IOException {
            destino.flush();
        }
    }
}
