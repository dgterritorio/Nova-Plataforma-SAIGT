package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.TamanhoExcedidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.service.documento.PedidoUpload;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o corpo `multipart/form-data` em streaming: as partes de texto vêm antes de `ficheiro`, cujo conteúdo segue
 * ligado ao pedido sem passar por memória nem disco. O multipart do Spring está desligado porque grava as partes
 * antes de o controlador as ver.
 */
@Component
public class LeitorUploadDocumento {

    private static final String PARTE_FICHEIRO = "ficheiro";
    private static final String PARTE_CATEGORIA = "categoria";
    private static final String PARTE_DOCUMENTO_CONFIG = "documentoConfigId";
    private static final String PARTE_TAGS = "tags";
    private static final int TAMANHO_MAXIMO_TEXTO = 1024;
    private static final int PARTES_MAXIMAS = 32;
    public PedidoUpload ler(HttpServletRequest pedido) {
        if (!JakartaServletFileUpload.isMultipartContent(pedido)) {
            throw erroCampo(PARTE_FICHEIRO, "O pedido tem de ser multipart/form-data.");
        }
        try {
            JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
            upload.setMaxFileCount(PARTES_MAXIMAS);
            return percorrer(upload.getItemIterator(pedido));
        } catch (FileUploadSizeException e) {
            throw new TamanhoExcedidoException();
        } catch (FileUploadException e) {
            throw erroCampo(PARTE_FICHEIRO, "Pedido multipart inválido.");
        } catch (IOException e) {
            throw erroCampo(PARTE_FICHEIRO, "Não foi possível ler o pedido.");
        }
    }

    private PedidoUpload percorrer(FileItemInputIterator partes) throws IOException {
        String categoria = null;
        Long documentoConfigId = null;
        List<String> tags = new ArrayList<>();
        while (partes.hasNext()) {
            FileItemInput parte = partes.next();
            if (parte.isFormField()) {
                String valor = lerTexto(parte);
                switch (parte.getFieldName()) {
                    case PARTE_CATEGORIA -> categoria = valor;
                    case PARTE_DOCUMENTO_CONFIG -> documentoConfigId = lerNumero(valor);
                    case PARTE_TAGS -> tags.add(valor);
                    default -> {
                        // Partes desconhecidas são ignoradas.
                    }
                }
            } else if (PARTE_FICHEIRO.equals(parte.getFieldName())) {
                return new PedidoUpload(categoria, documentoConfigId, List.copyOf(tags), parte.getName(),
                        parte.getContentType(), parte.getInputStream(), () -> verificarSemMaisPartes(partes));
            }
        }
        return new PedidoUpload(categoria, documentoConfigId, List.copyOf(tags), null, null, null, () -> { });
    }

    private static String lerTexto(FileItemInput parte) throws IOException {
        try (InputStream entrada = parte.getInputStream()) {
            byte[] bytes = entrada.readNBytes(TAMANHO_MAXIMO_TEXTO + 1);
            if (bytes.length > TAMANHO_MAXIMO_TEXTO) {
                throw erroCampo(parte.getFieldName(), "Valor demasiado longo.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static Long lerNumero(String valor) {
        try {
            return valor.isBlank() ? null : Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw erroCampo(PARTE_DOCUMENTO_CONFIG, "Tem de ser um número.");
        }
    }

    /** O ficheiro vem por último: uma parte depois dele só se descobre depois de gravado, por isso o serviço apaga o objeto. */
    private static void verificarSemMaisPartes(FileItemInputIterator partes) {
        try {
            if (partes.hasNext()) {
                throw erroCampo(PARTE_FICHEIRO, "A parte ficheiro tem de ser a última do pedido.");
            }
        } catch (FileUploadSizeException e) {
            throw new TamanhoExcedidoException();
        } catch (IOException e) {
            throw erroCampo(PARTE_FICHEIRO, "Pedido multipart inválido.");
        }
    }

    private static ValidacaoException erroCampo(String campo, String erro) {
        return new ValidacaoException("Documento inválido.", List.of(new ValidacaoException.CampoErro(campo, erro)));
    }
}
