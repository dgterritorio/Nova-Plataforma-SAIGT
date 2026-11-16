package com.conkord.saigt.controller.core;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.conkord.saigt.exception.DescargaIndisponivelException;
import com.conkord.saigt.exception.DescargasEmExcessoException;
import com.conkord.saigt.exception.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/** Único ponto de tradução de exceções para o envelope de erro (§22.1); só os códigos HTTP de §22.2. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SessaoInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoInvalida(SessaoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(toBody(ex, null));
    }

    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<ErrorResponse> handleValidacao(ValidacaoException ex) {
        List<ErrorResponse.CampoErro> campos = ex.getCampos() == null ? null :
                ex.getCampos().stream().map(c -> new ErrorResponse.CampoErro(c.path(), c.erro())).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(toBody(ex, campos));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(toBody(ex, null));
    }

    @ExceptionHandler(ElaboracaoInexistenteException.class)
    public ResponseEntity<ErrorResponse> handleElaboracaoInexistente(ElaboracaoInexistenteException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(toBody(ex, null));
    }

    @ExceptionHandler(ProcessoDesconhecidoException.class)
    public ResponseEntity<ErrorResponse> handleProcessoDesconhecido(ProcessoDesconhecidoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(toBody(ex, null));
    }

    @ExceptionHandler(AcaoNaoAutorizadaException.class)
    public ResponseEntity<ErrorResponse> handleNaoAutorizado(AcaoNaoAutorizadaException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(toBody(ex, null));
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleEstadoInvalido(EstadoInvalidoException ex) {
        List<ErrorResponse.CampoErro> campos = ex.getCampos() == null ? null :
                ex.getCampos().stream().map(c -> new ErrorResponse.CampoErro(c.path(), c.erro())).toList();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(toBody(ex, campos));
    }

    @ExceptionHandler(ReferenciaDuplicadaException.class)
    public ResponseEntity<ErrorResponse> handleReferenciaDuplicada(ReferenciaDuplicadaException ex) {
        // Quando a colisão é de um campo (a designação, o email do perfil), indica-o para o formulário o assinalar.
        List<ErrorResponse.CampoErro> campos = ex.getCampo() != null
                ? List.of(new ErrorResponse.CampoErro(ex.getCampo(), ex.getMessage())) : null;
        return ResponseEntity.status(HttpStatus.CONFLICT).body(toBody(ex, campos));
    }

    @ExceptionHandler(IncmIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleIncmIndisponivel(IncmIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(toBody(ex, null));
    }

    @ExceptionHandler(SnitIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleSnitIndisponivel(SnitIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(toBody(ex, null));
    }

    @ExceptionHandler(DescargasEmExcessoException.class)
    public ResponseEntity<ErrorResponse> handleDescargasEmExcesso(DescargasEmExcessoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(toBody(ex, null));
    }

    @ExceptionHandler(DescargaIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleDescargaIndisponivel(DescargaIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRepetirApos().toSeconds()))
                .body(toBody(ex, null));
    }

    @ExceptionHandler(CopiaDepositoIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleCopiaDepositoIndisponivel(CopiaDepositoIndisponivelException ex) {
        ErrorResponse body = toBody(ex, null);
        log.error("Storage indisponível na cópia para depósito — traceId={}", body.traceId(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(StorageIndisponivelException.class)
    public ResponseEntity<ErrorResponse> handleStorageIndisponivel(StorageIndisponivelException ex) {
        ErrorResponse body = toBody(ex, null);
        log.error("Storage indisponível — traceId={}", body.traceId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleArgumentoInvalido(MethodArgumentNotValidException ex) {
        List<ErrorResponse.CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.CampoErro(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorResponse body = new ErrorResponse(ValidacaoException.CODIGO, "Payload inválido.",
                OffsetDateTime.now(ZoneOffset.UTC), traceId(), campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Corpo que o Jackson não consegue ler (JSON mal formado, data inválida, tipo errado); com caminho, assinala o campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        List<ErrorResponse.CampoErro> campos = null;
        if (ex.getCause() instanceof JsonMappingException mapeamento && !mapeamento.getPath().isEmpty()) {
            campos = List.of(new ErrorResponse.CampoErro(caminho(mapeamento), "Valor inválido."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ValidacaoException.CODIGO,
                "Payload inválido.", OffsetDateTime.now(ZoneOffset.UTC), traceId(), campos));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ValidacaoException.CODIGO,
                "Parâmetros inválidos.", OffsetDateTime.now(ZoneOffset.UTC), traceId(),
                List.of(new ErrorResponse.CampoErro(ex.getName(), "Valor inválido."))));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleParteEmFalta(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ValidacaoException.CODIGO,
                "Payload inválido.", OffsetDateTime.now(ZoneOffset.UTC), traceId(),
                List.of(new ErrorResponse.CampoErro(ex.getRequestPartName(), "Obrigatório."))));
    }

    /** Rota inexistente (ou retirada): sem este handler cairia em `handleGenerica` e daria 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRotaInexistente(NoResourceFoundException ex) {
        return recursoNaoEncontrado();
    }

    /** Método retirado num caminho que existe (ex.: `DELETE /utilizadores/{id}`): 404, porque o contrato não usa o 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return recursoNaoEncontrado();
    }

    private ResponseEntity<ErrorResponse> recursoNaoEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(toBody(new RecursoNaoEncontradoException("Recurso não encontrado."), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenerica(Exception ex) {
        // Nunca expor detalhe interno na resposta; detalhe só em log.
        String traceId = traceId();
        log.error("Erro não tratado — traceId={}", traceId, ex);
        ErrorResponse body = new ErrorResponse("ERRO_INTERNO", "Ocorreu um erro interno.",
                OffsetDateTime.now(ZoneOffset.UTC), traceId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ErrorResponse toBody(ConkordException ex, List<ErrorResponse.CampoErro> campos) {
        return new ErrorResponse(ex.getCodigo(), ex.getMessage(), OffsetDateTime.now(ZoneOffset.UTC), traceId(), campos);
    }

    private static String caminho(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? "." + ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining())
                .replaceFirst("^\\.", "");
    }

    private String traceId() {
        String id = MDC.get("apiTraceId");
        return id != null ? id : "unknown";
    }
}
