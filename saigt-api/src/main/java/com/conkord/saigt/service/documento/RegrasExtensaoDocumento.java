package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ConfigExtensaoNaoAceiteException;
import com.conkord.saigt.exception.ExtensaoNaoAceiteException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.service.core.ExtensoesAceites;

import java.util.Set;

public final class RegrasExtensaoDocumento {

    public static final String MSG_ITEM = "Extensão não aceite para este documento.";
    public static final String MSG_PUBLICACAO = "Extensão não aceite para publicação.";

    private RegrasExtensaoDocumento() {
    }

    public static void validarFicheiro(String nomeOriginal, DocumentoConfig documentoConfig) {
        String extensao = CaminhoDocumento.extensao(nomeOriginal);
        if (!ExtensoesAceites.ler(documentoConfig).contains(extensao)) {
            throw new ExtensaoNaoAceiteException(MSG_ITEM);
        }
        if (documentoConfig.getCategoria() == CategoriaDocumento.PUBLICACAO
                && !TabelaExtensoesEf.extensoes(documentoConfig.getTipoElemento(), CategoriaDocumento.PUBLICACAO)
                        .map(aceites -> aceites.contains(extensao))
                        .orElse(true)) {
            throw new ExtensaoNaoAceiteException(MSG_PUBLICACAO);
        }
    }

    public static void validarConfig(DocumentoConfig documentoConfig) {
        Set<String> coluna = TabelaExtensoesEf.extensoes(documentoConfig.getTipoElemento(), documentoConfig.getCategoria())
                .orElse(null);
        if (coluna != null && !coluna.containsAll(ExtensoesAceites.ler(documentoConfig))) {
            throw new ConfigExtensaoNaoAceiteException();
        }
    }
}
