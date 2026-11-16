package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ResultadoDescarga;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Uma descarga já aceite: tem lugar no limite de simultâneas, linha `INICIADA` e o objeto aberto. {@link #fechar}
 * regista o resultado, fecha a leitura e liberta o lugar, aconteça o que acontecer ao envio.
 */
public final class DescargaEmCurso {

    private static final int TAMANHO_BLOCO = 64 * 1024;

    private final InputStream conteudo;
    private final Long descargaId;
    private final AuditoriaDescargaService auditoria;
    private final Runnable libertarLugar;
    private long enviados;
    private boolean concluida;
    private boolean fechada;

    DescargaEmCurso(InputStream conteudo, Long descargaId, AuditoriaDescargaService auditoria, Runnable libertarLugar) {
        this.conteudo = conteudo;
        this.descargaId = descargaId;
        this.auditoria = auditoria;
        this.libertarLugar = libertarLugar;
    }

    /** Copia até ao fim; só um fim sem erro conta como concluída. */
    public void transferir(OutputStream destino) throws IOException {
        byte[] bloco = new byte[TAMANHO_BLOCO];
        int lidos;
        while ((lidos = conteudo.read(bloco)) != -1) {
            destino.write(bloco, 0, lidos);
            enviados += lidos;
        }
        destino.flush();
        concluida = true;
    }

    public long bytesEnviados() {
        return enviados;
    }

    public ResultadoDescarga fechar() {
        if (fechada) {
            return concluida ? ResultadoDescarga.CONCLUIDA : ResultadoDescarga.INTERROMPIDA;
        }
        fechada = true;
        ResultadoDescarga resultado = concluida ? ResultadoDescarga.CONCLUIDA : ResultadoDescarga.INTERROMPIDA;
        try {
            conteudo.close();
        } catch (IOException e) {
            // A leitura já está terminada ou abandonada; o resultado não depende de a fechar bem.
        } finally {
            try {
                auditoria.concluir(descargaId, resultado, enviados);
            } finally {
                libertarLugar.run();
            }
        }
        return resultado;
    }
}
