package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.DesbloquearCampoRequest;
import com.conkord.saigt.dto.instrumento.DevolverDgtRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.ReatribuirAutorRequest;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.dto.instrumento.VersaoProcessoRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.documento.BloqueioDocumentosCorrecao;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.conkord.saigt.service.idus.IdusProcessoService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.notificacao.PedidoApoioService;
import com.conkord.saigt.service.regras.MotorRegrasService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Usa o {@link AutorizacaoService} real, sobre repositórios simulados: o que se testa é que a lista e os
 * endpoints partilham as mesmas verificações.
 */
class ProcessoAcoesDisponiveisTest {

    private static final String NIF = "501234567";
    private static final long VERSAO = 0L;

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final PermissaoAcaoRepository permissaoAcaoRepository = mock(PermissaoAcaoRepository.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final ProcessoValidacaoService processoValidacaoService = mock(ProcessoValidacaoService.class);
    private final RejeicaoProcessoService rejeicaoProcessoService = mock(RejeicaoProcessoService.class);

    private final AutorizacaoService autorizacaoService = new AutorizacaoService(permissaoAcaoRepository,
            mock(RegraRepository.class), mock(InstrumentoProcedimentoConfigRepository.class), processoRepository,
            mock(MotorRegrasService.class));

    private final EnvioIncmService envioIncmService = mock(EnvioIncmService.class);

    private final ProcessoService service = new ProcessoService(mock(DinamicaRepository.class),
            mock(InstrumentoProcedimentoConfigRepository.class), mock(TipoAtoRepository.class),
            processoRepository, utilizadorRepository,
            autorizacaoService, contextoUtilizadorResolver,
            mock(AuditoriaService.class), new ObjectMapper(), mock(DinamicaService.class),
            mock(UtilizadorEntidadeRepository.class), envioIncmService,
            mock(CompletudeDocumentalService.class), processoValidacaoService, new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper()),
            new CatalogoFormularios(), mock(CaducidadeDinamicaService.class), mock(IdusProcessoService.class),
            rejeicaoProcessoService, mock(MetadadosMotuService.class), mock(EntidadeRepository.class),
            mock(BloqueioDocumentosCorrecao.class), mock(RetencaoDocumentosService.class), mock(PedidoApoioService.class),
            mock(ApplicationEventPublisher.class), mock(com.conkord.saigt.service.notificacao.NotificacaoOrigemService.class),
            mock(com.conkord.saigt.service.documento.CopiaAnexosNotificacaoService.class),
            mock(com.conkord.saigt.service.documento.RetencaoRegistoExternoService.class),
            mock(DinamicaAssociadaService.class),
            new DestinoProcessoService(new CatalogoFormularios(),
                    mock(com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository.class)),
            mock(AtosReferiveis.class), mock(RegrasRedesDefesaSgifr.class),
                new RegrasTerritorioSgifr(mock(ListaValoresRepository.class), new ObjectMapper()), mock(RegrasFormularioRen.class));

    private Instrumento instrumento;
    private InstrumentoDinamicaConfig dinamicaConfig;
    private Dinamica dinamica;
    private Utilizador autor;

    private ContextoUtilizador contextoAutor;
    private ContextoUtilizador contextoOutroDaEntidade;
    private Entidade entidade;
    private ContextoUtilizador contextoTecnico;
    private ContextoUtilizador contextoMaster;
    private ContextoUtilizador contextoVisitante;
    private ContextoUtilizador contextoPcm;

    @BeforeEach
    void setUp() {
        when(processoValidacaoService.todosRevistos(any())).thenReturn(true);
        when(processoValidacaoService.todosDocumentosValidados(any())).thenReturn(true);
        when(rejeicaoProcessoService.registarRecusaDgt(any(), any(), any(), any()))
                .thenReturn(ProcessoRejeicao.builder().sequencia(1).build());
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        entidade = EntidadeTeste.nova(NIF, "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", pdm);

        autor = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        ReflectionTestUtils.setField(autor, "id", UUID.randomUUID());

        dinamicaConfig = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("ALTERACAO", "Alteração"));
        dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());

        contextoAutor = new ContextoUtilizador(autor.getId(), "INCM", null, NIF, "ENTIDADE", null, null, null, null, TipoEntidade.CM, 1L);
        contextoOutroDaEntidade = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, NIF, "ENTIDADE", null, null, null, null, TipoEntidade.CM, 1L);
        contextoTecnico = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.TECNICO, null, "DGT", null, null, null, null, null, null);
        contextoMaster = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null);
        contextoVisitante = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.VISITANTE, null, null, null, null, null, null, null, null);
        contextoPcm = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, null, "PCM", null, null, null, null, TipoEntidade.PCM, 3L);

        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), any())).thenReturn(true);
        when(utilizadorRepository.findById(any())).thenReturn(Optional.of(autor));
        when(processoRepository.saveAndFlush(any(Processo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Processo processo(EstadoProcesso estado, String serieDr) {
        return processo(estado, serieDr, "DECISAO_INICIO");
    }

    private Processo processo(EstadoProcesso estado, String serieDr, String tipoProcedimento) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento(tipoProcedimento, tipoProcedimento), 1, serieDr);
        ReflectionTestUtils.setField(config, "id", 10L);
        Processo processo = new Processo(dinamica, config, autor, entidade);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(estado);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }

    private Processo processo(EstadoProcesso estado) {
        return processo(estado, "SEGUNDA_H");
    }

    @Test
    void autorEmSubmissaoPodeEditarESubmeter() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);

        assertThat(service.editavel(processo, contextoAutor)).isTrue();
        assertThat(service.acoesDisponiveis(processo, contextoAutor)).containsExactly("submeter");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"REJEITADO_DGT", "ANULADO_INCM"})
    void autorEmRejeitadoDgtOuAnuladoIncmSoPodeCorrigirENaoEdita(EstadoProcesso estado) {
        Processo processo = processo(estado);

        assertThat(service.editavel(processo, contextoAutor)).isFalse();
        assertThat(service.acoesDisponiveis(processo, contextoAutor)).containsExactly("corrigir");
        assertThat(service.acoesDisponiveis(processo, contextoOutroDaEntidade)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).containsExactly("reatribuir-autor");
    }

    @Test
    void soOAutorDeUmProcessoEmCorrecaoVeDesbloquearCampo() {
        Processo emCorrecao = processo(EstadoProcesso.EM_SUBMISSAO);
        emCorrecao.setDataSubmissao(Instant.now());

        assertThat(service.acoesDisponiveis(emCorrecao, contextoAutor)).containsExactly("submeter", "desbloquear-campo");
        for (ContextoUtilizador contexto : List.of(contextoOutroDaEntidade, contextoTecnico, contextoMaster, contextoVisitante,
                contextoPcm)) {
            assertThat(service.acoesDisponiveis(emCorrecao, contexto)).doesNotContain("desbloquear-campo");
        }
        assertThat(service.acoesDisponiveis(processo(EstadoProcesso.EM_SUBMISSAO), contextoAutor))
                .doesNotContain("desbloquear-campo");
        for (EstadoProcesso estado : EstadoProcesso.values()) {
            if (estado != EstadoProcesso.EM_SUBMISSAO) {
                Processo submetido = processo(estado);
                submetido.setDataSubmissao(Instant.now());
                assertThat(service.acoesDisponiveis(submetido, contextoAutor)).as(estado.name()).doesNotContain("desbloquear-campo");
            }
        }
    }

    @Test
    void desbloquearCampoListadoNaoERecusadoPorEstadoNemPermissao() {
        Processo emCorrecao = processo(EstadoProcesso.EM_SUBMISSAO);
        emCorrecao.setDataSubmissao(Instant.now());
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoAutor);

        executar("desbloquear-campo", emCorrecao, EstadoProcesso.EM_SUBMISSAO, contextoAutor);

        assertThat(emCorrecao.getCamposDesbloqueados()).containsExactly("sumario");
        assertThat(emCorrecao.getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
    }

    @Test
    void autorSemPermissaoNoProcedimentoNaoVeCorrigir() {
        Processo processo = processo(EstadoProcesso.REJEITADO_DGT);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), any())).thenReturn(false);

        assertThat(service.acoesDisponiveis(processo, contextoAutor)).isEmpty();
    }

    @Test
    void outroUtilizadorDaEntidadeNaoEditaNemTemAcoes() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);

        assertThat(service.editavel(processo, contextoOutroDaEntidade)).isFalse();
        assertThat(service.acoesDisponiveis(processo, contextoOutroDaEntidade)).isEmpty();
    }

    @Test
    void autorSemPermissaoNoProcedimentoNaoPodeSubmeterMasContinuaEditavel() {
        Processo processo = processo(EstadoProcesso.EM_SUBMISSAO);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), any())).thenReturn(false);

        assertThat(service.editavel(processo, contextoAutor)).isTrue();
        assertThat(service.acoesDisponiveis(processo, contextoAutor)).isEmpty();
    }

    @Test
    void autorNaoEditaForaDosEstadosEditaveis() {
        for (EstadoProcesso estado : EstadoProcesso.values()) {
            if (!EstadoProcesso.EDITAVEIS.contains(estado)) {
                assertThat(service.editavel(processo(estado), contextoAutor)).as(estado.name()).isFalse();
            }
        }
    }

    @Test
    void enviadoDgtDaResgatarAoAutorECapturarAoTecnico() {
        Processo processo = processo(EstadoProcesso.ENVIADO_DGT);

        assertThat(service.acoesDisponiveis(processo, contextoAutor)).containsExactly("resgatar");
        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("capturar")
                .doesNotContain("resgatar");
    }

    @Test
    void emValidacaoDgtDaValidarERejeitarAoTecnicoESoOMasterAnula() {
        Processo processo = processo(EstadoProcesso.EM_VALIDACAO_DGT);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("validar", "rejeitar");
        assertThat(service.acoesDisponiveis(processo, contextoMaster))
                .containsExactly("validar", "rejeitar", "anular");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "EM_VALIDACAO_DGT", "VALIDADO_DGT"})
    void soOMasterVeAnularNosEstadosAnulaveis(EstadoProcesso estado) {
        Processo processo = processo(estado, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoMaster)).contains("anular");
        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).doesNotContain("anular");
        assertThat(service.acoesDisponiveis(processo, contextoAutor)).doesNotContain("anular");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "EM_VALIDACAO_DGT", "VALIDADO_DGT"},
            mode = EnumSource.Mode.EXCLUDE)
    void ninguemVeAnularDepoisDaIncmNemEmEstadosNaoAnulaveis(EstadoProcesso estado) {
        Processo processo = processo(estado, "PRIMEIRA");

        for (ContextoUtilizador contexto : List.of(contextoMaster, contextoTecnico, contextoAutor, contextoPcm)) {
            assertThat(service.acoesDisponiveis(processo, contexto)).as(estado.name()).doesNotContain("anular");
        }
    }

    @Test
    void masterEmValidadoDgtVeEnviarIncmEnviarPcmEAnular() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoMaster))
                .containsExactly("enviar-incm", "enviar-pcm", "anular");
    }

    @Test
    void validadoDgtDaEnviarIncmEEnviarPcmAoAtorDgtNaPrimeiraSerie() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-incm", "enviar-pcm");
    }

    @Test
    void soParaDepositoEmValidadoDgtNaoTemEnviarIncmNemEnviarPcm() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");
        processo.setDestino(com.conkord.saigt.persistence.operacional.DestinoProcesso.SO_DEPOSITO);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).containsExactly("anular");
    }

    /** A Ratificação passa pela PCM antes da publicação: sem a devolução da PCM nesta validação, só «Enviar PCM». */
    @Test
    void ratificacaoValidadaTemEnviarPcmESemEnviarIncm() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", "RATIFICACAO");

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-pcm");
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).containsExactly("enviar-pcm", "anular");
    }

    @Test
    void ratificacaoValidadaRecusaEnviarIncmComEstadoInvalidoSemChamarAIncm() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", "RATIFICACAO");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico);
        UUID id = processo.getId();
        VersaoProcessoRequest pedido = new VersaoProcessoRequest(VERSAO);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enviarIncm(id, pedido))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "ESTADO_INVALIDO")
                .hasMessage(ProcessoService.MSG_RATIFICACAO_SEM_PCM);
        org.mockito.Mockito.verify(envioIncmService, org.mockito.Mockito.never()).enviar(any(), any());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    @Test
    void outrosProcessosDaPrimeiraSerieContinuamComEnviarIncm() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", "PUBLICACAO_SUSPENSAO_GOVERNO");

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-incm", "enviar-pcm");
    }

    /** Os processos do Governo seguem a regra da 1.ª série: «Enviar PCM» disponível, o passo da PCM facultativo (TR-114). */
    @ParameterizedTest
    @ValueSource(strings = {"PUBLICACAO_SUSPENSAO_GOVERNO", "PUBLICACAO_MP_GOVERNO", "PRORROGACAO_SUSPENSAO_GOVERNO",
            "PRORROGACAO_MP_GOVERNO"})
    void osProcessosDoGovernoValidadosTemEnviarPcmComoOsRestantesDaPrimeiraSerie(String tipoProcedimento) {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", tipoProcedimento);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).contains("enviar-pcm").doesNotContain("publicar");
    }

    @Test
    void enviarPcmSoApareceNaPrimeiraSerie() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "SEGUNDA_H");

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-incm");
    }

    /** Um documento por validar (o que a PCM acrescenta antes de devolver) tira as duas ações de envio (FR-059). */
    @Test
    void enviarPcmEEnviarIncmNaoAparecemComDocumentosPorValidar() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");
        when(processoValidacaoService.todosDocumentosValidados(processo)).thenReturn(false);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).containsExactly("anular");
    }

    @Test
    void depoisDaDevolucaoEComOsDocumentosValidadosADgtTemAsDuasAcoesDeEnvio() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");
        processo.setPcmDevolvidoEm(java.time.Instant.now());

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-incm", "enviar-pcm");
    }

    /** Com a PCM o envio à INCM fica bloqueado (TR-114): a DGT não tem nenhuma das duas ações de envio. */
    @Test
    void enviarPcmEEnviarIncmNaoAparecemQuandoOProcessoJaEstaComAPcm() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");
        Utilizador pcm = new Utilizador("Técnico PCM", "pcm@pcm.gov.pt", "INCM");
        ReflectionTestUtils.setField(pcm, "id", UUID.randomUUID());
        processo.setPcmAtribuidoA(pcm);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).containsExactly("anular");
    }

    private Processo atribuidoAPcmDaSessao(String tipoProcedimento) {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", tipoProcedimento);
        Utilizador pcm = new Utilizador("Técnico PCM", "pcm@pcm.gov.pt", "INCM");
        ReflectionTestUtils.setField(pcm, "id", contextoPcm.utilizadorId());
        processo.setPcmAtribuidoA(pcm);
        return processo;
    }

    @Test
    void soAPcmAQuemOProcessoEstaAtribuidoVeDevolverDgt() {
        Processo processo = atribuidoAPcmDaSessao("DECISAO_INICIO");
        ContextoUtilizador outraPcm = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, null, "PCM", null, null, null,
                null, TipoEntidade.PCM, 3L);

        assertThat(service.acoesDisponiveis(processo, contextoPcm)).containsExactly("devolver-dgt");
        assertThat(service.acoesDisponiveis(processo, outraPcm)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).doesNotContain("devolver-dgt");
        assertThat(service.acoesDisponiveis(processo, contextoMaster)).doesNotContain("devolver-dgt");
    }

    @Test
    void semALinhaPcmNaConfiguracaoAPcmNaoVeDevolverDgt() {
        Processo processo = atribuidoAPcmDaSessao("DECISAO_INICIO");
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.PCM,
                processo.getInstrumentoProcedimentoConfig())).thenReturn(false);

        assertThat(service.acoesDisponiveis(processo, contextoPcm)).isEmpty();
    }

    @Test
    void devolverDgtListadoNaoERecusadoPorEstadoNemPermissao() {
        Processo processo = atribuidoAPcmDaSessao("DECISAO_INICIO");
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoPcm);
        assertThat(service.acoesDisponiveis(processo, contextoPcm)).contains("devolver-dgt");

        Processo devolvido = service.devolverDgt(processo.getId(), new DevolverDgtRequest(VERSAO, null));

        assertThat(devolvido.getPcmAtribuidoA()).isNull();
        assertThat(devolvido.getPcmDevolvidoEm()).isNotNull();
        assertThat(devolvido.getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
    }

    /** Depois da devolução pela PCM nesta validação, a Ratificação já pode ser publicada. */
    @Test
    void ratificacaoDevolvidaPelaPcmVoltaATerEnviarIncmEOEnvioSegue() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA", "RATIFICACAO");
        processo.setPcmDevolvidoEm(Instant.now());
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoTecnico);

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).containsExactly("enviar-incm", "enviar-pcm");
        service.enviarIncm(processo.getId(), new VersaoProcessoRequest(VERSAO));
        org.mockito.Mockito.verify(envioIncmService).enviar(processo.getId(), contextoTecnico.utilizadorId());
    }

    @Test
    void ratificacaoDevolvidaEEnviadaDeNovoAPcmFicaSemEnviarIncm() {
        Processo processo = atribuidoAPcmDaSessao("RATIFICACAO");
        processo.setPcmDevolvidoEm(Instant.now());

        assertThat(service.acoesDisponiveis(processo, contextoTecnico)).isEmpty();
        assertThat(service.acoesDisponiveis(processo, contextoPcm)).containsExactly("devolver-dgt");
    }

    @Test
    void pcmNaoTemAcoesNoProcessoQueNaoLheFoiAtribuidoENemEnviaIncmNoQueLheFoi() {
        Processo processo = processo(EstadoProcesso.VALIDADO_DGT, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoPcm)).isEmpty();

        Utilizador pcm = new Utilizador("Técnico PCM", "pcm@pcm.gov.pt", "INCM");
        ReflectionTestUtils.setField(pcm, "id", contextoPcm.utilizadorId());
        processo.setPcmAtribuidoA(pcm);

        assertThat(service.acoesDisponiveis(processo, contextoPcm)).doesNotContain("enviar-incm", "enviar-pcm");
    }

    @Test
    void visitanteNuncaTemAcoesNemEdita() {
        for (EstadoProcesso estado : EstadoProcesso.values()) {
            Processo processo = processo(estado, "PRIMEIRA");

            assertThat(service.acoesDisponiveis(processo, contextoVisitante)).as(estado.name()).isEmpty();
            assertThat(service.editavel(processo, contextoVisitante)).as(estado.name()).isFalse();
        }
    }

    @Test
    void emEstadosTerminaisNinguemTemAcoes() {
        for (EstadoProcesso estado : EstadoProcesso.TERMINAIS) {
            Processo processo = processo(estado);

            assertThat(service.acoesDisponiveis(processo, contextoMaster)).as(estado.name()).isEmpty();
            assertThat(service.acoesDisponiveis(processo, contextoTecnico)).as(estado.name()).isEmpty();
            assertThat(service.acoesDisponiveis(processo, contextoAutor)).as(estado.name()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "REJEITADO_DGT", "ANULADO_INCM"})
    void soOMasterVeReatribuirAutorNosEstadosEmQueOOwnerAge(EstadoProcesso estado) {
        Processo processo = processo(estado, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoMaster)).contains("reatribuir-autor");
        for (ContextoUtilizador contexto : List.of(contextoTecnico, contextoAutor, contextoVisitante, contextoPcm)) {
            assertThat(service.acoesDisponiveis(processo, contexto)).doesNotContain("reatribuir-autor");
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, names = {"EM_SUBMISSAO", "ENVIADO_DGT", "REJEITADO_DGT", "ANULADO_INCM"},
            mode = EnumSource.Mode.EXCLUDE)
    void ninguemVeReatribuirAutorNosEstadosEmQueOOwnerNaoAge(EstadoProcesso estado) {
        Processo processo = processo(estado, "PRIMEIRA");

        assertThat(service.acoesDisponiveis(processo, contextoMaster)).doesNotContain("reatribuir-autor");
    }

    @Test
    void outraEntidadeNaoTemAcoes() {
        ContextoUtilizador outraEntidade = new ContextoUtilizador(autor.getId(), "INCM", null, "999999990", "ENTIDADE", null, null, null, null, TipoEntidade.CM, 2L);

        assertThat(service.acoesDisponiveis(processo(EstadoProcesso.EM_SUBMISSAO), outraEntidade)).isEmpty();
    }

    @Test
    void qualquerAcaoListadaNaoERecusadaPorEstadoNemPermissao() {
        List<ContextoUtilizador> contextos = List.of(contextoAutor, contextoOutroDaEntidade, contextoTecnico, contextoMaster,
                contextoVisitante, contextoPcm);
        int acoesVerificadas = 0;

        for (String serieDr : List.of("PRIMEIRA", "SEGUNDA_H")) {
            for (EstadoProcesso estado : EstadoProcesso.values()) {
                for (ContextoUtilizador contexto : contextos) {
                    Processo processo = processo(estado, serieDr);
                    when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
                    List<String> acoes = new ArrayList<>(service.acoesDisponiveis(processo, contexto));

                    for (String acao : acoes) {
                        processo.setEstado(estado);
                        executar(acao, processo, estado, contexto);
                        acoesVerificadas++;
                    }
                }
            }
        }

        assertThat(acoesVerificadas).isPositive();
    }

    private void executar(String acao, Processo processo, EstadoProcesso estado, ContextoUtilizador contexto) {
        UUID id = processo.getId();
        VersaoProcessoRequest versao = new VersaoProcessoRequest(VERSAO);
        try {
            switch (acao) {
                case "submeter" -> service.submeter(id, PedidosSubmissao.confirmado(VERSAO, "Resumo", LocalDate.now(),
                        "Norma", "TEXTO_INTEGRAL", false, null));
                case "corrigir" -> service.corrigir(id, versao);
                case "desbloquear-campo" -> service.desbloquearCampo(id, new DesbloquearCampoRequest(VERSAO, "sumario", "Motivo."));
                case "resgatar" -> service.resgatar(id, versao);
                case "capturar" -> service.capturar(id, versao);
                case "validar" -> service.validar(id, versao);
                case "rejeitar" -> service.rejeitar(id, new RejeitarProcessoRequest(VERSAO, null));
                case "anular" -> service.anular(id, new AnularProcessoRequest(VERSAO, "Motivo."));
                case "enviar-incm" -> service.enviarIncm(id, versao);
                case "enviar-pcm" -> service.enviarPcm(id, new EnviarPcmRequest(VERSAO, UUID.randomUUID()));
                case "devolver-dgt" -> service.devolverDgt(id, new DevolverDgtRequest(VERSAO, null));
                case "reatribuir-autor" -> service.reatribuirAutor(id,
                        new ReatribuirAutorRequest(VERSAO, outroUtilizador().getId(), null));
                default -> fail("Ação desconhecida: " + acao);
            }
        } catch (EstadoInvalidoException | AcaoNaoAutorizadaException | RecursoNaoEncontradoException recusa) {
            fail("'%s' está listada para %s em %s mas o endpoint recusa-a: %s",
                    acao, contexto.perfilInterno() != null ? contexto.perfilInterno() : contexto.ator(), estado, recusa.getMessage());
        } catch (RuntimeException outra) {
            // Validação de conteúdo (campos em falta, motivo inexistente, novo autor inválido) não é estado nem permissão.
        }
    }

    /** O autor atual daria 409 por ser o mesmo, o que não é recusa por estado nem permissão. */
    private Utilizador outroUtilizador() {
        Utilizador outro = new Utilizador("Outro Autor", "outro.autor@cm-lisboa.pt", "INCM");
        ReflectionTestUtils.setField(outro, "id", UUID.randomUUID());
        when(utilizadorRepository.findById(outro.getId())).thenReturn(Optional.of(outro));
        return outro;
    }
}
