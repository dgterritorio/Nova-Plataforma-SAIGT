package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ConfirmacaoEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.PerguntaGeoconform;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.core.ClaimsUtilizador.AtoAutorizado;
import com.conkord.saigt.service.core.ClaimsUtilizador.AtosAutorizados;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ValidadorSubmissaoProcessoTest {

    private static final long ID_TIPO_ATO = 701L;
    private static final String MENSAGEM_EXTRATO = "O tipo de ato não permite publicar extrato.";

    private final ValidadorSubmissaoProcesso validador = new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper());

    private static ContextoUtilizador sessao(AtosAutorizados atosAutorizados, String incmEnrollmentId, Long drParteId) {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "501234567", "ENTIDADE", atosAutorizados, null,
                incmEnrollmentId, drParteId, TipoEntidade.CM, null);
    }

    private static ContextoUtilizador sessaoCompleta() {
        return sessao(null, "2015482", 1234567891234567L);
    }

    private static AtosAutorizados atosComTipo(boolean extrato) {
        return new AtosAutorizados(List.of(), List.of(new AtoAutorizado(ID_TIPO_ATO, null, extrato)));
    }

    private static Processo processo(String serieDr) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(null,
                new TipoProcedimento("DECISAO", "Decisão"), 1, serieDr);
        Processo processo = new Processo(null, config, null, null);
        processo.setTipoAto(new TipoAto("AVISO", "Aviso", 1));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.of(2026, 9, 1));
        return processo;
    }

    private static Processo processoDeSegundaSerie() {
        Processo processo = processo("SEGUNDA_H");
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        TipoAto tipoAto = new TipoAto("COD-ATO", "Ato de Teste", 1);
        tipoAto.setIncmActTypeIdSerie2(ID_TIPO_ATO);
        processo.setTipoAto(tipoAto);
        return processo;
    }

    private static Processo processoDePrimeiraSerie() {
        Processo processo = processo("PRIMEIRA");
        processo.setPublicarSuplemento(false);
        return processo;
    }

    private static Processo processoDeInstrumento(String categoria, List<String> elementosAfetados) {
        return processoDeInstrumento(categoria, "ALTERACAO", elementosAfetados);
    }

    private static Processo processoDeInstrumento(String categoria, String tipoDinamica, List<String> elementosAfetados) {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("TI-" + categoria, "Instrumento", categoria);
        Instrumento instrumento = new Instrumento(tipoInstrumento, "1", "Instrumento de teste", null, null);
        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(tipoInstrumento,
                new TipoDinamica(tipoDinamica, "Dinâmica " + tipoDinamica));
        Processo processo = new Processo(new Dinamica(instrumento, dinamicaConfig, "Dinâmica de teste", null), null, null, null);
        processo.setElementosAfetados(elementosAfetados);
        return processo;
    }

    private List<String> caminhos(Processo processo, ContextoUtilizador contexto) {
        return validador.pendencias(processo, contexto).stream().map(CampoErro::path).toList();
    }

    @Test
    void naoDeveTerPendenciasQuandoOsDadosDeSegundaSerieEstaoCompletos() {
        assertThat(validador.pendencias(processoDeSegundaSerie(), sessaoCompleta())).isEmpty();
    }

    @Test
    void naoDeveTerPendenciasQuandoOsDadosDePrimeiraSerieEstaoCompletos() {
        assertThat(validador.pendencias(processoDePrimeiraSerie(), sessao(null, "2015482", null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEGUNDA_H", "SEGUNDA_C", "PRIMEIRA"})
    void deveExigirSempreSumarioDataDeAssinaturaEIncmEnrollmentId(String serieDr) {
        Processo processo = processo(serieDr);
        processo.setSumario("  ");
        processo.setDataAssinatura(null);

        assertThat(caminhos(processo, sessao(null, null, 1234567891234567L)))
                .contains("sumario", "dataAssinatura", "incmEnrollmentId");
    }

    private static DocumentoConfig itemDocumental(PaginaDocumento pagina, ElementoInformacaoBase elemento,
                                                  CategoriaDocumento categoria) {
        DocumentoConfig item = mock(DocumentoConfig.class);
        when(item.getPagina()).thenReturn(pagina);
        when(item.getElementoInformacaoBase()).thenReturn(elemento);
        when(item.getCategoria()).thenReturn(categoria);
        return item;
    }

    private static ValidadorSubmissaoProcesso validadorComItens(Processo processo, DocumentoConfig... itens) {
        DocumentoConfigRepository repositorio = mock(DocumentoConfigRepository.class);
        when(repositorio.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(processo.getInstrumentoProcedimentoConfig()))
                .thenReturn(List.of(itens));
        return new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper(), new CatalogoFormularios(),
                null, null, repositorio, null);
    }

    @Test
    void semInformacaoBaseDoTextoDoAtoADataDeAssinaturaEDoFormularioComAMensagemDoCampo() {
        Processo processo = processoDeSegundaSerie();
        processo.setDataAssinatura(null);
        ValidadorSubmissaoProcesso comItens = validadorComItens(processo,
                itemDocumental(PaginaDocumento.DOCUMENTOS, ElementoInformacaoBase.DELIBERACAO, CategoriaDocumento.PUBLICACAO),
                itemDocumental(PaginaDocumento.DOCUMENTOS, null, CategoriaDocumento.PUBLICACAO));

        assertThat(comItens.dataAssinaturaNoFormulario(processo)).isTrue();
        assertThat(comItens.pendencias(processo, sessaoCompleta()))
                .containsExactly(new CampoErro("dataAssinatura", "Indique a data de assinatura do ato."));
    }

    @Test
    void comInformacaoBaseDoTextoDoAtoADataDeAssinaturaFicaNoBlocoDosDocumentos() {
        Processo processo = processoDeSegundaSerie();
        processo.setDataAssinatura(null);
        ValidadorSubmissaoProcesso comItens = validadorComItens(processo,
                itemDocumental(PaginaDocumento.DOCUMENTOS, ElementoInformacaoBase.TEXTO_ATO, CategoriaDocumento.PUBLICACAO));

        assertThat(comItens.dataAssinaturaNoFormulario(processo)).isFalse();
        assertThat(comItens.pendencias(processo, sessaoCompleta())).containsExactly(new CampoErro("dataAssinatura",
                "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."));
    }

    @Test
    void oTextoDoAtoDePublicacaoNumProcessoSoParaDepositoNaoContaParaADataDeAssinatura() {
        Processo processo = processoDeSegundaSerie();
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);

        assertThat(validadorComItens(processo,
                itemDocumental(PaginaDocumento.DOCUMENTOS, ElementoInformacaoBase.TEXTO_ATO, CategoriaDocumento.PUBLICACAO))
                .dataAssinaturaNoFormulario(processo)).isTrue();
        assertThat(validadorComItens(processo,
                itemDocumental(PaginaDocumento.DOCUMENTOS, ElementoInformacaoBase.TEXTO_ATO, CategoriaDocumento.DEPOSITO))
                .dataAssinaturaNoFormulario(processo)).isFalse();
    }

    @Test
    void oTextoDoAtoForaDaPaginaDosDocumentosNaoContaParaADataDeAssinatura() {
        Processo processo = processoDeSegundaSerie();

        assertThat(validadorComItens(processo,
                itemDocumental(PaginaDocumento.PLANTAS, ElementoInformacaoBase.TEXTO_ATO, CategoriaDocumento.PUBLICACAO))
                .dataAssinaturaNoFormulario(processo)).isTrue();
    }

    @Test
    void deveManterOMesmoPathEAMensagemDaDataDeAssinaturaQueApontaParaOTextoDoAto() {
        Processo processo = processoDeSegundaSerie();
        processo.setDataAssinatura(null);

        assertThat(validador.pendencias(processo, sessaoCompleta())).containsExactly(new CampoErro("dataAssinatura",
                "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEGUNDA_H", "SEGUNDA_C"})
    void deveExigirNormaLegalTipoDeInformacaoEDrParteIdNaSegundaSerie(String serieDr) {
        Processo processo = processo(serieDr);

        assertThat(caminhos(processo, sessao(null, "2015482", null)))
                .containsExactly("normaLegal", "tipoInformacao", "drParteId");
    }

    @Test
    void deveIndicarQueASessaoNaoTemPartId() {
        assertThat(validador.pendencias(processoDeSegundaSerie(), sessao(null, "2015482", null)))
                .containsExactly(new CampoErro("drParteId", "Sessão sem PartId da adesão INCM, não é possível submeter."));
    }

    @Test
    void naoDeveExigirCamposDaSegundaSerieNemDrParteIdNaPrimeiraSerie() {
        Processo processo = processo("PRIMEIRA");
        processo.setPublicarSuplemento(false);

        assertThat(caminhos(processo, sessao(null, "2015482", null))).isEmpty();
    }

    @Test
    void deveExigirPublicarSuplementoEDataDoSuplementoNaPrimeiraSerie() {
        Processo processo = processo("PRIMEIRA");
        ContextoUtilizador contexto = sessao(null, "2015482", null);

        assertThat(caminhos(processo, contexto)).containsExactly("publicarSuplemento");

        processo.setPublicarSuplemento(true);

        assertThat(caminhos(processo, contexto)).containsExactly("dataSuplemento");

        processo.setDataSuplemento(LocalDate.of(2026, 9, 2));

        assertThat(caminhos(processo, contexto)).isEmpty();
    }

    @Test
    void deveExigirOsCamposComunsEASerieDoProcessoQuandoOProcedimentoNaoTemSerie() {
        Processo processo = processo(null);

        assertThat(caminhos(processo, sessao(null, "2015482", null))).containsExactly("serieDr");
        assertThat(caminhos(processo, sessao(null, null, null))).containsExactly("serieDr", "incmEnrollmentId");
    }

    @Test
    void deveTratarOExtratoComoPendenciaQuandoOTipoDeAtoNaoOPermite() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoInformacao("EXTRATO");

        assertThat(validador.pendencias(processo, sessao(atosComTipo(false), "2015482", 1234567891234567L)))
                .containsExactly(new CampoErro("tipoInformacao", MENSAGEM_EXTRATO));
    }

    @Test
    void deveAceitarOExtratoQuandoOTipoDeAtoOPermite() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoInformacao("EXTRATO");

        assertThat(validador.pendencias(processo, sessao(atosComTipo(true), "2015482", 1234567891234567L))).isEmpty();
    }

    @Test
    void naoDeveAplicarARegraDoExtratoSemClaimDeAtosAutorizados() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoInformacao("EXTRATO");

        assertThat(validador.pendencias(processo, sessaoCompleta())).isEmpty();
        assertThatCode(() -> validador.verificarRegraDoExtrato(processo, sessaoCompleta())).doesNotThrowAnyException();
    }

    @Test
    void naoDeveRecusarTextoIntegralMesmoQuandoOTipoDeAtoNaoPermiteExtrato() {
        Processo processo = processoDeSegundaSerie();

        assertThat(validador.pendencias(processo, sessao(atosComTipo(false), "2015482", 1234567891234567L))).isEmpty();
    }

    @Test
    void deveContarUmTipoDeAtoForaDoClaimComoSemPermissaoParaExtrato() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoInformacao("EXTRATO");
        AtosAutorizados outroTipo = new AtosAutorizados(List.of(), List.of(new AtoAutorizado(999L, null, true)));

        assertThat(caminhos(processo, sessao(outroTipo, "2015482", 1234567891234567L))).containsExactly("tipoInformacao");
    }

    @Test
    void deveContarUmProcessoSemTipoDeAtoComoSemPermissaoParaExtrato() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoAto(null);
        processo.setTipoInformacao("EXTRATO");

        assertThat(caminhos(processo, sessao(atosComTipo(true), "2015482", 1234567891234567L)))
                .containsExactly("tipoAtoId", "tipoInformacao");
    }

    @Test
    void deveExigirOTipoDeAtoEmQualquerSerieComOsOutrosCamposDePublicacao() {
        for (String serieDr : java.util.Arrays.asList("PRIMEIRA", "SEGUNDA_H", null)) {
            Processo processo = "SEGUNDA_H".equals(serieDr) ? processoDeSegundaSerie()
                    : "PRIMEIRA".equals(serieDr) ? processoDePrimeiraSerie() : processo(null);
            processo.setTipoAto(null);
            processo.setSumario(null);

            List<String> esperados = serieDr == null ? List.of("tipoAtoId", "serieDr", "sumario") : List.of("tipoAtoId", "sumario");
            assertThat(caminhos(processo, sessaoCompleta())).as("serie %s", serieDr).containsExactlyElementsOf(esperados);
        }
    }

    @Test
    void semSerieNoProcedimentoExigeASerieEscolhidaNoProcessoComAMensagemDaRegra() {
        Processo processo = processo(null);

        assertThat(validador.pendencias(processo, sessaoCompleta()))
                .containsExactly(new CampoErro("serieDr", "Preencha os dados de publicação."));
    }

    @Test
    void aSerieEscolhidaNoProcessoDecideOsCamposDaSerie() {
        Processo segunda = processo(null);
        segunda.setSerieDr("SEGUNDA_C");
        assertThat(caminhos(segunda, sessao(null, "2015482", null))).containsExactly("normaLegal", "tipoInformacao", "drParteId");

        Processo primeira = processo(null);
        primeira.setSerieDr("PRIMEIRA");
        assertThat(caminhos(primeira, sessaoCompleta())).containsExactly("publicarSuplemento");
    }

    @Test
    void aRegraDoExtratoSegueASerieEscolhidaNoProcesso() {
        Processo processo = processoDeSegundaSerie();
        Processo semSerieNoProcedimento = processo(null);
        semSerieNoProcedimento.setSerieDr("SEGUNDA_H");
        semSerieNoProcedimento.setNormaLegal(processo.getNormaLegal());
        semSerieNoProcedimento.setTipoInformacao("EXTRATO");
        semSerieNoProcedimento.setTipoAto(processo.getTipoAto());

        assertThat(caminhos(semSerieNoProcedimento, sessao(atosComTipo(false), "2015482", 1234567891234567L)))
                .containsExactly("tipoInformacao");
    }

    @Test
    void naoDeveAplicarARegraDoExtratoNaPrimeiraSerie() {
        Processo processo = processoDePrimeiraSerie();
        processo.setTipoInformacao("EXTRATO");

        assertThat(caminhos(processo, sessao(atosComTipo(false), "2015482", null))).isEmpty();
    }

    @Test
    void deveRecusarOExtratoComValidacaoEOCampoTipoInformacao() {
        Processo processo = processoDeSegundaSerie();
        processo.setTipoInformacao("EXTRATO");
        ContextoUtilizador contexto = sessao(atosComTipo(false), "2015482", 1234567891234567L);

        assertThatThrownBy(() -> validador.verificarRegraDoExtrato(processo, contexto))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getMessage()).isEqualTo(MENSAGEM_EXTRATO);
                    assertThat(ex.getCampos()).containsExactly(new CampoErro("tipoInformacao", MENSAGEM_EXTRATO));
                });
    }

    @Test
    void deveVerificarARegraDoExtratoSemExigirOsDadosEmFalta() {
        Processo processo = processo("SEGUNDA_H");
        processo.setTipoInformacao("TEXTO_INTEGRAL");

        assertThatCode(() -> validador.verificarRegraDoExtrato(processo, sessao(atosComTipo(false), null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void deveExigirElementosAfetadosNosPlanosComCodigoProprioNoCampoElementosAfetados() {
        assertThat(validador.pendenciaElementosAfetados(processoDeInstrumento("PLANO", null)))
                .contains(new CampoErro("elementosAfetados", "Indique os elementos fundamentais afetados."));
        assertThat(validador.pendenciaElementosAfetados(processoDeInstrumento("PLANO", List.of()))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void deveExigirAEntidadeInstrutoraNumProgramaSemValor(String vazio) {
        Processo semValor = processoDeInstrumento("PROGRAMA", null);
        Processo emBranco = processoDeInstrumento("PROGRAMA", null);
        emBranco.setEntidadeInstrutora(vazio);

        assertThat(validador.pedeEntidadeInstrutora(semValor)).isTrue();
        assertThat(validador.pendenciaEntidadeInstrutora(semValor))
                .contains(new CampoErro("entidadeInstrutora", "Indique a entidade instrutora."));
        assertThat(validador.pendenciaEntidadeInstrutora(emBranco))
                .contains(new CampoErro("entidadeInstrutora", "Indique a entidade instrutora."));
    }

    @Test
    void naoDeveExigirAEntidadeInstrutoraNumProgramaEmQueNaoEObrigatoria() {
        Processo processo = processoDeInstrumento("PROGRAMA", null);
        processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().setEntidadeInstrutoraObrigatoria(false);

        assertThat(validador.pedeEntidadeInstrutora(processo)).isTrue();
        assertThat(validador.pendenciaEntidadeInstrutora(processo)).isEmpty();
    }

    @Test
    void naoDeveTerPendenciaDeEntidadeInstrutoraNumProgramaComValor() {
        Processo processo = processoDeInstrumento("PROGRAMA", null);
        processo.setEntidadeInstrutora("Comissão de Coordenação e Desenvolvimento Regional");

        assertThat(validador.pendenciaEntidadeInstrutora(processo)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANO", "SRUP"})
    void naoDevePedirNemExigirAEntidadeInstrutoraForaDosProgramas(String categoria) {
        Processo processo = processoDeInstrumento(categoria, null);

        assertThat(validador.pedeEntidadeInstrutora(processo)).isFalse();
        assertThat(validador.pendenciaEntidadeInstrutora(processo)).isEmpty();
    }

    @Test
    void naoDeveTerPendenciaDeElementosAfetadosNumPlanoComPeloMenosUm() {
        assertThat(validador.pendenciaElementosAfetados(processoDeInstrumento("PLANO", List.of("OUTROS")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAMA", "SRUP"})
    void naoDeveExigirElementosAfetadosForaDosPlanos(String categoria) {
        assertThat(validador.pendenciaElementosAfetados(processoDeInstrumento(categoria, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVISAO", "ALTERACAO"})
    void deveExigirElementosAfetadosNasDinamicasDeUmPlanoQueNaoSaoAElaboracao(String tipoDinamica) {
        Processo processo = processoDeInstrumento("PLANO", tipoDinamica, null);

        assertThat(validador.exigeElementosAfetados(processo)).isTrue();
        assertThat(validador.pendenciaElementosAfetados(processo)).isPresent();
    }

    @Test
    void naoDeveExigirElementosAfetadosNaElaboracaoDeUmPlano() {
        Processo processo = processoDeInstrumento("PLANO", "ELABORACAO", null);

        assertThat(validador.exigeElementosAfetados(processo)).isFalse();
        assertThat(validador.pendenciaElementosAfetados(processo)).isEmpty();
    }

    @Test
    void numaDinamicaUmOuMaisSemInstrumentosAPendenciaPedeQueSeAssociemENasOutrasNao() {
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("SUSPENSAO_GOVERNO", "Suspensão"));
        Dinamica dinamica = new Dinamica(config, "Suspensão", null);
        Processo processo = new Processo(dinamica, null, null, null);

        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.UM_OU_MAIS);
        assertThat(validador.pendenciaInstrumentosAssociados(processo))
                .contains(new CampoErro("instrumentos", "Associe os instrumentos que esta dinâmica exige."));
        dinamica.substituirInstrumentos(List.of(new Instrumento(pdm, "1", "PDM de Sintra", null, null)));
        assertThat(validador.pendenciaInstrumentosAssociados(processo)).isEmpty();

        dinamica.substituirInstrumentos(List.of());
        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.OPCIONAL);
        assertThat(validador.pendenciaInstrumentosAssociados(processo)).isEmpty();
        config.setAssociacaoInstrumentos(AssociacaoInstrumentos.UM);
        assertThat(validador.pendenciaInstrumentosAssociados(processo)).isEmpty();
    }

    @Test
    void asMedidasPreventivasDoMunicipioSemInstrumentoPedemOsElementosAfetadosPelaConfiguracaoDoPdm() {
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(pdm,
                new TipoDinamica("MEDIDAS_PREVENTIVAS", "Medidas Preventivas (iniciativa do município)"));
        Processo semIgt = new Processo(new Dinamica(config, "MP Sintra", null), null, null, null);

        assertThat(semIgt.getDinamica().getInstrumento()).isNull();
        assertThat(validador.exigeElementosAfetados(semIgt)).isTrue();
        assertThat(validador.pendenciaElementosAfetados(semIgt))
                .contains(new CampoErro("elementosAfetados", "Indique os elementos fundamentais afetados."));
        semIgt.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        assertThat(validador.pendenciaElementosAfetados(semIgt)).isEmpty();

        Processo comIgt = new Processo(new Dinamica(new Instrumento(pdm, "1", "PDM de Sintra", null, null), config,
                "MP Sintra", null), null, null, null);
        assertThat(validador.exigeElementosAfetados(comIgt)).isEqualTo(validador.exigeElementosAfetados(
                new Processo(new Dinamica(config, "MP Sintra", null), null, null, null)));
    }

    @Test
    void naoDeveExigirElementosAfetadosForaDosPlanosEmNenhumaDinamica() {
        assertThat(validador.exigeElementosAfetados(processoDeInstrumento("PROGRAMA", "ELABORACAO", null))).isFalse();
        assertThat(validador.exigeElementosAfetados(processoDeInstrumento("PROGRAMA", "REVISAO", null))).isFalse();
        assertThat(validador.exigeElementosAfetados(processoDeInstrumento("PROGRAMA", "ALTERACAO", null))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANO", "PROGRAMA"})
    void deveExigirAEntidadeEmitenteNosPlanosEProgramasNoCampoEntidadeEmitente(String categoria) {
        assertThat(validador.pendenciaEntidadeEmitente(processoDeInstrumento(categoria, null)))
                .contains(new CampoErro("entidadeEmitente", "Indique a identificação da entidade emitente."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void deveTratarAEntidadeEmitenteEmBrancoComoEmFalta(String valor) {
        Processo processo = processoDeInstrumento("PLANO", null);
        processo.setEntidadeEmitente(valor);

        assertThat(validador.pendenciaEntidadeEmitente(processo)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANO", "PROGRAMA"})
    void naoDeveTerPendenciaDeEntidadeEmitenteQuandoEstaPreenchida(String categoria) {
        Processo processo = processoDeInstrumento(categoria, null);
        processo.setEntidadeEmitente("Câmara Municipal de Teste");

        assertThat(validador.pendenciaEntidadeEmitente(processo)).isEmpty();
    }

    @Test
    void naoDeveExigirAEntidadeEmitenteNumInstrumentoSrup() {
        assertThat(validador.pendenciaEntidadeEmitente(processoDeInstrumento("SRUP", null))).isEmpty();
    }

    private static Processo processoComPergunta(PerguntaGeoconform pergunta, List<String> elementosAfetados) {
        Processo processo = processo("SEGUNDA_H");
        processo.getInstrumentoProcedimentoConfig().setPerguntaGeoconform(pergunta);
        processo.setElementosAfetados(elementosAfetados);
        return processo;
    }

    @Test
    void deveExigirAOpcaoGeoconformQuandoAPerguntaDoProcedimentoEObrigatoria() {
        assertThat(validador.exigeOpcaoGeoconform(processoComPergunta(PerguntaGeoconform.OBRIGATORIA, null))).isTrue();
        assertThat(validador.exigeOpcaoGeoconform(processoComPergunta(PerguntaGeoconform.OBRIGATORIA, List.of("PECAS_ESCRITAS"))))
                .isTrue();
    }

    @Test
    void deveExigirAOpcaoGeoconformSoComPecasGraficasQuandoAPerguntaDependeDelas() {
        assertThat(validador.exigeOpcaoGeoconform(
                processoComPergunta(PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS")))).isTrue();
        assertThat(validador.exigeOpcaoGeoconform(
                processoComPergunta(PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of("PECAS_ESCRITAS", "OUTROS")))).isFalse();
        assertThat(validador.exigeOpcaoGeoconform(processoComPergunta(PerguntaGeoconform.SE_PECAS_GRAFICAS, List.of()))).isFalse();
        assertThat(validador.exigeOpcaoGeoconform(processoComPergunta(PerguntaGeoconform.SE_PECAS_GRAFICAS, null))).isFalse();
    }

    @Test
    void naoDeveExigirAOpcaoGeoconformQuandoOProcedimentoNaoPergunta() {
        assertThat(validador.exigeOpcaoGeoconform(
                processoComPergunta(PerguntaGeoconform.NAO_APLICAVEL, List.of("PECAS_GRAFICAS")))).isFalse();
    }

    @Test
    void naoDeveJuntarAPendenciaDosElementosAfetadosAsDosDadosIncm() {
        assertThat(caminhos(processoDeSegundaSerie(), sessaoCompleta())).doesNotContain("elementosAfetados");
    }

    @Test
    void deveAceitarOsTresElementosAfetadosEUmaListaNulaOuVazia() {
        assertThatCode(() -> validador.verificarElementosAfetados(List.of("PECAS_ESCRITAS", "PECAS_GRAFICAS", "OUTROS")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarElementosAfetados(null)).doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarElementosAfetados(List.of())).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarUmElementoAfetadoForaDosTresComValidacaoNoCampoElementosAfetados() {
        assertThatThrownBy(() -> validador.verificarElementosAfetados(List.of("PECAS_ESCRITAS", "OUTRO_VALOR")))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getCampos()).extracting(CampoErro::path).containsExactly("elementosAfetados");
                    assertThat(ex.getMessage()).isEqualTo("Valores aceites: PECAS_ESCRITAS, PECAS_GRAFICAS, OUTROS.");
                });
    }

    private static Processo processoQuePedeFundamentacao(boolean pede) {
        Processo processo = processo("SEGUNDA_H");
        processo.getInstrumentoProcedimentoConfig().setPedeFundamentacaoRjigt(pede);
        return processo;
    }

    private static ValidadorSubmissaoProcesso validadorComOpcoes(String... codigos) {
        ListaValoresRepository repositorio = mock(ListaValoresRepository.class);
        List<ListaValores> itens = new ArrayList<>();
        for (int i = 0; i < codigos.length; i++) {
            itens.add(new ListaValores("FUNDAMENTACAO_RJIGT", codigos[i], "Opção " + codigos[i], i + 1));
        }
        when(repositorio.findByTipoAndAtivoTrueOrderByOrdemAsc("FUNDAMENTACAO_RJIGT")).thenReturn(itens);
        return new ValidadorSubmissaoProcesso(repositorio, new ObjectMapper());
    }

    @Test
    void fundamentacaoRjigtVaziaOuNulaPassaSemLerALista() {
        ListaValoresRepository repositorio = mock(ListaValoresRepository.class);
        ValidadorSubmissaoProcesso semLista = new ValidadorSubmissaoProcesso(repositorio, new ObjectMapper());

        assertThat(semLista.verificarFundamentacaoRjigt(processoQuePedeFundamentacao(false), null)).isNull();
        assertThat(semLista.verificarFundamentacaoRjigt(processoQuePedeFundamentacao(false), List.of())).isNull();
        verifyNoInteractions(repositorio);
    }

    @Test
    void fundamentacaoRjigtNumProcedimentoQueNaoAPedeERecusada() {
        assertThatThrownBy(() -> validadorComOpcoes("A1").verificarFundamentacaoRjigt(processoQuePedeFundamentacao(false), List.of("A1")))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCampos()).extracting(CampoErro::path).containsExactly("fundamentacaoRjigt");
                    assertThat(ex.getCampos()).extracting(CampoErro::erro)
                            .containsExactly("Este procedimento não pede a fundamentação RJIGT.");
                });
    }

    @Test
    void fundamentacaoRjigtComUmCodigoForaDosItensAtivosERecusada() {
        assertThatThrownBy(() -> validadorComOpcoes("A1", "A2")
                .verificarFundamentacaoRjigt(processoQuePedeFundamentacao(true), List.of("A1", "INATIVO")))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCampos()).extracting(CampoErro::path).containsExactly("fundamentacaoRjigt");
                    assertThat(ex.getCampos()).extracting(CampoErro::erro).containsExactly("Fundamentação RJIGT inválida.");
                });
    }

    @Test
    void fundamentacaoRjigtRepetidaGuardaCadaCodigoUmaVezPelaOrdemDoPedido() {
        assertThat(validadorComOpcoes("A1", "A2")
                .verificarFundamentacaoRjigt(processoQuePedeFundamentacao(true), List.of("A2", "A1", "A2")))
                .containsExactly("A2", "A1");
    }

    @Test
    void pendenciaFundamentacaoRjigtLeOValorGravadoEApontaUmCodigoDesativado() {
        Processo processo = processoQuePedeFundamentacao(true);
        processo.setDadosFormulario("{\"fundamentacaoRjigt\":[\"A1\",\"DESATIVADO\"]}");

        assertThat(validadorComOpcoes("A1").pendenciaFundamentacaoRjigt(processo))
                .map(CampoErro::path).contains("fundamentacaoRjigt");
        processo.setDadosFormulario("{\"fundamentacaoRjigt\":[\"A1\"]}");
        assertThat(validadorComOpcoes("A1").pendenciaFundamentacaoRjigt(processo)).isEmpty();
    }

    @Test
    void opcoesFundamentacaoRjigtSaoVaziasQuandoOProcedimentoNaoPedeOCampo() {
        assertThat(validadorComOpcoes("A1").opcoesFundamentacaoRjigt(processoQuePedeFundamentacao(false))).isEmpty();
        assertThat(validadorComOpcoes("A1", "A2").opcoesFundamentacaoRjigt(processoQuePedeFundamentacao(true)))
                .extracting(ListaValores::getCodigo).containsExactly("A1", "A2");
    }

    @Test
    void fundamentacaoRjigtBloqueadaDepoisDaSubmissaoNaoVoltaAVerificarOsCodigos() {
        Processo processo = processoQuePedeFundamentacao(true);
        processo.setDataSubmissao(Instant.now());
        processo.setDadosFormulario("{\"fundamentacaoRjigt\":[\"DESATIVADO\"]}");
        ValidadorSubmissaoProcesso validadorSemOpcoes = validadorComOpcoes("A1");

        assertThat(validadorSemOpcoes.pendenciaFundamentacaoRjigt(processo)).isEmpty();
        assertThat(validadorSemOpcoes.verificarFundamentacaoRjigt(processo, List.of("DESATIVADO"))).containsExactly("DESATIVADO");
    }

    private static final String MSG_GEOCONFORM_AFETA_PECAS_GRAFICAS =
            "Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.";
    private static final String MSG_PLANTA_NOVA_EM_FALTA = "Indique se a alteração introduz alguma planta nova.";
    private static final String MSG_SEM_PERGUNTA_PLANTA_NOVA =
            "Este procedimento não pergunta se a alteração introduz uma planta nova.";
    private static final String MSG_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS =
            "Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.";

    /** `SIM`, `NAO` ou `NULO`: o JUnit não distingue o texto «null» de um valor em falta num CSV. */
    private static Boolean triestado(String valor) {
        return switch (valor) {
            case "SIM" -> Boolean.TRUE;
            case "NAO" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static List<String> elementos(String valor) {
        return "NULO".equals(valor) ? null : "VAZIO".equals(valor) ? List.of() : List.of(valor.split(","));
    }

    private static Processo processoDaPublicacaoAlteracao(String categoria, String comGeoconform, String elementosAfetados,
                                                          String introduzPlantaNova) {
        Processo processo = processoDeInstrumento(categoria, elementos(elementosAfetados));
        processo.getDinamica().setComGeoconform(triestado(comGeoconform));
        Boolean resposta = triestado(introduzPlantaNova);
        if (resposta != null) {
            processo.setDadosFormulario("{\"introduzPlantaNova\":" + resposta + "}");
        }
        return processo;
    }

    private List<CampoErro> errosDaPublicacaoAlteracao(Processo processo) {
        try {
            validador.verificarPublicacaoAlteracao(processo);
            return List.of();
        } catch (ValidacaoException e) {
            assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
            return e.getCampos();
        }
    }

    @ParameterizedTest(name = "geo={0} elementos={1} resposta={2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "SIM  | PECAS_ESCRITAS                  | NULO | elementosAfetados",
            "SIM  | PECAS_ESCRITAS,OUTROS           | NULO | elementosAfetados",
            "SIM  | OUTROS                          | NULO | elementosAfetados",
            "SIM  | VAZIO                           | NULO | elementosAfetados",
            "SIM  | NULO                            | NULO | elementosAfetados",
            "SIM  | PECAS_GRAFICAS                  | NULO | -",
            "SIM  | PECAS_GRAFICAS,PECAS_ESCRITAS   | NULO | -",
            "SIM  | PECAS_GRAFICAS                  | NAO  | introduzPlantaNova",
            "SIM  | PECAS_GRAFICAS,PECAS_ESCRITAS   | SIM  | introduzPlantaNova",
            "SIM  | PECAS_GRAFICAS                  | SIM  | introduzPlantaNova,elementosAfetados",
            "NAO  | PECAS_GRAFICAS                  | NULO | introduzPlantaNova",
            "NAO  | PECAS_GRAFICAS                  | NAO  | -",
            "NAO  | PECAS_GRAFICAS                  | SIM  | elementosAfetados",
            "NAO  | PECAS_GRAFICAS,PECAS_ESCRITAS   | SIM  | -",
            "NAO  | PECAS_GRAFICAS,PECAS_ESCRITAS   | NAO  | -",
            "NAO  | PECAS_GRAFICAS,PECAS_ESCRITAS   | NULO | introduzPlantaNova",
            "NAO  | PECAS_ESCRITAS                  | NULO | -",
            "NAO  | PECAS_ESCRITAS                  | NAO  | introduzPlantaNova",
            "NAO  | PECAS_ESCRITAS                  | SIM  | introduzPlantaNova",
            "NAO  | OUTROS                          | SIM  | introduzPlantaNova,elementosAfetados",
            "NAO  | NULO                            | NULO | -",
            "NULO | PECAS_GRAFICAS                  | NULO | introduzPlantaNova",
            "NULO | PECAS_GRAFICAS                  | NAO  | -",
            "NULO | PECAS_GRAFICAS                  | SIM  | elementosAfetados",
            "NULO | PECAS_GRAFICAS,PECAS_ESCRITAS   | SIM  | -",
            "NULO | PECAS_ESCRITAS                  | NULO | -",
            "NULO | PECAS_ESCRITAS                  | NAO  | introduzPlantaNova",
            "NULO | NULO                            | SIM  | introduzPlantaNova,elementosAfetados",
            "NULO | VAZIO                           | NULO | -"})
    void deveVerificarAPublicacaoDaAlteracaoPelaCombinacaoDeComGeoconformElementosAfetadosEResposta(
            String comGeoconform, String elementosAfetados, String introduzPlantaNova, String caminhosEsperados) {
        Processo processo = processoDaPublicacaoAlteracao("PLANO", comGeoconform, elementosAfetados, introduzPlantaNova);

        List<String> esperados = "-".equals(caminhosEsperados) ? List.of() : List.of(caminhosEsperados.split(","));

        assertThat(errosDaPublicacaoAlteracao(processo)).extracting(CampoErro::path).containsExactlyElementsOf(esperados);
    }

    @Test
    void deveRecusarComAMensagemDeCadaRegraDaPublicacaoDaAlteracao() {
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao("PLANO", "SIM", "PECAS_ESCRITAS", "NULO")))
                .containsExactly(new CampoErro("elementosAfetados", MSG_GEOCONFORM_AFETA_PECAS_GRAFICAS));
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao("PLANO", "NAO", "PECAS_GRAFICAS", "NULO")))
                .containsExactly(new CampoErro("introduzPlantaNova", MSG_PLANTA_NOVA_EM_FALTA));
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao("PLANO", "NAO", "PECAS_ESCRITAS", "NAO")))
                .containsExactly(new CampoErro("introduzPlantaNova", MSG_SEM_PERGUNTA_PLANTA_NOVA));
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao("PLANO", "NAO", "PECAS_GRAFICAS", "SIM")))
                .containsExactly(new CampoErro("elementosAfetados", MSG_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS));
    }

    @Test
    void deveJuntarTodasAsFalhasDaPublicacaoDaAlteracaoNumSoValidacaoException() {
        Processo processo = processoDaPublicacaoAlteracao("PLANO", "SIM", "PECAS_ESCRITAS", "SIM");

        assertThatThrownBy(() -> validador.verificarPublicacaoAlteracao(processo))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getMessage()).isEqualTo("A Publicação da Alteração tem campos inválidos.");
                    assertThat(ex.getCampos()).containsExactly(
                            new CampoErro("elementosAfetados", MSG_GEOCONFORM_AFETA_PECAS_GRAFICAS),
                            new CampoErro("introduzPlantaNova", MSG_SEM_PERGUNTA_PLANTA_NOVA));
                });
    }

    @Test
    void deveUsarAMensagemDaRegraQuandoASubmissaoSoFalhaNumaDelas() {
        Processo processo = processoDaPublicacaoAlteracao("PLANO", "NAO", "PECAS_GRAFICAS", "NULO");

        assertThatThrownBy(() -> validador.verificarPublicacaoAlteracao(processo))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getMessage()).isEqualTo(MSG_PLANTA_NOVA_EM_FALTA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANO", "PROGRAMA", "SRUP"})
    void naoDeveDependerDoTipoDeInstrumentoNaPublicacaoDaAlteracao(String categoria) {
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao(categoria, "NAO", "PECAS_GRAFICAS", "NULO")))
                .extracting(CampoErro::path).containsExactly("introduzPlantaNova");
        assertThat(errosDaPublicacaoAlteracao(processoDaPublicacaoAlteracao(categoria, "SIM", "PECAS_GRAFICAS", "NULO"))).isEmpty();
    }

    @Test
    void deveLerARespostaDeDadosFormularioIgnorandoOutrasChaves() {
        Processo processo = processoDaPublicacaoAlteracao("PLANO", "NAO", "PECAS_GRAFICAS,PECAS_ESCRITAS", "NULO");
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2}},\"introduzPlantaNova\":true}");

        assertThat(errosDaPublicacaoAlteracao(processo)).isEmpty();
    }

    private static Processo discussaoPublica(String dadosFormulario, LocalDate dataCaducidade) {
        Processo processo = org.mockito.Mockito.mock(Processo.class);
        Dinamica dinamica = org.mockito.Mockito.mock(Dinamica.class);
        when(processo.getDadosFormulario()).thenReturn(dadosFormulario);
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(dinamica.getDataCaducidade()).thenReturn(dataCaducidade);
        return processo;
    }

    private static String comDataDeliberacao(String data) {
        return "{\"informacaoBase\":{\"deliberacao\":{\"dataDeliberacao\":\"" + data + "\"}}}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-03-30", "2027-03-31"})
    void aDataDaDeliberacaoAnteriorOuIgualADataLimitePassa(String data) {
        assertThatCode(() -> validador.verificarDataDeliberacaoNoPrazo(
                discussaoPublica(comDataDeliberacao(data), LocalDate.of(2027, 3, 31)))).doesNotThrowAnyException();
    }

    @Test
    void aDataDaDeliberacaoPosteriorADataLimiteERecusadaNoCampoDaDeliberacao() {
        Processo processo = discussaoPublica(comDataDeliberacao("2027-04-01"), LocalDate.of(2027, 3, 31));

        assertThatThrownBy(() -> validador.verificarDataDeliberacaoNoPrazo(processo))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.")
                .satisfies(erro -> assertThat(((ValidacaoException) erro).getCampos()).containsExactly(new CampoErro(
                        "informacaoBase.deliberacao.dataDeliberacao", "Posterior à data limite da dinâmica (31/03/2027).")));
    }

    @Test
    void semDeliberacaoComDataSemDadosOuSemDataLimiteARegraNaoSeAplica() {
        LocalDate limite = LocalDate.of(2027, 3, 31);
        assertThatCode(() -> validador.verificarDataDeliberacaoNoPrazo(
                discussaoPublica("{\"informacaoBase\":{\"deliberacao\":null}}", limite))).doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarDataDeliberacaoNoPrazo(
                discussaoPublica("{\"informacaoBase\":{\"deliberacao\":{\"dataDeliberacao\":null}}}", limite)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarDataDeliberacaoNoPrazo(discussaoPublica(null, limite)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarDataDeliberacaoNoPrazo(
                discussaoPublica(comDataDeliberacao("2030-01-01"), null))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(FormularioFixo.class)
    void deveRecusarConfirmacoesNulasOuVaziasComOCampoDaConfirmacaoEmFalta(FormularioFixo formulario) {
        for (List<String> confirmacoes : java.util.Arrays.asList(null, List.<String>of())) {
            assertThatThrownBy(() -> validador.verificarConfirmacoes(formulario, confirmacoes))
                    .isInstanceOfSatisfying(ConfirmacaoEmFaltaException.class, ex -> {
                        assertThat(ex.getCodigo()).isEqualTo("CONFIRMACAO_EM_FALTA");
                        assertThat(ex.getMessage()).isEqualTo("Confirme os campos assinalados.");
                        assertThat(ex.getCampos()).containsExactly(
                                new CampoErro("confirmacoes.CONTEUDO_PROCESSO", "Confirmação obrigatória."));
                    });
        }
    }

    @Test
    void deveRecusarQuandoSoVemUmaChaveQueOFormularioNaoExige() {
        assertThatThrownBy(() -> validador.verificarConfirmacoes(FormularioFixo.INFORMACAO_GERAL, List.of("OUTRA")))
                .isInstanceOf(ConfirmacaoEmFaltaException.class);
    }

    @ParameterizedTest
    @EnumSource(FormularioFixo.class)
    void deveAceitarAsConfirmacoesCompletasEIgnorarChavesAMais(FormularioFixo formulario) {
        assertThatCode(() -> validador.verificarConfirmacoes(formulario, List.of("CONTEUDO_PROCESSO")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.verificarConfirmacoes(formulario, List.of("OUTRA", "CONTEUDO_PROCESSO", "OUTRA")))
                .doesNotThrowAnyException();
    }
}
