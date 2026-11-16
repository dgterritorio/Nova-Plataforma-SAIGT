package com.conkord.saigt.exception;

import java.util.List;

/** Payload inválido. Falha → 400. */
public class ValidacaoException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "VALIDACAO";

    private final transient List<CampoErro> campos;

    public ValidacaoException(String mensagem, List<CampoErro> campos) {
        this(CODIGO, mensagem, campos);
    }

    protected ValidacaoException(String codigo, String mensagem, List<CampoErro> campos) {
        super(codigo, mensagem);
        this.campos = campos;
    }

    public List<CampoErro> getCampos() { return campos; }

    public record CampoErro(String path, String erro) {}
}
