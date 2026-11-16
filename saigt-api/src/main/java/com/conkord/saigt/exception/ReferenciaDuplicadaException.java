package com.conkord.saigt.exception;

/** Referência de Instrumento ou designação de Dinâmica já existente. Falha → 409, nunca 500. */
public class ReferenciaDuplicadaException extends ConkordException {
    private static final long serialVersionUID = 1L;

    public static final String DESIGNACAO_DUPLICADA = "DESIGNACAO_DUPLICADA";

    // Campo do pedido a assinalar no formulário; nulo quando a colisão não é de um só campo.
    private final String campo;

    public ReferenciaDuplicadaException(String mensagem) {
        this("REFERENCIA_DUPLICADA", mensagem, null);
    }

    private ReferenciaDuplicadaException(String codigo, String mensagem, String campo) {
        super(codigo, mensagem);
        this.campo = campo;
    }

    public static ReferenciaDuplicadaException designacao(String mensagem) {
        return designacao(mensagem, "designacao");
    }

    public static ReferenciaDuplicadaException designacao(String mensagem, String campo) {
        return new ReferenciaDuplicadaException(DESIGNACAO_DUPLICADA, mensagem, campo);
    }

    public static ReferenciaDuplicadaException noCampo(String campo, String mensagem) {
        return new ReferenciaDuplicadaException("REFERENCIA_DUPLICADA", mensagem, campo);
    }

    public String getCampo() { return campo; }
}
