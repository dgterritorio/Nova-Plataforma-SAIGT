package com.conkord.saigt.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Recusas do `infoEntradaVigor` com os códigos do contrato SAIGT para SNIT (`VALIDACAO_FALHOU`,
 * `PROCESSO_DESCONHECIDO`) e o 409 da repetição com outros dados (OI-412).
 */
public class EntradaVigorSnitException extends ConkordException {
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final transient List<ValidacaoException.CampoErro> campos;

    private EntradaVigorSnitException(HttpStatus status, String codigo, String mensagem,
                                      List<ValidacaoException.CampoErro> campos) {
        super(codigo, mensagem);
        this.status = status;
        this.campos = campos;
    }

    public static EntradaVigorSnitException validacao(String mensagem, List<ValidacaoException.CampoErro> campos) {
        return new EntradaVigorSnitException(HttpStatus.BAD_REQUEST, "VALIDACAO_FALHOU", mensagem, campos);
    }

    public static EntradaVigorSnitException processoDesconhecido(String mensagem) {
        return new EntradaVigorSnitException(HttpStatus.NOT_FOUND, "PROCESSO_DESCONHECIDO", mensagem, null);
    }

    public static EntradaVigorSnitException conflito(String mensagem) {
        return new EntradaVigorSnitException(HttpStatus.CONFLICT, "ENTRADA_EM_VIGOR_EM_CONFLITO", mensagem, null);
    }

    public HttpStatus getStatus() { return status; }

    public List<ValidacaoException.CampoErro> getCampos() { return campos; }
}
