package com.conkord.saigt.exception;

import java.util.List;

/** Estado do processo não permite a ação. Falha → 409. */
public class EstadoInvalidoException extends ConkordException {
    private static final long serialVersionUID = 1L;

    private final transient List<ValidacaoException.CampoErro> campos;

    public EstadoInvalidoException(String mensagem) {
        super("ESTADO_INVALIDO", mensagem);
        this.campos = null;
    }

    protected EstadoInvalidoException(String codigo, String mensagem) {
        this(codigo, mensagem, null);
    }

    protected EstadoInvalidoException(String codigo, String mensagem, List<ValidacaoException.CampoErro> campos) {
        super(codigo, mensagem);
        this.campos = campos;
    }

    /** Nula na maioria dos 409; só as recusas por campo a preenchem. */
    public List<ValidacaoException.CampoErro> getCampos() { return campos; }
}
