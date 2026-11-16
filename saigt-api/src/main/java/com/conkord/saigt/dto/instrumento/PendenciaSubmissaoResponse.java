package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.exception.ValidacaoException.CampoErro;

/**
 * Uma pendência que impede `submeter`, com a página do assistente Criar Processo onde se corrige. `campo` e `erro`
 * são os que a submissão devolveria.
 */
public record PendenciaSubmissaoResponse(Pagina pagina, String campo, String erro) {

    /** Pela ordem das páginas do assistente: o Resumo agrupa-as por esta ordem. */
    public enum Pagina {
        FORMULARIO, PLANTAS, DOCUMENTOS, METADADOS
    }

    public static PendenciaSubmissaoResponse de(Pagina pagina, CampoErro campo) {
        return new PendenciaSubmissaoResponse(pagina, campo.path(), campo.erro());
    }
}
