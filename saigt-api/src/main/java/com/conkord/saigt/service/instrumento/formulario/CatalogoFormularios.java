package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.persistence.core.TipoProcedimento;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;

@Component
public class CatalogoFormularios {

    private static final Map<String, FormularioFixo> FORMULARIO_POR_TIPO_PROCEDIMENTO = Map.ofEntries(
            entry("DECISAO_INICIO", FormularioFixo.INFORMACAO_GERAL),
            entry("CONSTITUICAO_CC", FormularioFixo.INFORMACAO_GERAL),
            entry("ABERTURA_DISCUSSAO_PUBLICA", FormularioFixo.DISCUSSAO_PUBLICA),
            entry("ELABORACAO", FormularioFixo.INFORMACAO_GERAL),
            entry("REVISAO", FormularioFixo.INFORMACAO_GERAL),
            entry("ALTERACAO", FormularioFixo.PUBLICACAO_ALTERACAO),
            entry("ALTERACAO_SIMPLIFICADA", FormularioFixo.INFORMACAO_GERAL),
            entry("ABERTURA_DISCUSSAO_PUBLICA_DL10", FormularioFixo.DISCUSSAO_PUBLICA),
            entry("ALTERACAO_SIMPLIFICADA_DL10", FormularioFixo.INFORMACAO_GERAL),
            entry("ALTERACAO_ADAPTACAO", FormularioFixo.INFORMACAO_GERAL),
            entry("CORRECAO_MATERIAL", FormularioFixo.INFORMACAO_GERAL),
            entry("REVOGACAO", FormularioFixo.INFORMACAO_GERAL),
            entry("PUBLICACAO_MP", FormularioFixo.INFORMACAO_GERAL),
            entry("PRORROGACAO_MP", FormularioFixo.INFORMACAO_GERAL),
            entry("PUBLICACAO_NP", FormularioFixo.INFORMACAO_GERAL),
            entry("PRORROGACAO_NP", FormularioFixo.INFORMACAO_GERAL),
            entry("PUBLICACAO_SUSPENSAO", FormularioFixo.INFORMACAO_GERAL),
            entry("PRORROGACAO_SUSPENSAO", FormularioFixo.INFORMACAO_GERAL),
            entry("ALTERACAO_ATO_ANEXOS", FormularioFixo.INFORMACAO_GERAL),
            entry("RETIFICACAO_ATO_ANEXOS", FormularioFixo.INFORMACAO_GERAL),
            entry("REVOGACAO_ATO", FormularioFixo.INFORMACAO_GERAL),
            entry("PRORROGACAO_ATO", FormularioFixo.INFORMACAO_GERAL),
            entry("DECLARACAO_NULIDADE_JUDICIAL", FormularioFixo.INFORMACAO_GERAL),
            entry("PUBLICACAO_SGIFR", FormularioFixo.SGIFR),
            entry("PUBLICACAO_REN", FormularioFixo.REN),
            entry("PUBLICACAO_SRUP", FormularioFixo.SRUP),
            entry("RATIFICACAO", FormularioFixo.RATIFICACAO),
            entry("SUSPENSAO_CCDR", FormularioFixo.SUSPENSAO_CCDR),
            entry("PUBLICACAO_SUSPENSAO_GOVERNO", FormularioFixo.SUSPENSAO_MP_GOVERNO),
            entry("PUBLICACAO_MP_GOVERNO", FormularioFixo.SUSPENSAO_MP_GOVERNO),
            entry("PRORROGACAO_SUSPENSAO_GOVERNO", FormularioFixo.PRORROGACAO_GOVERNO),
            entry("PRORROGACAO_MP_GOVERNO", FormularioFixo.PRORROGACAO_GOVERNO));

    /** A concordância com o conteúdo do processo é, por agora, a única confirmação definida; a chave é por formulário. */
    public static final String CONFIRMACAO_CONTEUDO_PROCESSO = "CONTEUDO_PROCESSO";

    private static final Map<FormularioFixo, Set<String>> CONFIRMACOES_POR_FORMULARIO = Map.of(
            FormularioFixo.INFORMACAO_GERAL, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.DISCUSSAO_PUBLICA, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.PUBLICACAO_ALTERACAO, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.SGIFR, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.REN, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.SRUP, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.RATIFICACAO, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.SUSPENSAO_CCDR, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.SUSPENSAO_MP_GOVERNO, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO),
            FormularioFixo.PRORROGACAO_GOVERNO, Set.of(CONFIRMACAO_CONTEUDO_PROCESSO));

    public Optional<FormularioFixo> formularioDe(TipoProcedimento tipoProcedimento) {
        return Optional.ofNullable(FORMULARIO_POR_TIPO_PROCEDIMENTO.get(tipoProcedimento.getCodigo()));
    }

    /** Sem formulário, o processo nem se grava nem se submete: não admite. */
    public boolean admiteSoDeposito(TipoProcedimento tipoProcedimento) {
        return formularioDe(tipoProcedimento).map(FormularioFixo::admiteSoDeposito).orElse(false);
    }

    public Set<String> confirmacoesExigidas(FormularioFixo formulario) {
        return CONFIRMACOES_POR_FORMULARIO.getOrDefault(formulario, Set.of());
    }
}
