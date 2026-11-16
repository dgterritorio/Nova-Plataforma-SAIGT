package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.incm.SubmitNewActRequest;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.operacional.Processo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MontadorPedidoAtoIncmTest {

    private static final long ACT_TYPE_ID_SERIE_2 = 1234567891234567L;
    private static final long ACT_TYPE_ID_SERIE_1 = 1234567891234569L;
    private static final long PART_ID = 1234567891234568L;
    private static final LocalDate DATA_SUPLEMENTO = LocalDate.of(2026, 8, 31);
    private static final LocalDate DATA_ASSINATURA = LocalDate.of(2025, 12, 31);
    private static final List<String> URLS = List.of(
            "https://saigt.dgterritorio.gov.pt/publico/abc/aviso.pdf",
            "https://saigt.dgterritorio.gov.pt/publico/def/regulamento.pdf");

    private static final LinksPublicosIncm.LinksIncm SEM_IMAGENS = new LinksPublicosIncm.LinksIncm(List.of(), URLS);

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final MontadorPedidoAtoIncm montador = new MontadorPedidoAtoIncm();

    private static Processo processoCompleto(String serieDr) {
        TipoAto tipoAto = new TipoAto("AVISO", "Aviso", 1);
        tipoAto.setIncmActTypeIdSerie2(ACT_TYPE_ID_SERIE_2);
        Processo processo = new Processo(null, new InstrumentoProcedimentoConfig(null, null, 1, serieDr), null, null);
        processo.setTipoAto(tipoAto);
        processo.setIncmEnrollmentId("2015482");
        processo.setDrParteId(PART_ID);
        processo.setNormaLegal("Artigo 76.º do RJIGT");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setSumario("Aviso de abertura do período de discussão pública.");
        processo.setDataAssinatura(DATA_ASSINATURA);
        return processo;
    }

    private static Processo processoCompleto() {
        return processoCompleto("SEGUNDA_H");
    }

    /** Sem os campos só da 2.ª série (parte, norma legal, tipo de informação). */
    private static Processo processoPrimeiraSerie(Boolean publicarSuplemento, LocalDate dataSuplemento) {
        TipoAto tipoAto = new TipoAto("RCM", "Resolução do Conselho de Ministros", 1);
        tipoAto.setIncmActTypeIdSerie1(ACT_TYPE_ID_SERIE_1);
        Processo processo = new Processo(null, new InstrumentoProcedimentoConfig(null, null, 1, "PRIMEIRA"), null, null);
        processo.setTipoAto(tipoAto);
        processo.setIncmEnrollmentId("2015482");
        processo.setPublicarSuplemento(publicarSuplemento);
        processo.setDataSuplemento(dataSuplemento);
        processo.setSumario("Resolução que ratifica o plano.");
        processo.setDataAssinatura(DATA_ASSINATURA);
        return processo;
    }

    private List<String> camposEmFalta(Processo processo) {
        DadosIncmEmFaltaException erro = catchThrowableOfType(
                () -> montador.validar(processo), DadosIncmEmFaltaException.class);
        assertThat(erro).isNotNull();
        assertThat(erro.getCodigo()).isEqualTo("DADOS_INCM_EM_FALTA");
        return erro.getCampos().stream().map(ValidacaoException.CampoErro::path).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEGUNDA_H", "SEGUNDA_C"})
    void deveAceitarUmProcessoDeSegundaSerieCompleto(String serieDr) {
        assertThatNoException().isThrownBy(() -> montador.validar(processoCompleto(serieDr)));
    }

    @Test
    void deveRecusarSemSerieDr() {
        assertThat(camposEmFalta(processoCompleto(null))).containsExactly("serieDr");
    }

    @Test
    void deveRecusarTipoAtoSemActTypeIdDaSegundaSerie() {
        Processo processo = processoCompleto();
        TipoAto soPrimeiraSerie = new TipoAto("RATIF", "Ratificação", 2);
        soPrimeiraSerie.setIncmActTypeIdSerie1(42L);
        processo.setTipoAto(soPrimeiraSerie);

        assertThat(camposEmFalta(processo)).containsExactly("tipoAto");
    }

    @Test
    void deveRecusarSemIncmEnrollmentId() {
        Processo processo = processoCompleto();
        processo.setIncmEnrollmentId(null);

        assertThat(camposEmFalta(processo)).containsExactly("incmEnrollmentId");
    }

    @Test
    void deveRecusarComOsTresCamposDaSegundaSerieNumaSoResposta() {
        Processo processo = processoCompleto();
        processo.setDrParteId(null);
        processo.setNormaLegal(null);
        processo.setTipoInformacao(null);

        DadosIncmEmFaltaException erro = catchThrowableOfType(
                () -> montador.validar(processo), DadosIncmEmFaltaException.class);

        assertThat(erro.getCampos()).containsExactly(
                new ValidacaoException.CampoErro("drParteId", "Faltam dados obrigatórios para publicação na 2.ª série."),
                new ValidacaoException.CampoErro("normaLegal", "Faltam dados obrigatórios para publicação na 2.ª série."),
                new ValidacaoException.CampoErro("tipoInformacao", "Faltam dados obrigatórios para publicação na 2.ª série."));
    }

    @Test
    void deveRecusarSemSumarioNemDataDeAssinatura() {
        Processo processo = processoCompleto();
        processo.setSumario("  ");
        processo.setDataAssinatura(null);

        assertThat(camposEmFalta(processo)).containsExactly("sumario", "dataAssinatura");
    }

    @Test
    void deveAceitarUmProcessoDePrimeiraSerieCompletoSemSuplemento() {
        assertThatNoException().isThrownBy(() -> montador.validar(processoPrimeiraSerie(false, null)));
    }

    @Test
    void deveAceitarUmProcessoDePrimeiraSerieComSuplementoEData() {
        assertThatNoException().isThrownBy(() -> montador.validar(processoPrimeiraSerie(true, DATA_SUPLEMENTO)));
    }

    @Test
    void naoDeveExigirNaPrimeiraSerieOsCamposDaSegunda() {
        Processo processo = processoPrimeiraSerie(false, null);

        assertThat(processo.getDrParteId()).isNull();
        assertThat(processo.getNormaLegal()).isNull();
        assertThat(processo.getTipoInformacao()).isNull();
        assertThatNoException().isThrownBy(() -> montador.validar(processo));
    }

    @Test
    void deveRecusarPrimeiraSerieComSuplementoSemData() {
        DadosIncmEmFaltaException erro = catchThrowableOfType(
                () -> montador.validar(processoPrimeiraSerie(true, null)), DadosIncmEmFaltaException.class);

        assertThat(erro.getCodigo()).isEqualTo("DADOS_INCM_EM_FALTA");
        assertThat(erro.getCampos()).containsExactly(new ValidacaoException.CampoErro("dataSuplemento",
                "Indique a data prevista de publicação em suplemento."));
    }

    @Test
    void deveRecusarPrimeiraSerieSemPublicarSuplemento() {
        DadosIncmEmFaltaException erro = catchThrowableOfType(
                () -> montador.validar(processoPrimeiraSerie(null, null)), DadosIncmEmFaltaException.class);

        assertThat(erro.getCampos()).containsExactly(new ValidacaoException.CampoErro("publicarSuplemento",
                "Obrigatório na submissão de atos de 1.ª série."));
    }

    @Test
    void deveRecusarPrimeiraSerieComTipoAtoSemActTypeIdDaPrimeiraSerie() {
        Processo processo = processoPrimeiraSerie(false, null);
        TipoAto soSegundaSerie = new TipoAto("AVISO2", "Aviso", 1);
        soSegundaSerie.setIncmActTypeIdSerie2(ACT_TYPE_ID_SERIE_2);
        processo.setTipoAto(soSegundaSerie);

        DadosIncmEmFaltaException erro = catchThrowableOfType(
                () -> montador.validar(processo), DadosIncmEmFaltaException.class);

        assertThat(erro.getCampos()).containsExactly(new ValidacaoException.CampoErro("tipoAto",
                "O tipo de ato não está disponível na série deste procedimento."));
    }

    @Test
    void deveRecusarPrimeiraSerieSemTipoAto() {
        Processo processo = processoPrimeiraSerie(false, null);
        processo.setTipoAto(null);

        assertThat(camposEmFalta(processo)).containsExactly("tipoAto");
    }

    @Test
    void deveRecusarPrimeiraSerieSemOsDadosComunsNumaSoResposta() {
        Processo processo = processoPrimeiraSerie(true, null);
        processo.setIncmEnrollmentId(" ");
        processo.setSumario(null);
        processo.setDataAssinatura(null);

        assertThat(camposEmFalta(processo))
                .containsExactly("incmEnrollmentId", "dataSuplemento", "sumario", "dataAssinatura");
    }

    @Test
    void naoDeveAceitarNaSegundaSerieUmTipoAtoSoDaPrimeiraNemDispensarOsCamposDaSegunda() {
        Processo processo = processoCompleto("SEGUNDA_C");
        TipoAto soPrimeiraSerie = new TipoAto("RATIF", "Ratificação", 2);
        soPrimeiraSerie.setIncmActTypeIdSerie1(ACT_TYPE_ID_SERIE_1);
        processo.setTipoAto(soPrimeiraSerie);
        processo.setNormaLegal(null);
        processo.setPublicarSuplemento(true);

        assertThat(camposEmFalta(processo)).containsExactly("tipoAto", "normaLegal");
    }

    @Test
    void deveMontarOPedidoDePrimeiraSerieSemSuplemento() {
        SubmitNewActRequest.DgtAct dgtAct = montador.montar(processoPrimeiraSerie(false, null), SEM_IMAGENS).dgtAct();

        assertThat(dgtAct.userEnrollmentAgreementIdentifier()).isEqualTo("2015482");
        assertThat(dgtAct.diarioRepublicaSeries2Act()).isNull();
        assertThat(dgtAct.fileUrlList()).containsExactlyElementsOf(URLS);
        assertThat(dgtAct.imageUrlList()).isNotNull().isEmpty();
        assertThat(dgtAct.diarioRepublicaSeries1Act()).isEqualTo(new SubmitNewActRequest.DiarioRepublicaSeries1Act(
                ACT_TYPE_ID_SERIE_1, false, null, "Resolução que ratifica o plano.", DATA_ASSINATURA));
    }

    @Test
    void naoDeveEnviarADataDoSuplementoQuandoNaoEPublicadoEmSuplemento() {
        SubmitNewActRequest.DgtAct dgtAct =
                montador.montar(processoPrimeiraSerie(false, DATA_SUPLEMENTO), SEM_IMAGENS).dgtAct();

        assertThat(dgtAct.diarioRepublicaSeries1Act().publishInSuplement()).isFalse();
        assertThat(dgtAct.diarioRepublicaSeries1Act().suplementPublishingDate()).isNull();
    }

    @Test
    void deveMontarOPedidoDePrimeiraSerieComSuplemento() {
        SubmitNewActRequest.DgtAct dgtAct =
                montador.montar(processoPrimeiraSerie(true, DATA_SUPLEMENTO), SEM_IMAGENS).dgtAct();

        assertThat(dgtAct.diarioRepublicaSeries2Act()).isNull();
        assertThat(dgtAct.diarioRepublicaSeries1Act()).isEqualTo(new SubmitNewActRequest.DiarioRepublicaSeries1Act(
                ACT_TYPE_ID_SERIE_1, true, DATA_SUPLEMENTO, "Resolução que ratifica o plano.", DATA_ASSINATURA));
    }

    @Test
    void deveUsarAsMesmasListasDeUrlNaPrimeiraSerie() {
        List<String> imagens = List.of("https://saigt.dgterritorio.gov.pt/api/v1/publico/ghi/planta.jpg");

        SubmitNewActRequest.DgtAct dgtAct = montador.montar(processoPrimeiraSerie(false, null),
                new LinksPublicosIncm.LinksIncm(imagens, URLS)).dgtAct();

        assertThat(dgtAct.imageUrlList()).containsExactlyElementsOf(imagens);
        assertThat(dgtAct.fileUrlList()).containsExactlyElementsOf(URLS);
    }

    @Test
    void deveEscolherAEstruturaPelaSerieDoProcedimentoMesmoComOsCamposDaOutraSerie() {
        Processo segunda = processoCompleto("SEGUNDA_H");
        segunda.getTipoAto().setIncmActTypeIdSerie1(ACT_TYPE_ID_SERIE_1);
        segunda.setPublicarSuplemento(true);
        segunda.setDataSuplemento(DATA_SUPLEMENTO);
        Processo primeira = processoCompleto("PRIMEIRA");
        primeira.getTipoAto().setIncmActTypeIdSerie1(ACT_TYPE_ID_SERIE_1);
        primeira.setPublicarSuplemento(false);

        SubmitNewActRequest.DgtAct dgtActSegunda = montador.montar(segunda, SEM_IMAGENS).dgtAct();
        SubmitNewActRequest.DgtAct dgtActPrimeira = montador.montar(primeira, SEM_IMAGENS).dgtAct();

        assertThat(dgtActSegunda.diarioRepublicaSeries1Act()).isNull();
        assertThat(dgtActSegunda.diarioRepublicaSeries2Act().actTypeId()).isEqualTo(ACT_TYPE_ID_SERIE_2);
        assertThat(dgtActPrimeira.diarioRepublicaSeries2Act()).isNull();
        assertThat(dgtActPrimeira.diarioRepublicaSeries1Act().actTypeId()).isEqualTo(ACT_TYPE_ID_SERIE_1);
    }

    @Test
    void deveSerializarAPrimeiraSerieSemSuplementoComOsNomesDoContrato() {
        JsonNode dgtAct = OBJECT_MAPPER.valueToTree(
                montador.montar(processoPrimeiraSerie(false, DATA_SUPLEMENTO), SEM_IMAGENS)).get("DGTAct");

        assertThat(dgtAct.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("UserEnrollmentAgreementIdentifier", "DiarioRepublicaSeries1Act",
                        "ImageURLList", "FileURLList");
        JsonNode serie1 = dgtAct.get("DiarioRepublicaSeries1Act");
        assertThat(serie1.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("ActTypeId", "PublishInSuplement", "ActSummary", "SignatureDate");
        assertThat(serie1.get("ActTypeId").isIntegralNumber()).isTrue();
        assertThat(serie1.get("ActTypeId").asLong()).isEqualTo(ACT_TYPE_ID_SERIE_1);
        assertThat(serie1.get("PublishInSuplement").isBoolean()).isTrue();
        assertThat(serie1.get("PublishInSuplement").asBoolean()).isFalse();
        assertThat(serie1.get("SignatureDate").asText()).isEqualTo("2025-12-31");
    }

    @Test
    void deveSerializarAPrimeiraSerieComSuplementoEADataEmFormatoIso() {
        JsonNode serie1 = OBJECT_MAPPER.valueToTree(
                        montador.montar(processoPrimeiraSerie(true, DATA_SUPLEMENTO), SEM_IMAGENS))
                .get("DGTAct").get("DiarioRepublicaSeries1Act");

        assertThat(serie1.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("ActTypeId", "PublishInSuplement", "SuplementPublishingDate",
                        "ActSummary", "SignatureDate");
        assertThat(serie1.get("PublishInSuplement").asBoolean()).isTrue();
        assertThat(serie1.get("SuplementPublishingDate").asText()).isEqualTo("2026-08-31");
    }

    @Test
    void deveMontarOPedidoDeSegundaSerieComOsCamposDoProcesso() {
        SubmitNewActRequest pedido = montador.montar(processoCompleto(), SEM_IMAGENS);

        SubmitNewActRequest.DgtAct dgtAct = pedido.dgtAct();
        assertThat(dgtAct.userEnrollmentAgreementIdentifier()).isEqualTo("2015482");
        assertThat(dgtAct.diarioRepublicaSeries1Act()).isNull();
        assertThat(dgtAct.fileUrlList()).containsExactlyElementsOf(URLS);
        assertThat(dgtAct.imageUrlList()).isNotNull().isEmpty();
        assertThat(dgtAct.diarioRepublicaSeries2Act()).isEqualTo(new SubmitNewActRequest.DiarioRepublicaSeries2Act(
                ACT_TYPE_ID_SERIE_2, PART_ID, "full-text",
                "Aviso de abertura do período de discussão pública.", DATA_ASSINATURA, "Artigo 76.º do RJIGT"));
    }

    @Test
    void deveColocarAsImagensNaImageUrlListEOsFicheirosNaFileUrlList() {
        List<String> imagens = List.of("https://saigt.dgterritorio.gov.pt/publico/ghi/planta.jpg");

        SubmitNewActRequest.DgtAct dgtAct = montador.montar(processoCompleto(),
                new LinksPublicosIncm.LinksIncm(imagens, URLS)).dgtAct();

        assertThat(dgtAct.imageUrlList()).containsExactlyElementsOf(imagens);
        assertThat(dgtAct.fileUrlList()).containsExactlyElementsOf(URLS).doesNotContainAnyElementsOf(imagens);
    }

    @Test
    void deveTraduzirOTipoDeInformacaoParaOValorDaIncm() {
        Processo extrato = processoCompleto();
        extrato.setTipoInformacao("EXTRATO");
        Processo textoIntegral = processoCompleto();
        textoIntegral.setTipoInformacao("TEXTO_INTEGRAL");

        assertThat(montador.montar(extrato, SEM_IMAGENS).dgtAct().diarioRepublicaSeries2Act().informationTypeIdentifier())
                .isEqualTo("excerpt");
        assertThat(montador.montar(textoIntegral, SEM_IMAGENS).dgtAct().diarioRepublicaSeries2Act().informationTypeIdentifier())
                .isEqualTo("full-text");
    }

    @Test
    void deveManterAsMarcasDoMockNoSumario() {
        Processo processo = processoCompleto();
        processo.setSumario("[MOCK:REJEITAR] Aviso de abertura.");

        assertThat(montador.montar(processo, SEM_IMAGENS).dgtAct().diarioRepublicaSeries2Act().actSummary())
                .isEqualTo("[MOCK:REJEITAR] Aviso de abertura.");
    }

    @Test
    void deveSerializarComOsNomesETiposDoExemploSerie2DoContrato() throws Exception {
        JsonNode dgtAct = OBJECT_MAPPER.valueToTree(montador.montar(processoCompleto(), SEM_IMAGENS)).get("DGTAct");

        assertThat(dgtAct.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("UserEnrollmentAgreementIdentifier", "DiarioRepublicaSeries2Act",
                        "ImageURLList", "FileURLList");
        assertThat(dgtAct.get("UserEnrollmentAgreementIdentifier").isTextual()).isTrue();
        assertThat(dgtAct.get("ImageURLList").isArray()).isTrue();
        assertThat(dgtAct.get("FileURLList")).hasSize(2);

        JsonNode serie2 = dgtAct.get("DiarioRepublicaSeries2Act");
        assertThat(serie2.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("ActTypeId", "PartId", "InformationTypeIdentifier", "ActSummary",
                        "SignatureDate", "LegalProvision");
        assertThat(serie2.get("ActTypeId").isIntegralNumber()).isTrue();
        assertThat(serie2.get("PartId").isIntegralNumber()).isTrue();
        assertThat(serie2.get("SignatureDate").asText()).isEqualTo("2025-12-31");
    }

    /** Restantes servidões: o Procedimento não tem série e a do processo decide a variante. */
    private static Processo processoComSerieEscolhida(String serieEscolhida) {
        Processo processo = processoCompleto(null);
        processo.getTipoAto().setIncmActTypeIdSerie1(ACT_TYPE_ID_SERIE_1);
        processo.setPublicarSuplemento(false);
        processo.setSerieDr(serieEscolhida);
        return processo;
    }

    @Test
    void comASegundaCEscolhidaNoProcessoMontaAVarianteDaSegundaSerieComAParte() {
        Processo processo = processoComSerieEscolhida("SEGUNDA_C");

        assertThatNoException().isThrownBy(() -> montador.validar(processo));
        SubmitNewActRequest.DgtAct dgtAct = montador.montar(processo, SEM_IMAGENS).dgtAct();

        assertThat(dgtAct.diarioRepublicaSeries1Act()).isNull();
        assertThat(dgtAct.diarioRepublicaSeries2Act()).isNotNull();
        assertThat(dgtAct.diarioRepublicaSeries2Act().partId()).isEqualTo(PART_ID);
        assertThat(dgtAct.diarioRepublicaSeries2Act().actTypeId()).isEqualTo(ACT_TYPE_ID_SERIE_2);
    }

    @Test
    void comAPrimeiraEscolhidaNoProcessoMontaAVarianteDaPrimeiraSerie() {
        Processo processo = processoComSerieEscolhida("PRIMEIRA");

        assertThatNoException().isThrownBy(() -> montador.validar(processo));
        SubmitNewActRequest.DgtAct dgtAct = montador.montar(processo, SEM_IMAGENS).dgtAct();

        assertThat(dgtAct.diarioRepublicaSeries2Act()).isNull();
        assertThat(dgtAct.diarioRepublicaSeries1Act().actTypeId()).isEqualTo(ACT_TYPE_ID_SERIE_1);
    }

    @Test
    void semSerieNoProcedimentoNemNoProcessoRecusaComASerieEmFalta() {
        assertThat(camposEmFalta(processoComSerieEscolhida(null))).contains("serieDr");
    }

    @Test
    void aSerieDoProcedimentoPrevaleceSobreUmaGravadaNoProcesso() {
        Processo processo = processoCompleto("SEGUNDA_H");
        processo.setSerieDr("PRIMEIRA");

        assertThat(montador.montar(processo, SEM_IMAGENS).dgtAct().diarioRepublicaSeries2Act()).isNotNull();
    }
}
