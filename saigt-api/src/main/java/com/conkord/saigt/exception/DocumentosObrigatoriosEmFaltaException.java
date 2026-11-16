package com.conkord.saigt.exception;

import java.util.List;

/** Submissão sem os documentos obrigatórios do Procedimento. Falha → 400. */
public class DocumentosObrigatoriosEmFaltaException extends ValidacaoException {
    private static final long serialVersionUID = 1L;

    public DocumentosObrigatoriosEmFaltaException(String mensagem, List<CampoErro> campos) {
        super("DOCUMENTOS_OBRIGATORIOS_EM_FALTA", mensagem, campos);
    }
}
