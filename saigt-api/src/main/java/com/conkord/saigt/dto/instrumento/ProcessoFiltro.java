package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.SituacaoPcm;
import com.conkord.saigt.service.instrumento.GrupoProcesso;

import java.time.LocalDate;
import java.util.List;

/**
 * `entidadeId` só conta para perfis internos; um utilizador externo fica sempre na entidade da sua sessão.
 * `estado` aceita vários valores (`?estado=X&estado=Y`) e, com `grupo`, combina por interseção.
 * `ano` é o do `criado_em` em `Europe/Lisbon`.
 * `concelho` e `nutsII` olham para os concelhos do Instrumento; a NUTS II resolve-se pela réplica do SNIT.
 * `motivosAlteracao` (algum dos motivos do formulário) e `situacoesPcm` só contam para perfis internos, como `entidadeId`.
 */
public record ProcessoFiltro(
        String designacaoInstrumento,
        String numeroInstrumento,
        List<String> estado,
        Long tipoInstrumentoId,
        Long entidadeId,
        String numeroProcesso,
        Integer ano,
        GrupoProcesso grupo,
        LocalDate dataDe,
        LocalDate dataAte,
        String numeroDr,
        String incmActIdentifier,
        String concelho,
        String nutsII,
        List<MotivoAlteracaoRequest> motivosAlteracao,
        List<SituacaoPcm> situacoesPcm
) {

    public ProcessoFiltro(String designacaoInstrumento, String numeroInstrumento, List<String> estado,
                          Long tipoInstrumentoId, Long entidadeId, String numeroProcesso, Integer ano, GrupoProcesso grupo,
                          LocalDate dataDe, LocalDate dataAte, String numeroDr, String incmActIdentifier, String concelho,
                          String nutsII) {
        this(designacaoInstrumento, numeroInstrumento, estado, tipoInstrumentoId, entidadeId, numeroProcesso, ano, grupo,
                dataDe, dataAte, numeroDr, incmActIdentifier, concelho, nutsII, null, null);
    }

    public ProcessoFiltro(String designacaoInstrumento, String numeroInstrumento, List<String> estado,
                          Long tipoInstrumentoId, Long entidadeId, String numeroProcesso, Integer ano, GrupoProcesso grupo,
                          LocalDate dataDe, LocalDate dataAte, String numeroDr, String incmActIdentifier, String concelho,
                          String nutsII, List<MotivoAlteracaoRequest> motivosAlteracao) {
        this(designacaoInstrumento, numeroInstrumento, estado, tipoInstrumentoId, entidadeId, numeroProcesso, ano, grupo,
                dataDe, dataAte, numeroDr, incmActIdentifier, concelho, nutsII, motivosAlteracao, null);
    }
}
