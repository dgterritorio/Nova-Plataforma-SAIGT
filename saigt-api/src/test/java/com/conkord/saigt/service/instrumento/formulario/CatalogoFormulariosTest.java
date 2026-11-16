package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.persistence.core.TipoProcedimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoFormulariosTest {

    private final CatalogoFormularios catalogo = new CatalogoFormularios();

    @ParameterizedTest
    @ValueSource(strings = {"DECISAO_INICIO", "CONSTITUICAO_CC", "ELABORACAO", "REVISAO",
            "ALTERACAO_SIMPLIFICADA",
            "ALTERACAO_SIMPLIFICADA_DL10", "ALTERACAO_ADAPTACAO", "CORRECAO_MATERIAL",
            "REVOGACAO", "PUBLICACAO_MP", "PRORROGACAO_MP", "PUBLICACAO_NP", "PRORROGACAO_NP", "PUBLICACAO_SUSPENSAO",
            "PRORROGACAO_SUSPENSAO", "ALTERACAO_ATO_ANEXOS", "RETIFICACAO_ATO_ANEXOS", "REVOGACAO_ATO", "PRORROGACAO_ATO",
            "DECLARACAO_NULIDADE_JUDICIAL"})
    void deveDevolverInformacaoGeralParaCadaTipoDeProcedimentoDoSeedExcetoOFinalDaAlteracaoEADiscussaoPublica(String codigo) {
        assertThat(catalogo.formularioDe(new TipoProcedimento(codigo, codigo))).contains(FormularioFixo.INFORMACAO_GERAL);
    }

    @Test
    void deveDevolverOFormularioDaPublicacaoDaAlteracaoSoParaOProcedimentoAlteracao() {
        assertThat(catalogo.formularioDe(new TipoProcedimento("ALTERACAO", "Alteração")))
                .contains(FormularioFixo.PUBLICACAO_ALTERACAO);
    }

    @Test
    void deveDevolverOFormularioSgifrParaOProcedimentoDePublicacaoDoSgifr() {
        assertThat(catalogo.formularioDe(new TipoProcedimento("PUBLICACAO_SGIFR", "Publicação do programa SGIFR")))
                .contains(FormularioFixo.SGIFR);
    }

    @Test
    void deveDevolverOFormularioRenParaOProcedimentoDePublicacaoDaRen() {
        assertThat(catalogo.formularioDe(new TipoProcedimento("PUBLICACAO_REN", "Publicação da REN")))
                .contains(FormularioFixo.REN);
    }

    @Test
    void deveDevolverVazioParaUmTipoDeProcedimentoSemFormulario() {
        assertThat(catalogo.formularioDe(new TipoProcedimento("NOVO_EM_BACKOFFICE", "Novo"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(FormularioFixo.class)
    void deveExigirAConfirmacaoDoConteudoDoProcessoEmCadaFormulario(FormularioFixo formulario) {
        assertThat(catalogo.confirmacoesExigidas(formulario)).containsExactly("CONTEUDO_PROCESSO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABERTURA_DISCUSSAO_PUBLICA", "ABERTURA_DISCUSSAO_PUBLICA_DL10"})
    void deveDevolverOFormularioDaDiscussaoPublicaParaAsDuasAberturas(String codigo) {
        assertThat(catalogo.formularioDe(new TipoProcedimento(codigo, codigo))).contains(FormularioFixo.DISCUSSAO_PUBLICA);
    }

    /** Só os das restantes servidões e da REN admitem. */
    @ParameterizedTest
    @EnumSource(value = FormularioFixo.class, mode = EnumSource.Mode.EXCLUDE, names = {"SRUP", "REN"})
    void nenhumDosOutrosFormulariosAdmiteSoDeposito(FormularioFixo formulario) {
        assertThat(formulario.admiteSoDeposito()).isFalse();
    }

    @Test
    void deveDevolverOFormularioDasRestantesServidoesParaAPublicacaoDaServidaoQueAdmiteSoDeposito() {
        TipoProcedimento publicacaoSrup = new TipoProcedimento("PUBLICACAO_SRUP", "Publicação da servidão");

        assertThat(catalogo.formularioDe(publicacaoSrup)).contains(FormularioFixo.SRUP);
        assertThat(FormularioFixo.SRUP.admiteSoDeposito()).isTrue();
        assertThat(catalogo.admiteSoDeposito(publicacaoSrup)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECISAO_INICIO", "ALTERACAO", "ABERTURA_DISCUSSAO_PUBLICA", "CORRECAO_MATERIAL",
            "NOVO_EM_BACKOFFICE"})
    void oCatalogoSegueOFormularioDoTipoDeProcedimentoESemFormularioNaoAdmite(String codigo) {
        TipoProcedimento tipo = new TipoProcedimento(codigo, codigo);

        assertThat(catalogo.admiteSoDeposito(tipo))
                .isEqualTo(catalogo.formularioDe(tipo).map(FormularioFixo::admiteSoDeposito).orElse(false))
                .isFalse();
    }

    @Test
    void deveDevolverOFormularioDaRatificacaoParaOProcedimentoRatificacaoSemSoDepositoNemElementosAfetados() {
        TipoProcedimento ratificacao = new TipoProcedimento("RATIFICACAO", "Ratificação");

        assertThat(catalogo.formularioDe(ratificacao)).contains(FormularioFixo.RATIFICACAO);
        assertThat(catalogo.admiteSoDeposito(ratificacao)).isFalse();
        assertThat(FormularioFixo.RATIFICACAO.perguntaElementosAfetados()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = FormularioFixo.class, mode = EnumSource.Mode.EXCLUDE, names = {"SRUP", "SGIFR", "RATIFICACAO", "SUSPENSAO_CCDR",
            "SUSPENSAO_MP_GOVERNO", "PRORROGACAO_GOVERNO", "REN"})
    void osFormulariosDaInformacaoGeralPerguntamPelosElementosAfetados(FormularioFixo formulario) {
        assertThat(formulario.perguntaElementosAfetados()).isTrue();
    }

    @Test
    void deveDevolverOFormularioDaSuspensaoDaCcdrComAConcordanciaSemSoDepositoNemElementosAfetados() {
        TipoProcedimento suspensao = new TipoProcedimento("SUSPENSAO_CCDR", "Suspensão (CCDR)");

        assertThat(catalogo.formularioDe(suspensao)).contains(FormularioFixo.SUSPENSAO_CCDR);
        assertThat(catalogo.admiteSoDeposito(suspensao)).isFalse();
        assertThat(catalogo.confirmacoesExigidas(FormularioFixo.SUSPENSAO_CCDR))
                .containsExactly(CatalogoFormularios.CONFIRMACAO_CONTEUDO_PROCESSO);
        assertThat(FormularioFixo.SUSPENSAO_CCDR.perguntaElementosAfetados()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PUBLICACAO_SUSPENSAO_GOVERNO,SUSPENSAO_MP_GOVERNO", "PUBLICACAO_MP_GOVERNO,SUSPENSAO_MP_GOVERNO",
            "PRORROGACAO_SUSPENSAO_GOVERNO,PRORROGACAO_GOVERNO", "PRORROGACAO_MP_GOVERNO,PRORROGACAO_GOVERNO"})
    void deveDevolverOsFormulariosDoGovernoPelaPublicacaoEPelaProrrogacaoSemSoDepositoNemElementosAfetados(
            String codigo, FormularioFixo esperado) {
        TipoProcedimento tipo = new TipoProcedimento(codigo, codigo);

        assertThat(catalogo.formularioDe(tipo)).contains(esperado);
        assertThat(catalogo.admiteSoDeposito(tipo)).isFalse();
        assertThat(catalogo.confirmacoesExigidas(esperado)).containsExactly(CatalogoFormularios.CONFIRMACAO_CONTEUDO_PROCESSO);
        assertThat(esperado.perguntaElementosAfetados()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICACAO_MP", "PRORROGACAO_MP", "PUBLICACAO_SUSPENSAO", "PRORROGACAO_SUSPENSAO"})
    void asDinamicasDoMunicipioContinuamComAInformacaoGeral(String codigo) {
        assertThat(catalogo.formularioDe(new TipoProcedimento(codigo, codigo))).contains(FormularioFixo.INFORMACAO_GERAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABERTURA_DISCUSSAO_PUBLICA_RERAE", "ALTERACAO_RERAE"})
    void naoDeveTerFormularioParaOsProcedimentosDaAlteracaoRerae(String codigo) {
        assertThat(catalogo.formularioDe(new TipoProcedimento(codigo, codigo))).isEmpty();
    }
}
