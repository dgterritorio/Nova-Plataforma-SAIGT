package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.DinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.DinamicaConfigResponse;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoEstruturaServiceTest {

    private static final UUID MASTER_ID = UUID.randomUUID();
    private static final long GB5 = 5_368_709_120L;

    @Mock
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Mock
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Mock
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Mock
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Mock
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Mock
    private DinamicaRepository dinamicaRepository;
    @Mock
    private ProcessoRepository processoRepository;
    @Spy
    private CatalogoFormularios catalogoFormularios = new CatalogoFormularios();
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private AutorizacaoService autorizacaoService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private ConfiguracaoEstruturaService service;

    private final TipoInstrumento prot = comId(new TipoInstrumento("PROT", "Programa Regional", "PROGRAMA"), 1L);
    private final TipoInstrumento pdm = comId(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), 2L);
    private final TipoDinamica revisao = comId(new TipoDinamica("REVISAO", "Revisão"), 10L);
    private final TipoDinamica alteracao = comId(new TipoDinamica("ALTERACAO", "Alteração"), 11L);
    private final TipoProcedimento decisao = comId(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"), 20L);
    private final TipoProcedimento semFormulario = comId(new TipoProcedimento("SEM_FORMULARIO", "Sem formulário"), 21L);

    @BeforeEach
    void contextoDoMaster() {
        lenient().when(contextoUtilizadorResolver.resolver()).thenReturn(
                new ContextoUtilizador(MASTER_ID, "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null));
        lenient().when(tipoInstrumentoRepository.findById(1L)).thenReturn(Optional.of(prot));
        lenient().when(tipoInstrumentoRepository.findById(2L)).thenReturn(Optional.of(pdm));
        lenient().when(tipoDinamicaRepository.findById(10L)).thenReturn(Optional.of(revisao));
        lenient().when(tipoDinamicaRepository.findById(11L)).thenReturn(Optional.of(alteracao));
        lenient().when(tipoProcedimentoRepository.findById(20L)).thenReturn(Optional.of(decisao));
        lenient().when(tipoProcedimentoRepository.findById(21L)).thenReturn(Optional.of(semFormulario));
        lenient().when(dinamicaConfigRepository.save(any())).thenAnswer(inv -> comIdSeVazio(inv.getArgument(0), 100L));
        lenient().when(procedimentoConfigRepository.save(any())).thenAnswer(inv -> comIdSeVazio(inv.getArgument(0), 200L));
    }

    private static <T> T comId(T entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }

    private static <T> T comIdSeVazio(T entidade, Long id) {
        if (ReflectionTestUtils.getField(entidade, "id") == null) {
            ReflectionTestUtils.setField(entidade, "id", id);
        }
        return entidade;
    }

    private static DinamicaConfigRequest dinamica(long tipoInstrumentoId, long tipoDinamicaId,
                                                  AssociacaoInstrumentos associacao, boolean ativo) {
        return new DinamicaConfigRequest(tipoInstrumentoId, tipoDinamicaId, associacao, ativo);
    }

    private static ProcedimentoConfigRequest procedimento(long dinamicaConfigId, long tipoProcedimentoId, int ordem,
                                                          boolean obrigatorio, String serie, Long tamanho,
                                                          boolean repetivel, boolean ativo) {
        return new ProcedimentoConfigRequest(dinamicaConfigId, tipoProcedimentoId, ordem, obrigatorio, serie, tamanho,
                repetivel, false, ativo, null);
    }

    private InstrumentoDinamicaConfig configDinamica(long id, TipoInstrumento tipo, TipoDinamica tipoDinamica) {
        InstrumentoDinamicaConfig config = comId(new InstrumentoDinamicaConfig(tipo, tipoDinamica), id);
        lenient().when(dinamicaConfigRepository.findById(id)).thenReturn(Optional.of(config));
        return config;
    }

    private InstrumentoProcedimentoConfig configProcedimento(long id, InstrumentoDinamicaConfig dinamicaConfig,
                                                             TipoProcedimento tipo) {
        InstrumentoProcedimentoConfig config = comId(new InstrumentoProcedimentoConfig(dinamicaConfig, tipo, 1, "SEGUNDA_H"), id);
        lenient().when(procedimentoConfigRepository.findById(id)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void naoMasterNaoDeveLerNemGravarNemAuditar() {
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação.")).when(autorizacaoService)
                .verificarPerfilMaster(any());

        assertThatThrownBy(() -> service.listarDinamicas(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.criarDinamica(dinamica(1, 10, AssociacaoInstrumentos.UM, true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizarDinamica(100L, dinamica(1, 10, AssociacaoInstrumentos.UM, true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.listarProcedimentos(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.criarProcedimento(procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizarProcedimento(200L,
                procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(dinamicaConfigRepository, procedimentoConfigRepository, dinamicaRepository,
                processoRepository, auditoriaService);
    }

    @Test
    void criarDinamicaDeveGravarSemUsoEAuditar() {
        DinamicaConfigResponse resposta = service.criarDinamica(dinamica(1, 10, AssociacaoInstrumentos.UM, true));

        assertThat(resposta.id()).isEqualTo(100L);
        assertThat(resposta.tipoInstrumento().codigo()).isEqualTo("PROT");
        assertThat(resposta.tipoDinamica().nome()).isEqualTo("Revisão");
        assertThat(resposta.associacaoInstrumentos()).isEqualTo(AssociacaoInstrumentos.UM);
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.emUso()).isFalse();
        verify(auditoriaService).registar(eq("CONFIGURACAO"), eq("dinamicas-config:100"), eq("CRIAR"), eq(MASTER_ID),
                any());
        verifyNoInteractions(procedimentoConfigRepository);
    }

    @Test
    void parDeTiposJaConfiguradoDeveDar400NoTipoDinamicaSemGravar() {
        when(dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_Id(2L, 11L)).thenReturn(true);

        assertThatThrownBy(() -> service.criarDinamica(dinamica(2, 11, AssociacaoInstrumentos.UM, true)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("tipoDinamicaId");
                    assertThat(e.getCampos().get(0).erro())
                            .isEqualTo("Esta Dinâmica já está configurada para este tipo de Instrumento.");
                });
        verify(dinamicaConfigRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void tiposInexistentesDevemDar400ComOsDoisCampos() {
        when(tipoInstrumentoRepository.findById(99L)).thenReturn(Optional.empty());
        when(tipoDinamicaRepository.findById(98L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarDinamica(dinamica(99, 98, AssociacaoInstrumentos.UM, true)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("tipoInstrumentoId", "tipoDinamicaId"));
        verify(dinamicaConfigRepository, never()).save(any());
    }

    @Test
    void mudarOTipoDeDinamicaComDinamicasIniciadasDeveDar409SemGravar() {
        InstrumentoDinamicaConfig config = configDinamica(100L, pdm, alteracao);
        when(dinamicaRepository.configsComDinamicas(List.of(100L))).thenReturn(Set.of(100L));

        assertThatThrownBy(() -> service.atualizarDinamica(100L, dinamica(2, 10, AssociacaoInstrumentos.UM, true)))
                .isInstanceOf(ConfiguracaoEmUsoException.class);

        assertThat(config.getTipoDinamica()).isSameAs(alteracao);
        verify(dinamicaConfigRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void mudarSoAAssociacaoComDinamicasIniciadasDeveGravarEAuditarSoEsseCampo() {
        configDinamica(100L, pdm, alteracao);
        when(dinamicaRepository.configsComDinamicas(List.of(100L))).thenReturn(Set.of(100L));

        DinamicaConfigResponse resposta = service.atualizarDinamica(100L,
                dinamica(2, 11, AssociacaoInstrumentos.UM_OU_MAIS, true));

        assertThat(resposta.associacaoInstrumentos()).isEqualTo(AssociacaoInstrumentos.UM_OU_MAIS);
        assertThat(resposta.emUso()).isTrue();
        verify(auditoriaService).registar("CONFIGURACAO", "dinamicas-config:100", "ATUALIZAR", MASTER_ID,
                Map.of("associacaoInstrumentos", "UM_OU_MAIS"));
    }

    @Test
    void mudarAChaveSemUsoDeveGravarOsTiposNovos() {
        InstrumentoDinamicaConfig config = configDinamica(100L, pdm, alteracao);
        when(dinamicaRepository.configsComDinamicas(List.of(100L))).thenReturn(Set.of());

        DinamicaConfigResponse resposta = service.atualizarDinamica(100L, dinamica(1, 10, AssociacaoInstrumentos.UM, true));

        assertThat(config.getTipoInstrumento()).isSameAs(prot);
        assertThat(config.getTipoDinamica()).isSameAs(revisao);
        assertThat(resposta.tipoInstrumento().codigo()).isEqualTo("PROT");
        verify(dinamicaConfigRepository).save(config);
    }

    @Test
    void mudarAChaveParaUmParJaConfiguradoDeveDar400AntesDoEmUso() {
        configDinamica(100L, pdm, alteracao);
        when(dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(2L, 10L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarDinamica(100L, dinamica(2, 10, AssociacaoInstrumentos.UM, true)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("tipoDinamicaId"));
    }

    @Test
    void atualizarConfiguracaoInexistenteDeveDar404() {
        when(dinamicaConfigRepository.findById(5L)).thenReturn(Optional.empty());
        when(procedimentoConfigRepository.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarDinamica(5L, dinamica(1, 10, AssociacaoInstrumentos.UM, true)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.atualizarProcedimento(6L,
                procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarDinamicasDeveCalcularOEmUsoNumaSoConsulta() {
        InstrumentoDinamicaConfig a = comId(new InstrumentoDinamicaConfig(pdm, alteracao), 1L);
        InstrumentoDinamicaConfig b = comId(new InstrumentoDinamicaConfig(prot, revisao), 2L);
        when(dinamicaConfigRepository.pesquisar(null, true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2));
        when(dinamicaRepository.configsComDinamicas(List.of(1L, 2L))).thenReturn(Set.of(1L));

        Page<DinamicaConfigResponse> pagina = service.listarDinamicas(null, true, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(DinamicaConfigResponse::emUso).containsExactly(true, false);
        verify(dinamicaRepository, times(1)).configsComDinamicas(anyCollection());
    }

    @Test
    void paginaVaziaNaoDeveConsultarOUso() {
        when(procedimentoConfigRepository.pesquisar(7L, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.listarProcedimentos(7L, null, PageRequest.of(0, 20)).getContent()).isEmpty();
        verifyNoInteractions(processoRepository);
    }

    @Test
    void criarProcedimentoDeveGravarTodosOsCamposEDevolverOFormulario() {
        configDinamica(100L, prot, revisao);

        ProcedimentoConfigResponse resposta = service.criarProcedimento(
                new ProcedimentoConfigRequest(100L, 20L, 1, true, "SEGUNDA_H", GB5, false, false, true, true));

        assertThat(resposta.id()).isEqualTo(200L);
        assertThat(resposta.instrumentoDinamicaConfigId()).isEqualTo(100L);
        assertThat(resposta.tipoProcedimento().codigo()).isEqualTo("DECISAO_INICIO");
        assertThat(resposta.ordem()).isEqualTo(1);
        assertThat(resposta.obrigatorio()).isTrue();
        assertThat(resposta.serieDr()).isEqualTo("SEGUNDA_H");
        assertThat(resposta.tamanhoMaximoProcesso()).isEqualTo(GB5);
        assertThat(resposta.repetivel()).isFalse();
        assertThat(resposta.atoPontual()).isFalse();
        assertThat(resposta.formulario()).isEqualTo("INFORMACAO_GERAL");
        assertThat(resposta.emUso()).isFalse();
        assertThat(resposta.visivelNaDinamica()).isTrue();
        verify(auditoriaService).registar(eq("CONFIGURACAO"), eq("procedimentos-config:200"), eq("CRIAR"), eq(MASTER_ID),
                any());
    }

    @Test
    void procedimentoSemFormularioDeveDevolverFormularioNulo() {
        configDinamica(100L, prot, revisao);

        ProcedimentoConfigResponse resposta = service.criarProcedimento(
                procedimento(100, 21, 2, false, "SEGUNDA_C", null, true, true));

        assertThat(resposta.formulario()).isNull();
        assertThat(resposta.tamanhoMaximoProcesso()).isNull();
        assertThat(resposta.repetivel()).isTrue();
    }

    @Test
    void referenciasInexistentesDoProcedimentoDevemDar400NosCampos() {
        when(dinamicaConfigRepository.findById(404L)).thenReturn(Optional.empty());
        when(tipoProcedimentoRepository.findById(405L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarProcedimento(procedimento(404, 405, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("instrumentoDinamicaConfigId", "tipoProcedimentoId"));
        verify(procedimentoConfigRepository, never()).save(any());
    }

    @Test
    void procedimentoJaConfiguradoNaDinamicaDeveDar400NoTipoProcedimento() {
        configDinamica(100L, pdm, alteracao);
        when(procedimentoConfigRepository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_Id(100L, 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criarProcedimento(procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("tipoProcedimentoId");
                    assertThat(e.getCampos().get(0).erro()).isEqualTo("Este Procedimento já está configurado nesta Dinâmica.");
                });
        verify(procedimentoConfigRepository, never()).save(any());
    }

    @Test
    void mudarOTipoDeProcedimentoComProcessosDeveDar409SemGravar() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, dinamicaConfig, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of(200L));

        assertThatThrownBy(() -> service.atualizarProcedimento(200L,
                procedimento(100, 21, 1, true, "SEGUNDA_H", null, false, true)))
                .isInstanceOf(ConfiguracaoEmUsoException.class);

        assertThat(config.getTipoProcedimento()).isSameAs(decisao);
        verify(procedimentoConfigRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void mudarOsCamposQueNaoSaoChaveComProcessosDeveGravarEAuditarSoOQueMudou() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, dinamicaConfig, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of(200L));

        ProcedimentoConfigResponse resposta = service.atualizarProcedimento(200L,
                procedimento(100, 20, 1, false, "SEGUNDA_C", GB5, true, true));

        assertThat(resposta.emUso()).isTrue();
        assertThat(config.isObrigatorio()).isFalse();
        assertThat(config.isRepetivel()).isTrue();
        assertThat(config.getTamanhoMaximoProcesso()).isEqualTo(GB5);
        assertThat(config.getSerieDr()).isEqualTo("SEGUNDA_C");
        verify(auditoriaService).registar("CONFIGURACAO", "procedimentos-config:200", "ATUALIZAR", MASTER_ID,
                Map.of("obrigatorio", "false", "serieDr", "SEGUNDA_C", "tamanhoMaximoProcesso", String.valueOf(GB5),
                        "repetivel", "true"));
    }

    @Test
    void procedimentoSemSerieDeveAceitarAtribuirUmaSerieNoBackoffice() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, dinamicaConfig, decisao);
        config.setSerieDr(null);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of(200L));

        service.atualizarProcedimento(200L, procedimento(100, 20, 1, true, "PRIMEIRA", null, false, true));

        assertThat(config.getSerieDr()).isEqualTo("PRIMEIRA");
        verify(auditoriaService).registar("CONFIGURACAO", "procedimentos-config:200", "ATUALIZAR", MASTER_ID,
                Map.of("serieDr", "PRIMEIRA"));
    }

    @Test
    void desativarProcedimentoComProcessosDeveGravarSoOAtivo() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, dinamicaConfig, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of(200L));

        service.atualizarProcedimento(200L, procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, false));

        assertThat(config.isAtivo()).isFalse();
        verify(auditoriaService).registar("CONFIGURACAO", "procedimentos-config:200", "ATUALIZAR", MASTER_ID,
                Map.of("ativo", "false"));
    }

    @Test
    void mudarAChaveDoProcedimentoSemProcessosDeveGravarAsReferenciasNovas() {
        InstrumentoDinamicaConfig origem = configDinamica(100L, pdm, alteracao);
        InstrumentoDinamicaConfig destino = configDinamica(101L, prot, revisao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, origem, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of());

        service.atualizarProcedimento(200L, procedimento(101, 21, 3, true, "SEGUNDA_H", null, false, true));

        assertThat(config.getInstrumentoDinamicaConfig()).isSameAs(destino);
        assertThat(config.getTipoProcedimento()).isSameAs(semFormulario);
        assertThat(config.getOrdem()).isEqualTo(3);
        verify(procedimentoConfigRepository).save(config);
    }

    @Test
    void visivelNaDinamicaDeveMudarSoQuandoVemNoPedidoEFicarNaAuditoria() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        InstrumentoProcedimentoConfig config = configProcedimento(200L, dinamicaConfig, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of(200L));

        ProcedimentoConfigResponse resposta = service.atualizarProcedimento(200L,
                new ProcedimentoConfigRequest(100L, 20L, 1, true, "SEGUNDA_H", null, false, false, true, true));
        service.atualizarProcedimento(200L, procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true));

        assertThat(resposta.visivelNaDinamica()).isTrue();
        assertThat(config.isVisivelNaDinamica()).isTrue();
        verify(auditoriaService).registar("CONFIGURACAO", "procedimentos-config:200", "ATUALIZAR", MASTER_ID,
                Map.of("visivelNaDinamica", "true"));
    }

    @Test
    void putSemAlteracoesNaoDeveAuditar() {
        InstrumentoDinamicaConfig dinamicaConfig = configDinamica(100L, pdm, alteracao);
        configProcedimento(200L, dinamicaConfig, decisao);
        when(processoRepository.configsComProcessos(List.of(200L))).thenReturn(Set.of());
        when(dinamicaRepository.configsComDinamicas(List.of(100L))).thenReturn(Set.of());

        service.atualizarProcedimento(200L, procedimento(100, 20, 1, true, "SEGUNDA_H", null, false, true));
        service.atualizarDinamica(100L, dinamica(2, 11, AssociacaoInstrumentos.UM, true));

        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(), any());
    }
}
