package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TabelaExtensoesEf {

    private static final Map<TipoElemento, Map<CategoriaDocumento, Set<String>>> TABELA = Map.of(
            TipoElemento.PECA_ESCRITA, Map.of(
                    CategoriaDocumento.PUBLICACAO,
                    Set.of("doc", "docx", "rtf", "odf", "odt", "xls", "xlsx", "csv", "ods", "pdf", "txt"),
                    CategoriaDocumento.DEPOSITO, Set.of("pdf", "jpg", "png")),
            TipoElemento.PECA_GRAFICA, Map.of(
                    CategoriaDocumento.PUBLICACAO, Set.of("zip"),
                    CategoriaDocumento.DEPOSITO, Set.of("zip")));

    private TabelaExtensoesEf() {
    }

    /** Vazio quando o tipo de elemento não tem linha na tabela. */
    public static Optional<Set<String>> extensoes(TipoElemento tipoElemento, CategoriaDocumento categoria) {
        return Optional.ofNullable(TABELA.get(tipoElemento)).map(colunas -> colunas.get(categoria));
    }
}
