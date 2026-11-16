package com.conkord.saigt.service.instrumento;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.dto.instrumento.InformacaoBaseTextoAtoRequest;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaducidadeDinamicaServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 2);

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private final CaducidadeDinamicaService servico =
            new CaducidadeDinamicaService(dinamicaRepository, auditoriaService, publicador, new ObjectMapper());

    private final UUID id = UUID.randomUUID();

    private Dinamica dinamica(EstadoDinamica estado, LocalDate dataCaducidade, String... alertas) {
        Dinamica dinamica = new Dinamica();
        ReflectionTestUtils.setField(dinamica, "id", id);
        dinamica.setEstado(estado);
        dinamica.setDataCaducidade(dataCaducidade);
        dinamica.setAlertasEnviados(alertas.length == 0 ? null : List.of(alertas));
        when(dinamicaRepository.findById(id)).thenReturn(Optional.of(dinamica));
        when(dinamicaRepository.registarAlertas(any(), anyList())).thenReturn(1);
        when(dinamicaRepository.marcarCaducada(id)).thenReturn(1);
        return dinamica;
    }

    private AlertaCaducidadeDinamicaEvento eventoPublicado() {
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(publicador).publishEvent(evento.capture());
        return (AlertaCaducidadeDinamicaEvento) evento.getValue();
    }

    @Test
    void deveRegistarUmMesSemCaducarQuandoADataEDaquiAExatamenteUmMes() {
        LocalDate data = HOJE.plusMonths(1);
        dinamica(EstadoDinamica.EM_CURSO, data);

        Optional<LimiarCaducidade> resultado = servico.avaliar(id, HOJE);

        assertThat(resultado).contains(LimiarCaducidade.UM_MES);
        verify(dinamicaRepository).registarAlertas(id, List.of("UM_MES"));
        verify(dinamicaRepository, never()).marcarCaducada(any());
        verifyNoInteractions(auditoriaService);
        assertThat(eventoPublicado()).isEqualTo(new AlertaCaducidadeDinamicaEvento(id, LimiarCaducidade.UM_MES, data));
    }

    @Test
    void naoDeveFazerNadaNoDiaSeguinteQuandoOLimiarJaEstaRegistado() {
        dinamica(EstadoDinamica.EM_CURSO, HOJE.plusMonths(1), "UM_MES");

        Optional<LimiarCaducidade> resultado = servico.avaliar(id, HOJE.plusDays(1));

        assertThat(resultado).isEmpty();
        verify(dinamicaRepository, never()).registarAlertas(any(), anyList());
        verifyNoInteractions(publicador, auditoriaService);
    }

    @Test
    void deveAcrescentarQuinzeDiasAosAlertasJaRegistados() {
        LocalDate data = HOJE.plusDays(15);
        dinamica(EstadoDinamica.EM_CURSO, data, "UM_MES");

        Optional<LimiarCaducidade> resultado = servico.avaliar(id, HOJE);

        assertThat(resultado).contains(LimiarCaducidade.QUINZE_DIAS);
        verify(dinamicaRepository).registarAlertas(id, List.of("UM_MES", "QUINZE_DIAS"));
        verify(dinamicaRepository, never()).marcarCaducada(any());
        assertThat(eventoPublicado().limiar()).isEqualTo(LimiarCaducidade.QUINZE_DIAS);
    }

    @Test
    void deveCaducarAuditarEPublicarDataQuandoADataEHoje() {
        dinamica(EstadoDinamica.EM_CURSO, HOJE, "UM_MES", "QUINZE_DIAS");

        Optional<LimiarCaducidade> resultado = servico.avaliar(id, HOJE);

        assertThat(resultado).contains(LimiarCaducidade.DATA);
        verify(dinamicaRepository).registarAlertas(id, List.of("UM_MES", "QUINZE_DIAS", "DATA"));
        verify(dinamicaRepository).marcarCaducada(id);
        verify(auditoriaService).registarSistema("DINAMICA", id.toString(), "CADUCIDADE",
                new TransicaoEstado("EM_CURSO", "CADUCADA"), Map.of("dataCaducidade", HOJE));
        assertThat(eventoPublicado()).isEqualTo(new AlertaCaducidadeDinamicaEvento(id, LimiarCaducidade.DATA, HOJE));
    }

    @Test
    void deveRegistarOsTresLimiaresCaducarEPublicarSoDataQuandoADataJaPassou() {
        LocalDate data = HOJE.minusDays(10);
        dinamica(EstadoDinamica.EM_CURSO, data);

        Optional<LimiarCaducidade> resultado = servico.avaliar(id, HOJE);

        assertThat(resultado).contains(LimiarCaducidade.DATA);
        verify(dinamicaRepository).registarAlertas(id, List.of("UM_MES", "QUINZE_DIAS", "DATA"));
        verify(dinamicaRepository).marcarCaducada(id);
        assertThat(eventoPublicado()).isEqualTo(new AlertaCaducidadeDinamicaEvento(id, LimiarCaducidade.DATA, data));
    }

    @Test
    void deveAtingirUmMesNoFimDeMesQuandoOMesAnteriorTemMenosDias() {
        dinamica(EstadoDinamica.EM_CURSO, LocalDate.of(2026, 3, 31));

        assertThat(servico.avaliar(id, LocalDate.of(2026, 2, 28))).contains(LimiarCaducidade.UM_MES);
    }

    @Test
    void naoDeveAtingirUmMesUmDiaAntesDoFimDeMesEmAnoBissexto() {
        dinamica(EstadoDinamica.EM_CURSO, LocalDate.of(2028, 3, 31));

        assertThat(servico.avaliar(id, LocalDate.of(2028, 2, 28))).isEmpty();
        assertThat(servico.avaliar(id, LocalDate.of(2028, 2, 29))).contains(LimiarCaducidade.UM_MES);
    }

    @Test
    void naoDeveLerNemAlterarUmaDinamicaSemDataDeCaducidade() {
        dinamica(EstadoDinamica.EM_CURSO, null);

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        verify(dinamicaRepository, never()).registarAlertas(any(), anyList());
        verifyNoInteractions(publicador, auditoriaService);
    }

    @Test
    void naoDeveAlterarUmaDinamicaTerminadaComDataPassada() {
        dinamica(EstadoDinamica.TERMINADA, HOJE.minusDays(30));

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        verify(dinamicaRepository, never()).registarAlertas(any(), anyList());
        verify(dinamicaRepository, never()).marcarCaducada(any());
        verifyNoInteractions(publicador, auditoriaService);
    }

    @Test
    void naoDeveFazerNadaQuandoADinamicaNaoExiste() {
        when(dinamicaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        verifyNoInteractions(publicador, auditoriaService);
    }

    @Test
    void naoDeveAuditarNemPublicarQuandoADinamicaMudouDeEstadoAntesDeRegistarOsAlertas() {
        dinamica(EstadoDinamica.EM_CURSO, HOJE);
        when(dinamicaRepository.registarAlertas(any(), anyList())).thenReturn(0);

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        verify(dinamicaRepository, never()).marcarCaducada(any());
        verifyNoInteractions(publicador, auditoriaService);
    }

    @Test
    void naoDeveAuditarNemPublicarQuandoMarcarCaducadaDevolveZero() {
        dinamica(EstadoDinamica.EM_CURSO, HOJE);
        when(dinamicaRepository.marcarCaducada(id)).thenReturn(0);

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        verifyNoInteractions(publicador, auditoriaService);
    }

    private static final LocalDate PUBLICACAO = LocalDate.of(2026, 10, 24);

    private static String dadosComPrazo(Integer anos, Integer meses, Integer dias) {
        return "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":" + anos + ",\"prazoMeses\":" + meses
                + ",\"prazoDias\":" + dias + ",\"avaliacaoAmbiental\":false}}}";
    }

    private Processo publicado(String codigoTipoProcedimento, LocalDate dataPublicacao, String dadosFormulario) {
        Dinamica dinamica = new Dinamica();
        ReflectionTestUtils.setField(dinamica, "id", id);
        dinamica.setEstado(EstadoDinamica.EM_CURSO);
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(null,
                new TipoProcedimento(codigoTipoProcedimento, "Procedimento"), 1, null);
        Processo processo = new Processo(dinamica, config, null, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setDataPublicacaoDr(dataPublicacao);
        processo.setDadosFormulario(dadosFormulario);
        return processo;
    }

    @Test
    void deveSomarOPrazoDoTextoDoAtoADataDePublicacaoDaDecisaoDeInicio() {
        Processo processo = publicado("DECISAO_INICIO", PUBLICACAO, dadosComPrazo(2, 3, 10));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2029, 2, 3));
        verifyNoInteractions(dinamicaRepository, auditoriaService, publicador);
    }

    @Test
    void deveSomarOsMesesAntesDosDias() {
        // Com os dias primeiro daria 28/02/2027.
        Processo processo = publicado("DECISAO_INICIO", LocalDate.of(2027, 1, 30), dadosComPrazo(0, 1, 1));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void deveContarComoZeroOsValoresDoPrazoQueVemNulos() {
        Processo processo = publicado("DECISAO_INICIO", PUBLICACAO, dadosComPrazo(null, 6, null));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2027, 4, 24));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":null,\"prazoMeses\":null,\"prazoDias\":null}}}",
            "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":0,\"prazoMeses\":0,\"prazoDias\":0}}}",
            "{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2}}}",
            "{\"informacaoBase\":",
            ""})
    void naoDeveCalcularADataSemPrazoNoTextoDoAto(String dadosFormulario) {
        Processo processo = publicado("DECISAO_INICIO", PUBLICACAO, dadosFormulario);

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isNull();
    }

    @Test
    void naoDeveCalcularNemLancarSemDataDePublicacao() {
        Processo processo = publicado("DECISAO_INICIO", null, dadosComPrazo(2, 0, 0));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isNull();
    }

    @Test
    void naoDeveMudarADataNaPublicacaoDeOutroProcedimento() {
        Processo processo = publicado("ABERTURA_DISCUSSAO_PUBLICA", PUBLICACAO, dadosComPrazo(1, 0, 0));
        processo.getDinamica().setDataCaducidade(LocalDate.of(2028, 1, 1));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    void deveEscreverPorCimaDaDataAnteriorSemMexerNosAlertasEnviados() {
        Processo processo = publicado("DECISAO_INICIO", PUBLICACAO, dadosComPrazo(1, 0, 0));
        processo.getDinamica().setDataCaducidade(LocalDate.of(2026, 12, 1));
        processo.getDinamica().setAlertasEnviados(List.of("UM_MES"));

        servico.definirNaPublicacao(processo);

        assertThat(processo.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2027, 10, 24));
        assertThat(processo.getDinamica().getAlertasEnviados()).containsExactly("UM_MES");
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void prazoValidoDeveRecusarPrazoAusenteOuTodoAZero() {
        assertThat(CaducidadeDinamicaService.prazoValido(null)).isEmpty();
        assertThat(CaducidadeDinamicaService.prazoValido(new InformacaoBaseTextoAtoRequest(null, null, null, true))).isEmpty();
        assertThat(CaducidadeDinamicaService.prazoValido(new InformacaoBaseTextoAtoRequest(0, 0, 0, true))).isEmpty();
    }

    @Test
    void prazoValidoDeveAceitarUmSoValorPositivo() {
        assertThat(CaducidadeDinamicaService.prazoValido(new InformacaoBaseTextoAtoRequest(0, null, 1, null)))
                .contains(Period.ofDays(1));
    }

    @Test
    void soADecisaoDeInicioDefineACaducidade() {
        assertThat(CaducidadeDinamicaService.defineCaducidade("DECISAO_INICIO")).isTrue();
        assertThat(CaducidadeDinamicaService.defineCaducidade("ABERTURA_DISCUSSAO_PUBLICA")).isFalse();
    }

    @Test
    void soAProrrogacaoDoAtoRecalculaACaducidade() {
        assertThat(CaducidadeDinamicaService.recalculaCaducidade("PRORROGACAO_ATO")).isTrue();
        assertThat(CaducidadeDinamicaService.recalculaCaducidade("PRORROGACAO_MP")).isFalse();
        assertThat(CaducidadeDinamicaService.defineCaducidade("PRORROGACAO_ATO")).isFalse();
    }

    // ---------- Prorrogação do ato ----------

    private final ListAppender<ILoggingEvent> registos = new ListAppender<>();

    @BeforeEach
    void ouvirOLog() {
        registos.start();
        ((Logger) LoggerFactory.getLogger(CaducidadeDinamicaService.class)).addAppender(registos);
    }

    @AfterEach
    void deixarDeOuvirOLog() {
        ((Logger) LoggerFactory.getLogger(CaducidadeDinamicaService.class)).detachAppender(registos);
    }

    private List<String> avisos() {
        return registos.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Dinâmica `EM_CURSO` com a Decisão de início publicada a 2017-11-01 e a data de caducidade a 2018-11-01. */
    private record Cenario(Dinamica dinamica, Processo decisao) {
    }

    private Cenario dinamicaComDecisaoPublicada() {
        Processo decisao = publicado("DECISAO_INICIO", LocalDate.of(2017, 11, 1), dadosComPrazo(1, 0, 0));
        servico.definirNaPublicacao(decisao, HOJE);
        assertThat(decisao.getDinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        return new Cenario(decisao.getDinamica(), decisao);
    }

    private Processo prorrogacao(Dinamica dinamica, String codigo, Processo visado, LocalDate dataPublicacao,
                                 String dadosFormulario) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(null,
                new TipoProcedimento(codigo, "Prorrogação"), 104, null);
        Processo processo = new Processo(dinamica, config, null, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setProcessoReferido(visado);
        processo.setDataPublicacaoDr(dataPublicacao);
        processo.setDadosFormulario(dadosFormulario);
        return processo;
    }

    private Processo prorrogacaoDaDecisao(Cenario cenario, LocalDate dataPublicacao, String dadosFormulario) {
        return prorrogacao(cenario.dinamica(), "PRORROGACAO_ATO", cenario.decisao(), dataPublicacao, dadosFormulario);
    }

    @Test
    void deveRecalcularADataComAPublicacaoEOPrazoDaProrrogacaoDaDecisaoDeInicio() {
        Cenario cenario = dinamicaComDecisaoPublicada();

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, LocalDate.of(2018, 11, 1), dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2019, 11, 1));
        assertThat(avisos()).isEmpty();
        verifyNoInteractions(dinamicaRepository, auditoriaService, publicador);
    }

    @Test
    void deveSomarAnosDepoisMesesDepoisDiasNaProrrogacao() {
        Cenario cenario = dinamicaComDecisaoPublicada();

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, LocalDate.of(2026, 1, 31), dadosComPrazo(1, 2, 3)), HOJE);

        // 2026-01-31 + 1 ano = 2027-01-31; + 2 meses = 2027-03-31; + 3 dias = 2027-04-03.
        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2027, 4, 3));
    }

    @Test
    void cadaProrrogacaoPublicadaRecalculaEValeAUltimaMesmoQueMaisCurta() {
        Cenario cenario = dinamicaComDecisaoPublicada();

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, LocalDate.of(2018, 11, 1), dadosComPrazo(2, 0, 0)), HOJE);
        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2020, 11, 1));

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, LocalDate.of(2019, 3, 15), dadosComPrazo(0, 6, 0)), HOJE);
        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2019, 9, 15));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":0,\"prazoMeses\":0,\"prazoDias\":0}}}",
            "{\"informacaoBase\":{\"textoAto\":{\"prazoAnos\":null,\"prazoMeses\":null,\"prazoDias\":null}}}",
            "{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2}}}"})
    void naoDeveMudarADataNumaProrrogacaoSemPrazoEDeixaUmAvisoComOProcessoEADinamica(String dadosFormulario) {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Processo prorrogacao = prorrogacaoDaDecisao(cenario, LocalDate.of(2018, 11, 1), dadosFormulario);

        servico.definirNaPublicacao(prorrogacao, HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem prazo do Texto do ato")
                .contains("processoId=" + prorrogacao.getId()).contains("dinamicaId=" + id);
    }

    @Test
    void naoDeveMudarADataNumaProrrogacaoSemDataDePublicacaoEDeixaUmAviso() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Processo prorrogacao = prorrogacaoDaDecisao(cenario, null, dadosComPrazo(1, 0, 0));

        servico.definirNaPublicacao(prorrogacao, HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem data no DR")
                .contains("processoId=" + prorrogacao.getId()).contains("dinamicaId=" + id);
    }

    @Test
    void naoDeveMudarADataNumaProrrogacaoRegistadaExternamenteSemFormularioNemAtoVisado() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Processo registoExterno = prorrogacao(cenario.dinamica(), "PRORROGACAO_ATO", null, LocalDate.of(2018, 11, 1), null);

        servico.definirNaPublicacao(registoExterno, HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem prazo do Texto do ato")
                .contains("processoId=" + registoExterno.getId()).contains("dinamicaId=" + id);
    }

    @Test
    void naoDeveMudarADataNumaProrrogacaoComPrazoMasSemAtoVisadoEDeixaUmAviso() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Processo semVisado = prorrogacao(cenario.dinamica(), "PRORROGACAO_ATO", null, LocalDate.of(2018, 11, 1),
                dadosComPrazo(1, 0, 0));

        servico.definirNaPublicacao(semVisado, HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).singleElement().asString().contains("sem ato visado")
                .contains("processoId=" + semVisado.getId()).contains("dinamicaId=" + id);
    }

    @Test
    void deveLimparOsAlertasQueDeixamDeEstarAtingidosComADataNova() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setDataCaducidade(HOJE.plusDays(10));
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS"));

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, HOJE, dadosComPrazo(0, 6, 0)), HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(HOJE.plusMonths(6));
        assertThat(cenario.dinamica().getAlertasEnviados()).isEmpty();
    }

    @Test
    void deveManterOsAlertasQueContinuamAtingidosComADataNova() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setDataCaducidade(HOJE.plusDays(5));
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS"));

        // Publicada há quase um ano com 1 ano de prazo: a data nova fica a 10 dias de hoje.
        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, HOJE.plusDays(10).minusYears(1), dadosComPrazo(1, 0, 0)),
                HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(HOJE.plusDays(10));
        assertThat(cenario.dinamica().getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS");
    }

    @Test
    void deveTirarSoOQuinzeDiasQuandoADataNovaFicaEntreQuinzeDiasEUmMes() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setDataCaducidade(HOJE.plusDays(5));
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS"));

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, HOJE.plusDays(20).minusYears(1), dadosComPrazo(1, 0, 0)),
                HOJE);

        assertThat(cenario.dinamica().getAlertasEnviados()).containsExactly("UM_MES");
    }

    @Test
    void oJobVoltaAAvisarUmMesAntesDaDataNovaDepoisDeOsAlertasSeremLimpos() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setDataCaducidade(HOJE.plusDays(10));
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS"));
        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, HOJE, dadosComPrazo(0, 6, 0)), HOJE);
        when(dinamicaRepository.findById(id)).thenReturn(Optional.of(cenario.dinamica()));
        when(dinamicaRepository.registarAlertas(any(), anyList())).thenReturn(1);

        assertThat(servico.avaliar(id, HOJE)).isEmpty();
        assertThat(servico.avaliar(id, HOJE.plusMonths(5))).contains(LimiarCaducidade.UM_MES);
        verify(dinamicaRepository).registarAlertas(id, List.of("UM_MES"));
    }

    @Test
    void naoDeveMexerNosAlertasQuandoADataNaoMuda() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS"));

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, LocalDate.of(2017, 11, 1), dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS");
    }

    @Test
    void limiaresAindaAtingidosMantemUmValorQueNaoEUmLimiar() {
        assertThat(CaducidadeDinamicaService.limiaresAindaAtingidos(List.of("UM_MES", "OUTRO"), HOJE.plusYears(1), HOJE))
                .containsExactly("OUTRO");
    }

    @Test
    void naoDeveMudarADataNumaProrrogacaoQueVisaUmaDiscussaoPublica() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Processo discussao = prorrogacao(cenario.dinamica(), "ABERTURA_DISCUSSAO_PUBLICA", null, LocalDate.of(2018, 3, 1), null);

        servico.definirNaPublicacao(prorrogacao(cenario.dinamica(), "PRORROGACAO_ATO", discussao, LocalDate.of(2018, 11, 1),
                dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).isEmpty();
    }

    @Test
    void naoDeveMudarADataNumaProrrogacaoQueVisaADecisaoDeInicioDeOutraDinamica() {
        Cenario cenario = dinamicaComDecisaoPublicada();
        Dinamica outra = new Dinamica();
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        Processo decisaoDeOutra = prorrogacao(outra, "DECISAO_INICIO", null, LocalDate.of(2017, 1, 1), null);

        servico.definirNaPublicacao(prorrogacao(cenario.dinamica(), "PRORROGACAO_ATO", decisaoDeOutra,
                LocalDate.of(2018, 11, 1), dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRORROGACAO_MP", "PRORROGACAO_NP", "PRORROGACAO_SUSPENSAO"})
    void naoDeveMudarADataNasProrrogacoesQueSaoDinamicasProprias(String codigo) {
        Cenario cenario = dinamicaComDecisaoPublicada();

        servico.definirNaPublicacao(prorrogacao(cenario.dinamica(), codigo, cenario.decisao(), LocalDate.of(2018, 11, 1),
                dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(avisos()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoDinamica.class, names = {"CADUCADA", "TERMINADA"})
    void naoDeveMudarOEstadoNemADataDeUmaDinamicaQueNaoEstaEmCurso(EstadoDinamica estado) {
        Cenario cenario = dinamicaComDecisaoPublicada();
        cenario.dinamica().setEstado(estado);
        cenario.dinamica().setAlertasEnviados(List.of("UM_MES", "QUINZE_DIAS", "DATA"));

        servico.definirNaPublicacao(prorrogacaoDaDecisao(cenario, HOJE, dadosComPrazo(1, 0, 0)), HOJE);

        assertThat(cenario.dinamica().getEstado()).isEqualTo(estado);
        assertThat(cenario.dinamica().getDataCaducidade()).isEqualTo(LocalDate.of(2018, 11, 1));
        assertThat(cenario.dinamica().getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS", "DATA");
        verifyNoInteractions(dinamicaRepository, auditoriaService, publicador);
    }
}
