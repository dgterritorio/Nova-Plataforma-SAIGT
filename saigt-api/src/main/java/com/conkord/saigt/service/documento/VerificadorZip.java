package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ConkordException;
import com.conkord.saigt.exception.ZipInvalidoException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Conteúdo de um ZIP já gravado: sem pastas e com nomes que cumprem as regras de nomenclatura. Lê só o diretório
 * central, por leituras parciais do objeto; nenhuma entrada é descomprimida. O nome do próprio ZIP não é validado.
 */
@Component
public class VerificadorZip {

    /**
     * Entrada não confiável: o diretório central fica todo em memória e um ZIP forjado pode declarar milhões de
     * entradas. 16 MiB chegam para ~150 mil entradas com nomes normais, muito acima de qualquer ZIP real.
     */
    static final long LEITURA_MAXIMA_BYTES = 16L * 1024 * 1024;
    static final String MSG_ILEGIVEL = "ficheiro ZIP ilegível";

    private static final Logger log = LoggerFactory.getLogger(VerificadorZip.class);
    private static final String EXTENSAO_ZIP = "zip";

    private final ArmazenamentoDocumentos armazenamento;
    private final long leituraMaxima;

    @Autowired
    public VerificadorZip(ArmazenamentoDocumentos armazenamento) {
        this(armazenamento, LEITURA_MAXIMA_BYTES);
    }

    VerificadorZip(ArmazenamentoDocumentos armazenamento, long leituraMaxima) {
        this.armazenamento = armazenamento;
        this.leituraMaxima = leituraMaxima;
    }

    public static boolean aplicaSe(String nomeFicheiro) {
        return EXTENSAO_ZIP.equals(CaminhoDocumento.extensao(nomeFicheiro));
    }

    /**
     * Falha uma vez com todas as entradas recusadas. `versao` é o `versionId` devolvido pela gravação, nulo num
     * bucket sem versionamento. Devolve os nomes das entradas de um ZIP aceite.
     */
    public List<String> verificar(UUID processoDocumentoId, String caminho, String versao, long tamanhoBytes) {
        List<String> recusadas;
        List<String> entradas;
        try (CanalObjetoArmazenado canal = new CanalObjetoArmazenado(armazenamento, caminho, versao, tamanhoBytes,
                leituraMaxima);
             ZipFile zip = ZipFile.builder().setSeekableByteChannel(canal).setIgnoreLocalFileHeader(true).get()) {
            entradas = Collections.list(zip.getEntries()).stream().map(ZipArchiveEntry::getName).toList();
            recusadas = entradasRecusadas(zip);
        } catch (ConkordException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Só o tipo: a mensagem de um ZIP corrompido pode trazer nomes de entradas.
            log.warn("ZIP ilegível recusado — processoDocumentoId={} motivo={}", processoDocumentoId,
                    e.getClass().getSimpleName());
            throw new ZipInvalidoException(MSG_ILEGIVEL);
        }
        if (!recusadas.isEmpty()) {
            log.warn("ZIP recusado — processoDocumentoId={} entradasRecusadas={}", processoDocumentoId,
                    recusadas.size());
            throw new ZipInvalidoException(String.join(", ", recusadas));
        }
        return entradas;
    }

    /** Os nomes das entradas de um ZIP já aceite, sem voltar a aplicar as regras do conteúdo. */
    public List<String> entradas(UUID processoDocumentoId, String caminho, String versao, long tamanhoBytes) {
        try (CanalObjetoArmazenado canal = new CanalObjetoArmazenado(armazenamento, caminho, versao, tamanhoBytes,
                leituraMaxima);
             ZipFile zip = ZipFile.builder().setSeekableByteChannel(canal).setIgnoreLocalFileHeader(true).get()) {
            return Collections.list(zip.getEntries()).stream().map(ZipArchiveEntry::getName).toList();
        } catch (ConkordException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("ZIP ilegível recusado — processoDocumentoId={} motivo={}", processoDocumentoId,
                    e.getClass().getSimpleName());
            throw new ZipInvalidoException(MSG_ILEGIVEL);
        }
    }

    private static List<String> entradasRecusadas(ZipFile zip) {
        List<String> recusadas = new ArrayList<>();
        for (ZipArchiveEntry entrada : Collections.list(zip.getEntries())) {
            String nome = entrada.getName();
            if (temPasta(entrada) || RegrasNomeFicheiro.erroNome(nome).isPresent()) {
                recusadas.add(nome);
            }
        }
        return recusadas;
    }

    private static boolean temPasta(ZipArchiveEntry entrada) {
        String nome = entrada.getName();
        return entrada.isDirectory() || nome.indexOf('/') >= 0 || nome.indexOf('\\') >= 0;
    }
}
