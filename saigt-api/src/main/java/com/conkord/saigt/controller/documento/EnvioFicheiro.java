package com.conkord.saigt.controller.documento;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Intervalo pedido e cabeçalhos de uma descarga em streaming (BL-DOC-029), comuns ao ticket e ao link público. Só um
 * intervalo conta; `If-Range` diferente do ETag, sintaxe inválida ou vários intervalos dão o ficheiro inteiro.
 */
final class EnvioFicheiro {

    private final String nomeFicheiro;
    private final long total;
    private final String etag;
    private final boolean semIndexacao;
    private long inicio;
    private long fim;
    private boolean parcial;
    private boolean foraDoFicheiro;

    private EnvioFicheiro(String nomeFicheiro, long total, String hashSha256, boolean semIndexacao) {
        this.nomeFicheiro = nomeFicheiro;
        this.total = total;
        this.etag = "\"" + hashSha256 + "\"";
        this.semIndexacao = semIndexacao;
        this.inicio = 0;
        this.fim = total - 1;
    }

    static EnvioFicheiro de(HttpServletRequest pedido, String nomeFicheiro, long total, String hashSha256,
                            boolean semIndexacao) {
        EnvioFicheiro envio = new EnvioFicheiro(nomeFicheiro, total, hashSha256, semIndexacao);
        List<HttpRange> intervalos = envio.lerIntervalos(pedido);
        if (intervalos.size() == 1 && total > 0) {
            HttpRange intervalo = intervalos.get(0);
            long inicioPedido = intervalo.getRangeStart(total);
            if (inicioPedido >= total) {
                envio.foraDoFicheiro = true;
            } else {
                envio.inicio = inicioPedido;
                envio.fim = intervalo.getRangeEnd(total);
                envio.parcial = true;
            }
        }
        return envio;
    }

    /** 416 quando o intervalo começa depois do fim do ficheiro; nesse caso não há mais nada a enviar. */
    boolean recusarSeForaDoFicheiro(HttpServletResponse resposta) {
        if (!foraDoFicheiro) {
            return false;
        }
        resposta.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
        resposta.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + total);
        return true;
    }

    long inicio() {
        return inicio;
    }

    long comprimento() {
        return total == 0 ? 0 : fim - inicio + 1;
    }

    void escreverCabecalhos(HttpServletResponse resposta) {
        resposta.setStatus(parcial ? HttpStatus.PARTIAL_CONTENT.value() : HttpStatus.OK.value());
        resposta.setHeader(HttpHeaders.CONTENT_TYPE, MediaTypeFactory.getMediaType(nomeFicheiro)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString());
        resposta.setContentLengthLong(comprimento());
        resposta.setHeader(HttpHeaders.ETAG, etag);
        resposta.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (parcial) {
            resposta.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + inicio + "-" + fim + "/" + total);
        }
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(nomeFicheiro, StandardCharsets.UTF_8).build().toString());
        escreverCabecalhosDeSeguranca(resposta);
        if (semIndexacao) {
            resposta.setHeader("X-Robots-Tag", "noindex");
        }
    }

    /** Os cabeçalhos de BL-DOC-029 comuns a qualquer descarga, ficheiro ou ZIP gerado. */
    static void escreverCabecalhosDeSeguranca(HttpServletResponse resposta) {
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        resposta.setHeader("Referrer-Policy", "no-referrer");
    }

    private List<HttpRange> lerIntervalos(HttpServletRequest pedido) {
        String range = pedido.getHeader(HttpHeaders.RANGE);
        String ifRange = pedido.getHeader(HttpHeaders.IF_RANGE);
        if (range == null || (ifRange != null && !ifRange.equals(etag))) {
            return List.of();
        }
        try {
            return HttpRange.parseRanges(range);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
