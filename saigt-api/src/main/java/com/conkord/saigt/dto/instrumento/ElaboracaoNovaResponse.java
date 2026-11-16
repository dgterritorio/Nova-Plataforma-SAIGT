package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;

import java.util.List;

/**
 * `concelhoEntidade` é o concelho da câmara da sessão, para pré-preencher; nulo nas outras entidades.
 * `pedeLocalizacao` é o do primeiro Procedimento: o formulário abre antes de o Processo existir.
 */
public record ElaboracaoNovaResponse(
        Long instrumentoDinamicaConfigId,
        String nomeDinamica,
        ProcedimentoDisponivelResponse procedimento,
        boolean exigeOpcaoGeoconform,
        String pedeLocalizacao,
        List<ItemTerritorio> nutsII,
        List<ConcelhoElaboracao> concelhos,
        String concelhoEntidade,
        AmbitoTerritorial ambitoTerritorial
) {

    /** `nutIICodigo` é nulo quando a réplica do SNIT não dá a NUT II do concelho. */
    public record ConcelhoElaboracao(String codigo, String nome, String nutIICodigo) {
    }
}
