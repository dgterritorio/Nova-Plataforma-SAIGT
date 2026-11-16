package com.conkord.saigt.configuration;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Envelope ErrorResponse para falhas da cadeia de segurança, que nunca chegam ao @RestControllerAdvice. */
final class EnvelopeErrorWriter {

    private EnvelopeErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String codigo, String mensagem)
            throws IOException {
        String traceId = MDC.get("apiTraceId");
        ErrorResponse body = new ErrorResponse(codigo, mensagem, OffsetDateTime.now(ZoneOffset.UTC),
                traceId != null ? traceId : "unknown", null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Sem isto o writer do servlet usa ISO-8859-1 e as mensagens acentuadas chegam corrompidas.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
