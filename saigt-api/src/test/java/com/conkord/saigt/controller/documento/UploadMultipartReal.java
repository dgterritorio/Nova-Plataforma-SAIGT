package com.conkord.saigt.controller.documento;

import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * O `multipart()` do MockMvc guarda as partes como objetos e deixa o corpo do pedido vazio, por isso não passa pelo
 * stream que o leitor de uploads consome. Aqui cada pedido segue com um corpo `multipart/form-data` escrito à mão:
 * as partes de texto primeiro e `ficheiro` no fim, como o contrato exige.
 */
public final class UploadMultipartReal {

    private static final String CRLF = "\r\n";

    private UploadMultipartReal() {
    }

    public static MockMultipartHttpServletRequestBuilder multipart(String url, Object... variaveis) {
        MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart(url, variaveis);
        pedido.with(corpoReal(false));
        return pedido;
    }

    static MockMultipartHttpServletRequestBuilder multipart(HttpMethod metodo, String url, Object... variaveis) {
        MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart(metodo, url, variaveis);
        pedido.with(corpoReal(false));
        return pedido;
    }

    /** Escreve as partes de texto depois do ficheiro, para provar que o pedido é recusado. */
    static RequestPostProcessor ficheiroAntesDoTexto() {
        return corpoReal(true);
    }

    private static RequestPostProcessor corpoReal(boolean ficheiroPrimeiro) {
        return pedido -> {
            if (!(pedido instanceof MockMultipartHttpServletRequest multipart)) {
                return pedido;
            }
            String fronteira = "----TesteSaigt" + UUID.randomUUID();
            ByteArrayOutputStream corpo = new ByteArrayOutputStream();
            if (ficheiroPrimeiro) {
                escreverFicheiros(corpo, multipart, fronteira);
                escreverTexto(corpo, multipart, fronteira);
            } else {
                escreverTexto(corpo, multipart, fronteira);
                escreverFicheiros(corpo, multipart, fronteira);
            }
            escrever(corpo, "--" + fronteira + "--" + CRLF);
            pedido.setContent(corpo.toByteArray());
            pedido.setContentType("multipart/form-data; boundary=" + fronteira);
            return pedido;
        };
    }

    private static void escreverTexto(ByteArrayOutputStream corpo, MockHttpServletRequest pedido, String fronteira) {
        for (Map.Entry<String, String[]> parametro : pedido.getParameterMap().entrySet()) {
            for (String valor : parametro.getValue()) {
                escrever(corpo, "--" + fronteira + CRLF
                        + "Content-Disposition: form-data; name=\"" + parametro.getKey() + "\"" + CRLF + CRLF
                        + valor + CRLF);
            }
        }
    }

    private static void escreverFicheiros(ByteArrayOutputStream corpo, MockMultipartHttpServletRequest pedido,
                                          String fronteira) {
        for (Map.Entry<String, MultipartFile> parte : pedido.getFileMap().entrySet()) {
            MultipartFile ficheiro = parte.getValue();
            escrever(corpo, "--" + fronteira + CRLF
                    + "Content-Disposition: form-data; name=\"" + parte.getKey() + "\"; filename=\""
                    + ficheiro.getOriginalFilename() + "\"" + CRLF
                    + "Content-Type: " + ficheiro.getContentType() + CRLF + CRLF);
            try {
                corpo.write(ficheiro.getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            escrever(corpo, CRLF);
        }
    }

    private static void escrever(ByteArrayOutputStream corpo, String texto) {
        corpo.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
    }
}
