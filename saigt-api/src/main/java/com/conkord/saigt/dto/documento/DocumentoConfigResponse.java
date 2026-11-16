package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.service.core.ExtensoesAceites;

import java.util.List;

/** `regrasTiff` só numa peça gráfica: as gravadas ou, com a coluna vazia, as iniciais gerais. */
public record DocumentoConfigResponse(Long id, Long instrumentoProcedimentoConfigId, String codigo, String nome,
                                      String separador, String descricao, CategoriaDocumento categoria,
                                      TipoElemento tipoElemento, FormatoDocumento formato,
                                      List<String> extensoesAceites, boolean obrigatorio, int ordem,
                                      ElementoInformacaoBase elementoInformacaoBase, Long tamanhoMaximoFicheiro,
                                      Long tamanhoMaximoTotal, Integer numeroMaximoFicheiros,
                                      boolean linkPublicoPermitido, boolean ativo,
                                      RegrasTiff regrasTiff, RegraNomenclatura regraNomenclatura) {

    public static DocumentoConfigResponse de(DocumentoConfig item) {
        return new DocumentoConfigResponse(item.getId(), item.getInstrumentoProcedimentoConfig().getId(),
                item.getCodigo(), item.getNome(), item.getSeparador(), item.getDescricao(), item.getCategoria(),
                item.getTipoElemento(), item.getFormato(), ExtensoesAceites.ler(item), item.isObrigatorio(),
                item.getOrdem(), item.getElementoInformacaoBase(), item.getTamanhoMaximoFicheiro(),
                item.getTamanhoMaximoTotal(), item.getNumeroMaximoFicheiros(), item.isLinkPublicoPermitido(),
                item.isAtivo(), item.regrasTiffEfetivas().orElse(null),
                item.getRegraNomenclatura());
    }
}
