package com.conkord.saigt.controller.snit;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.conkord.saigt.dto.snit.InfoEntradaVigorRequest;
import com.conkord.saigt.dto.snit.InfoEntradaVigorResponse;
import com.conkord.saigt.exception.EntradaVigorSnitException;
import com.conkord.saigt.service.idus.EntradaEmVigorRegistada;
import com.conkord.saigt.service.idus.EntradaVigorService;
import com.conkord.saigt.service.idus.PropagacaoIdusService;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * `infoEntradaVigor` (contrato SAIGT para SNIT v1.1). Só o cliente SNIT lhe chega (`SecurityConfig`); os erros usam os
 * códigos desse contrato e não os da API da SAIGT.
 */
@RestController
public class EntradaVigorSnitController {

    private static final String VALIDACAO_FALHOU = "VALIDACAO_FALHOU";

    private final EntradaVigorService entradaVigorService;
    private final PropagacaoIdusService propagacaoIdusService;

    public EntradaVigorSnitController(EntradaVigorService entradaVigorService,
                                      PropagacaoIdusService propagacaoIdusService) {
        this.entradaVigorService = entradaVigorService;
        this.propagacaoIdusService = propagacaoIdusService;
    }

    @PostMapping("/api/v1/integracoes/snit/dinamicas/vigencia")
    public ResponseEntity<InfoEntradaVigorResponse> infoEntradaVigor(@Valid @RequestBody InfoEntradaVigorRequest pedido) {
        // `registar` confirma a transação ao sair: a propagação corre depois, fora dela.
        EntradaEmVigorRegistada registo = entradaVigorService.registar(pedido);
        propagacaoIdusService.propagar(registo);
        return ResponseEntity.status(201).body(InfoEntradaVigorResponse.recebido(registo.numeroDeposito()));
    }

    @ExceptionHandler(EntradaVigorSnitException.class)
    public ResponseEntity<ErrorResponse> recusado(EntradaVigorSnitException ex) {
        List<ErrorResponse.CampoErro> campos = ex.getCampos() == null ? null
                : ex.getCampos().stream().map(c -> new ErrorResponse.CampoErro(c.path(), c.erro())).toList();
        return ResponseEntity.status(ex.getStatus()).body(erro(ex.getCodigo(), ex.getMessage(), campos));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalido(MethodArgumentNotValidException ex) {
        List<ErrorResponse.CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.CampoErro(fe.getField(),
                        fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(erro(VALIDACAO_FALHOU, "Payload inválido.", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> ilegivel(HttpMessageNotReadableException ex) {
        List<ErrorResponse.CampoErro> campos = null;
        if (ex.getCause() instanceof JsonMappingException mapeamento && !mapeamento.getPath().isEmpty()) {
            String campo = mapeamento.getPath().get(mapeamento.getPath().size() - 1).getFieldName();
            campos = List.of(new ErrorResponse.CampoErro(campo, "Formato inválido."));
        }
        return ResponseEntity.badRequest().body(erro(VALIDACAO_FALHOU, "Payload inválido.", campos));
    }

    private static ErrorResponse erro(String codigo, String mensagem, List<ErrorResponse.CampoErro> campos) {
        return new ErrorResponse(codigo, mensagem, OffsetDateTime.now(ZoneOffset.UTC), MDC.get("apiTraceId"), campos);
    }
}
