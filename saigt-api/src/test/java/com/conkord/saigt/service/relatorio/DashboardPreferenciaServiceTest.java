package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PreferenciaKpiRequest;
import com.conkord.saigt.dto.relatorio.PreferenciaKpiResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.relatorio.DashboardPreferencia;
import com.conkord.saigt.persistence.relatorio.DashboardPreferenciaRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardPreferenciaServiceTest {

    private static final UUID MASTER_ID = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-12T10:00:00Z");

    @Mock
    private DashboardPreferenciaRepository repository;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    private ValidatorFactory validatorFactory;
    private DashboardPreferenciaService service;

    @BeforeEach
    void preparar() {
        AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
        doCallRealMethod().when(autorizacaoService).verificarPerfilMaster(any());
        validatorFactory = Validation.buildDefaultValidatorFactory();
        service = new DashboardPreferenciaService(repository, autorizacaoService, contextoUtilizadorResolver,
                validatorFactory.getValidator());
        when(contextoUtilizadorResolver.resolver()).thenReturn(interno(MASTER_ID, PerfilInterno.MASTER));
    }

    @AfterEach
    void fechar() {
        validatorFactory.close();
    }

    private static ContextoUtilizador interno(UUID id, PerfilInterno perfil) {
        return new ContextoUtilizador(id, "AD_DGT", perfil, null, "DGT", null, null, null, null, null, null);
    }

    private static ContextoUtilizador externo() {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "500051062", "ENTIDADE", null, null, null, null,
                TipoEntidade.CM, 1L);
    }

    private static DashboardPreferencia gravada(String codigo, boolean visivel, int ordem) {
        return new DashboardPreferencia(MASTER_ID, codigo, visivel, (short) ordem, AGORA);
    }

    private void comGravadas(DashboardPreferencia... preferencias) {
        when(repository.findByIdUtilizadorIdOrderByOrdemAsc(MASTER_ID)).thenReturn(List.of(preferencias));
    }

    private static List<String> codigos(List<PreferenciaKpiResponse> lista) {
        return lista.stream().map(PreferenciaKpiResponse::kpiCodigo).toList();
    }

    private static List<String> catalogoSem(String... excluidos) {
        List<String> fora = List.of(excluidos);
        return Arrays.stream(KpiCodigo.values()).map(Enum::name).filter(codigo -> !fora.contains(codigo)).toList();
    }

    @Test
    void semPreferenciasDevolveAOmissaoComTodosVisiveisPelaOrdemDoCatalogo() {
        comGravadas();

        List<PreferenciaKpiResponse> resultado = service.obter();

        assertThat(resultado).hasSize(KpiCodigo.values().length);
        assertThat(resultado).extracting(PreferenciaKpiResponse::kpiCodigo, PreferenciaKpiResponse::designacao,
                        PreferenciaKpiResponse::visivel, PreferenciaKpiResponse::ordem)
                .containsExactlyElementsOf(IntStream.range(0, KpiCodigo.values().length)
                        .mapToObj(i -> tuple(KpiCodigo.values()[i].name(), KpiCodigo.values()[i].getDesignacao(), true, i))
                        .toList());
        assertThat(service.configuracaoPorOmissao()).isEqualTo(resultado);
    }

    @Test
    void comPreferenciasCompletasDevolveAsPelaOrdemGravada() {
        List<KpiCodigo> invertido = new ArrayList<>(Arrays.asList(KpiCodigo.values()));
        Collections.reverse(invertido);
        comGravadas(IntStream.range(0, invertido.size())
                .mapToObj(i -> gravada(invertido.get(i).name(), i % 2 == 0, i * 10))
                .toArray(DashboardPreferencia[]::new));

        List<PreferenciaKpiResponse> resultado = service.obter();

        assertThat(codigos(resultado)).containsExactlyElementsOf(invertido.stream().map(Enum::name).toList());
        assertThat(resultado).extracting(PreferenciaKpiResponse::ordem).containsExactly(0, 10, 20, 30, 40, 50, 60, 70, 80, 90);
        assertThat(resultado).extracting(PreferenciaKpiResponse::visivel)
                .containsExactly(true, false, true, false, true, false, true, false, true, false);
    }

    @Test
    void comPreferenciasParciaisAcrescentaOsRestantesPelaOrdemDoCatalogoAsSeguirAMaiorOrdem() {
        comGravadas(gravada("TAXA_REJEICAO", true, 0), gravada("GRAFICO_MES", false, 4));

        List<PreferenciaKpiResponse> resultado = service.obter();

        List<String> esperados = new ArrayList<>(List.of("TAXA_REJEICAO", "GRAFICO_MES"));
        esperados.addAll(catalogoSem("TAXA_REJEICAO", "GRAFICO_MES"));
        assertThat(codigos(resultado)).containsExactlyElementsOf(esperados);
        assertThat(resultado).extracting(PreferenciaKpiResponse::ordem).containsExactly(0, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(resultado).extracting(PreferenciaKpiResponse::visivel)
                .containsExactly(true, false, true, true, true, true, true, true, true, true);
        assertThat(resultado.get(2).designacao()).isEqualTo(KpiCodigo.TOTAL_POR_TIPOLOGIA.getDesignacao());
    }

    @Test
    void ignoraAsPreferenciasDeCodigosRetiradosDoCatalogo() {
        comGravadas(gravada("ATIVIDADE_RECENTE", false, 0), gravada("PROCESSOS_POR_IGT", true, 1),
                gravada("A_CADUCAR", true, 2));

        List<PreferenciaKpiResponse> resultado = service.obter();

        assertThat(codigos(resultado)).doesNotContain("PROCESSOS_POR_IGT").hasSize(KpiCodigo.values().length)
                .startsWith("ATIVIDADE_RECENTE", "A_CADUCAR", "TOTAL_POR_TIPOLOGIA");
        assertThat(resultado.get(1).ordem()).isEqualTo(2);
        assertThat(resultado.get(2).ordem()).isEqualTo(3);
    }

    @Test
    void soComCodigosRetiradosDevolveAOmissao() {
        comGravadas(gravada("PROCESSOS_POR_IGT", false, 0));

        assertThat(service.obter()).isEqualTo(service.configuracaoPorOmissao());
    }

    @Test
    void substituirApagaAsDoChamadorAntesDeInserirEDevolveALeituraNova() {
        comGravadas(gravada("TAXA_REJEICAO", true, 0), gravada("GRAFICO_MES", false, 1));

        List<PreferenciaKpiResponse> resultado = service.substituir(List.of(
                new PreferenciaKpiRequest("TAXA_REJEICAO", true, 0), new PreferenciaKpiRequest("GRAFICO_MES", false, 1)));

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).apagarDoUtilizador(MASTER_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DashboardPreferencia>> gravadas = ArgumentCaptor.forClass(List.class);
        ordem.verify(repository).saveAllAndFlush(gravadas.capture());
        ordem.verify(repository).findByIdUtilizadorIdOrderByOrdemAsc(MASTER_ID);
        assertThat(gravadas.getValue()).extracting(p -> p.getId().getUtilizadorId(), DashboardPreferencia::getKpiCodigo,
                        DashboardPreferencia::isVisivel, DashboardPreferencia::getOrdem)
                .containsExactly(tuple(MASTER_ID, "TAXA_REJEICAO", true, (short) 0),
                        tuple(MASTER_ID, "GRAFICO_MES", false, (short) 1));
        assertThat(gravadas.getValue()).allSatisfy(p -> assertThat(p.getAtualizadoEm()).isNotNull());
        assertThat(codigos(resultado)).startsWith("TAXA_REJEICAO", "GRAFICO_MES").hasSize(KpiCodigo.values().length);
    }

    @Test
    void substituirComListaVaziaApagaTudoEVoltaAOmissao() {
        comGravadas();

        List<PreferenciaKpiResponse> resultado = service.substituir(List.of());

        verify(repository).apagarDoUtilizador(MASTER_ID);
        verify(repository).saveAllAndFlush(List.of());
        assertThat(resultado).isEqualTo(service.configuracaoPorOmissao());
    }

    static Stream<Arguments> pedidosInvalidos() {
        List<PreferenciaKpiRequest> demasiados = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            demasiados.add(new PreferenciaKpiRequest(KpiCodigo.values()[i % KpiCodigo.values().length].name(), true, i));
        }
        return Stream.of(
                Arguments.of(List.of(new PreferenciaKpiRequest("XPTO", true, 0)),
                        List.of(new CampoErro("[0].kpiCodigo", "KPI desconhecido: XPTO."))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, 3),
                                new PreferenciaKpiRequest("TAXA_REJEICAO", true, 3)),
                        List.of(new CampoErro("[1].ordem", "Posição repetida no dashboard."))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, 0),
                                new PreferenciaKpiRequest("GRAFICO_MES", false, 1)),
                        List.of(new CampoErro("[1].kpiCodigo", "KPI repetido: GRAFICO_MES."))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, -1)),
                        List.of(new CampoErro("[0].ordem", null))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, 32768)),
                        List.of(new CampoErro("[0].ordem", null))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", null, 0)),
                        List.of(new CampoErro("[0].visivel", null))),
                Arguments.of(List.of(new PreferenciaKpiRequest(" ", true, 0)),
                        List.of(new CampoErro("[0].kpiCodigo", null))),
                Arguments.of(List.of(new PreferenciaKpiRequest("X".repeat(61), true, 0)),
                        List.of(new CampoErro("[0].kpiCodigo", null))),
                Arguments.of(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, null)),
                        List.of(new CampoErro("[0].ordem", null))),
                Arguments.of(Collections.singletonList(null), List.of(new CampoErro("[0]", "Obrigatório."))),
                Arguments.of(demasiados, List.of(new CampoErro("corpo", "Há mais KPIs do que os do catálogo."),
                        new CampoErro("[10].kpiCodigo", "KPI repetido: TOTAL_POR_TIPOLOGIA."),
                        new CampoErro("[11].kpiCodigo", "KPI repetido: PROCESSOS_POR_ESTADO."))));
    }

    @ParameterizedTest
    @MethodSource("pedidosInvalidos")
    void pedidoInvalidoDa400ValidacaoComOCampoENadaEGravado(List<PreferenciaKpiRequest> pedido, List<CampoErro> esperados) {
        assertThatThrownBy(() -> service.substituir(pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(erro.getCampos()).extracting(CampoErro::path)
                            .containsExactlyElementsOf(esperados.stream().map(CampoErro::path).toList());
                    for (int i = 0; i < esperados.size(); i++) {
                        if (esperados.get(i).erro() != null) {
                            assertThat(erro.getCampos().get(i).erro()).isEqualTo(esperados.get(i).erro());
                        } else {
                            assertThat(erro.getCampos().get(i).erro()).isNotBlank();
                        }
                    }
                });
        verify(repository, never()).apagarDoUtilizador(any());
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void juntaTodasAsFalhasDoPedidoNumaSoExcecao() {
        assertThatThrownBy(() -> service.substituir(List.of(new PreferenciaKpiRequest("XPTO", true, 0),
                new PreferenciaKpiRequest("GRAFICO_MES", null, 0))))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> assertThat(erro.getCampos())
                        .extracting(CampoErro::path).containsExactly("[0].kpiCodigo", "[1].visivel", "[1].ordem"));
    }

    @Test
    void pedidoNuloDa400() {
        assertThatThrownBy(() -> service.substituir(null)).isInstanceOf(ValidacaoException.class);
        verify(repository, never()).apagarDoUtilizador(any());
    }

    static Stream<ContextoUtilizador> semAcesso() {
        return Stream.of(interno(UUID.randomUUID(), PerfilInterno.TECNICO),
                interno(UUID.randomUUID(), PerfilInterno.VISITANTE), externo());
    }

    @ParameterizedTest
    @MethodSource("semAcesso")
    void outrosPerfisEUtilizadoresExternosRecebem403(ContextoUtilizador contexto) {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);

        assertThatThrownBy(() -> service.obter()).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.substituir(List.of(new PreferenciaKpiRequest("GRAFICO_MES", true, 0))))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void aAutorizacaoVemAntesDaValidacao() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(interno(UUID.randomUUID(), PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.substituir(List.of(new PreferenciaKpiRequest("XPTO", true, -1))))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }
}
