package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidadorInformacaoBaseTest {

    private static final LocalDate DATA_TEXTO_ATO = LocalDate.of(2027, 3, 31);
    private static final LocalDate DATA_LIMITE = LocalDate.of(2027, 3, 31);
    private static final String CAMPO_PRAZO = "informacaoBase.textoAto.prazoAnos";
    private static final String CAMPO_AVALIACAO = "informacaoBase.textoAto.avaliacaoAmbiental";
    private static final String CAMPO_DATA = "informacaoBase.deliberacao.dataDeliberacao";
    private static final String MSG_PRAZO = "O prazo tem de ser o mesmo da Deliberação.";
    private static final String MSG_AVALIACAO = "A avaliação ambiental tem de ser a mesma da Deliberação.";
    private static final String MSG_DATA = "A data da deliberação tem de ser igual ou anterior à data do texto do ato.";
    private static final String MSG_DATA_LIMITE = "A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.";
    private static final Set<ElementoInformacaoBase> OS_DOIS =
            Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);

    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final NumeroDepositoService numeroDepositoService = mock(NumeroDepositoService.class);
    private final ValidadorSubmissaoProcesso validador = new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class),
            new ObjectMapper(), new CatalogoFormularios(), null, null, documentoConfigRepository, numeroDepositoService);

    private Processo processo(String informacaoBase, Set<ElementoInformacaoBase> previstos, LocalDate dataLimite,
                              boolean procedimentoFinal) {
        Processo processo = mock(Processo.class);
        Dinamica dinamica = mock(Dinamica.class);
        InstrumentoProcedimentoConfig config = mock(InstrumentoProcedimentoConfig.class);
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(config);
        when(processo.getDataAssinatura()).thenReturn(DATA_TEXTO_ATO);
        when(processo.getDadosFormulario()).thenReturn(informacaoBase == null ? null : "{\"informacaoBase\":{" + informacaoBase + "}}");
        when(dinamica.getDataCaducidade()).thenReturn(dataLimite);
        when(documentoConfigRepository.findElementosInformacaoBaseAtivos(config)).thenReturn(previstos);
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(procedimentoFinal);
        return processo;
    }

    private Processo comOsDois(String informacaoBase) {
        return processo(informacaoBase, OS_DOIS, null, false);
    }

    private static String ib(String deliberacao, String textoAto) {
        return "\"deliberacao\":{" + deliberacao + "},\"textoAto\":{" + textoAto + "}";
    }

    @Test
    void oPrazoDiferenteERecusadoNoPrazoDoTextoDoAto() {
        Processo processo = comOsDois(ib("\"prazoAnos\":1", "\"prazoAnos\":2"));

        assertThatThrownBy(() -> validador.verificarInformacaoBase(processo, FormularioFixo.DISCUSSAO_PUBLICA))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getMessage()).isEqualTo(MSG_PRAZO);
                    assertThat(e.getCampos()).containsExactly(new CampoErro(CAMPO_PRAZO, MSG_PRAZO));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"prazoAnos\":1,\"prazoMeses\":2,\"prazoDias\":3|\"prazoAnos\":1,\"prazoMeses\":2,\"prazoDias\":3",
            "\"prazoAnos\":1|\"prazoAnos\":1,\"prazoMeses\":0,\"prazoDias\":0",
            "\"prazoAnos\":1,\"prazoMeses\":null|\"prazoAnos\":1,\"prazoDias\":0"})
    void osPrazosIguaisCampoACampoComNuloComoZeroPassam(String lados) {
        String[] partes = lados.split("\\|");

        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(ib(partes[0], partes[1])))).isEmpty();
    }

    @Test
    void umAnoNaoEDozeMeses() {
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(ib("\"prazoAnos\":1", "\"prazoMeses\":12"))))
                .containsExactly(new CampoErro(CAMPO_PRAZO, MSG_PRAZO));
    }

    @Test
    void aAvaliacaoAmbientalDiferenteERecusadaNaAvaliacaoDoTextoDoAto() {
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(
                ib("\"avaliacaoAmbiental\":true", "\"avaliacaoAmbiental\":false"))))
                .containsExactly(new CampoErro(CAMPO_AVALIACAO, MSG_AVALIACAO));
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(
                ib("\"avaliacaoAmbiental\":false", "\"avaliacaoAmbiental\":false")))).isEmpty();
    }

    @Test
    void aDataDaDeliberacaoPosteriorADoTextoDoAtoERecusadaEAIgualPassa() {
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(ib("\"dataDeliberacao\":\"2027-04-01\"", ""))))
                .containsExactly(new CampoErro(CAMPO_DATA, MSG_DATA));
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(ib("\"dataDeliberacao\":\"2027-03-31\"", ""))))
                .isEmpty();
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(ib("\"dataDeliberacao\":\"2027-03-01\"", ""))))
                .isEmpty();
    }

    @Test
    void variosDesacordosSaemNumaSoRecusaComTodosOsCampos() {
        Processo processo = comOsDois(ib("\"prazoAnos\":1,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2027-04-01\"",
                "\"prazoAnos\":2,\"avaliacaoAmbiental\":false"));

        assertThatThrownBy(() -> validador.verificarInformacaoBase(processo, FormularioFixo.INFORMACAO_GERAL))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("A Informação Base tem campos incoerentes.");
                    assertThat(e.getCampos()).extracting(CampoErro::path)
                            .containsExactly(CAMPO_PRAZO, CAMPO_AVALIACAO, CAMPO_DATA);
                });
    }

    @Test
    void umLadoEmBrancoNaoEComparado() {
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(
                "\"deliberacao\":null,\"textoAto\":{\"prazoAnos\":2,\"avaliacaoAmbiental\":false}"))).isEmpty();
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(
                "\"deliberacao\":{\"prazoAnos\":1,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2030-01-01\"}")))
                .containsExactly(new CampoErro(CAMPO_DATA, MSG_DATA));
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(
                ib("\"prazoAnos\":1,\"avaliacaoAmbiental\":null", "\"prazoMeses\":null,\"avaliacaoAmbiental\":false")))).isEmpty();
        assertThat(validador.errosCoerenciaInformacaoBase(comOsDois(null))).isEmpty();
    }

    @Test
    void semDataDoTextoDoAtoADataDaDeliberacaoNaoEComparada() {
        Processo processo = comOsDois(ib("\"dataDeliberacao\":\"2030-01-01\"", ""));
        when(processo.getDataAssinatura()).thenReturn(null);

        assertThat(validador.errosCoerenciaInformacaoBase(processo)).isEmpty();
    }

    @Test
    void soComOTextoDoAtoPrevistoNaoHaCoerenciaAVerificar() {
        Processo processo = processo(ib("\"prazoAnos\":1,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2030-01-01\"",
                "\"prazoAnos\":2,\"avaliacaoAmbiental\":false"), Set.of(ElementoInformacaoBase.TEXTO_ATO), null, false);

        assertThatCode(() -> validador.verificarInformacaoBase(processo, FormularioFixo.DISCUSSAO_PUBLICA))
                .doesNotThrowAnyException();
    }

    @Test
    void oTextoAPublicarNaoTemValidacoesConfiguradas() {
        Processo processo = comOsDois(ib("\"prazoAnos\":1,\"textoPublicar\":\"Sem o prazo nem a data no texto.\"",
                "\"prazoAnos\":1"));

        assertThatCode(() -> validador.verificarInformacaoBase(processo, FormularioFixo.INFORMACAO_GERAL))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = FormularioFixo.class, names = {"INFORMACAO_GERAL", "PUBLICACAO_ALTERACAO"})
    void noProcedimentoFinalADataDaDeliberacaoDepoisDaDataLimiteERecusadaComADataLimite(FormularioFixo formulario) {
        Processo processo = processo(ib("\"dataDeliberacao\":\"2027-04-01\"", ""), Set.of(), DATA_LIMITE, true);

        assertThatThrownBy(() -> validador.verificarInformacaoBase(processo, formulario))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(MSG_DATA_LIMITE);
                    assertThat(e.getCampos()).containsExactly(
                            new CampoErro(CAMPO_DATA, "Posterior à data limite da dinâmica (31/03/2027)."));
                });
    }

    @Test
    void noProcedimentoFinalADataIgualADataLimitePassa() {
        Processo processo = processo(ib("\"dataDeliberacao\":\"2027-03-31\"", ""), OS_DOIS, DATA_LIMITE, true);

        assertThatCode(() -> validador.verificarInformacaoBase(processo, FormularioFixo.INFORMACAO_GERAL))
                .doesNotThrowAnyException();
    }

    @Test
    void noProcedimentoFinalSemDataLimiteOuSemDataDaDeliberacaoARegraNaoSeAplica() {
        Processo semDataLimite = processo(ib("\"dataDeliberacao\":\"2027-04-01\"", ""), Set.of(), null, true);
        Processo semDataDeliberacao = processo(ib("\"prazoAnos\":1", ""), Set.of(), DATA_LIMITE, true);

        assertThatCode(() -> validador.verificarInformacaoBase(semDataLimite, FormularioFixo.INFORMACAO_GERAL))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarInformacaoBase(semDataDeliberacao, FormularioFixo.INFORMACAO_GERAL))
                .doesNotThrowAnyException();
    }

    @Test
    void foraDoProcedimentoFinalComoNaDecisaoDeInicioADataLimiteNaoSeAplica() {
        Processo processo = processo(ib("\"dataDeliberacao\":\"2027-04-01\"", ""), Set.of(), DATA_LIMITE, false);

        assertThatCode(() -> validador.verificarInformacaoBase(processo, FormularioFixo.INFORMACAO_GERAL))
                .doesNotThrowAnyException();
    }

    @Test
    void naDiscussaoPublicaADataLimiteContinuaAAplicarSeSemPerguntarPeloProcedimentoFinal() {
        Processo processo = processo(ib("\"dataDeliberacao\":\"2027-04-01\"", ""), Set.of(), DATA_LIMITE, false);

        assertThatThrownBy(() -> validador.verificarInformacaoBase(processo, FormularioFixo.DISCUSSAO_PUBLICA))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(MSG_DATA_LIMITE);
                    assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly(CAMPO_DATA);
                });
        verify(numeroDepositoService, never()).doProcedimentoFinal(processo);
    }

    @ParameterizedTest
    @EnumSource(value = FormularioFixo.class, names = {"SRUP", "RATIFICACAO", "SUSPENSAO_CCDR"})
    void osFormulariosSemProcedimentoFinalDaDinamicaNaoVerificamADataLimite(FormularioFixo formulario) {
        Processo processo = processo(ib("\"dataDeliberacao\":\"2027-04-01\"", ""), Set.of(), DATA_LIMITE, true);

        assertThatCode(() -> validador.verificarInformacaoBase(processo, formulario)).doesNotThrowAnyException();
    }
}
