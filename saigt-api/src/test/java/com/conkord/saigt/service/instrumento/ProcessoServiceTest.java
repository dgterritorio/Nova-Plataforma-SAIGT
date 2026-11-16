package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.documento.BloqueioDocumentosCorrecao;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.conkord.saigt.service.documento.CopiaAnexosNotificacaoService;
import com.conkord.saigt.service.documento.RetencaoRegistoExternoService;
import com.conkord.saigt.service.notificacao.NotificacaoOrigemService;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.service.idus.IdusProcessoService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.AtualizarDadosFormularioRequest;
import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseDeliberacaoRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseTextoAtoRequest;
import com.conkord.saigt.dto.instrumento.ReatribuirAutorRequest;
import com.conkord.saigt.dto.instrumento.RegistoExternoProcessoRequest;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.dto.instrumento.VersaoProcessoRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ElementosAfetadosEmFaltaException;
import com.conkord.saigt.exception.CampoBloqueadoException;
import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.DocumentosObrigatoriosEmFaltaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.IdusInvalidoException;
import com.conkord.saigt.exception.ItensPorReverException;
import com.conkord.saigt.exception.PcmAtorNaoAutorizadoException;
import com.conkord.saigt.exception.PcmEstadoInvalidoException;
import com.conkord.saigt.exception.PcmUtilizadorInvalidoException;
import com.conkord.saigt.exception.ProcessoEmCursoException;
import com.conkord.saigt.exception.ReferenciaInvalidaException;
import com.conkord.saigt.exception.TipoAtoNaoAutorizadoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.ConfirmacaoEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.VersaoDesatualizadaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.exception.DinamicaAssociadaInvalidaException;
import com.conkord.saigt.exception.DestinoInvalidoException;
import com.conkord.saigt.exception.DestinoSoDepositoException;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.notificacao.PedidoApoioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessoServiceTest {

    private static final long VERSAO = 0L;
    private static final VersaoProcessoRequest PEDIDO_VERSAO = new VersaoProcessoRequest(VERSAO);

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository =
            mock(InstrumentoProcedimentoConfigRepository.class);
    private final TipoAtoRepository tipoAtoRepository = mock(TipoAtoRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final DinamicaService dinamicaService = mock(DinamicaService.class);
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository = mock(UtilizadorEntidadeRepository.class);
    private final EntidadeRepository entidadeRepository = mock(EntidadeRepository.class);
    private final EnvioIncmService envioIncmService = mock(EnvioIncmService.class);
    private final CompletudeDocumentalService completudeDocumentalService = mock(CompletudeDocumentalService.class);
    private final ProcessoValidacaoService processoValidacaoService = mock(ProcessoValidacaoService.class);
    private final IdusProcessoService idusProcessoService = mock(IdusProcessoService.class);
    private final RejeicaoProcessoService rejeicaoProcessoService = mock(RejeicaoProcessoService.class);
    private final MetadadosMotuService metadadosMotuService = mock(MetadadosMotuService.class);
    private final BloqueioDocumentosCorrecao bloqueioDocumentosCorrecao = mock(BloqueioDocumentosCorrecao.class);
    private final RetencaoDocumentosService retencaoDocumentosService = mock(RetencaoDocumentosService.class);
    private final PedidoApoioService pedidoApoioService = mock(PedidoApoioService.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private static final Instant AGORA = Instant.parse("2026-11-13T10:00:00Z");
    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final CopiaAnexosNotificacaoService copiaAnexosNotificacaoService = mock(CopiaAnexosNotificacaoService.class);
    private final RetencaoRegistoExternoService retencaoRegistoExternoService = mock(RetencaoRegistoExternoService.class);

    private final DinamicaAssociadaService dinamicaAssociadaService = mock(DinamicaAssociadaService.class);
    /** Duplo do catálogo: nenhum formulário existente admite «só depósito»; os testes do caso verdadeiro forçam-no. */
    private final CatalogoFormularios catalogoDestino = spy(new CatalogoFormularios());
    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final DestinoProcessoService destinoProcessoService =
            new DestinoProcessoService(catalogoDestino, processoDocumentoRepository);
    /** Vazio por omissão: a coerência da Informação Base só corre nos testes que configuram os dois elementos. */
    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final NumeroDepositoService numeroDepositoService = mock(NumeroDepositoService.class);
    private final ProcessoService service = new ProcessoService(dinamicaRepository, instrumentoProcedimentoConfigRepository,
            tipoAtoRepository, processoRepository, utilizadorRepository,
            autorizacaoService, contextoUtilizadorResolver, auditoriaService, objectMapper, dinamicaService,
            utilizadorEntidadeRepository, envioIncmService, completudeDocumentalService,
            processoValidacaoService, new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper(),
                    new CatalogoFormularios(), null, null, documentoConfigRepository, numeroDepositoService),
            new CatalogoFormularios(),
            mock(CaducidadeDinamicaService.class), idusProcessoService, rejeicaoProcessoService, metadadosMotuService,
            entidadeRepository, bloqueioDocumentosCorrecao, retencaoDocumentosService, pedidoApoioService, publicador,
            new NotificacaoOrigemService(notificacaoRepository, Clock.fixed(AGORA, ZoneOffset.UTC)),
            copiaAnexosNotificacaoService, retencaoRegistoExternoService, dinamicaAssociadaService, destinoProcessoService,
            new AtosReferiveis(processoRepository), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));

    private UUID dinamicaId;
    private Dinamica dinamica;
    private Entidade entidade;
    private InstrumentoProcedimentoConfig config;
    private TipoAto tipoAto;
    private UUID utilizadorId;
    private Utilizador utilizador;
    private ContextoUtilizador contexto;
    private CriarProcessoRequest request;

    @BeforeEach
    void setUp() {
        // As camadas da gravação do formulário são as do autor (verificarAutoria, stubbed nos testes), salvo a PCM.
        doCallRealMethod().when(autorizacaoService).verificarPermissaoDeEditar(any(), any());
        // Sem documentos por validar, como num processo que chegou a VALIDADO_DGT; os testes que o negam dizem-no.
        when(processoValidacaoService.todosDocumentosValidados(any())).thenReturn(true);
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", pdm);

        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("ALTERACAO", "Alteração"));
        config = new InstrumentoProcedimentoConfig(dinamicaConfig, new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"), 1, "SEGUNDA_H");
        ReflectionTestUtils.setField(config, "id", 10L);

        dinamicaId = UUID.randomUUID();
        Utilizador criadorDaDinamica = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadorDaDinamica);
        ReflectionTestUtils.setField(dinamica, "id", dinamicaId);

        tipoAto = new TipoAto("COD-ATO", "Ato de Teste", 1);
        ReflectionTestUtils.setField(tipoAto, "id", 20L);

        utilizadorId = UUID.randomUUID();
        utilizador = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        ReflectionTestUtils.setField(utilizador, "id", utilizadorId);

        contexto = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), "ENTIDADE", null, null,
                "2015482", 1234567891234567L, TipoEntidade.CM, entidade.getId());
        request = new CriarProcessoRequest(10L, 20L);

        when(entidadeRepository.findById(1L)).thenReturn(Optional.of(entidade));
        when(dinamicaRepository.findById(dinamicaId)).thenReturn(Optional.of(dinamica));
        when(dinamicaRepository.findByIdParaCriarProcesso(dinamicaId)).thenReturn(Optional.of(dinamica));
        when(processoValidacaoService.todosRevistos(any())).thenReturn(true);
        when(rejeicaoProcessoService.registarRecusaDgt(any(), any(), any(), any()))
                .thenReturn(ProcessoRejeicao.builder().sequencia(1).build());
        when(processoValidacaoService.elementosPrevistos(any()))
                .thenReturn(Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(instrumentoProcedimentoConfigRepository.findById(10L)).thenReturn(Optional.of(config));
        when(tipoAtoRepository.findById(20L)).thenReturn(Optional.of(tipoAto));
        when(utilizadorRepository.findById(utilizadorId)).thenReturn(Optional.of(utilizador));
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.empty());
        when(processoRepository.saveAndFlush(any(Processo.class))).thenAnswer(invocation -> {
            Processo processo = invocation.getArgument(0);
            ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
            return processo;
        });
    }

    @Test
    void deveCriarPrimeiraTentativaEmSubmissaoQuandoElegivel() {
        Processo processo = service.criar(dinamicaId, request);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getOcorrencia()).isEqualTo(1);
        assertThat(processo.getVersao()).isZero();
        assertThat(processo.getOrigemDados()).isEqualTo("NATIVO");
        assertThat(processo.getCriadoPor()).isEqualTo(utilizador);
        assertThat(processo.getTipoAto()).isEqualTo(tipoAto);
    }

    @Test
    void deveCriarNaTentativaDaDinamicaComAPrimeiraOcorrenciaMesmoComODaTentativaAnterior() {
        Processo daPrimeira = new Processo(dinamica, config, utilizador, entidade);
        daPrimeira.setEstado(EstadoProcesso.PUBLICADO);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, 1, config))
                .thenReturn(Optional.of(daPrimeira));
        ReflectionTestUtils.setField(dinamica, "tentativa", 2);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, 2, config))
                .thenReturn(Optional.empty());

        Processo processo = service.criar(dinamicaId, request);

        assertThat(processo.getTentativa()).isEqualTo(2);
        assertThat(processo.getOcorrencia()).isEqualTo(1);
        assertThat(processo.getAproveitaAtosAnteriores()).isNull();
        assertThat(daPrimeira.getTentativa()).isEqualTo(1);
        verify(processoRepository, never())
                .findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, 1, config);
    }

    @Test
    void deveContarAOcorrenciaSeguinteDentroDaTentativaDepoisDeAnulado() {
        ReflectionTestUtils.setField(dinamica, "tentativa", 2);
        Processo anulado = new Processo(dinamica, config, utilizador, entidade);
        anulado.setEstado(EstadoProcesso.ANULADO);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, 2, config))
                .thenReturn(Optional.of(anulado));

        Processo processo = service.criar(dinamicaId, request);

        assertThat(processo.getTentativa()).isEqualTo(2);
        assertThat(processo.getOcorrencia()).isEqualTo(2);
    }

    @Test
    void deveCriarOProcessoNaEntidadeDaSessaoEnuncaNaDoInstrumento() {
        Processo processo = service.criar(dinamicaId, request);

        assertThat(processo.getEntidade()).isSameAs(entidade);
    }

    @Test
    void deveRecusarCriarProcessoQuandoASessaoNaoTemEntidadeId() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(utilizadorId, "INCM", null,
                entidade.getNipc(), "ENTIDADE", null, null, "2015482", 1234567891234567L, TipoEntidade.CM, null));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCriarProcessoQuandoAEntidadeDaSessaoNaoExiste() {
        when(entidadeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRegistarAuditoriaDeCriacaoDeProcesso() {
        Processo processo = service.criar(dinamicaId, request);

        verify(auditoriaService).registar("PROCESSO", processo.getId().toString(), "CRIACAO", utilizadorId, entidade.getId(), null, null);
    }

    @Test
    void deveCriarOIdusDoProcessoGravadoNaCriacao() {
        Processo processo = service.criar(dinamicaId, request);

        verify(idusProcessoService).criar(processo);
    }

    @Test
    void deveCriarASerieMotuDoProcessoGravadoNaCriacao() {
        Processo processo = service.criar(dinamicaId, request);

        verify(metadadosMotuService).criarSerie(processo);
    }

    @Test
    void deveDeixarSubirAFalhaDoIdusSemRegistarAuditoria() {
        when(idusProcessoService.criar(any(Processo.class)))
                .thenThrow(new IdusInvalidoException("Segmento Ordem_Dinâmica do IDUS inválido."));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(IdusInvalidoException.class);

        verifyNoInteractions(auditoriaService);
    }

    @Test
    void naoDeveTraduzirOIdusRepetidoNoConflitoDeTentativa() {
        when(idusProcessoService.criar(any(Processo.class)))
                .thenThrow(new DataIntegrityViolationException("uq_idus_processo_idus"));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveLancar404QuandoDinamicaNaoExiste() {
        UUID outraId = UUID.randomUUID();
        when(dinamicaRepository.findByIdParaCriarProcesso(outraId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(outraId, request)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePropagarO409DaCamada4QuandoBloqueadoPorSequenciamento() {
        doThrow(new EstadoInvalidoException("Procedimento indisponível.")).when(autorizacaoService)
                .verificarPossibilidadeDeAgir(contexto, dinamica, config);

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(EstadoInvalidoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
        verify(tipoAtoRepository, never()).findById(any());
    }

    @Test
    void devePropagarO403DaCamada3cQuandoTipoAtoNaoAutorizado() {
        doThrow(new AcaoNaoAutorizadaException("Tipo de ato não autorizado ao utilizador pela INCM."))
                .when(autorizacaoService).verificarAutorizacaoPublicacao(eq(contexto), any(Processo.class));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409QuandoUltimaTentativaEstaEmSubmissao() {
        Processo ultima = new Processo(dinamica, config, utilizador, entidade);
        ultima.setEstado(EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(ultima));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409QuandoUltimaTentativaEstaRejeitadaPelaDgt() {
        Processo ultima = new Processo(dinamica, config, utilizador, entidade);
        ultima.setEstado(EstadoProcesso.REJEITADO_DGT);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(ultima));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class);
    }

    @Test
    void deveDevolver409QuandoUltimaTentativaFoiAnuladaPelaIncm() {
        Processo anuladaIncm = new Processo(dinamica, config, utilizador, entidade);
        anuladaIncm.setEstado(EstadoProcesso.ANULADO_INCM);
        anuladaIncm.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(anuladaIncm));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409QuandoUltimaTentativaEstaEmValidacaoDgt() {
        Processo ultima = new Processo(dinamica, config, utilizador, entidade);
        ultima.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(ultima));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveCriarNovaOcorrenciaQuandoProcedimentoRepetivelTemAUltimaPublicada() {
        config.setRepetivel(true);
        Processo publicada = new Processo(dinamica, config, utilizador, entidade);
        publicada.setEstado(EstadoProcesso.PUBLICADO);
        publicada.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(publicada));

        Processo nova = service.criar(dinamicaId, request);

        assertThat(nova.getOcorrencia()).isEqualTo(2);
        assertThat(nova.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(publicada.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
    }

    /** Uma Retificação (ato pontual) na mesma Dinâmica do `config` (Decisão de início), com permissão. */
    private InstrumentoProcedimentoConfig retificacao() {
        InstrumentoProcedimentoConfig pontual = new InstrumentoProcedimentoConfig(config.getInstrumentoDinamicaConfig(),
                new TipoProcedimento("RETIFICACAO_ATO_ANEXOS", "Retificação do ato e anexos"), 102, "SEGUNDA_H");
        pontual.setObrigatorio(false);
        pontual.setAtoPontual(true);
        ReflectionTestUtils.setField(pontual, "id", 30L);
        when(instrumentoProcedimentoConfigRepository.findById(30L)).thenReturn(Optional.of(pontual));
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                dinamica, dinamica.getTentativa(), pontual)).thenReturn(Optional.empty());
        return pontual;
    }

    private Processo processoGravado(Dinamica daDinamica, InstrumentoProcedimentoConfig doProcedimento, EstadoProcesso estado) {
        Processo processo = new Processo(daDinamica, doProcedimento, utilizador, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(estado);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }

    @Test
    void deveCriarOAtoPontualComODecisaoDeInicioPublicadaComoAtoReferido() {
        InstrumentoProcedimentoConfig pontual = retificacao();
        Processo decisao = processoGravado(dinamica, config, EstadoProcesso.PUBLICADO);

        Processo processo = service.criar(dinamicaId, new CriarProcessoRequest(30L, null, decisao.getId()));

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getInstrumentoProcedimentoConfig()).isEqualTo(pontual);
        assertThat(processo.getProcessoReferido()).isEqualTo(decisao);
        assertThat(processo.getOcorrencia()).isEqualTo(1);
    }

    @Test
    void deveCriarOAtoPontualSobreUmaDecisaoDeInicioRegistadaExternamente() {
        retificacao();
        Processo decisao = processoGravado(dinamica, config, EstadoProcesso.PUBLICADO);
        decisao.setOrigemDados("REGISTO_EXTERNO");

        Processo processo = service.criar(dinamicaId, new CriarProcessoRequest(30L, null, decisao.getId()));

        assertThat(processo.getProcessoReferido()).isEqualTo(decisao);
    }

    @Test
    void deveRecusarOAtoPontualSemAtoReferidoComReferenciaInvalidaNoCampo() {
        retificacao();

        assertThatThrownBy(() -> service.criar(dinamicaId, new CriarProcessoRequest(30L)))
                .isInstanceOf(ReferenciaInvalidaException.class)
                .hasMessage("Ato referido inválido.")
                .hasFieldOrPropertyWithValue("codigo", "REFERENCIA_INVALIDA")
                .satisfies(e -> assertThat(((ReferenciaInvalidaException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("processoReferidoId"));
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarOAtoPontualSobreUmProcessoDeOutraDinamicaNaoPublicadoOuForaDaDecisaoEDaDiscussao() {
        retificacao();
        Dinamica outraDinamica = new Dinamica(dinamica.getInstrumento(), config.getInstrumentoDinamicaConfig(), "Outra", utilizador);
        ReflectionTestUtils.setField(outraDinamica, "id", UUID.randomUUID());
        Processo deOutraDinamica = processoGravado(outraDinamica, config, EstadoProcesso.PUBLICADO);
        Processo emSubmissao = processoGravado(dinamica, config, EstadoProcesso.EM_SUBMISSAO);
        InstrumentoProcedimentoConfig constituicaoCc = new InstrumentoProcedimentoConfig(config.getInstrumentoDinamicaConfig(),
                new TipoProcedimento("CONSTITUICAO_CC", "Constituição da Comissão Consultiva"), 2, "SEGUNDA_H");
        Processo daConstituicaoCc = processoGravado(dinamica, constituicaoCc, EstadoProcesso.PUBLICADO);
        UUID inexistente = UUID.randomUUID();

        for (UUID referido : List.of(deOutraDinamica.getId(), emSubmissao.getId(), daConstituicaoCc.getId(), inexistente)) {
            assertThatThrownBy(() -> service.criar(dinamicaId, new CriarProcessoRequest(30L, null, referido)))
                    .isInstanceOf(ReferenciaInvalidaException.class);
        }
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarOAtoReferidoNumProcedimentoQueNaoEAtoPontual() {
        Processo decisao = processoGravado(dinamica, config, EstadoProcesso.PUBLICADO);

        assertThatThrownBy(() -> service.criar(dinamicaId, new CriarProcessoRequest(10L, null, decisao.getId())))
                .isInstanceOf(ReferenciaInvalidaException.class);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarOSegundoAtoPontualDoMesmoTipoComOPrimeiroEmCursoEAceitaLoComOPrimeiroPublicado() {
        InstrumentoProcedimentoConfig pontual = retificacao();
        Processo decisao = processoGravado(dinamica, config, EstadoProcesso.PUBLICADO);
        Processo primeira = new Processo(dinamica, pontual, utilizador, entidade);
        primeira.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                dinamica, dinamica.getTentativa(), pontual)).thenReturn(Optional.of(primeira));
        CriarProcessoRequest pedido = new CriarProcessoRequest(30L, null, decisao.getId());

        assertThatThrownBy(() -> service.criar(dinamicaId, pedido)).isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO")
                .hasMessage("Já existe um processo em curso deste ato.");
        verify(processoRepository, never()).saveAndFlush(any());

        primeira.setEstado(EstadoProcesso.PUBLICADO);
        Processo segunda = service.criar(dinamicaId, pedido);

        assertThat(segunda.getOcorrencia()).isEqualTo(2);
        assertThat(segunda.getProcessoReferido()).isEqualTo(decisao);
    }

    @Test
    void deveRecusarOAtoPontualNumaDinamicaTerminada() {
        retificacao();
        Processo decisao = processoGravado(dinamica, config, EstadoProcesso.PUBLICADO);
        dinamica.setEstado(EstadoDinamica.TERMINADA);

        assertThatThrownBy(() -> service.criar(dinamicaId, new CriarProcessoRequest(30L, null, decisao.getId())))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "ESTADO_INVALIDO");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveVerificarACamada3AntesDoAtoReferido() {
        InstrumentoProcedimentoConfig pontual = retificacao();
        doThrow(new AcaoNaoAutorizadaException("Tipo da entidade da sessão sem permissão neste procedimento."))
                .when(autorizacaoService).verificarPossibilidadeDeAgir(contexto, dinamica, pontual);

        assertThatThrownBy(() -> service.criar(dinamicaId, new CriarProcessoRequest(30L, null, UUID.randomUUID())))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(processoRepository, never()).findById(any());
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409QuandoProcedimentoRepetivelTemAUltimaEnviadaADgt() {
        config.setRepetivel(true);
        Processo enviada = new Processo(dinamica, config, utilizador, entidade);
        enviada.setEstado(EstadoProcesso.ENVIADO_DGT);
        enviada.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(enviada));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409QuandoProcedimentoNaoRepetivelTemAUltimaPublicada() {
        Processo publicada = new Processo(dinamica, config, utilizador, entidade);
        publicada.setEstado(EstadoProcesso.PUBLICADO);
        publicada.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(publicada));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveLerADinamicaComBloqueioAntesDeVerificarOProcessoEmCurso() {
        service.criar(dinamicaId, request);

        verify(dinamicaRepository).findByIdParaCriarProcesso(dinamicaId);
    }

    @Test
    void deveCriarSegundaTentativaQuandoAnteriorFoiAnulada() {
        Processo anulada = new Processo(dinamica, config, utilizador, entidade);
        anulada.setEstado(EstadoProcesso.ANULADO);
        anulada.setOcorrencia(1);
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(anulada));

        Processo nova = service.criar(dinamicaId, request);

        assertThat(nova.getOcorrencia()).isEqualTo(2);
        assertThat(anulada.getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        assertThat(anulada.getOcorrencia()).isEqualTo(1);
    }

    @Test
    void deveDevolver409QuandoSaveViolaConstraintUnico() {
        when(processoRepository.saveAndFlush(any(Processo.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.criar(dinamicaId, request)).isInstanceOf(EstadoInvalidoException.class);

        verify(auditoriaService, never()).registar(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Registado antes de `findById`: ao stubar, `saveAndFlush(processo)` ainda cai no stub genérico do
     * {@code setUp}, que muda o `id` por reflexão; só depois o `id` fica estável.
     */
    private Processo prepararProcesso(EstadoProcesso estado, InstrumentoProcedimentoConfig configDoProcesso) {
        Processo processo = new Processo(dinamica, configDoProcesso, utilizador, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(estado);
        processo.setTipoAto(tipoAto);
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        when(processoRepository.saveAndFlush(processo)).thenReturn(processo);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }

    private InstrumentoProcedimentoConfig configComSerie(String serieDr) {
        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(null, null);
        InstrumentoProcedimentoConfig outraConfig = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC"), 1, serieDr);
        ReflectionTestUtils.setField(outraConfig, "id", 11L);
        return outraConfig;
    }

    private SubmeterProcessoRequest pedidoSubmeterSegundaSerieCompleto() {
        return PedidosSubmissao.confirmado(VERSAO, "Resumo do ato.", LocalDate.now(), "Norma Legal X", "TEXTO_INTEGRAL", null, null);
    }

    private AtualizarDadosFormularioRequest rascunhoVazio(long versao) {
        return new AtualizarDadosFormularioRequest(versao, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void deveGravarOsCamposIncmESemTransitarEstado() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        LocalDate data = LocalDate.of(2026, 9, 1);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, "Sumário.", data, "Norma X",
                "EXTRATO", true, data, null, null, null, null, null, null);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedido);

        assertThat(atualizado.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(atualizado.getSumario()).isEqualTo("Sumário.");
        assertThat(atualizado.getDataAssinatura()).isEqualTo(data);
        assertThat(atualizado.getNormaLegal()).isEqualTo("Norma X");
        assertThat(atualizado.getTipoInformacao()).isEqualTo("EXTRATO");
        assertThat(atualizado.getPublicarSuplemento()).isTrue();
        assertThat(atualizado.getDataSuplemento()).isEqualTo(data);
    }

    @Test
    void aPcmAtribuidaGravaOFormularioEmValidadoDgtSemMudarOEstado() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(autorizacaoService.correcaoPelaPcm(contexto, processo)).thenReturn(true);
        // As camadas da PCM (Camada 3 com a linha PCM) cobrem-nas o AutorizacaoServiceTest e o teste de integração.
        doNothing().when(autorizacaoService).verificarPermissaoDeEditar(contexto, processo);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(atualizado.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        verify(autorizacaoService, never()).verificarAutoria(any(), any());
        verify(processoRepository).saveAndFlush(processo);
    }

    @Test
    void aPcmAtribuidaRecebe409ForaDeValidadoDgtENadaEGravado() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        when(autorizacaoService.correcaoPelaPcm(contexto, processo)).thenReturn(true);
        // As camadas da PCM (Camada 3 com a linha PCM) cobrem-nas o AutorizacaoServiceTest e o teste de integração.
        doNothing().when(autorizacaoService).verificarPermissaoDeEditar(contexto, processo);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, rascunhoVazio(VERSAO)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage(ProcessoService.MSG_PCM_SO_EM_VALIDADO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void oAutorContinuaSemGravarEmValidadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, rascunhoVazio(VERSAO)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Só é possível editar ou submeter em EM_SUBMISSAO.");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void oDetalheEEditavelPelaPcmSoComAAutorizacaoEOFormulario() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(autorizacaoService.pcmPodeCorrigir(contexto, processo)).thenReturn(true);
        assertThat(service.editavelPelaPcm(processo, contexto)).isTrue();
        assertThat(service.editavel(processo, contexto)).isFalse();

        when(autorizacaoService.pcmPodeCorrigir(contexto, processo)).thenReturn(false);
        assertThat(service.editavelPelaPcm(processo, contexto)).isFalse();
    }

    @Test
    void deveGravarRascunhoSemOsCamposDePublicacaoSemValidarObrigatoriedade() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setNormaLegal("Antiga");
        processo.setSumario("Antigo");

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(atualizado.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(atualizado.getSumario()).isNull();
        assertThat(atualizado.getNormaLegal()).isNull();
        assertThat(atualizado.getDataAssinatura()).isNull();
        assertThat(atualizado.getTipoInformacao()).isNull();
        assertThat(atualizado.getPublicarSuplemento()).isNull();
        assertThat(atualizado.getDataSuplemento()).isNull();
    }

    @Test
    void deveGravarAInformacaoBaseEmDadosFormularioSemACopiarADataDeAssinatura() throws Exception {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        LocalDate assinatura = LocalDate.of(2026, 9, 2);
        InformacaoBaseRequest informacaoBase = new InformacaoBaseRequest(
                new InformacaoBaseDeliberacaoRequest(1, 2, 3, true, LocalDate.of(2026, 9, 1), "Texto a publicar."),
                new InformacaoBaseTextoAtoRequest(4, 5, 6, false));
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, assinatura, null, null, null, null, null, null, null, null, null,
                informacaoBase);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedido);

        JsonNode dados = objectMapper.readTree(atualizado.getDadosFormulario());
        assertThat(dados.fieldNames()).toIterable().containsExactly("informacaoBase");
        JsonNode deliberacao = dados.path("informacaoBase").path("deliberacao");
        assertThat(deliberacao.path("prazoAnos").asInt()).isEqualTo(1);
        assertThat(deliberacao.path("prazoMeses").asInt()).isEqualTo(2);
        assertThat(deliberacao.path("prazoDias").asInt()).isEqualTo(3);
        assertThat(deliberacao.path("avaliacaoAmbiental").asBoolean()).isTrue();
        assertThat(deliberacao.path("dataDeliberacao").asText()).isEqualTo("2026-09-01");
        assertThat(deliberacao.path("textoPublicar").asText()).isEqualTo("Texto a publicar.");
        JsonNode textoAto = dados.path("informacaoBase").path("textoAto");
        assertThat(textoAto.path("prazoAnos").asInt()).isEqualTo(4);
        assertThat(textoAto.path("avaliacaoAmbiental").asBoolean()).isFalse();
        assertThat(atualizado.getDataAssinatura()).isEqualTo(assinatura);
        assertThat(atualizado.getDadosFormulario()).doesNotContain("2026-09-02").doesNotContain("dataAssinatura");
    }

    @Test
    void deveApagarDadosFormularioQuandoAInformacaoBaseNaoVem() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDadosFormulario("{\"campo\":\"antigo\"}");

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(atualizado.getDadosFormulario()).isNull();
    }

    @Test
    void deveGravarRascunhoSoComAInformacaoBase() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        InformacaoBaseRequest informacaoBase = new InformacaoBaseRequest(null, null);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, null, null, null, null, null, null, null, null,
                informacaoBase);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedido);

        assertThat(atualizado.getDadosFormulario()).isEqualTo("{\"informacaoBase\":{\"deliberacao\":null,\"textoAto\":null}}");
    }

    private static InformacaoBaseDeliberacaoRequest deliberacao() {
        return new InformacaoBaseDeliberacaoRequest(1, 2, 3, true, LocalDate.of(2026, 9, 1), "Texto a publicar.");
    }

    private static InformacaoBaseTextoAtoRequest textoAto() {
        return new InformacaoBaseTextoAtoRequest(4, 5, 6, false);
    }

    private static AtualizarDadosFormularioRequest pedidoComInformacaoBase(String sumario, InformacaoBaseRequest informacaoBase) {
        return new AtualizarDadosFormularioRequest(VERSAO, sumario, null, null, null, null, null, null, null, null, null, null, informacaoBase);
    }

    private static List<String> caminhosDosErros(ValidacaoException e) {
        return e.getCampos().stream().map(ValidacaoException.CampoErro::path).toList();
    }

    @Test
    void deveRecusarAsDuasInformacoesBaseSemGravarNadaQuandoOProcedimentoNaoPrevNenhumElemento() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setSumario("Antigo");
        when(processoValidacaoService.elementosPrevistos(config)).thenReturn(Set.of());
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = pedidoComInformacaoBase("Novo.",
                new InformacaoBaseRequest(deliberacao(), textoAto()));

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getMessage()).isEqualTo("Informação Base não prevista neste procedimento.");
                    assertThat(caminhosDosErros(e)).containsExactly("informacaoBase.deliberacao", "informacaoBase.textoAto");
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::erro).containsExactly(
                            "Este procedimento não prevê Informação Base na Deliberação.",
                            "Este procedimento não prevê Informação Base no Texto do ato.");
                });

        assertThat(processo.getSumario()).isEqualTo("Antigo");
        assertThat(processo.getDadosFormulario()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveAceitarAsDuasInformacoesBaseQuandoOProcedimentoPrevOsDoisElementos() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        when(processoValidacaoService.elementosPrevistos(config))
                .thenReturn(Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO));

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(),
                pedidoComInformacaoBase("Novo.", new InformacaoBaseRequest(deliberacao(), textoAto())));

        assertThat(atualizado.getSumario()).isEqualTo("Novo.");
        assertThat(atualizado.getDadosFormulario()).contains("\"deliberacao\":{").contains("\"textoAto\":{");
    }

    @Test
    void deveAceitarOTextoDoAtoERecusarADeliberacaoQuandoOProcedimentoSoPrevTextoAto() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        when(processoValidacaoService.elementosPrevistos(config)).thenReturn(Set.of(ElementoInformacaoBase.TEXTO_ATO));
        UUID id = processo.getId();

        Processo atualizado = service.atualizarDadosFormulario(id,
                pedidoComInformacaoBase(null, new InformacaoBaseRequest(null, textoAto())));
        assertThat(atualizado.getDadosFormulario()).contains("\"textoAto\":{");

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id,
                pedidoComInformacaoBase(null, new InformacaoBaseRequest(deliberacao(), textoAto()))))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(caminhosDosErros(e)).containsExactly("informacaoBase.deliberacao"));
    }

    @Test
    void deveAceitarInformacaoBaseNulaOuSemElementosEmQualquerProcedimentoSemConsultarOsPrevistos() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{}}}");
        UUID id = processo.getId();

        Processo semInformacaoBase = service.atualizarDadosFormulario(id, pedidoComInformacaoBase(null, null));
        assertThat(semInformacaoBase.getDadosFormulario()).isNull();

        Processo semElementos = service.atualizarDadosFormulario(id,
                pedidoComInformacaoBase(null, new InformacaoBaseRequest(null, null)));
        assertThat(semElementos.getDadosFormulario()).isEqualTo("{\"informacaoBase\":{\"deliberacao\":null,\"textoAto\":null}}");

        verify(processoValidacaoService, never()).elementosPrevistos(any());
    }

    @Test
    void deveDevolver403AntesDeVerificarAInformacaoBase() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        doThrow(new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la."))
                .when(autorizacaoService).verificarAutoria(contexto, processo);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id,
                pedidoComInformacaoBase(null, new InformacaoBaseRequest(deliberacao(), null))))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoValidacaoService, never()).elementosPrevistos(any());
    }

    @Test
    void deveDevolver409AntesDeVerificarAInformacaoBaseQuandoOEstadoNaoPermiteEdicao() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id,
                pedidoComInformacaoBase(null, new InformacaoBaseRequest(deliberacao(), null))))
                .isInstanceOf(EstadoInvalidoException.class);

        verify(processoValidacaoService, never()).elementosPrevistos(any());
    }

    @Test
    void deveDevolver403AoGravarRascunhoQuandoNaoEAutor() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        doThrow(new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la."))
                .when(autorizacaoService).verificarAutoria(contexto, processo);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, rascunhoVazio(VERSAO)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409AoGravarRascunhoEmEstadoQueNaoPermite() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, rascunhoVazio(VERSAO)))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void deveSubmeterQuandoTodosOsCamposObrigatoriosEstaoPreenchidos() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(submetido.getDataSubmissao()).isNotNull();
    }

    @Test
    void deveRegistarASubmissaoNoIdusDepoisDeGravarOProcessoSubmetido() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        InOrder ordem = inOrder(processoRepository, idusProcessoService);
        ordem.verify(processoRepository).saveAndFlush(submetido);
        ordem.verify(idusProcessoService).registarSubmissao(submetido);
    }

    @Test
    void naoDeveRegistarSubmissaoNoIdusQuandoOPedidoNaoChegaASerSubmetido() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_SUBMISSAO, 4L);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(3L, "Resumo do ato.", LocalDate.now(), "Norma Legal X",
                "TEXTO_INTEGRAL", null, null);

        assertThatThrownBy(() -> service.submeter(id, pedido)).isInstanceOf(VersaoDesatualizadaException.class);

        verify(idusProcessoService, never()).registarSubmissao(any());
    }

    private InstrumentoProcedimentoConfig configDoProcedimento(String codigo, long id) {
        InstrumentoProcedimentoConfig configDoProcedimento = new InstrumentoProcedimentoConfig(
                new InstrumentoDinamicaConfig(null, null), new TipoProcedimento(codigo, codigo), 1, "SEGUNDA_H");
        ReflectionTestUtils.setField(configDoProcedimento, "id", id);
        return configDoProcedimento;
    }

    private static AtualizarDadosFormularioRequest pedidoComPlantaNova(Boolean introduzPlantaNova, InformacaoBaseRequest informacaoBase) {
        return new AtualizarDadosFormularioRequest(VERSAO, null, null, null, null, null, null, null, null, null, null, null,
                informacaoBase, null, introduzPlantaNova);
    }

    @Test
    void deveGravarIntroduzPlantaNovaEmDadosFormularioJuntoComAInformacaoBase() throws Exception {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(),
                pedidoComPlantaNova(true, new InformacaoBaseRequest(deliberacao(), null)));

        JsonNode dados = objectMapper.readTree(atualizado.getDadosFormulario());
        assertThat(dados.fieldNames()).toIterable().containsExactly("informacaoBase", "introduzPlantaNova");
        assertThat(dados.path("introduzPlantaNova").isBoolean()).isTrue();
        assertThat(dados.path("introduzPlantaNova").asBoolean()).isTrue();
    }

    @Test
    void deveGravarIntroduzPlantaNovaFalsoESoElaQuandoNaoHaInformacaoBase() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedidoComPlantaNova(false, null));

        assertThat(atualizado.getDadosFormulario()).isEqualTo("{\"introduzPlantaNova\":false}");
    }

    @Test
    void deveOmitirIntroduzPlantaNovaDeDadosFormularioQuandoENulo() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        processo.setDadosFormulario("{\"introduzPlantaNova\":true}");

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(),
                pedidoComPlantaNova(null, new InformacaoBaseRequest(null, null)));

        assertThat(atualizado.getDadosFormulario()).isEqualTo("{\"informacaoBase\":{\"deliberacao\":null,\"textoAto\":null}}");
    }

    @Test
    void deveAceitarORascunhoComIntroduzPlantaNovaNumProcedimentoSemAPerguntaENaoAplicarAsRegrasNaSubmissao() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ABERTURA_DISCUSSAO_PUBLICA", 13L));

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedidoComPlantaNova(true, null));
        assertThat(atualizado.getDadosFormulario()).isEqualTo("{\"introduzPlantaNova\":true}");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveSubmeterAPublicacaoDaAlteracaoQuandoARespostaEstaCoerenteComOsElementosAfetados() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        processo.setElementosAfetados(List.of("PECAS_GRAFICAS", "PECAS_ESCRITAS"));
        processo.setDadosFormulario("{\"introduzPlantaNova\":true}");

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveRecusarAPublicacaoDaAlteracaoSemRespostaComPecasGraficasSemGravarNada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        processo.setElementosAfetados(List.of("PECAS_GRAFICAS"));
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getCampos()).containsExactly(new ValidacaoException.CampoErro("introduzPlantaNova",
                            "Indique se a alteração introduz alguma planta nova."));
                });

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getDataSubmissao()).isNull();
        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveRecusarAPublicacaoDaAlteracaoNumaDinamicaComGeoconformSemPecasGraficas() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        dinamica.setComGeoconform(true);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos()).containsExactly(
                        new ValidacaoException.CampoErro("elementosAfetados",
                                "Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.")));
    }

    @Test
    void deveCorrerAsValidacoesDaInformacaoGeralAntesDasDaPublicacaoDaAlteracao() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        processo.setElementosAfetados(List.of());
        processo.setDadosFormulario("{\"introduzPlantaNova\":true}");
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido)).isInstanceOf(ElementosAfetadosEmFaltaException.class);
    }

    @Test
    void deveDevolver403AntesDeAplicarAsRegrasDaPublicacaoDaAlteracao() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento("ALTERACAO", 12L));
        processo.setElementosAfetados(List.of("PECAS_GRAFICAS"));
        doThrow(new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la."))
                .when(autorizacaoService).verificarAutoria(contexto, processo);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedidoComPlantaNova(true, null)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveCongelarIncmEnrollmentIdEDrParteIdNaSubmissaoDeSegundaSerie() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getIncmEnrollmentId()).isEqualTo("2015482");
        assertThat(submetido.getDrParteId()).isEqualTo(1234567891234567L);
    }

    @Test
    void naoDeveCongelarDrParteIdNaSubmissaoDePrimeiraSerie() {
        InstrumentoProcedimentoConfig configPrimeira = configComSerie("PRIMEIRA");
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configPrimeira);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), null, null, false, null);

        Processo submetido = service.submeter(processo.getId(), pedido);

        assertThat(submetido.getIncmEnrollmentId()).isEqualTo("2015482");
        assertThat(submetido.getDrParteId()).isNull();
    }

    @Test
    void deveRecongelarIdentificadoresNaResubmissaoComSessaoDiferente() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setIncmEnrollmentId("2015482");
        processo.setDrParteId(1234567891234567L);
        ContextoUtilizador novaSessao = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), "ENTIDADE",
                null, null, "9999999", 7777777777777777L, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(novaSessao);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getIncmEnrollmentId()).isEqualTo("9999999");
        assertThat(submetido.getDrParteId()).isEqualTo(7777777777777777L);
    }

    @Test
    void deveRecusarSubmissaoQuandoSessaoSemIncmEnrollmentId() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        ContextoUtilizador semIncmEnrollmentId = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(),
                "ENTIDADE", null, null, null, 1234567891234567L, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(semIncmEnrollmentId);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).contains("incmEnrollmentId"));

        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveRecusarSubmissaoQuandoTipoDeAtoJaNaoEstaAutorizado() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        doThrow(new TipoAtoNaoAutorizadoException("Tipo de ato não autorizado ao utilizador pela INCM."))
                .when(autorizacaoService).verificarAutorizacaoPublicacao(any(), eq(processo));
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOf(TipoAtoNaoAutorizadoException.class)
                .hasFieldOrPropertyWithValue("codigo", "TIPO_ATO_NAO_AUTORIZADO");

        verify(processoRepository, never()).saveAndFlush(processo);
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveDevolver400QuandoSegundaSerieSemNormaLegal() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), null, "TEXTO_INTEGRAL", null, null);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).contains("normaLegal"));
    }

    @Test
    void deveDevolver400QuandoPrimeiraSerieSemPublicarSuplemento() {
        InstrumentoProcedimentoConfig configPrimeira = configComSerie("PRIMEIRA");
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configPrimeira);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), null, null, null, null);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).contains("publicarSuplemento"));
    }

    @Test
    void deveDevolver400QuandoPublicarSuplementoVerdadeiroSemDataSuplemento() {
        InstrumentoProcedimentoConfig configPrimeira = configComSerie("PRIMEIRA");
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configPrimeira);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), null, null, true, null);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).contains("dataSuplemento"));
    }

    @Test
    void deveRecusarSubmissaoDeSegundaSerieQuandoSessaoSemDrParteId() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        ContextoUtilizador semDrParteId = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(),
                "ENTIDADE", null, null, "2015482", null, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(semDrParteId);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("drParteId"));

        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void naoDeveExigirDrParteIdNaSubmissaoDePrimeiraSerie() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configComSerie("PRIMEIRA"));
        ContextoUtilizador semDrParteId = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(),
                "ENTIDADE", null, null, "2015482", null, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(semDrParteId);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), null, null, false, null);

        assertThat(service.submeter(processo.getId(), pedido).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void semInformacaoBaseDoTextoDoAtoARecusaPedeADataDeAssinaturaDoFormulario() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        UUID id = processo.getId();
        SubmeterProcessoRequest semData = PedidosSubmissao.confirmado(VERSAO, "Resumo", null, "Norma Legal X", "TEXTO_INTEGRAL",
                null, null);

        assertThatThrownBy(() -> service.submeter(id, semData))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> assertThat(ex.getCampos())
                        .containsExactly(new ValidacaoException.CampoErro("dataAssinatura",
                                "Indique a data de assinatura do ato.")));
    }

    @Test
    void deveApontarParaOBlocoDoTextoDoAtoQuandoFaltaADataDeAssinatura() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        DocumentoConfig textoAto = mock(DocumentoConfig.class);
        when(textoAto.getPagina()).thenReturn(PaginaDocumento.DOCUMENTOS);
        when(textoAto.getCategoria()).thenReturn(CategoriaDocumento.PUBLICACAO);
        when(textoAto.getElementoInformacaoBase()).thenReturn(ElementoInformacaoBase.TEXTO_ATO);
        when(documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(config))
                .thenReturn(List.of(textoAto));
        UUID id = processo.getId();
        SubmeterProcessoRequest semData = PedidosSubmissao.confirmado(VERSAO, "Resumo", null, "Norma Legal X", "TEXTO_INTEGRAL",
                null, null);

        assertThatThrownBy(() -> service.submeter(id, semData))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> assertThat(ex.getCampos())
                        .containsExactly(new ValidacaoException.CampoErro("dataAssinatura",
                                "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato.")));
    }

    @Test
    void naoDeveTerAValidacaoDeObrigatoriedadeDentroDoProcessoService() {
        assertThat(Arrays.stream(ProcessoService.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("validarObrigatoriedadeDeSubmissao");
    }

    private ContextoUtilizador contextoComTipoDeAto(long idTipoAto, boolean permiteExtrato) {
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(idTipoAto, null, permiteExtrato)));
        ContextoUtilizador comClaim = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), "ENTIDADE", atos,
                null, "2015482", 1234567891234567L, TipoEntidade.CM, entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(comClaim);
        return comClaim;
    }

    private Processo prepararProcessoComTipoDeAtoDaSegundaSerie(long idTipoAto) {
        tipoAto.setIncmActTypeIdSerie2(idTipoAto);
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(tipoAto);
        return processo;
    }

    @Test
    void deveRecusarAoGravarExtratoQuandoOTipoDeAtoNaoOPermite() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(701L, false);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, "EXTRATO", null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getCampos()).containsExactly(new ValidacaoException.CampoErro("tipoInformacao",
                            "O tipo de ato não permite publicar extrato."));
                });

        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveAceitarAoGravarExtratoQuandoOTipoDeAtoOPermite() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(701L, true);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, "EXTRATO", null, null,
                null, null, null, null, null, null);

        assertThat(service.atualizarDadosFormulario(processo.getId(), pedido).getTipoInformacao()).isEqualTo("EXTRATO");
    }

    @Test
    void deveAceitarAoGravarTextoIntegralMesmoQuandoOTipoDeAtoNaoPermiteExtrato() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(701L, false);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, "TEXTO_INTEGRAL", null,
                null, null, null, null, null, null, null);

        assertThat(service.atualizarDadosFormulario(processo.getId(), pedido).getTipoInformacao()).isEqualTo("TEXTO_INTEGRAL");
    }

    @Test
    void deveRecusarAoGravarExtratoQuandoOTipoDeAtoNaoEstaNoClaim() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(999L, true);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, "EXTRATO", null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido)).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void naoDeveAplicarARegraDoExtratoNaPrimeiraSerie() {
        tipoAto.setIncmActTypeIdSerie2(701L);
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configComSerie("PRIMEIRA"));
        processo.setTipoAto(tipoAto);
        contextoComTipoDeAto(701L, false);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(VERSAO, null, null, null, "EXTRATO", null, null,
                null, null, null, null, null, null);

        assertThat(service.atualizarDadosFormulario(processo.getId(), pedido).getTipoInformacao()).isEqualTo("EXTRATO");
    }

    @Test
    void deveRecusarNaSubmissaoOExtratoComValidacaoEAntesDosDadosEmFalta() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(701L, false);
        UUID id = processo.getId();
        SubmeterProcessoRequest semSumarioComExtrato = PedidosSubmissao.confirmado(VERSAO, null, LocalDate.now(), "Norma Legal X",
                "EXTRATO", null, null);

        assertThatThrownBy(() -> service.submeter(id, semSumarioComExtrato))
                .isNotInstanceOf(DadosIncmEmFaltaException.class)
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("tipoInformacao");
                });

        verify(completudeDocumentalService, never()).verificar(any());
        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveSubmeterComExtratoQuandoOTipoDeAtoOPermite() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        contextoComTipoDeAto(701L, true);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), "Norma Legal X", "EXTRATO",
                null, null);

        assertThat(service.submeter(processo.getId(), pedido).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void naoDeveAplicarARegraDoExtratoSemClaimDeAtosAutorizados() {
        Processo processo = prepararProcessoComTipoDeAtoDaSegundaSerie(701L);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(), "Norma Legal X", "EXTRATO",
                null, null);

        assertThat(service.submeter(processo.getId(), pedido).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveVerificarACompletudeDocumentalNaSubmissao() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        verify(completudeDocumentalService).verificar(processo);
    }

    @Test
    void deveRecusarPrimeiroOsDadosIncmEmFaltaSemVerificarOsDocumentos() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        UUID id = processo.getId();
        SubmeterProcessoRequest semSumario = PedidosSubmissao.confirmado(VERSAO, null, LocalDate.now(), "Norma Legal X",
                "TEXTO_INTEGRAL", null, null);

        assertThatThrownBy(() -> service.submeter(id, semSumario)).isInstanceOf(DadosIncmEmFaltaException.class);

        verify(completudeDocumentalService, never()).verificar(any());
    }

    @Test
    void deveManterOProcessoEmSubmissaoSemCongelarIdentificadoresQuandoFaltamDocumentos() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        doThrow(new DocumentosObrigatoriosEmFaltaException("Faltam documentos obrigatórios para a submissão.",
                List.of(new ValidacaoException.CampoErro("documentos.DELIBERACAO_PUB", "Falta carregar."))))
                .when(completudeDocumentalService).verificar(processo);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOf(DocumentosObrigatoriosEmFaltaException.class)
                .hasFieldOrPropertyWithValue("codigo", "DOCUMENTOS_OBRIGATORIOS_EM_FALTA");

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getIncmEnrollmentId()).isNull();
        verify(processoRepository, never()).saveAndFlush(processo);
        verify(auditoriaService, never()).registarTransicao(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void deveRecusarSubmissaoEEdicaoSemCorrigirPrimeiro(EstadoProcesso estado) {
        Processo processo = prepararProcesso(estado, config);

        assertThatThrownBy(() -> service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto()))
                .isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO)))
                .isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.resgatar(processo.getId(), PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);
        assertThat(processo.getEstado()).isEqualTo(estado);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void deveCorrigirParaEmSubmissaoMantendoIdentificacaoEDados(EstadoProcesso estadoAntes) {
        Processo processo = prepararProcesso(estadoAntes, config);
        processo.setOcorrencia(2);
        ReflectionTestUtils.setField(processo, "numeroProcesso", "00007");
        processo.setSumario("Sumário gravado");
        processo.setDataSubmissao(Instant.parse("2026-10-01T10:00:00Z"));
        UUID id = processo.getId();

        when(bloqueioDocumentosCorrecao.bloquearParaCorrecao(processo, estadoAntes)).thenReturn(2L);

        Processo corrigido = service.corrigir(id, PEDIDO_VERSAO);

        assertThat(corrigido.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(corrigido.getId()).isEqualTo(id);
        assertThat(corrigido.getOcorrencia()).isEqualTo(2);
        assertThat(corrigido.getNumeroProcesso()).isEqualTo("00007");
        assertThat(corrigido.getSumario()).isEqualTo("Sumário gravado");
        assertThat(corrigido.getDataSubmissao()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        verify(auditoriaService).registarTransicao("PROCESSO", id.toString(), "CORRIGIR",
                new TransicaoEstado(estadoAntes.name(), "EM_SUBMISSAO"), utilizadorId, entidade.getId(),
                Map.of("documentosBloqueados", 2L));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void deveBloquearOsFicheirosDepoisDeGravarEAntesDaAuditoriaAoCorrigir(EstadoProcesso estadoAntes) {
        Processo processo = prepararProcesso(estadoAntes, config);

        service.corrigir(processo.getId(), PEDIDO_VERSAO);

        var ordem = inOrder(processoRepository, bloqueioDocumentosCorrecao, auditoriaService);
        ordem.verify(processoRepository).saveAndFlush(processo);
        ordem.verify(bloqueioDocumentosCorrecao).bloquearParaCorrecao(processo, estadoAntes);
        ordem.verify(auditoriaService).registarTransicao(eq("PROCESSO"), eq(processo.getId().toString()), eq("CORRIGIR"),
                any(), any(), any(), any());
    }

    @Test
    void naoDeveBloquearFicheirosQuandoCorrigirFalhaPorConflitoDeVersaoAoGravar() {
        Processo processo = prepararProcesso(EstadoProcesso.REJEITADO_DGT, config);
        when(processoRepository.saveAndFlush(processo))
                .thenThrow(new ObjectOptimisticLockingFailureException(Processo.class, processo.getId()));
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.corrigir(id, PEDIDO_VERSAO)).isInstanceOf(VersaoDesatualizadaException.class);

        verifyNoInteractions(bloqueioDocumentosCorrecao);
        verify(auditoriaService, never()).registarTransicao(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void naoDeveBloquearFicheirosQuandoAVersaoDoPedidoEstaDesatualizadaAoCorrigir() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.REJEITADO_DGT, 5L);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.corrigir(id, new VersaoProcessoRequest(4L)))
                .isInstanceOf(VersaoDesatualizadaException.class);

        verifyNoInteractions(bloqueioDocumentosCorrecao);
    }

    @Test
    void naoDeveMexerNoBloqueioDosFicheirosAoResgatar() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);

        service.resgatar(processo.getId(), PEDIDO_VERSAO);

        verifyNoInteractions(bloqueioDocumentosCorrecao);
    }

    @Test
    void deveSubmeterDepoisDeCorrigirRejeitadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.REJEITADO_DGT, config);

        service.corrigir(processo.getId(), PEDIDO_VERSAO);
        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"}, mode = EnumSource.Mode.EXCLUDE)
    void deveDevolver409AoCorrigirForaDeRejeitadoDgtEAnuladoIncm(EstadoProcesso estado) {
        Processo processo = prepararProcesso(estado, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.corrigir(id, PEDIDO_VERSAO))
                .isExactlyInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Este processo não pode ser corrigido neste estado.");

        assertThat(processo.getEstado()).isEqualTo(estado);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registarTransicao(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaAntesDoEstadoAoCorrigir() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.PUBLICADO, 5L);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.corrigir(id, new VersaoProcessoRequest(4L)))
                .isInstanceOf(VersaoDesatualizadaException.class);
    }

    @Test
    void deveDevolver403AoCorrigirQuandoNaoEAutor() {
        Processo processo = prepararProcesso(EstadoProcesso.REJEITADO_DGT, config);
        doThrow(new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la."))
                .when(autorizacaoService).verificarAutoria(contexto, processo);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.corrigir(id, PEDIDO_VERSAO)).isInstanceOf(AcaoNaoAutorizadaException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRegistarAuditoriaDeSubmissaoComEstadoAntesEDepois() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        verify(auditoriaService).registarTransicao("PROCESSO", processo.getId().toString(), "SUBMETER",
                new TransicaoEstado("EM_SUBMISSAO", "ENVIADO_DGT"), utilizadorId, entidade.getId(),
                Map.of("confirmacoes", List.of("CONTEUDO_PROCESSO")));
    }

    @Test
    void deveRecusarASubmissaoSemConfirmacoesSemGravarNada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        SubmeterProcessoRequest semConfirmacoes = new SubmeterProcessoRequest(VERSAO, "Outro resumo", LocalDate.now(),
                "Norma Legal X", "TEXTO_INTEGRAL", null, null);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.submeter(id, semConfirmacoes))
                .isInstanceOfSatisfying(ConfirmacaoEmFaltaException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("CONFIRMACAO_EM_FALTA");
                    assertThat(ex.getCampos()).extracting(ValidacaoException.CampoErro::path)
                            .containsExactly("confirmacoes.CONTEUDO_PROCESSO");
                });

        verify(processoRepository, never()).saveAndFlush(processo);
        verify(auditoriaService, never()).registarTransicao(any(), any(), any(), any(), any(), any(), any());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getSumario()).isNotEqualTo("Outro resumo");
    }

    @Test
    void deveIgnorarConfirmacoesQueOFormularioNaoExige() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        SubmeterProcessoRequest pedido = new SubmeterProcessoRequest(VERSAO, "Resumo do ato.", LocalDate.now(),
                "Norma Legal X", "TEXTO_INTEGRAL", null, null, null, List.of("CONTEUDO_PROCESSO", "OUTRA"));

        assertThat(service.submeter(processo.getId(), pedido).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        verify(auditoriaService).registarTransicao(eq("PROCESSO"), eq(processo.getId().toString()), eq("SUBMETER"),
                any(), any(), any(), eq(Map.of("confirmacoes", List.of("CONTEUDO_PROCESSO"))));
    }

    @Test
    void deveResgatarDeEnviadoDgtParaEmSubmissao() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);

        Processo resgatado = service.resgatar(processo.getId(), PEDIDO_VERSAO);

        assertThat(resgatado.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        verify(auditoriaService).registarTransicao("PROCESSO", processo.getId().toString(), "RESGATAR",
                new TransicaoEstado("ENVIADO_DGT", "EM_SUBMISSAO"), utilizadorId, entidade.getId(), null);
    }

    @Test
    void deveDevolver409AoResgatarQuandoNaoEstaEnviadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.resgatar(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    private ContextoUtilizador contextoMaster() {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null);
    }

    private ContextoUtilizador contextoTecnico() {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.TECNICO, null, "DGT", null, null, null, null, null, null);
    }

    @Test
    void deveCapturarQuandoEnviadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        Processo capturado = service.capturar(processo.getId(), PEDIDO_VERSAO);

        assertThat(capturado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
    }

    @Test
    void deveValidarQuandoEmValidacaoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        Processo validado = service.validar(processo.getId(), PEDIDO_VERSAO);

        assertThat(validado.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    @Test
    void deveReterOsDocumentosNaEntradaEmValidadoDgtAntesDeGravarOProcesso() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        service.validar(processo.getId(), PEDIDO_VERSAO);

        var ordem = inOrder(retencaoDocumentosService, processoRepository);
        ordem.verify(retencaoDocumentosService).reterDocumentosSemRetencao(processo, MomentoRetencao.ENTRADA_VALIDADO_DGT);
        ordem.verify(processoRepository).saveAndFlush(processo);
    }

    @Test
    void naoDeveGravarNemAuditarAValidacaoQuandoARetencaoFalha() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        doThrow(new StorageIndisponivelException("Armazenamento indisponível.", new IllegalStateException()))
                .when(retencaoDocumentosService).reterDocumentosSemRetencao(any(), any());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.validar(id, PEDIDO_VERSAO)).isInstanceOf(StorageIndisponivelException.class);

        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registarTransicao(any(), any(), eq("VALIDAR"), any(), any(), any(), any());
    }

    @Test
    void deveDevolverItensPorReverAoValidarQuandoFaltaReverUmItem() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.todosRevistos(processo)).thenReturn(false);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.validar(id, PEDIDO_VERSAO))
                .isInstanceOf(ItensPorReverException.class)
                .hasMessage("Reveja todos os documentos antes de continuar.");

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registarTransicao(any(), any(), eq("VALIDAR"), any(), any(), any(), any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaEAntesDosItensPorReverAoValidar() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_VALIDACAO_DGT, 5L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.todosRevistos(processo)).thenReturn(false);
        UUID id = processo.getId();
        VersaoProcessoRequest antiga = new VersaoProcessoRequest(4L);

        assertThatThrownBy(() -> service.validar(id, antiga)).isInstanceOf(VersaoDesatualizadaException.class);
    }

    @Test
    void deveDevolverEstadoInvalidoEAntesDosItensPorReverAoValidarForaDeEmValidacaoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.todosRevistos(processo)).thenReturn(false);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.validar(id, PEDIDO_VERSAO))
                .isExactlyInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void deveRejeitarCriandoARecusaAPartirDoRascunhoEApagarORascunho() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        Utilizador autorDaRecusa = new Utilizador("Técnica DGT", "tecnica@dgt.pt", "AD_DGT");
        when(utilizadorRepository.getReferenceById(admin.utilizadorId())).thenReturn(autorDaRecusa);
        List<ProcessoValidacaoRejeicao> rascunho = List.of(mock(ProcessoValidacaoRejeicao.class),
                mock(ProcessoValidacaoRejeicao.class));
        when(processoValidacaoService.rascunhoParaRecusa(processo)).thenReturn(rascunho);
        when(rejeicaoProcessoService.registarRecusaDgt(any(), any(), any(), any()))
                .thenReturn(ProcessoRejeicao.builder().sequencia(3).build());

        Processo rejeitado = service.rejeitar(processo.getId(), new RejeitarProcessoRequest(VERSAO, "  Rever o regulamento.  "));

        assertThat(rejeitado.getEstado()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
        verify(rejeicaoProcessoService).registarRecusaDgt(processo, autorDaRecusa, "Rever o regulamento.", rascunho);
        verify(processoValidacaoService).apagarRascunho(processo);
        verify(auditoriaService).registarTransicao("PROCESSO", processo.getId().toString(), "REJEITAR",
                new TransicaoEstado("EM_VALIDACAO_DGT", "REJEITADO_DGT"), admin.utilizadorId(), null,
                Map.of("sequencia", 3, "itens", 2));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void deveGravarNotasGeraisNulasQuandoVemVaziasOuSoComEspacos(String notasGerais) {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        service.rejeitar(processo.getId(), new RejeitarProcessoRequest(VERSAO, notasGerais));

        verify(rejeicaoProcessoService).registarRecusaDgt(eq(processo), any(), eq(null), any());
    }

    @Test
    void naoDeveMudarOEstadoNemCriarARecusaQuandoORascunhoEInvalido() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.rascunhoParaRecusa(processo)).thenThrow(new ValidacaoException(
                "Indique pelo menos um item rejeitado.",
                List.of(new ValidacaoException.CampoErro("itens", "Indique pelo menos um item rejeitado."))));
        UUID id = processo.getId();
        RejeitarProcessoRequest pedido = new RejeitarProcessoRequest(VERSAO, null);

        assertThatThrownBy(() -> service.rejeitar(id, pedido)).isInstanceOf(ValidacaoException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(rejeicaoProcessoService, never()).registarRecusaDgt(any(), any(), any(), any());
        verify(processoValidacaoService, never()).apagarRascunho(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "EM_VALIDACAO_DGT", "VALIDADO_DGT"})
    void deveAnularComMotivoEAuditarOEstadoDeOrigemReal(EstadoProcesso origem) {
        Processo processo = prepararProcesso(origem, config);
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);

        Processo anulado = service.anular(processo.getId(), new AnularProcessoRequest(VERSAO, "Pedido retirado pela entidade."));

        assertThat(anulado.getEstado()).isEqualTo(EstadoProcesso.ANULADO);
        verify(auditoriaService).registarTransicao("PROCESSO", processo.getId().toString(), "ANULAR",
                new TransicaoEstado(origem.name(), "ANULADO"), admin.utilizadorId(), null, Map.of("motivo", "Pedido retirado pela entidade."));
        verifyNoInteractions(dinamicaService);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class,
            names = {"ENVIADO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO", "ANULADO", "ANULADO_INCM", "REJEITADO_DGT"})
    void deveDevolver409AoAnularForaDosEstadosAnulaveis(EstadoProcesso estado) {
        Processo processo = prepararProcesso(estado, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        AnularProcessoRequest pedido = new AnularProcessoRequest(VERSAO, "motivo");

        assertThatThrownBy(() -> service.anular(id, pedido)).isInstanceOf(EstadoInvalidoException.class);

        assertThat(processo.getEstado()).isEqualTo(estado);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void devePermitirAoTecnicoCapturarValidarERejeitarMasNaoAnular() {
        Processo capturarProcesso = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        Processo validarProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        Processo rejeitarProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        Processo anularProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        ContextoUtilizador tecnico = contextoTecnico();
        when(contextoUtilizadorResolver.resolver()).thenReturn(tecnico);
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."))
                .when(autorizacaoService).verificarPossibilidadeDeAgirComoMaster(eq(tecnico), any());
        UUID anularId = anularProcesso.getId();
        AnularProcessoRequest anularRequest = new AnularProcessoRequest(VERSAO, "motivo");

        assertThat(service.capturar(capturarProcesso.getId(), PEDIDO_VERSAO).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(service.validar(validarProcesso.getId(), PEDIDO_VERSAO).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(service.rejeitar(rejeitarProcesso.getId(), new RejeitarProcessoRequest(VERSAO, null)).getEstado())
                .isEqualTo(EstadoProcesso.REJEITADO_DGT);
        assertThatThrownBy(() -> service.anular(anularId, anularRequest))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        assertThat(anularProcesso.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        verify(autorizacaoService, times(3)).verificarPossibilidadeDeAgirComoDgt(eq(tecnico), any());
    }

    @Test
    void deveDevolver403NasQuatroTransicoesQuandoVisitante() {
        Processo capturarProcesso = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        Processo validarProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        Processo rejeitarProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        Processo anularProcesso = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        ContextoUtilizador visitante = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.VISITANTE, null, null, null, null, null, null, null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(visitante);
        doThrow(new AcaoNaoAutorizadaException("Só a DGT (MASTER ou TECNICO) pode agir sobre esta tentativa."))
                .when(autorizacaoService).verificarPossibilidadeDeAgirComoDgt(eq(visitante), any());
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."))
                .when(autorizacaoService).verificarPossibilidadeDeAgirComoMaster(eq(visitante), any());

        UUID capturarId = capturarProcesso.getId();
        UUID validarId = validarProcesso.getId();
        UUID rejeitarId = rejeitarProcesso.getId();
        RejeitarProcessoRequest rejeitarRequest = new RejeitarProcessoRequest(VERSAO, null);
        UUID anularId = anularProcesso.getId();
        AnularProcessoRequest anularRequest = new AnularProcessoRequest(VERSAO, "motivo");

        assertThatThrownBy(() -> service.capturar(capturarId, PEDIDO_VERSAO)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.validar(validarId, PEDIDO_VERSAO)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.rejeitar(rejeitarId, rejeitarRequest))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.anular(anularId, anularRequest))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409AoResgatarQuandoRejeitadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.REJEITADO_DGT, config);
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.resgatar(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409EmTodasAsTransicoesQuandoAnuladoPelaIncm() {
        Processo processo = prepararProcesso(EstadoProcesso.ANULADO_INCM, config);
        UUID id = processo.getId();
        RejeitarProcessoRequest rejeitar = new RejeitarProcessoRequest(VERSAO, null);
        AnularProcessoRequest anular = new AnularProcessoRequest(VERSAO, "Pedido retirado pela entidade.");

        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        assertThatThrownBy(() -> service.capturar(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.validar(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.rejeitar(id, rejeitar)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.anular(id, anular)).isInstanceOf(EstadoInvalidoException.class);

        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoPcm(entidade.getNipc()));
        assertThatThrownBy(() -> service.enviarIncm(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver409QuandoCapturarOuRejeitarForaDoEstadoEsperado() {
        Processo paraCapturar = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        Processo paraRejeitar = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        UUID capturarId = paraCapturar.getId();
        UUID rejeitarId = paraRejeitar.getId();
        RejeitarProcessoRequest rejeitarRequest = new RejeitarProcessoRequest(VERSAO, null);

        assertThatThrownBy(() -> service.capturar(capturarId, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> service.rejeitar(rejeitarId, rejeitarRequest))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void deveDevolver409QuandoConflitoDeVersaoAoCapturar() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoRepository.saveAndFlush(processo)).thenThrow(new ObjectOptimisticLockingFailureException(Processo.class, processo.getId()));
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.capturar(id, PEDIDO_VERSAO)).isInstanceOf(VersaoDesatualizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "VERSAO_DESATUALIZADA");
    }

    @Test
    void deveDevolver409QuandoConflitoDeVersaoAoResgatar() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(processoRepository.saveAndFlush(processo)).thenThrow(new ObjectOptimisticLockingFailureException(Processo.class, processo.getId()));
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.resgatar(id, PEDIDO_VERSAO)).isInstanceOf(VersaoDesatualizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "VERSAO_DESATUALIZADA");
    }

    private Processo prepararProcessoNaVersao(EstadoProcesso estado, long versao) {
        Processo processo = prepararProcesso(estado, config);
        ReflectionTestUtils.setField(processo, "versao", versao);
        return processo;
    }

    @Test
    void deveAceitarEscritaQuandoAVersaoDoPedidoCoincideComADoProcesso() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.ENVIADO_DGT, 3L);

        Processo resgatado = service.resgatar(processo.getId(), new VersaoProcessoRequest(3L));

        assertThat(resgatado.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void deveDevolverVersaoDesatualizadaESemAlterarNadaQuandoARascunhoChegaComVersaoAntiga() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_SUBMISSAO, 4L);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(3L, "Sumário.", null, null, null, null, null, null, null, null, null, null,
                new InformacaoBaseRequest(new InformacaoBaseDeliberacaoRequest(1, null, null, null, null, null), null));

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOf(VersaoDesatualizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "VERSAO_DESATUALIZADA")
                .hasMessage("O processo foi alterado; recarregue.");

        assertThat(processo.getDadosFormulario()).isNull();
        assertThat(processo.getSumario()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaESemAlterarNadaQuandoSubmeterChegaComVersaoAntiga() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_SUBMISSAO, 4L);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(3L, "Resumo do ato.", LocalDate.now(), "Norma Legal X",
                "TEXTO_INTEGRAL", null, null);

        assertThatThrownBy(() -> service.submeter(id, pedido)).isInstanceOf(VersaoDesatualizadaException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getSumario()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaEnquantoOEstadoNaoDizOutraCoisaQuandoACapturaChegaDepoisDoResgate() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_SUBMISSAO, 4L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.capturar(id, new VersaoProcessoRequest(3L)))
                .isInstanceOf(VersaoDesatualizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaNasTransicoesDaDgtQuandoAVersaoDoPedidoEAntiga() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_VALIDACAO_DGT, 5L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        RejeitarProcessoRequest rejeitar = new RejeitarProcessoRequest(4L, null);
        AnularProcessoRequest anular = new AnularProcessoRequest(4L, "motivo");
        ReatribuirAutorRequest reatribuir = new ReatribuirAutorRequest(4L, UUID.randomUUID(), null);
        EnviarPcmRequest enviarPcm = new EnviarPcmRequest(4L, UUID.randomUUID());
        VersaoProcessoRequest antiga = new VersaoProcessoRequest(4L);

        assertThatThrownBy(() -> service.validar(id, antiga)).isInstanceOf(VersaoDesatualizadaException.class);
        assertThatThrownBy(() -> service.rejeitar(id, rejeitar)).isInstanceOf(VersaoDesatualizadaException.class);
        assertThatThrownBy(() -> service.anular(id, anular)).isInstanceOf(VersaoDesatualizadaException.class);
        assertThatThrownBy(() -> service.reatribuirAutor(id, reatribuir)).isInstanceOf(VersaoDesatualizadaException.class);
        assertThatThrownBy(() -> service.enviarPcm(id, enviarPcm)).isInstanceOf(VersaoDesatualizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverVersaoDesatualizadaAoEnviarParaAIncmQuandoAVersaoDoPedidoEAntiga() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.VALIDADO_DGT, 5L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.enviarIncm(id, new VersaoProcessoRequest(4L)))
                .isInstanceOf(VersaoDesatualizadaException.class);

        verify(envioIncmService, never()).enviar(any(), any());
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver403AoReatribuirQuandoNaoEMaster() {
        Processo paraReatribuir = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        ContextoUtilizador tecnico = contextoTecnico();
        when(contextoUtilizadorResolver.resolver()).thenReturn(tecnico);
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."))
                .when(autorizacaoService).verificarPossibilidadeDeAgirComoMaster(eq(tecnico), any());

        UUID reatribuirId = paraReatribuir.getId();
        ReatribuirAutorRequest reatribuirRequest = new ReatribuirAutorRequest(VERSAO, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.reatribuirAutor(reatribuirId, reatribuirRequest))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver403AoEditarFormularioQuandoNaoEAutor() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        ContextoUtilizador master = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(master);
        doThrow(new AcaoNaoAutorizadaException("Só o autor pode executar esta ação."))
                .when(autorizacaoService).verificarAutoria(eq(master), any());
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoVazio(VERSAO);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveReatribuirAutorSemMotivoEAuditarSoOsDoisAutores() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Utilizador autorAnterior = processo.getCriadoPor();
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo.autor@cm-lisboa.pt");
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);

        Processo reatribuido = service.reatribuirAutor(processo.getId(), new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), null));

        assertThat(reatribuido.getCriadoPor()).isEqualTo(novoUtilizador);
        assertThat(reatribuido.getEntidade()).isEqualTo(entidade);
        verify(auditoriaService).registar("PROCESSO", processo.getId().toString(), "REATRIBUIR_AUTOR",
                admin.utilizadorId(), null, null,
                Map.of("autorAnteriorId", autorAnterior.getId(), "novoAutorId", novoUtilizador.getId()));
        verifyNoInteractions(pedidoApoioService);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "REJEITADO_DGT", "ANULADO_INCM"})
    void deveReatribuirAutorNosEstadosEmQueOOwnerAge(EstadoProcesso estado) {
        Processo processo = prepararProcesso(estado, config);
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo." + estado.name().toLowerCase() + "@cm-lisboa.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        Processo reatribuido = service.reatribuirAutor(processo.getId(), new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), null));

        assertThat(reatribuido.getEstado()).isEqualTo(estado);
        assertThat(reatribuido.getCriadoPor()).isEqualTo(novoUtilizador);
    }

    /** O estado vem antes de qualquer outra verificação do pedido: nem se procura o novo utilizador. */
    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class,
            names = {"EM_VALIDACAO_DGT", "VALIDADO_DGT", "ENVIADO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO", "ANULADO"})
    void deveRecusarReatribuirCom409NosEstadosEmQueOOwnerNaoAge(EstadoProcesso estado) {
        Processo processo = prepararProcesso(estado, config);
        Utilizador autorAnterior = processo.getCriadoPor();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, utilizadorId, UUID.randomUUID());

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("O processo não pode ser transferido neste estado.")
                .extracting("codigo").isEqualTo("ESTADO_INVALIDO");

        assertThat(processo.getCriadoPor()).isEqualTo(autorAnterior);
        verify(utilizadorRepository, never()).findById(any());
        verify(utilizadorEntidadeRepository, never()).existsByIdUtilizadorIdAndIdEntidadeId(any(), any());
        verifyNoInteractions(pedidoApoioService);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(any(), any(), eq("REATRIBUIR_AUTOR"), any(), any(), any(), any());
    }

    @Test
    void osEstadosTransferiveisSaoExatamenteOsQuatroEmQueOOwnerAge() {
        assertThat(ProcessoService.ESTADOS_TRANSFERIVEIS).containsExactlyInAnyOrder(EstadoProcesso.EM_SUBMISSAO,
                EstadoProcesso.ENVIADO_DGT, EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM);
    }

    @Test
    void deveRecusarReatribuirAUtilizadorSemVinculoAEntidadeDoInstrumento() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        Utilizador semVinculo = novoUtilizador("sem.vinculo@cm-porto.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, semVinculo.getId(), null);

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("O utilizador indicado não pertence à entidade do Instrumento.")
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("novoUtilizadorId"));
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver404AoReatribuirAUmUtilizadorInexistente() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Utilizador não encontrado.");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    /** A desativação saiu: quem estava desativado antes da migração é um utilizador como os outros. */
    @Test
    void deveAceitarReatribuirAQuemEstavaDesativadoAntesDaMigracao() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        Utilizador antesDesativado = novoUtilizadorDaEntidade("antes.desativado@cm-lisboa.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        Processo reatribuido = service.reatribuirAutor(processo.getId(),
                new ReatribuirAutorRequest(VERSAO, antesDesativado.getId(), null));

        assertThat(reatribuido.getCriadoPor()).isEqualTo(antesDesativado);
    }

    @Test
    void deveRecusarReatribuirAoProprioAutorAtualCom409() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(utilizadorEntidadeRepository.existsByIdUtilizadorIdAndIdEntidadeId(utilizadorId, entidade.getId())).thenReturn(true);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, utilizadorId, null);

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("O utilizador indicado já é o autor do Processo.")
                .extracting("codigo").isEqualTo("ESTADO_INVALIDO");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void oLogDaReatribuicaoTemOProcessoIdESemNomesNemEmails(CapturedOutput saida) {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo.autor.log@cm-lisboa.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        service.reatribuirAutor(processo.getId(), new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), null));

        assertThat(saida.getOut()).contains("processoId=" + processo.getId())
                .doesNotContain("novo.autor.log@cm-lisboa.pt", "ana.ferreira@cm-lisboa.pt", "Novo Autor", "Ana Ferreira");
    }

    @Test
    void deveResolverOPedidoDeApoioIndicadoAntesDeGravarEAuditarOPedidoApoioId() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        Utilizador autorAnterior = processo.getCriadoPor();
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo.autor.pedido@cm-lisboa.pt");
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        UUID pedidoApoioId = UUID.randomUUID();

        Processo reatribuido = service.reatribuirAutor(processo.getId(),
                new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), pedidoApoioId));

        assertThat(reatribuido.getCriadoPor()).isEqualTo(novoUtilizador);
        InOrder ordem = inOrder(pedidoApoioService, processoRepository, auditoriaService);
        ordem.verify(pedidoApoioService).resolverPedidoApoio(processo, pedidoApoioId);
        ordem.verify(processoRepository).saveAndFlush(processo);
        ordem.verify(auditoriaService).registar("PROCESSO", processo.getId().toString(), "REATRIBUIR_AUTOR",
                admin.utilizadorId(), null, null, Map.of("autorAnteriorId", autorAnterior.getId(),
                        "novoAutorId", novoUtilizador.getId(), "pedidoApoioId", pedidoApoioId));
    }

    /** O pedido de apoio vê-se depois do novo autor: um novo autor inválido não chega a tocar no pedido. */
    @Test
    void naoTocaNoPedidoDeApoioQuandoONovoAutorEInvalido() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Utilizador semVinculo = novoUtilizador("sem.vinculo.pedido@cm-porto.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, semVinculo.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido)).isInstanceOf(ValidacaoException.class);

        verifyNoInteractions(pedidoApoioService);
    }

    @Test
    void umPedidoDeApoioRecusadoDeixaOAutorEAAuditoriaComoEstavam() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Utilizador autorAnterior = processo.getCriadoPor();
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo.autor.recusado@cm-lisboa.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID pedidoApoioId = UUID.randomUUID();
        String erro = "O pedido de apoio indicado não está pendente.";
        when(pedidoApoioService.resolverPedidoApoio(processo, pedidoApoioId)).thenThrow(new ValidacaoException(erro,
                List.of(new ValidacaoException.CampoErro("pedidoApoioId", erro))));
        UUID id = processo.getId();
        ReatribuirAutorRequest pedido = new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), pedidoApoioId);

        assertThatThrownBy(() -> service.reatribuirAutor(id, pedido))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage(erro);

        assertThat(processo.getCriadoPor()).isEqualTo(autorAnterior);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(any(), any(), eq("REATRIBUIR_AUTOR"), any(), any(), any(), any());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void oLogDaReatribuicaoComPedidoDeApoioTemOProcessoIdEOPedidoApoioIdSemNomesNemEmails(CapturedOutput saida) {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Utilizador novoUtilizador = novoUtilizadorDaEntidade("novo.autor.logpedido@cm-lisboa.pt");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID pedidoApoioId = UUID.randomUUID();

        service.reatribuirAutor(processo.getId(), new ReatribuirAutorRequest(VERSAO, novoUtilizador.getId(), pedidoApoioId));

        assertThat(saida.getOut()).contains("processoId=" + processo.getId() + ", pedidoApoioId=" + pedidoApoioId)
                .doesNotContain("novo.autor.logpedido@cm-lisboa.pt", "ana.ferreira@cm-lisboa.pt", "Novo Autor", "Ana Ferreira");
    }

    private Utilizador novoUtilizadorDaEntidade(String email) {
        Utilizador novo = novoUtilizador(email);
        when(utilizadorEntidadeRepository.existsByIdUtilizadorIdAndIdEntidadeId(novo.getId(), entidade.getId())).thenReturn(true);
        return novo;
    }

    private Utilizador novoUtilizador(String email) {
        Utilizador novo = new Utilizador("Novo Autor", email, "INCM");
        ReflectionTestUtils.setField(novo, "id", UUID.randomUUID());
        when(utilizadorRepository.findById(novo.getId())).thenReturn(Optional.of(novo));
        return novo;
    }

    private Utilizador utilizadorMaster(UUID id) {
        Utilizador admin = new Utilizador("Admin DGT", "admin.dgt" + id + "@dgt.pt", "AD_DGT");
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    @Test
    void deveRegistarProcessoExternoJaPublicado() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(),
                "Publicado antes da plataforma existir.", null);

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(processo.getOrigemDados()).isEqualTo("REGISTO_EXTERNO");
        assertThat(processo.getOcorrencia()).isEqualTo(1);
        assertThat(processo.getNumeroDr()).isEqualTo("12345");
        assertThat(processo.getCriadoPor().getId()).isEqualTo(admin.utilizadorId());
        // Nasce publicado, sem transição nem autor real: não gera aviso.
        verifyNoInteractions(publicador);
    }

    @Test
    void deveCriarOIdusDoProcessoNoRegistoExterno() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        verify(idusProcessoService).criar(processo);
        verify(retencaoRegistoExternoService).reterAposCommit(processo.getId());
        verifyNoInteractions(retencaoDocumentosService);
    }

    @Test
    void deveAcrescentarOPubIncmAoIdusDepoisDeCriarONoRegistoExterno() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        InOrder ordem = inOrder(idusProcessoService);
        ordem.verify(idusProcessoService).criar(processo);
        ordem.verify(idusProcessoService).registarPublicacaoIncm(processo);
    }

    @Test
    void deveDevolver403AoRegistarExternoQuandoNaoEMaster() {
        ContextoUtilizador tecnico = contextoTecnico();
        when(contextoUtilizadorResolver.resolver()).thenReturn(tecnico);
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."))
                .when(autorizacaoService).verificarPerfilMaster(tecnico);
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver403AoRegistarExternoQuandoAtorEntidade() {
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação."))
                .when(autorizacaoService).verificarPerfilMaster(contexto);
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido)).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveAceitarRegistoExternoAindaQueAnterioresObrigatoriosNaoEstejamPublicados() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        when(autorizacaoService.procedimentoDisponivel(any(), any(), any())).thenReturn(false);
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        verify(autorizacaoService, never()).procedimentoDisponivel(any(), any(), any());
        verify(autorizacaoService, never()).verificarPossibilidadeDeAgir(any(), any(), any());
    }

    @Test
    void deveAtribuirNumeroTentativaSeguinteSemGateDeAnulacao() {
        Processo existente = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        when(processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(dinamica, dinamica.getTentativa(), config))
                .thenReturn(Optional.of(existente));
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "motivo", null);

        Processo registoExterno = service.registoExterno(dinamicaId, pedido).processo();

        assertThat(registoExterno.getOcorrencia()).isEqualTo(existente.getOcorrencia() + 1);
    }

    @Test
    void deveRegistarAuditoriaDeRegistoExternoComEstadoDepoisPublicado() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(),
                "Publicado antes da plataforma.", null);

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        verify(auditoriaService).registarTransicao("PROCESSO", processo.getId().toString(), "REGISTO_EXTERNO",
                new TransicaoEstado(null, "PUBLICADO"), admin.utilizadorId(), entidade.getId(),
                Map.of("motivo", "Publicado antes da plataforma.", "documentosCopiados", 0));
    }

    @Test
    void deveRegistarOProcessoExternoNaEntidadeIndicadaNoPedido() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        Entidade outra = EntidadeTeste.nova("509999999", "Câmara Municipal de Faro");
        ReflectionTestUtils.setField(outra, "id", 77L);
        when(entidadeRepository.findById(77L)).thenReturn(Optional.of(outra));

        Processo processo = service.registoExterno(dinamicaId,
                new RegistoExternoProcessoRequest(10L, 77L, "12345", LocalDate.now(), "motivo", null)).processo();

        assertThat(processo.getEntidade()).isSameAs(outra);
    }

    @Test
    void deveDevolver404AoRegistarExternoNumaEntidadeInexistente() {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        when(entidadeRepository.findById(99L)).thenReturn(Optional.empty());
        RegistoExternoProcessoRequest pedido = new RegistoExternoProcessoRequest(10L, 99L, "12345", LocalDate.now(), "motivo", null);

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido)).isInstanceOf(RecursoNaoEncontradoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    private Notificacao notificacaoOrigem(TipoNotificacao tipo, EstadoNotificacao estado) {
        Notificacao notificacao = new Notificacao(tipo, "Regularização", "Publicado fora da SAIGT.", utilizador);
        ReflectionTestUtils.setField(notificacao, "id", UUID.randomUUID());
        notificacao.setEstado(estado);
        when(notificacaoRepository.findComBloqueioById(notificacao.getId())).thenReturn(Optional.of(notificacao));
        return notificacao;
    }

    private RegistoExternoProcessoRequest pedidoComOrigem(UUID notificacaoOrigemId) {
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        when(utilizadorRepository.findById(admin.utilizadorId())).thenReturn(Optional.of(utilizadorMaster(admin.utilizadorId())));
        return new RegistoExternoProcessoRequest(10L, 1L, "12345", LocalDate.now(), "Publicado fora.", notificacaoOrigemId);
    }

    @Test
    void deveResolverARegularizacaoAtivaLigarAoProcessoECopiarOsAnexosNoRegistoExterno() {
        Notificacao regularizacao = notificacaoOrigem(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        when(copiaAnexosNotificacaoService.copiarParaDeposito(any(), eq(regularizacao.getId()))).thenReturn(2);

        ResultadoRegistoExterno resultado = service.registoExterno(dinamicaId, pedidoComOrigem(regularizacao.getId()));

        assertThat(resultado.documentosCopiados()).isEqualTo(2);
        assertThat(resultado.processo().getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(resultado.processo().getOrigemDados()).isEqualTo("REGISTO_EXTERNO");
        assertThat(regularizacao.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(regularizacao.getResolvidoEm()).isEqualTo(AGORA);
        assertThat(regularizacao.getProcesso()).isSameAs(resultado.processo());
        verify(copiaAnexosNotificacaoService).copiarParaDeposito(resultado.processo(), regularizacao.getId());
        verify(retencaoRegistoExternoService).reterAposCommit(resultado.processo().getId());
    }

    @Test
    void deveRegistarNaAuditoriaOMotivoANotificacaoDeOrigemEOsDocumentosCopiados() {
        Notificacao regularizacao = notificacaoOrigem(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.LIDA);
        when(copiaAnexosNotificacaoService.copiarParaDeposito(any(), any())).thenReturn(2);
        RegistoExternoProcessoRequest pedido = pedidoComOrigem(regularizacao.getId());

        Processo processo = service.registoExterno(dinamicaId, pedido).processo();

        verify(auditoriaService).registarTransicao(eq("PROCESSO"), eq(processo.getId().toString()), eq("REGISTO_EXTERNO"),
                eq(new TransicaoEstado(null, "PUBLICADO")), any(), eq(entidade.getId()),
                eq(Map.of("motivo", "Publicado fora.", "notificacaoOrigemId", regularizacao.getId().toString(),
                        "documentosCopiados", 2)));
    }

    @Test
    void deveCopiarOsAnexosSemMudarOEstadoNemAResolucaoDeUmaRespostaObrigatoriaJaResolvida() {
        Notificacao resposta = notificacaoOrigem(TipoNotificacao.RESPOSTA_OBRIGATORIA, EstadoNotificacao.RESOLVIDA);
        Instant resolvidaEm = Instant.parse("2026-11-01T09:00:00Z");
        resposta.setResolvidoEm(resolvidaEm);
        Processo original = prepararProcesso(EstadoProcesso.PUBLICADO, config);
        resposta.setProcesso(original);
        when(copiaAnexosNotificacaoService.copiarParaDeposito(any(), eq(resposta.getId()))).thenReturn(1);

        ResultadoRegistoExterno resultado = service.registoExterno(dinamicaId, pedidoComOrigem(resposta.getId()));

        assertThat(resultado.documentosCopiados()).isEqualTo(1);
        assertThat(resposta.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(resposta.getResolvidoEm()).isEqualTo(resolvidaEm);
        assertThat(resposta.getProcesso()).isSameAs(original);
    }

    @Test
    void deveDevolver409QuandoARegularizacaoJaEstaResolvidaSemCriarProcesso() {
        Notificacao regularizacao = notificacaoOrigem(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.RESOLVIDA);
        RegistoExternoProcessoRequest pedido = pedidoComOrigem(regularizacao.getId());

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("A notificação de origem já foi resolvida.");

        verify(processoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(copiaAnexosNotificacaoService, retencaoRegistoExternoService);
    }

    @Test
    void deveDevolver409QuandoARespostaObrigatoriaFoiRemovida() {
        Notificacao resposta = notificacaoOrigem(TipoNotificacao.RESPOSTA_OBRIGATORIA, EstadoNotificacao.REMOVIDA);
        RegistoExternoProcessoRequest pedido = pedidoComOrigem(resposta.getId());

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido)).isInstanceOf(EstadoInvalidoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver400NoCampoNotificacaoOrigemIdQuandoEUmPedidoDeApoio() {
        Notificacao pedidoApoio = notificacaoOrigem(TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA);
        RegistoExternoProcessoRequest pedido = pedidoComOrigem(pedidoApoio.getId());

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .containsExactly(new ValidacaoException.CampoErro("notificacaoOrigemId",
                                "A notificação de origem não é uma regularização nem uma resposta obrigatória.")));

        verify(processoRepository, never()).saveAndFlush(any());
        assertThat(pedidoApoio.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    void deveDevolver404QuandoANotificacaoDeOrigemNaoExiste() {
        UUID inexistente = UUID.randomUUID();
        when(notificacaoRepository.findComBloqueioById(inexistente)).thenReturn(Optional.empty());
        RegistoExternoProcessoRequest pedido = pedidoComOrigem(inexistente);

        assertThatThrownBy(() -> service.registoExterno(dinamicaId, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Notificação de origem não encontrada.");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRegistarSemNotificacaoComoHojeEComZeroDocumentosCopiados() {
        ResultadoRegistoExterno resultado = service.registoExterno(dinamicaId, pedidoComOrigem(null));

        assertThat(resultado.documentosCopiados()).isZero();
        assertThat(resultado.processo().getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        verifyNoInteractions(notificacaoRepository, copiaAnexosNotificacaoService);
        verify(retencaoRegistoExternoService).reterAposCommit(resultado.processo().getId());
    }

    private ContextoUtilizador contextoPcm(String nif) {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, nif, "PCM", null, null, null, null, TipoEntidade.PCM, null);
    }

    private Utilizador utilizadorPcm(UUID id) {
        Utilizador pcm = new Utilizador("Utilizador PCM", "pcm" + id + "@dgterritorio.gov.pt", "INCM");
        ReflectionTestUtils.setField(pcm, "id", id);
        return pcm;
    }

    /** Destinatário com vínculo a uma entidade PCM ativa: o caminho feliz. */
    private UUID destinatarioPcmValido() {
        UUID pcmId = UUID.randomUUID();
        when(utilizadorEntidadeRepository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(pcmId, TipoEntidade.PCM))
                .thenReturn(true);
        when(utilizadorRepository.findById(pcmId)).thenReturn(Optional.of(utilizadorPcm(pcmId)));
        return pcmId;
    }

    private Processo processoProntoParaPcm() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configComSerie("PRIMEIRA"));
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(true);
        return processo;
    }

    @Test
    void deveEnviarParaPcmQuandoSerieDrPrimeiraEValidadoDgt() {
        Processo processo = processoProntoParaPcm();
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        UUID pcmId = destinatarioPcmValido();

        Processo enviado = service.enviarPcm(processo.getId(), new EnviarPcmRequest(VERSAO, pcmId));

        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(enviado.getPcmAtribuidoA().getId()).isEqualTo(pcmId);
        verify(autorizacaoService).verificarPossibilidadeDeEnviarPcm(admin, processo);
        verify(auditoriaService).registar("PROCESSO", processo.getId().toString(), "ENVIAR_PCM",
                admin.utilizadorId(), null, null, Map.of("pcmAtribuidoAId", pcmId));
    }

    @Test
    void deveEnviarParaPcmProcedimentoDeRatificacaoDeUmPlano() {
        // `dinamica` (setUp) já pertence a um Instrumento de categoria "PLANO" — só a serie_dr do Procedimento importa.
        Processo processo = processoProntoParaPcm();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID pcmId = destinatarioPcmValido();

        Processo enviado = service.enviarPcm(processo.getId(), new EnviarPcmRequest(VERSAO, pcmId));

        assertThat(enviado.getPcmAtribuidoA().getId()).isEqualTo(pcmId);
    }

    @Test
    void deveEnviarParaPcmQuandoTecnico() {
        Processo processo = processoProntoParaPcm();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID pcmId = destinatarioPcmValido();

        Processo enviado = service.enviarPcm(processo.getId(), new EnviarPcmRequest(VERSAO, pcmId));

        assertThat(enviado.getPcmAtribuidoA().getId()).isEqualTo(pcmId);
    }

    @Test
    void deveDevolverPcmAtorNaoAutorizadoAoEnviarParaPcmQuandoVisitante() {
        Processo processo = processoProntoParaPcm();
        ContextoUtilizador visitante = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.VISITANTE, null, null, null, null, null, null, null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(visitante);
        doThrow(new PcmAtorNaoAutorizadoException("Só a DGT (MASTER ou TECNICO) pode enviar processos à PCM."))
                .when(autorizacaoService).verificarPossibilidadeDeEnviarPcm(eq(visitante), any());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmAtorNaoAutorizadoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_ATOR_NAO_AUTORIZADO");

        assertThat(processo.getPcmAtribuidoA()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverPcmSerieNaoPrimeiraAoEnviarParaPcmQuandoSerieDrNaoEPrimeira() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config); // config nasce com serie_dr = SEGUNDA_H (setUp)
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(true);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_SERIE_NAO_PRIMEIRA");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverPcmEstadoInvalidoAoEnviarParaPcmEmValidacaoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, configComSerie("PRIMEIRA"));
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(true);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_ESTADO_INVALIDO");

        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverPcmJaAtribuidoSemMudarAAtribuicao() {
        Processo processo = processoProntoParaPcm();
        Utilizador primeiro = utilizadorPcm(UUID.randomUUID());
        processo.setPcmAtribuidoA(primeiro);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_JA_ATRIBUIDO")
                .hasMessage("O processo já está com a PCM.");

        assertThat(processo.getPcmAtribuidoA()).isSameAs(primeiro);
        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(any(), any(), eq("ENVIAR_PCM"), any(), any(), any(), any());
    }

    @Test
    void deveDevolverPcmDocumentosPorValidarQuandoHaDocumentosNaoValidados() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configComSerie("PRIMEIRA"));
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(false);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_DOCUMENTOS_POR_VALIDAR")
                .hasMessage("Existem documentos por validar.");

        assertThat(processo.getPcmAtribuidoA()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolverPcmUtilizadorInvalidoNoCampoQuandoODestinatarioNaoTemVinculoAPcm() {
        Processo processo = processoProntoParaPcm();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID semVinculo = UUID.randomUUID();
        when(utilizadorRepository.findById(semVinculo)).thenReturn(Optional.of(utilizadorPcm(semVinculo)));
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, semVinculo);

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOf(PcmUtilizadorInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_UTILIZADOR_INVALIDO")
                .hasMessage("O utilizador indicado não pertence à PCM.")
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("utilizadorId"));

        assertThat(processo.getPcmAtribuidoA()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveVerificarAVersaoAntesDasRegrasDeEstadoAoEnviarParaPcm() {
        Processo processo = prepararProcessoNaVersao(EstadoProcesso.EM_VALIDACAO_DGT, 5L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID id = processo.getId();
        EnviarPcmRequest antiga = new EnviarPcmRequest(4L, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, antiga)).isInstanceOf(VersaoDesatualizadaException.class);
    }

    private void envioLevaPara(Processo processo, EstadoProcesso estadoFinal) {
        when(envioIncmService.enviar(eq(processo.getId()), any())).thenAnswer(invocacao -> {
            processo.setEstado(estadoFinal);
            return processo;
        });
    }

    /** Com a PCM, o envio à INCM fica bloqueado antes de chamar a INCM (TR-114). */
    @Test
    void deveRecusarEnviarIncmComPcmJaAtribuidoQuandoOProcessoEstaComAPcm() {
        InstrumentoProcedimentoConfig configPrimeira = configComSerie("PRIMEIRA");
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configPrimeira);
        ReflectionTestUtils.setField(processo, "pcmAtribuidoA", utilizadorPcm(UUID.randomUUID()));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        assertThatThrownBy(() -> service.enviarIncm(processo.getId(), PEDIDO_VERSAO))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_JA_ATRIBUIDO")
                .hasMessage("O processo está com a PCM.");
        verify(envioIncmService, never()).enviar(any(), any());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    /** O documento que a PCM acrescentou e ainda está por validar não pode ir no `SubmitNewAct` (FR-059). */
    @Test
    void deveRecusarEnviarIncmComPcmDocumentosPorValidarDepoisDaDevolucao() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configComSerie("PRIMEIRA"));
        processo.setPcmDevolvidoEm(java.time.Instant.now());
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(false);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());

        assertThatThrownBy(() -> service.enviarIncm(processo.getId(), PEDIDO_VERSAO))
                .isInstanceOf(PcmEstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_DOCUMENTOS_POR_VALIDAR")
                .hasMessage("Existem documentos por validar.");
        verify(envioIncmService, never()).enviar(any(), any());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    @Test
    void deveAutorizarPcmAEnviarIncmQuandoPcmAtribuidoAVazio() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoPcm(entidade.getNipc()));
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        Processo enviado = service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(enviado.getPcmAtribuidoA()).isNull();
    }

    @Test
    void deveAutorizarPcmAEnviarIncmMesmoDeEntidadeDiferenteDoInstrumento() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoPcm("999999999")); // entidade diferente da do Instrumento
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        Processo enviado = service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
    }

    @Test
    void deveDevolverEmValidacaoIncmQuandoOServicoDeEnvioAceitaOAto() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        ContextoUtilizador tecnico = contextoTecnico();
        when(contextoUtilizadorResolver.resolver()).thenReturn(tecnico);
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        Processo enviado = service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        verify(envioIncmService).enviar(processo.getId(), tecnico.utilizadorId());
    }

    @Test
    void deveDevolverAnuladoIncmQuandoOServicoDeEnvioRejeitaOAto() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        ContextoUtilizador admin = contextoMaster();
        when(contextoUtilizadorResolver.resolver()).thenReturn(admin);
        envioLevaPara(processo, EstadoProcesso.ANULADO_INCM);

        Processo enviado = service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        verify(envioIncmService).enviar(processo.getId(), admin.utilizadorId());
    }

    @Test
    void naoDeveGravarNemAuditarNoProcessoServiceAoEnviarIncm() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        verify(processoRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registarTransicao(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveDeixarSubirIncmIndisponivelSemAlterarOProcesso() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        doThrow(new IncmIndisponivelException("A INCM não respondeu ao envio do ato. Tente novamente mais tarde."))
                .when(envioIncmService).enviar(eq(processo.getId()), any());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.enviarIncm(id, PEDIDO_VERSAO)).isInstanceOf(IncmIndisponivelException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver409AoPcmTentarEnviarIncmForaDeValidadoDgt() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoPcm(entidade.getNipc()));
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.enviarIncm(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);

        verify(processoRepository, never()).saveAndFlush(any());
        verify(envioIncmService, never()).enviar(any(), any());
    }

    @Test
    void deveDevolver403AoEnviarIncmQuandoAtorEntidade() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        // `contexto` (setUp) já é ator ENTIDADE.
        doThrow(new AcaoNaoAutorizadaException("Só a DGT ou a PCM podem enviar esta tentativa à INCM."))
                .when(autorizacaoService).verificarPossibilidadeDeEnviarIncm(eq(contexto), any());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.enviarIncm(id, PEDIDO_VERSAO)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(processoRepository, never()).saveAndFlush(any());
        verify(envioIncmService, never()).enviar(any(), any());
    }

    private Processo discussaoPublicaComDataDeliberacao(String dataDeliberacao, String codigoProcedimento, long idConfig) {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcedimento(codigoProcedimento, idConfig));
        processo.getDinamica().setDataCaducidade(LocalDate.of(2027, 3, 31));
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"dataDeliberacao\":\"" + dataDeliberacao + "\"}}}");
        return processo;
    }

    @Test
    void deveRecusarADiscussaoPublicaComADataDaDeliberacaoDepoisDaDataLimiteSemGravarNada() {
        Processo processo = discussaoPublicaComDataDeliberacao("2027-04-01", "ABERTURA_DISCUSSAO_PUBLICA", 13L);
        UUID id = processo.getId();
        long versao = processo.getVersao();

        ValidacaoException erro = (ValidacaoException) org.assertj.core.api.Assertions.catchThrowable(
                () -> service.submeter(id, pedidoSubmeterSegundaSerieCompleto()));

        assertThat(erro).isNotNull();
        assertThat(caminhosDosErros(erro)).containsExactly("informacaoBase.deliberacao.dataDeliberacao");
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getVersao()).isEqualTo(versao);
        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveSubmeterADiscussaoPublicaComADataDaDeliberacaoIgualADataLimite() {
        Processo processo = discussaoPublicaComDataDeliberacao("2027-03-31", "ABERTURA_DISCUSSAO_PUBLICA", 13L);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void naDecisaoDeInicioADataDaDeliberacaoDepoisDaDataLimiteNaoEVerificada() {
        Processo processo = discussaoPublicaComDataDeliberacao("2027-04-01", "DECISAO_INICIO", 14L);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveGravarORascunhoDaDiscussaoPublicaComADataForaDoPrazo() {
        Processo processo = discussaoPublicaComDataDeliberacao("2027-04-01", "ABERTURA_DISCUSSAO_PUBLICA", 13L);

        assertThatCode(() -> service.atualizarDadosFormulario(processo.getId(),
                pedidoComInformacaoBase("Resumo do ato.", null))).doesNotThrowAnyException();
    }

    // --- Informação Base na submissão: coerência entre a Deliberação e o Texto do ato e data limite no final ---

    private static final LocalDate DATA_TEXTO_ATO = LocalDate.of(2027, 3, 31);
    private static final String DELIBERACAO_UM_ANO_SIM = "\"deliberacao\":{\"prazoAnos\":1,\"avaliacaoAmbiental\":true,"
            + "\"dataDeliberacao\":\"2027-03-31\",\"textoPublicar\":\"Qualquer texto, sem validações configuradas.\"}";

    private Processo comInformacaoBase(String codigoProcedimento, long idConfig, String informacaoBase,
                                       boolean procedimentoFinal, Set<ElementoInformacaoBase> previstos) {
        InstrumentoProcedimentoConfig configDoProcesso = configDoProcedimento(codigoProcedimento, idConfig);
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configDoProcesso);
        processo.setDadosFormulario("{\"informacaoBase\":{" + informacaoBase + "}}");
        when(documentoConfigRepository.findElementosInformacaoBaseAtivos(configDoProcesso)).thenReturn(previstos);
        when(numeroDepositoService.doProcedimentoFinal(processo)).thenReturn(procedimentoFinal);
        return processo;
    }

    private Processo comOsDoisElementos(String codigoProcedimento, long idConfig, String informacaoBase, boolean procedimentoFinal) {
        return comInformacaoBase(codigoProcedimento, idConfig, informacaoBase, procedimentoFinal,
                Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO));
    }

    private SubmeterProcessoRequest pedidoSubmeterComTextoAtoDe(LocalDate dataAssinatura) {
        return PedidosSubmissao.confirmado(VERSAO, "Resumo do ato.", dataAssinatura, "Norma Legal X", "TEXTO_INTEGRAL", null, null);
    }

    private ValidacaoException recusaDaSubmissao(Processo processo, LocalDate dataAssinatura) {
        long versao = processo.getVersao();
        ValidacaoException erro = (ValidacaoException) org.assertj.core.api.Assertions.catchThrowable(
                () -> service.submeter(processo.getId(), pedidoSubmeterComTextoAtoDe(dataAssinatura)));
        assertThat(erro).isNotNull();
        assertThat(erro.getCodigo()).isEqualTo("VALIDACAO");
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getVersao()).isEqualTo(versao);
        verify(processoRepository, never()).saveAndFlush(processo);
        return erro;
    }

    @Test
    void deveRecusarADiscussaoPublicaComOPrazoDaDeliberacaoDiferenteDoDoTextoDoAto() {
        Processo processo = comOsDoisElementos("ABERTURA_DISCUSSAO_PUBLICA", 203L,
                DELIBERACAO_UM_ANO_SIM + ",\"textoAto\":{\"prazoAnos\":2,\"avaliacaoAmbiental\":true}", false);

        ValidacaoException erro = recusaDaSubmissao(processo, DATA_TEXTO_ATO);

        assertThat(erro.getMessage()).isEqualTo("O prazo tem de ser o mesmo da Deliberação.");
        assertThat(caminhosDosErros(erro)).containsExactly("informacaoBase.textoAto.prazoAnos");
    }

    @Test
    void deveRecusarOProcedimentoFinalIncoerenteEForaDoPrazoNumaSoRecusaComTodosOsCampos() {
        processoFinalForaDoPrazo();
        Processo processo = comOsDoisElementos("ELABORACAO", 204L,
                "\"deliberacao\":{\"prazoAnos\":1,\"avaliacaoAmbiental\":true,\"dataDeliberacao\":\"2027-04-02\"},"
                        + "\"textoAto\":{\"prazoMeses\":12,\"avaliacaoAmbiental\":false}", true);

        ValidacaoException erro = recusaDaSubmissao(processo, LocalDate.of(2027, 4, 1));

        assertThat(erro.getMessage()).isEqualTo("A Informação Base tem campos incoerentes.");
        assertThat(erro.getCampos()).containsExactly(
                new ValidacaoException.CampoErro("informacaoBase.textoAto.prazoAnos", "O prazo tem de ser o mesmo da Deliberação."),
                new ValidacaoException.CampoErro("informacaoBase.textoAto.avaliacaoAmbiental",
                        "A avaliação ambiental tem de ser a mesma da Deliberação."),
                new ValidacaoException.CampoErro("informacaoBase.deliberacao.dataDeliberacao",
                        "A data da deliberação tem de ser igual ou anterior à data do texto do ato."),
                new ValidacaoException.CampoErro("informacaoBase.deliberacao.dataDeliberacao",
                        "Posterior à data limite da dinâmica (31/03/2027)."));
    }

    private void processoFinalForaDoPrazo() {
        dinamica.setDataCaducidade(LocalDate.of(2027, 3, 31));
    }

    @ParameterizedTest
    @CsvSource({"ELABORACAO, 204", "REVISAO, 208", "DECISAO_INICIO, 201", "ALTERACAO, 211"})
    void deveRecusarAAvaliacaoAmbientalDiferenteNoProcedimentoFinalNaDecisaoDeInicioENaAlteracao(String codigo, long idConfig) {
        Processo processo = comOsDoisElementos(codigo, idConfig,
                DELIBERACAO_UM_ANO_SIM + ",\"textoAto\":{\"prazoAnos\":1,\"avaliacaoAmbiental\":false}",
                !"DECISAO_INICIO".equals(codigo));

        ValidacaoException erro = recusaDaSubmissao(processo, DATA_TEXTO_ATO);

        assertThat(caminhosDosErros(erro)).containsExactly("informacaoBase.textoAto.avaliacaoAmbiental");
    }

    @Test
    void deveSubmeterOProcedimentoFinalCoerenteComADataDaDeliberacaoIgualADataLimiteEAoTextoDoAto() {
        processoFinalForaDoPrazo();
        Processo processo = comOsDoisElementos("REVISAO", 208L,
                DELIBERACAO_UM_ANO_SIM + ",\"textoAto\":{\"prazoAnos\":1,\"prazoMeses\":0,\"avaliacaoAmbiental\":true}", true);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterComTextoAtoDe(DATA_TEXTO_ATO));

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveRecusarOProcedimentoFinalComADataDaDeliberacaoDepoisDaDataLimite() {
        processoFinalForaDoPrazo();
        Processo processo = comOsDoisElementos("ELABORACAO", 204L,
                "\"deliberacao\":{\"dataDeliberacao\":\"2027-04-01\"}", true);

        ValidacaoException erro = recusaDaSubmissao(processo, LocalDate.of(2027, 5, 1));

        assertThat(erro.getMessage()).isEqualTo("A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.");
        assertThat(erro.getCampos()).containsExactly(new ValidacaoException.CampoErro(
                "informacaoBase.deliberacao.dataDeliberacao", "Posterior à data limite da dinâmica (31/03/2027)."));
    }

    @Test
    void naDecisaoDeInicioENumProcedimentoQueNaoEOFinalADataLimiteNaoSeAplica() {
        processoFinalForaDoPrazo();
        Processo processo = comOsDoisElementos("DECISAO_INICIO", 201L,
                "\"deliberacao\":{\"dataDeliberacao\":\"2027-04-01\"}", false);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterComTextoAtoDe(LocalDate.of(2027, 5, 1)));

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void noProcedimentoFinalSemDataLimiteARegraNaoSeAplica() {
        Processo processo = comOsDoisElementos("ELABORACAO", 204L,
                "\"deliberacao\":{\"dataDeliberacao\":\"2027-04-01\"}", true);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterComTextoAtoDe(LocalDate.of(2027, 5, 1)));

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void numProcedimentoSoComOTextoDoAtoNaoHaCoerenciaAVerificar() {
        Processo processo = comInformacaoBase("ABERTURA_DISCUSSAO_PUBLICA", 203L,
                "\"textoAto\":{\"prazoAnos\":2,\"avaliacaoAmbiental\":false}", false,
                Set.of(ElementoInformacaoBase.TEXTO_ATO));

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterComTextoAtoDe(DATA_TEXTO_ATO));

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveGravarORascunhoComAInformacaoBaseIncoerente() {
        Processo processo = comOsDoisElementos("ELABORACAO", 204L, DELIBERACAO_UM_ANO_SIM, true);
        when(processoValidacaoService.elementosPrevistos(processo.getInstrumentoProcedimentoConfig()))
                .thenReturn(Set.of(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO));
        InformacaoBaseRequest incoerente = new InformacaoBaseRequest(
                new InformacaoBaseDeliberacaoRequest(1, null, null, true, LocalDate.of(2030, 1, 1), null),
                new InformacaoBaseTextoAtoRequest(2, null, null, false));

        assertThatCode(() -> service.atualizarDadosFormulario(processo.getId(), pedidoComInformacaoBase("Resumo do ato.",
                incoerente))).doesNotThrowAnyException();
        verify(documentoConfigRepository, never()).findElementosInformacaoBaseAtivos(any());
    }

    // --- Eventos das transições (avisos no Centro de Notificações) ---

    private void publicouSo(Object evento) {
        verify(publicador, times(1)).publishEvent(any(Object.class));
        verify(publicador).publishEvent(evento);
    }

    @Test
    void devePublicarUmEventoAoSubmeter() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        publicouSo(new ProcessoTransitouEvento(processo.getId(), EstadoProcesso.EM_SUBMISSAO,
                EstadoProcesso.ENVIADO_DGT, null, null, List.of()));
    }

    @Test
    void devePublicarUmEventoAoResgatar() {
        Processo processo = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);

        service.resgatar(processo.getId(), PEDIDO_VERSAO);

        publicouSo(ProcessoTransitouEvento.de(processo.getId(), EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_SUBMISSAO));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void devePublicarUmEventoAoCorrigir(EstadoProcesso estadoAntes) {
        Processo processo = prepararProcesso(estadoAntes, config);

        service.corrigir(processo.getId(), PEDIDO_VERSAO);

        publicouSo(ProcessoTransitouEvento.de(processo.getId(), estadoAntes, EstadoProcesso.EM_SUBMISSAO));
    }

    @Test
    void devePublicarUmEventoAoCapturarEAoValidar() {
        Processo capturar = prepararProcesso(EstadoProcesso.ENVIADO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());

        service.capturar(capturar.getId(), PEDIDO_VERSAO);

        publicouSo(ProcessoTransitouEvento.de(capturar.getId(), EstadoProcesso.ENVIADO_DGT,
                EstadoProcesso.EM_VALIDACAO_DGT));
        service.validar(capturar.getId(), PEDIDO_VERSAO);
        verify(publicador).publishEvent(ProcessoTransitouEvento.de(capturar.getId(), EstadoProcesso.EM_VALIDACAO_DGT,
                EstadoProcesso.VALIDADO_DGT));
        verify(publicador, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void devePublicarAoRejeitarOEventoComARecusaCriada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        when(rejeicaoProcessoService.registarRecusaDgt(any(), any(), any(), any()))
                .thenReturn(ProcessoRejeicao.builder().id(31L).sequencia(1).build());

        service.rejeitar(processo.getId(), new RejeitarProcessoRequest(VERSAO, null));

        publicouSo(new ProcessoTransitouEvento(processo.getId(), EstadoProcesso.EM_VALIDACAO_DGT,
                EstadoProcesso.REJEITADO_DGT, 31L, null, List.of()));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "EM_VALIDACAO_DGT", "VALIDADO_DGT"})
    void devePublicarAoAnularOEventoComOMotivoEAOrigemReal(EstadoProcesso origem) {
        Processo processo = prepararProcesso(origem, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());

        service.anular(processo.getId(), new AnularProcessoRequest(VERSAO, "Duplicado"));

        publicouSo(new ProcessoTransitouEvento(processo.getId(), origem, EstadoProcesso.ANULADO, null, "Duplicado",
                List.of()));
    }

    @Test
    void devePublicarUmEventoAoEnviarParaPcm() {
        Processo processo = processoProntoParaPcm();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID pcmId = destinatarioPcmValido();

        service.enviarPcm(processo.getId(), new EnviarPcmRequest(VERSAO, pcmId));

        publicouSo(new ProcessoEnviadoPcmEvento(processo.getId(), pcmId));
    }

    @Test
    void naoDevePublicarQuandoATransicaoFalha() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.resgatar(id, PEDIDO_VERSAO)).isInstanceOf(EstadoInvalidoException.class);

        verifyNoInteractions(publicador);
    }

    private Dinamica outraDinamica() {
        Dinamica outra = new Dinamica(dinamica.getInstrumentoDinamicaConfig(), "Alteração de outro IGT", utilizador);
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        return outra;
    }

    private static AtualizarDadosFormularioRequest pedidoComDinamicaAssociada(UUID dinamicaAssociadaId) {
        return new AtualizarDadosFormularioRequest(VERSAO, "Resumo novo.", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, dinamicaAssociadaId);
    }

    @Test
    void deveGravarADinamicaAssociadaDepoisDeVerificadaEAuditarAMudancaSoComOsIds() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Dinamica associada = outraDinamica();
        when(dinamicaRepository.getReferenceById(associada.getId())).thenReturn(associada);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedidoComDinamicaAssociada(associada.getId()));

        verify(dinamicaAssociadaService).verificar(processo, associada.getId());
        assertThat(atualizado.getDinamicaAssociada()).isSameAs(associada);
        Map<String, Object> dados = new java.util.LinkedHashMap<>();
        dados.put("dinamicaAssociadaIdAnterior", null);
        dados.put("dinamicaAssociadaId", associada.getId());
        verify(auditoriaService).registar("PROCESSO", processo.getId().toString(), "ASSOCIAR_DINAMICA", utilizadorId, dados);
    }

    @Test
    void deveLimparADinamicaAssociadaQuandoOPedidoNaoATrazENaoAuditarSemMudanca() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        service.atualizarDadosFormulario(processo.getId(), pedidoComDinamicaAssociada(null));

        verify(dinamicaAssociadaService).verificar(processo, null);
        assertThat(processo.getDinamicaAssociada()).isNull();
        verify(auditoriaService, never()).registar(eq("PROCESSO"), any(), eq("ASSOCIAR_DINAMICA"), any(), any());
    }

    @Test
    void naoDeveGravarNadaQuandoADinamicaAssociadaERecusada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        UUID indisponivel = UUID.randomUUID();
        doThrow(new DinamicaAssociadaInvalidaException()).when(dinamicaAssociadaService).verificar(processo, indisponivel);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = pedidoComDinamicaAssociada(indisponivel);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);

        assertThat(processo.getDinamicaAssociada()).isNull();
        assertThat(processo.getSumario()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarASubmissaoComADinamicaAssociadaEntretantoTerminadaEDeixarOProcessoEmSubmissao() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        Dinamica terminada = outraDinamica();
        processo.setDinamicaAssociada(terminada);
        doThrow(new DinamicaAssociadaInvalidaException()).when(dinamicaAssociadaService).verificar(processo, terminada.getId());
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getDataSubmissao()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
        verify(completudeDocumentalService, never()).verificar(any());
    }

    @Test
    void deveSubmeterSemAssociacaoComAVerificacaoDoValorNulo() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        verify(dinamicaAssociadaService).verificar(processo, null);
        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void naoDeveConsultarADinamicaAssociadaAoEnviarIncm() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        processo.setDinamicaAssociada(outraDinamica());
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        verify(envioIncmService).enviar(eq(processo.getId()), any());
        verifyNoInteractions(dinamicaAssociadaService);
    }
    private static AtualizarDadosFormularioRequest pedidoComSimultaneoComPmot(String resposta, UUID dinamicaAssociadaId) {
        return new AtualizarDadosFormularioRequest(VERSAO, "Resumo novo.", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, dinamicaAssociadaId,
                new com.conkord.saigt.dto.instrumento.DadosFormularioRequest(resposta));
    }

    @Test
    void deveGravarARespostaAPerguntaDoPmotEmDadosFormularioDepoisDeVerificada() throws Exception {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedidoComSimultaneoComPmot("SIM", null));

        verify(dinamicaAssociadaService).verificarResposta(processo, "SIM", null);
        assertThat(objectMapper.readTree(atualizado.getDadosFormulario()).path("simultaneoComPmot").asText()).isEqualTo("SIM");
        assertThat(atualizado.getDinamicaAssociada()).isNull();
    }

    @Test
    void deveRetirarARespostaAPerguntaDoPmotQuandoOPedidoNaoATraz() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDadosFormulario("{\"simultaneoComPmot\":\"SIM\"}");

        Processo atualizado = service.atualizarDadosFormulario(processo.getId(), pedidoComDinamicaAssociada(null));

        verify(dinamicaAssociadaService).verificarResposta(processo, null, null);
        assertThat(atualizado.getDadosFormulario()).satisfiesAnyOf(
                dados -> assertThat(dados).isNull(), dados -> assertThat(dados).doesNotContain("simultaneoComPmot"));
    }

    @Test
    void naoDeveGravarNadaQuandoARespostaAPerguntaDoPmotERecusada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        UUID associada = UUID.randomUUID();
        doThrow(new DinamicaAssociadaInvalidaException()).when(dinamicaAssociadaService)
                .verificarResposta(processo, "NAO", associada);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = pedidoComSimultaneoComPmot("NAO", associada);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);

        assertThat(processo.getDadosFormulario()).isNull();
        assertThat(processo.getSumario()).isNull();
        assertThat(processo.getDinamicaAssociada()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    private static AtualizarDadosFormularioRequest pedidoComDestino(String destino) {
        return new AtualizarDadosFormularioRequest(VERSAO, "Resumo novo.", null, null, null, null, null, null, null, null,
                List.of("PECAS_ESCRITAS"), null, null, null, null, null, null, null, null, null, null, destino);
    }

    /** O formulário da REN ainda não existe: o duplo do catálogo faz o formulário deste processo admitir. */
    private void formularioAdmiteSoDeposito() {
        doReturn(true).when(catalogoDestino).admiteSoDeposito(any());
    }

    @Test
    void deveGravarSoDepositoNumFormularioQueOAdmiteSemFicheiros() {
        formularioAdmiteSoDeposito();
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), pedidoComDestino("SO_DEPOSITO"));

        assertThat(gravado.getDestino()).isEqualTo(DestinoProcesso.SO_DEPOSITO);
        assertThat(gravado.getSumario()).isEqualTo("Resumo novo.");
        verify(processoRepository).saveAndFlush(processo);
    }

    @Test
    void deveRecusarSoDepositoNumFormularioQueNaoOAdmiteSemGravarNada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setSumario("Antigo");
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = pedidoComDestino("SO_DEPOSITO");

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(DestinoInvalidoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("DESTINO_INVALIDO");
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("destino");
                });

        assertThat(processo.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(processo.getSumario()).isEqualTo("Antigo");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarMudarODestinoDeUmProcessoComFicheirosEAceitarOMesmoDestino() {
        formularioAdmiteSoDeposito();
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setSumario("Antigo");
        when(processoDocumentoRepository.existsByProcesso_Id(processo.getId())).thenReturn(true);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest mudar = pedidoComDestino("SO_DEPOSITO");

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, mudar))
                .isExactlyInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Remova os ficheiros carregados antes de mudar o destino.");
        assertThat(processo.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(processo.getSumario()).isEqualTo("Antigo");
        verify(processoRepository, never()).saveAndFlush(any());

        Processo gravado = service.atualizarDadosFormulario(id, pedidoComDestino("PUBLICACAO_DEPOSITO"));

        assertThat(gravado.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(gravado.getSumario()).isEqualTo("Resumo novo.");
    }

    @Test
    void deveManterODestinoGravadoQuandoOPedidoOOmite() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(gravado.getDestino()).isEqualTo(DestinoProcesso.SO_DEPOSITO);
        verify(processoDocumentoRepository, never()).existsByProcesso_Id(any());
    }

    @Test
    void deveRecusarASubmissaoDeUmSoDepositoGravadoQueOFormularioNaoAdmite() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido)).isInstanceOf(DestinoInvalidoException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveSubmeterUmSoDepositoNumFormularioQueOAdmite() {
        formularioAdmiteSoDeposito();
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);

        Processo submetido = service.submeter(processo.getId(), pedidoSubmeterSegundaSerieCompleto());

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
    }

    @Test
    void deveRecusarEnviarIncmUmSoDepositoSemChamarAIncm() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico());
        UUID id = processo.getId();

        assertThatThrownBy(() -> service.enviarIncm(id, PEDIDO_VERSAO))
                .isInstanceOfSatisfying(DestinoSoDepositoException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("DESTINO_SO_DEPOSITO"));

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        verify(envioIncmService, never()).enviar(any(), any());
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarEnviarPcmUmSoDepositoSemAtribuirNemNotificar() {
        Processo processo = processoProntoParaPcm();
        processo.setDestino(DestinoProcesso.SO_DEPOSITO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID id = processo.getId();
        EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

        assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                .isInstanceOfSatisfying(DestinoSoDepositoException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("DESTINO_SO_DEPOSITO"));

        assertThat(processo.getPcmAtribuidoA()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
        verify(publicador, never()).publishEvent(any(Object.class));
    }

    @Test
    void deveEnviarIncmUmProcessoParaPublicacaoEDepositoComoHoje() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, config);
        ContextoUtilizador tecnico = contextoTecnico();
        when(contextoUtilizadorResolver.resolver()).thenReturn(tecnico);
        envioLevaPara(processo, EstadoProcesso.EM_VALIDACAO_INCM);

        Processo enviado = service.enviarIncm(processo.getId(), PEDIDO_VERSAO);

        assertThat(processo.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
        assertThat(enviado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        verify(envioIncmService).enviar(processo.getId(), tecnico.utilizadorId());
    }

    // ---------- Tipo de ato escolhido no cabeçalho (OI-487) ----------

    private AtualizarDadosFormularioRequest rascunhoComTipoAto(Long tipoAtoId) {
        return new AtualizarDadosFormularioRequest(VERSAO, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, tipoAtoId);
    }

    /** Um tipo de 2.ª série com o `ActTypeId` indicado, encontrado por `findById`. */
    private TipoAto tipoAtoDaSegundaSerie(long id, long actTypeId) {
        TipoAto outro = new TipoAto("ATO-" + id, "Ato " + id, 1);
        ReflectionTestUtils.setField(outro, "id", id);
        outro.setIncmActTypeIdSerie2(actTypeId);
        when(tipoAtoRepository.findById(id)).thenReturn(Optional.of(outro));
        return outro;
    }

    private ContextoUtilizador contextoComAtosDaSegundaSerie(long... actTypeIds) {
        List<ClaimsUtilizador.AtoAutorizado> atos = Arrays.stream(actTypeIds)
                .mapToObj(id -> new ClaimsUtilizador.AtoAutorizado(id, null, true)).toList();
        ContextoUtilizador comClaim = new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), "ENTIDADE",
                new ClaimsUtilizador.AtosAutorizados(List.of(), atos), "H", "2015482", 1234567891234567L, TipoEntidade.CM,
                entidade.getId());
        when(contextoUtilizadorResolver.resolver()).thenReturn(comClaim);
        return comClaim;
    }

    @Test
    void deveCriarSemTipoAtoComOTipoNuloNaVerificacaoDaCamada3c() {
        Processo processo = service.criar(dinamicaId, new CriarProcessoRequest(10L));

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getTipoAto()).isNull();
        verify(tipoAtoRepository, never()).findById(any());
        verify(autorizacaoService).verificarAutorizacaoPublicacao(eq(contexto),
                org.mockito.ArgumentMatchers.argThat(p -> p.getTipoAto() == null));
        verify(processoRepository).saveAndFlush(processo);
    }

    @Test
    void deveRecusarCriarSemTipoAtoQuandoAParteDaAdesaoNaoCoincide() {
        doThrow(new com.conkord.saigt.exception.ParteDrNaoAutorizadaException("Parte da adesão da entidade não coincide."))
                .when(autorizacaoService).verificarAutorizacaoPublicacao(eq(contexto), any(Processo.class));
        CriarProcessoRequest pedido = new CriarProcessoRequest(10L);

        assertThatThrownBy(() -> service.criar(dinamicaId, pedido))
                .isInstanceOf(com.conkord.saigt.exception.ParteDrNaoAutorizadaException.class);
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarCriarComTipoAtoInexistenteCom404() {
        when(tipoAtoRepository.findById(99L)).thenReturn(Optional.empty());
        CriarProcessoRequest pedido = new CriarProcessoRequest(10L, 99L);

        assertThatThrownBy(() -> service.criar(dinamicaId, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class).hasMessage("Tipo de Ato não encontrado.");
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveGravarNoRascunhoUmTipoAtoDosDisponiveis() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        TipoAto escolhido = tipoAtoDaSegundaSerie(21L, 801L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAto, escolhido));
        contextoComAtosDaSegundaSerie(801L);

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoComTipoAto(21L));

        assertThat(gravado.getTipoAto()).isEqualTo(escolhido);
    }

    @Test
    void deveRecusarCom403UmTipoAtoNaoAutorizadoAEntidadeSemMudarNada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        processo.setSumario("Sumário gravado");
        TipoAto naoAutorizado = tipoAtoDaSegundaSerie(22L, 802L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAto, naoAutorizado));
        contextoComAtosDaSegundaSerie(999L);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComTipoAto(22L);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(TipoAtoNaoAutorizadoException.class,
                        ex -> assertThat(ex.getCodigo()).isEqualTo("TIPO_ATO_NAO_AUTORIZADO"));
        assertThat(processo.getTipoAto()).isNull();
        assertThat(processo.getSumario()).isEqualTo("Sumário gravado");
        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveRecusarCom403UmTipoAtoForaDaSerieDoProcedimento() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        TipoAto daPrimeiraSerie = new TipoAto("ATO-1S", "Decreto", 1);
        ReflectionTestUtils.setField(daPrimeiraSerie, "id", 23L);
        daPrimeiraSerie.setIncmActTypeIdSerie1(803L);
        when(tipoAtoRepository.findById(23L)).thenReturn(Optional.of(daPrimeiraSerie));
        tipoAto.setIncmActTypeIdSerie2(700L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAto));
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComTipoAto(23L);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido)).isInstanceOf(TipoAtoNaoAutorizadoException.class);
        assertThat(processo.getTipoAto()).isNull();
    }

    @Test
    void deveRecusarCom404UmTipoAtoInexistenteNoRascunho() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        when(tipoAtoRepository.findById(98L)).thenReturn(Optional.empty());
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComTipoAto(98L);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class).hasMessage("Tipo de Ato não encontrado.");
        assertThat(processo.getTipoAto()).isNull();
    }

    @Test
    void deveAceitarAUmUtilizadorInternoUmTipoAtoDaSerieSemACamada3c() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        TipoAto daSerie = tipoAtoDaSegundaSerie(24L, 804L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAto, daSerie));

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoComTipoAto(24L));

        assertThat(contexto.atosAutorizados()).isNull();
        assertThat(gravado.getTipoAto()).isEqualTo(daSerie);
    }

    @Test
    void deveManterOTipoAtoGravadoQuandoORascunhoOOmite() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(gravado.getTipoAto()).isEqualTo(tipoAto);
        verify(tipoAtoRepository, never()).findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull();
    }

    @Test
    void deveRecusarAlterarOTipoAtoDepoisDaPrimeiraSubmissaoComCampoBloqueado() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setDataSubmissao(Instant.parse("2026-10-01T10:00:00Z"));
        tipoAtoDaSegundaSerie(25L, 805L);
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComTipoAto(25L);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, ex -> assertThat(ex.getCampos())
                        .contains(new ValidacaoException.CampoErro("tipoAtoId", CampoBloqueadoException.ERRO_CAMPO)));
        assertThat(processo.getTipoAto()).isEqualTo(tipoAto);
    }

    @Test
    void deveRecusarSubmeterSemTipoAtoComDadosIncmEmFaltaNoCampoTipoAtoId() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setTipoAto(null);
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("DADOS_INCM_EM_FALTA");
                    assertThat(ex.getCampos()).containsExactly(
                            new ValidacaoException.CampoErro("tipoAtoId", "Indique o tipo de ato."));
                });
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(processo.getDataSubmissao()).isNull();
    }

    @Test
    void deveListarOsTiposAtoDisponiveisDoProcessoComARegraDoDisponivel() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        TipoAto autorizado = tipoAtoDaSegundaSerie(26L, 806L);
        tipoAto.setIncmActTypeIdSerie2(700L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(tipoAto, autorizado));
        Processo semSerie = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configComSerie(null));

        assertThat(service.tiposAtoDisponiveis(processo, contexto)).containsExactly(tipoAto, autorizado);
        assertThat(service.tiposAtoDisponiveis(processo, contextoComAtosDaSegundaSerie(806L))).containsExactly(autorizado);
        assertThat(service.tiposAtoDisponiveis(semSerie, contexto)).isEmpty();
    }

    // ---------- Série escolhida no processo (restantes servidões, OI-196) ----------

    private InstrumentoProcedimentoConfig configSemSerie() {
        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(null, null);
        InstrumentoProcedimentoConfig semSerie = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento("PUBLICACAO_SRUP", "Publicação da servidão"), 1, null);
        ReflectionTestUtils.setField(semSerie, "id", 12L);
        return semSerie;
    }

    private AtualizarDadosFormularioRequest rascunhoComSerie(String serieDr, Long tipoAtoId) {
        return new AtualizarDadosFormularioRequest(VERSAO, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, tipoAtoId, serieDr);
    }

    /** Um tipo que existe nas duas séries, como o Aviso ou o Despacho do catálogo. */
    private TipoAto tipoAtoDasDuasSeries(long id) {
        TipoAto dasDuas = new TipoAto("DUAS-" + id, "Ato " + id, 1);
        ReflectionTestUtils.setField(dasDuas, "id", id);
        dasDuas.setIncmActTypeIdSerie1(900L + id);
        dasDuas.setIncmActTypeIdSerie2(950L + id);
        when(tipoAtoRepository.findById(id)).thenReturn(Optional.of(dasDuas));
        return dasDuas;
    }

    @Test
    void deveGravarASerieEscolhidaNumProcedimentoSemSerieEListarOsTiposDessaSerie() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setTipoAto(null);
        TipoAto daPrimeira = tipoAtoDasDuasSeries(31L);
        TipoAto soDaSegunda = tipoAtoDaSegundaSerie(32L, 832L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(daPrimeira));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull()).thenReturn(List.of(daPrimeira, soDaSegunda));
        assertThat(service.tiposAtoDisponiveis(processo, contexto)).isEmpty();

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoComSerie("PRIMEIRA", null));

        assertThat(gravado.getSerieDr()).isEqualTo("PRIMEIRA");
        assertThat(gravado.serieDrEfetiva()).isEqualTo("PRIMEIRA");
        assertThat(service.tiposAtoDisponiveis(gravado, contexto)).containsExactly(daPrimeira);

        service.atualizarDadosFormulario(processo.getId(), rascunhoComSerie("SEGUNDA_H", null));

        assertThat(processo.serieDrEfetiva()).isEqualTo("SEGUNDA_H");
        assertThat(service.tiposAtoDisponiveis(processo, contexto)).containsExactly(daPrimeira, soDaSegunda);
    }

    @Test
    void deveManterASerieGravadaQuandoORascunhoAOmite() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setSerieDr("SEGUNDA_C");

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoVazio(VERSAO));

        assertThat(gravado.getSerieDr()).isEqualTo("SEGUNDA_C");
        assertThat(gravado.getTipoAto()).isEqualTo(tipoAto);
    }

    @Test
    void deveRecusarCom400ASerieNumProcedimentoComSerieConfiguradaSemMudarNada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setSumario("Sumário gravado");
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComSerie("SEGUNDA_H", null);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(ex.getCampos()).containsExactly(
                            new ValidacaoException.CampoErro("serieDr", "Campo não aplicável a este procedimento."));
                });
        assertThat(processo.getSerieDr()).isNull();
        assertThat(processo.getSumario()).isEqualTo("Sumário gravado");
        verify(processoRepository, never()).saveAndFlush(processo);
    }

    @Test
    void deveLimparOTipoAtoQueDeixaDeEstarNaSerieNova() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setSerieDr("SEGUNDA_H");
        TipoAto soDaSegunda = tipoAtoDaSegundaSerie(33L, 833L);
        processo.setTipoAto(soDaSegunda);
        TipoAto daPrimeira = tipoAtoDasDuasSeries(34L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(daPrimeira));

        // O ecrã reenvia o tipo gravado com a série nova: não é uma escolha nova, limpa-se em vez de dar 403.
        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoComSerie("PRIMEIRA", 33L));

        assertThat(gravado.getSerieDr()).isEqualTo("PRIMEIRA");
        assertThat(gravado.getTipoAto()).isNull();
    }

    @Test
    void deveManterOTipoAtoQueContinuaNaSerieNova() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setSerieDr("SEGUNDA_H");
        TipoAto dasDuas = tipoAtoDasDuasSeries(35L);
        processo.setTipoAto(dasDuas);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(dasDuas));

        Processo gravado = service.atualizarDadosFormulario(processo.getId(), rascunhoComSerie("PRIMEIRA", null));

        assertThat(gravado.getTipoAto()).isEqualTo(dasDuas);
    }

    @Test
    void deveVerificarContraASerieNovaUmTipoAtoNovoNoMesmoPedido() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setTipoAto(null);
        TipoAto daPrimeira = tipoAtoDasDuasSeries(36L);
        tipoAtoDaSegundaSerie(37L, 837L);
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of(daPrimeira));
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest foraDaSerie = rascunhoComSerie("PRIMEIRA", 37L);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, foraDaSerie))
                .isInstanceOf(TipoAtoNaoAutorizadoException.class);
        assertThat(processo.getSerieDr()).isNull();

        Processo gravado = service.atualizarDadosFormulario(id, rascunhoComSerie("PRIMEIRA", 36L));

        assertThat(gravado.getSerieDr()).isEqualTo("PRIMEIRA");
        assertThat(gravado.getTipoAto()).isEqualTo(daPrimeira);
    }

    @Test
    void deveRecusarMudarASerieDepoisDaPrimeiraSubmissaoComCampoBloqueado() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setSerieDr("SEGUNDA_H");
        processo.setDataSubmissao(Instant.parse("2026-10-01T10:00:00Z"));
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComSerie("SEGUNDA_C", null);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, ex -> assertThat(ex.getCampos())
                        .contains(new ValidacaoException.CampoErro("serieDr", CampoBloqueadoException.ERRO_CAMPO)));
        assertThat(processo.getSerieDr()).isEqualTo("SEGUNDA_H");
        assertThat(CamposFormularioInicial.camposBloqueados(processo)).contains("serieDr");
    }

    @Test
    void deveRecusarComCampoBloqueadoLimparUmTipoAtoBloqueadoAoMudarASerieDesbloqueada() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        processo.setSerieDr("SEGUNDA_H");
        TipoAto soDaSegunda = tipoAtoDaSegundaSerie(38L, 838L);
        processo.setTipoAto(soDaSegunda);
        processo.setDataSubmissao(Instant.parse("2026-10-01T10:00:00Z"));
        processo.setCamposDesbloqueados(List.of("serieDr"));
        when(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()).thenReturn(List.of());
        UUID id = processo.getId();
        AtualizarDadosFormularioRequest pedido = rascunhoComSerie("PRIMEIRA", null);

        assertThatThrownBy(() -> service.atualizarDadosFormulario(id, pedido))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, ex -> assertThat(ex.getCampos())
                        .contains(new ValidacaoException.CampoErro("tipoAtoId", CampoBloqueadoException.ERRO_CAMPO))
                        .extracting(ValidacaoException.CampoErro::path).doesNotContain("serieDr"));
        assertThat(processo.getSerieDr()).isEqualTo("SEGUNDA_H");
        assertThat(processo.getTipoAto()).isEqualTo(soDaSegunda);
    }

    @Test
    void deveRecusarSubmeterSemSerieComDadosIncmEmFaltaNoCampoSerieDrESubmeterDepoisDeEscolhida() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, configSemSerie());
        UUID id = processo.getId();
        SubmeterProcessoRequest pedido = pedidoSubmeterSegundaSerieCompleto();

        assertThatThrownBy(() -> service.submeter(id, pedido))
                .isInstanceOfSatisfying(DadosIncmEmFaltaException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("DADOS_INCM_EM_FALTA");
                    assertThat(ex.getCampos()).containsExactly(
                            new ValidacaoException.CampoErro("serieDr", "Preencha os dados de publicação."));
                });
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);

        processo.setSerieDr("SEGUNDA_H");
        Processo submetido = service.submeter(id, pedido);

        assertThat(submetido.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(submetido.getDrParteId()).isEqualTo(contexto.entidadeDrParteId());
    }

    @Test
    void deveEnviarParaPcmUmProcessoComAPrimeiraSerieEscolhida() {
        Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configSemSerie());
        processo.setSerieDr("PRIMEIRA");
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(true);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        UUID pcmId = destinatarioPcmValido();

        Processo enviado = service.enviarPcm(processo.getId(), new EnviarPcmRequest(VERSAO, pcmId));

        assertThat(enviado.getPcmAtribuidoA().getId()).isEqualTo(pcmId);
    }

    @Test
    void deveDevolverPcmSerieNaoPrimeiraComASegundaSerieEscolhidaOuSemSerie() {
        for (String serieEscolhida : Arrays.asList("SEGUNDA_H", null)) {
            Processo processo = prepararProcesso(EstadoProcesso.VALIDADO_DGT, configSemSerie());
            processo.setSerieDr(serieEscolhida);
            when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(true);
            when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
            UUID id = processo.getId();
            EnviarPcmRequest pedido = new EnviarPcmRequest(VERSAO, destinatarioPcmValido());

            assertThatThrownBy(() -> service.enviarPcm(id, pedido))
                    .isInstanceOf(PcmEstadoInvalidoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "PCM_SERIE_NAO_PRIMEIRA");
            assertThat(processo.getPcmAtribuidoA()).isNull();
        }
    }

    @Test
    void aSerieDoProcedimentoPrevaleceSobreUmaSerieGravadaNoProcesso() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_SUBMISSAO, config);
        processo.setSerieDr("PRIMEIRA");

        assertThat(processo.escolheSerieDr()).isFalse();
        assertThat(processo.serieDrEfetiva()).isEqualTo("SEGUNDA_H");
    }

    @Test
    void deveGravarOsCamposDoResumoAoValidarEOTextoEmBrancoFicaNulo() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        com.conkord.saigt.persistence.core.ListaValores poap =
                new com.conkord.saigt.persistence.core.ListaValores("ALTERACAO_PEOT", "POAP", "POAP", 1);
        when(processoValidacaoService.alteracaoPeotEscolhida(7L)).thenReturn(poap);

        Processo validado = service.validar(processo.getId(), new com.conkord.saigt.dto.instrumento.ValidarProcessoRequest(
                VERSAO, " Nota INCM ", "   ", false, 7L));

        assertThat(validado.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(validado.getNotasIncm()).isEqualTo("Nota INCM");
        assertThat(validado.getNotasDgt()).isNull();
        assertThat(validado.getRegUsoSolo()).isFalse();
        assertThat(validado.getAlteracaoPeot()).isSameAs(poap);
    }

    @Test
    void naoDeveGravarOsCamposDoResumoNemValidarQuandoAAlteracaoPeotNaoEAtiva() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.alteracaoPeotEscolhida(9L)).thenThrow(new ValidacaoException("Valor de lista inválido.",
                List.of(new ValidacaoException.CampoErro("alteracaoPeotId", "Não existe ou não é um valor ativo de ALTERACAO_PEOT."))));
        UUID id = processo.getId();
        var pedido = new com.conkord.saigt.dto.instrumento.ValidarProcessoRequest(VERSAO, "x", "y", true, 9L);

        assertThatThrownBy(() -> service.validar(id, pedido)).isInstanceOf(ValidacaoException.class);

        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(processo.getNotasIncm()).isNull();
        assertThat(processo.getRegUsoSolo()).isNull();
        verify(processoRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveVerificarAAlteracaoPeotQuandoHaItensPorRever() {
        Processo processo = prepararProcesso(EstadoProcesso.EM_VALIDACAO_DGT, config);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoMaster());
        when(processoValidacaoService.todosRevistos(processo)).thenReturn(false);
        UUID id = processo.getId();
        var pedido = new com.conkord.saigt.dto.instrumento.ValidarProcessoRequest(VERSAO, "x", null, null, 9L);

        assertThatThrownBy(() -> service.validar(id, pedido)).isInstanceOf(ItensPorReverException.class);

        verify(processoValidacaoService, never()).alteracaoPeotEscolhida(any());
        assertThat(processo.getNotasIncm()).isNull();
    }
}
