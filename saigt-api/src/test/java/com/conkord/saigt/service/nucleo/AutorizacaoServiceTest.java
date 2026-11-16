package com.conkord.saigt.service.nucleo;

import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.DinamicaNaoElegivelException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ParteDrNaoAutorizadaException;
import com.conkord.saigt.exception.PcmAtorNaoAutorizadoException;
import com.conkord.saigt.exception.ProcessoEmCursoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.SequenciamentoNaoCumpridoException;
import com.conkord.saigt.exception.TipoAtoNaoAutorizadoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.regras.MotorRegrasService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutorizacaoServiceTest {

    private final PermissaoAcaoRepository permissaoAcaoRepository = mock(PermissaoAcaoRepository.class);
    private final RegraRepository regraRepository = mock(RegraRepository.class);
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository =
            mock(InstrumentoProcedimentoConfigRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final MotorRegrasService motorRegrasService = mock(MotorRegrasService.class);
    private final AutorizacaoService autorizacaoService = new AutorizacaoService(permissaoAcaoRepository,
            regraRepository, instrumentoProcedimentoConfigRepository, processoRepository, motorRegrasService);

    private Entidade entidadeA;
    private Entidade entidadeB;
    private Entidade entidadeC;
    private Entidade entidadeComOMesmoNipcDeA;
    private Instrumento instrumento;
    private Dinamica dinamicaDaEntidadeA;
    private Dinamica dinamicaDaEntidadeB;
    private Processo processoDaEntidadeA;
    private Processo processoDaEntidadeB;
    private InstrumentoProcedimentoConfig procedimentoConfig;
    private Utilizador utilizadorA;
    private Utilizador utilizadorB;

    @BeforeEach
    void setUp() {
        entidadeA = EntidadeTeste.nova("501111111", "Câmara Municipal A");
        ReflectionTestUtils.setField(entidadeA, "id", 1L);
        entidadeB = EntidadeTeste.nova("501111112", "Câmara Municipal B");
        ReflectionTestUtils.setField(entidadeB, "id", 2L);

        entidadeC = EntidadeTeste.nova("999999999", "Entidade C");
        ReflectionTestUtils.setField(entidadeC, "id", 3L);
        entidadeComOMesmoNipcDeA = EntidadeTeste.nova("501111111", "Câmara Municipal A (outro gabinete)");
        ReflectionTestUtils.setField(entidadeComOMesmoNipcDeA, "id", 4L);

        instrumento = new Instrumento();

        utilizadorA = new Utilizador("Utilizador A", "a@municipio.pt");
        ReflectionTestUtils.setField(utilizadorA, "id", UUID.randomUUID());
        utilizadorB = new Utilizador("Utilizador B", "b@municipio.pt");
        ReflectionTestUtils.setField(utilizadorB, "id", UUID.randomUUID());

        dinamicaDaEntidadeA = new Dinamica(instrumento, null, "Alteração do PDM", utilizadorA);
        dinamicaDaEntidadeB = new Dinamica(instrumento, null, "Alteração do PDM", utilizadorA);

        procedimentoConfig = new InstrumentoProcedimentoConfig();
        ReflectionTestUtils.setField(procedimentoConfig, "id", 10L);

        processoDaEntidadeA = new Processo(dinamicaDaEntidadeA, procedimentoConfig, utilizadorA, entidadeA);
        processoDaEntidadeB = new Processo(dinamicaDaEntidadeB, procedimentoConfig, utilizadorA, entidadeB);
    }

    private InstrumentoDinamicaConfig dinamicaConfigGenerico() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        return new InstrumentoDinamicaConfig(tipoInstrumento, null);
    }

    private InstrumentoProcedimentoConfig procedimentoConfigComOrdem(InstrumentoDinamicaConfig dinamicaConfig, int ordem,
                                                                       boolean obrigatorio) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig, null, ordem, "SEGUNDA_H");
        config.setObrigatorio(obrigatorio);
        return config;
    }

    /** O Procedimento inicial da configuração (ordem 1) e o que o tipo de entidade tem nele. */
    private InstrumentoProcedimentoConfig comProcedimentoInicial(InstrumentoDinamicaConfig dinamicaConfig, TipoEntidade tipo,
                                                                  boolean tipoTemLinha) {
        InstrumentoProcedimentoConfig inicial = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(dinamicaConfig))
                .thenReturn(Optional.of(inicial));
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(tipo, inicial))
                .thenReturn(tipoTemLinha);
        return inicial;
    }

    private ContextoUtilizador contextoComPermissao(Entidade entidade, InstrumentoProcedimentoConfig config) {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidade, "ENTIDADE");
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, config))
                .thenReturn(true);
        return contexto;
    }

    private InstrumentoProcedimentoConfig procedimentoConfigComSerieDr(String serieDr) {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("COD", "Nome", "PLANO");
        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        return new InstrumentoProcedimentoConfig(dinamicaConfig, null, 1, serieDr);
    }

    private ContextoUtilizador contextoExternoComAtos(Entidade entidade, ClaimsUtilizador.AtosAutorizados atos, String drParteLetra) {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, entidade.getNipc(), "ENTIDADE", atos, drParteLetra, null, null,
                TipoEntidade.CM, entidade.getId());
    }

    private TipoAto tipoAtoComIds(Long idSerie1, Long idSerie2) {
        TipoAto tipoAto = new TipoAto("COD-ATO", "Nome do Ato", 1);
        tipoAto.setIncmActTypeIdSerie1(idSerie1);
        tipoAto.setIncmActTypeIdSerie2(idSerie2);
        return tipoAto;
    }

    private ContextoUtilizador contextoExterno(UUID utilizadorId, Entidade entidade, String ator) {
        TipoEntidade tipoEntidade = switch (ator) {
            case "ENTIDADE" -> TipoEntidade.CM;
            case "PCM" -> TipoEntidade.PCM;
            default -> null;
        };
        return contextoExterno(utilizadorId, entidade, ator, tipoEntidade);
    }

    private ContextoUtilizador contextoExterno(UUID utilizadorId, Entidade entidade, String ator, TipoEntidade tipoEntidade) {
        return new ContextoUtilizador(utilizadorId, "INCM", null, entidade.getNipc(), ator, null, null, null, null, tipoEntidade,
                entidade.getId());
    }

    private ContextoUtilizador contextoInterno(UUID utilizadorId, PerfilInterno perfilInterno, String ator) {
        return new ContextoUtilizador(utilizadorId, "AD_DGT", perfilInterno, null, ator, null, null, null, null, null, null);
    }

    @Test
    void deveDevolver404QuandoEntidadePedeProcessoDeOutraEntidade() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePassarCamada2QuandoMasterPedeProcessoDeQualquerEntidade() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .doesNotThrowAnyException();
    }

    @Test
    void devePassarCamada2QuandoTecnicoPedeProcessoDeQualquerEntidade() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .doesNotThrowAnyException();
    }

    @Test
    void devePassarCamada2QuandoVisitantePedeProcessoDeQualquerEntidade() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver404QuandoInternoSemPerfilPedeProcesso() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePermitirAgirComoDgtAoMasterEAoTecnico() {
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgirComoDgt(master, processoDaEntidadeA))
                .doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgirComoDgt(tecnico, processoDaEntidadeA))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403AoAgirComoDgtQuandoVisitante() {
        ContextoUtilizador visitante = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgirComoDgt(visitante, processoDaEntidadeA))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveExigirPerfilMasterMesmoComAtorDgt() {
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");
        ContextoUtilizador visitante = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);
        ContextoUtilizador externo = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatCode(() -> autorizacaoService.verificarPerfilMaster(master)).doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacaoService.verificarPerfilMaster(tecnico)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> autorizacaoService.verificarPerfilMaster(visitante)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> autorizacaoService.verificarPerfilMaster(externo)).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveDevolver404AntesDe403AoAgirComoMasterSobreProcessoInvisivel() {
        ContextoUtilizador externo = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgirComoMaster(externo, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolver403AoAgirComoMasterQuandoTecnico() {
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgirComoMaster(tecnico, processoDaEntidadeA))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveDevolver404QuandoUtilizadorPcmPedeProcessoDeOutraEntidadeNaoAtribuidoANele() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeC, "PCM");
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(contexto.temVisibilidadeTransversal()).isFalse();
    }

    @Test
    void deveDarAoUtilizadorPcmOProcessoValidadoDgtQueLheFoiAtribuido() {
        ContextoUtilizador contexto = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB)).doesNotThrowAnyException();
    }

    @Test
    void deveDevolver404AoUtilizadorPcmQuandoOProcessoAtribuidoJaNaoEstaEmValidadoDgt() {
        ContextoUtilizador contexto = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");
        processoDaEntidadeB.setEstado(EstadoProcesso.ENVIADO_INCM);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolver404AoOutroUtilizadorPcmQueNaoOTemAtribuido() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeC, "PCM");
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);

        assertThat(autorizacaoService.processoVisivel(contexto, processoDaEntidadeB)).isFalse();
    }

    @Test
    void deveDarLeituraDoProcessoDeUmProcedimentoVisivelNaDinamicaAQuemTemProcessoNaMesmaDinamica() {
        procedimentoConfig.setVisivelNaDinamica(true);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(processoRepository.existsByDinamicaAndEntidade_Id(dinamicaDaEntidadeB, entidadeA.getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB)).doesNotThrowAnyException();
    }

    @Test
    void naoDeveDeixarAgirNoProcessoQueSoSeVePelaDinamica() {
        procedimentoConfig.setVisivelNaDinamica(true);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, procedimentoConfig);
        when(processoRepository.existsByDinamicaAndEntidade_Id(dinamicaDaEntidadeB, entidadeA.getId())).thenReturn(true);

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeB))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveDevolver404QuandoAEntidadeNaoTemProcessosNaDinamicaMesmoComProcedimentoVisivel() {
        procedimentoConfig.setVisivelNaDinamica(true);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(processoRepository.existsByDinamicaAndEntidade_Id(dinamicaDaEntidadeB, entidadeA.getId())).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolver404QuandoOProcedimentoNaoEVisivelNaDinamicaMesmoComProcessosDaEntidadeNela() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(processoRepository.existsByDinamicaAndEntidade_Id(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDarSessaoDeEntidadeDeTipoDgtAVisibilidadeEAccaoEmQualquerProcessoSemRecorrerAAutoria() {
        ContextoUtilizador contexto = contextoExterno(utilizadorB.getId(), entidadeC, "ENTIDADE", TipoEntidade.DGT);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.DGT, procedimentoConfig))
                .thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarAutoria(contexto, processoDaEntidadeB)).doesNotThrowAnyException();
    }

    @Test
    void naoDeveDarVisibilidadeTransversalAUmaEntidadeQueSoTemOMesmoNipcQueADgt() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeComOMesmoNipcDeA, "ENTIDADE", TipoEntidade.CM);

        assertThat(autorizacaoService.processoVisivel(contexto, processoDaEntidadeA)).isFalse();
    }

    @Test
    void soODgtEnviaAIncmEAPcmRecebe403MesmoNoProcessoQueLheFoiAtribuido() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeEnviarIncm(pcm, processoDaEntidadeB))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThat(autorizacaoService.podeEnviarIncm(pcm, processoDaEntidadeB)).isFalse();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeEnviarIncm(master, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeEnviarIncm(tecnico, processoDaEntidadeB)).doesNotThrowAnyException();
    }

    // «Devolver à DGT»: só o ator PCM, no processo atribuído ao utilizador da sessão, com a linha `PCM` na configuração.

    @Test
    void soAPcmDevolveADgtEOsOutrosAtoresRecebem403PcmAtorNaoAutorizado() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(processoDaEntidadeB.getInstrumentoProcedimentoConfig());

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(pcm, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThat(autorizacaoService.podeDevolverDgt(pcm, processoDaEntidadeB)).isTrue();
        for (ContextoUtilizador outro : List.of(contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT"),
                contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT"),
                contextoExterno(UUID.randomUUID(), processoDaEntidadeB.getEntidade(), "ENTIDADE"))) {
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(outro, processoDaEntidadeB))
                    .isInstanceOf(PcmAtorNaoAutorizadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "PCM_ATOR_NAO_AUTORIZADO");
            assertThat(autorizacaoService.podeDevolverDgt(outro, processoDaEntidadeB)).isFalse();
        }
    }

    @Test
    void naDevolucaoOProcessoNaoAtribuidoAoUtilizadorDa404EOAtribuidoContaEmQualquerEstado() {
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(processoDaEntidadeB.getInstrumentoProcedimentoConfig());
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(pcm, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorA);
        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(pcm, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        // Anulado enquanto estava com a PCM: passa a Camada 2 e é o estado que o recusa (409, no serviço).
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        processoDaEntidadeB.setEstado(EstadoProcesso.ANULADO);
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(pcm, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThat(autorizacaoService.processoVisivel(pcm, processoDaEntidadeB)).isFalse();
    }

    @Test
    void semALinhaPcmNaConfiguracaoADevolucaoDa403PcmAtorNaoAutorizado() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeDevolverDgt(pcm, processoDaEntidadeB))
                .isInstanceOf(PcmAtorNaoAutorizadoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PCM_ATOR_NAO_AUTORIZADO");
        assertThat(autorizacaoService.podeDevolverDgt(pcm, processoDaEntidadeB)).isFalse();
    }

    // «Corrigir Processo» pela PCM: gravar o formulário e acrescentar documentos no processo atribuído (TR-104, TR-106).

    @Test
    void aPcmAtribuidaEditaSemAutoriaComALinhaPcmEOEstadoFicaParaQuemChama() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(processoDaEntidadeB.getInstrumentoProcedimentoConfig());

        assertThatCode(() -> autorizacaoService.verificarPermissaoDeEditar(pcm, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThat(autorizacaoService.correcaoPelaPcm(pcm, processoDaEntidadeB)).isTrue();
        assertThat(autorizacaoService.pcmPodeCorrigir(pcm, processoDaEntidadeB)).isTrue();

        // Noutro estado passa as camadas: é o serviço que recusa com o 409 de estado.
        processoDaEntidadeB.setEstado(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThatCode(() -> autorizacaoService.verificarPermissaoDeEditar(pcm, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThat(autorizacaoService.correcaoPelaPcm(pcm, processoDaEntidadeB)).isTrue();
        assertThat(autorizacaoService.pcmPodeCorrigir(pcm, processoDaEntidadeB)).isFalse();
    }

    @Test
    void naEdicaoAPcmComOProcessoDeOutroUtilizadorOuNuncaAtribuidoRecebe404() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(processoDaEntidadeB.getInstrumentoProcedimentoConfig());

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeEditar(pcm, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorA);
        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeEditar(pcm, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(autorizacaoService.correcaoPelaPcm(pcm, processoDaEntidadeB)).isFalse();
        assertThat(autorizacaoService.pcmPodeCorrigir(pcm, processoDaEntidadeB)).isFalse();
    }

    @Test
    void naEdicaoSemALinhaPcmAPcmAtribuidaRecebe403DaCamada3() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeEditar(pcm, processoDaEntidadeB))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .isNotInstanceOf(PcmAtorNaoAutorizadoException.class);
        assertThat(autorizacaoService.pcmPodeCorrigir(pcm, processoDaEntidadeB)).isFalse();
    }

    @Test
    void naEdicaoOAutorEOutroUtilizadorDaEntidadeSeguemAsCamadasDeSempre() {
        processoDaEntidadeB.setEstado(EstadoProcesso.EM_SUBMISSAO);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM,
                processoDaEntidadeB.getInstrumentoProcedimentoConfig())).thenReturn(true);
        ContextoUtilizador autor = contextoExterno(utilizadorA.getId(), entidadeB, "ENTIDADE");
        ContextoUtilizador colega = contextoExterno(UUID.randomUUID(), entidadeB, "ENTIDADE");

        assertThatCode(() -> autorizacaoService.verificarPermissaoDeEditar(autor, processoDaEntidadeB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeEditar(colega, processoDaEntidadeB))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThat(autorizacaoService.correcaoPelaPcm(autor, processoDaEntidadeB)).isFalse();
    }

    @Test
    void soODgtEnviaAPcmEOsRestantesRecebemPcmAtorNaoAutorizado() {
        processoDaEntidadeA.setEstado(EstadoProcesso.VALIDADO_DGT);
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");
        ContextoUtilizador visitante = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);
        ContextoUtilizador daEntidade = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeEnviarPcm(master, processoDaEntidadeA)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeEnviarPcm(tecnico, processoDaEntidadeA)).doesNotThrowAnyException();
        assertThat(autorizacaoService.podeEnviarPcm(tecnico, processoDaEntidadeA)).isTrue();
        for (ContextoUtilizador recusado : java.util.List.of(visitante, daEntidade)) {
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeEnviarPcm(recusado, processoDaEntidadeA))
                    .isInstanceOf(PcmAtorNaoAutorizadoException.class)
                    .hasFieldOrPropertyWithValue("codigo", "PCM_ATOR_NAO_AUTORIZADO");
            assertThat(autorizacaoService.podeEnviarPcm(recusado, processoDaEntidadeA)).isFalse();
        }
    }

    @Test
    void deveDevolver404AoEnviarAPcmUmProcessoQueAEntidadeNaoVe() {
        ContextoUtilizador outraEntidade = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeEnviarPcm(outraEntidade, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolver403AEntidadeQueNaoEDgtAoEnviarAIncm() {
        ContextoUtilizador entidade = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeEnviarIncm(entidade, processoDaEntidadeA))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void devePassarCamada2EFalharCamada3Com403QuandoTecnicoAgeComoAutor() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(
                contexto, dinamicaDaEntidadeA, procedimentoConfig))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void devePassarCamada2EFalharCamada3Com403QuandoVisitanteSemAtor() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(
                contexto, dinamicaDaEntidadeA, procedimentoConfig))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolver403QuandoMasterInternoAgeComoAutorMesmoComLinhaDgt() {
        ContextoUtilizador contexto = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeA))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão neste procedimento.");

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void devePassarCamada3QuandoEntidadeDeTipoDgtAgeNumProcedimentoComLinhaDgt() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.DGT);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.DGT, procedimentoConfig))
                .thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeA))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403AoAgirQuandoEntidadeSemTipoSemConsultarPermissaoAcao() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", null);

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeA))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão neste procedimento.");

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolver404EmVezDe403AoAgirQuandoSemVisibilidadeMesmoSemTipo() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", null);

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveResolverOTipoDaEntidadeDaSessaoSoParaExternosComTipo() {
        assertThat(autorizacaoService.tipoEntidadeDaSessao(
                contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.CM)))
                .contains(TipoEntidade.CM);
        assertThat(autorizacaoService.tipoEntidadeDaSessao(
                contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", null))).isEmpty();
        assertThat(autorizacaoService.tipoEntidadeDaSessao(
                contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT"))).isEmpty();
        assertThat(autorizacaoService.tipoEntidadeDaSessao(
                contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT"))).isEmpty();
        assertThat(autorizacaoService.tipoEntidadeDaSessao(
                contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null))).isEmpty();
    }

    @Test
    void naoDeveResolverOTipoDeUmInternoMesmoQueOContextoTragaUmTipo() {
        ContextoUtilizador internoComTipo = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null,
                "DGT", null, null, null, null, TipoEntidade.DGT, null);

        assertThat(autorizacaoService.tipoEntidadeDaSessao(internoComTipo)).isEmpty();
    }

    @Test
    void deveDevolver403QuandoProcedimentoSemPermissaoAcaoParaOAtor() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(
                TipoEntidade.CM, procedimentoConfig)).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(
                contexto, dinamicaDaEntidadeA, procedimentoConfig))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveAvaliarCamada2ContraAEntidadeDoProcessoENaoContraODoInstrumentoPai() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoDeveConsultarPermissaoAcaoQuandoCamada2Falha() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPermissaoDeAgir(contexto, processoDaEntidadeB))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void devePermitirCriarProcessoNumaDinamicaIniciadaPorOutraEntidade() {
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, procedimentoConfig);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(eq(procedimentoConfig), any())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamicaDaEntidadeB, procedimentoConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver404QuandoAEntidadeDaSessaoTemOMesmoNipcMasOutroIdQueODoProcesso() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeComOMesmoNipcDeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(autorizacaoService.processoVisivel(contexto, processoDaEntidadeA)).isFalse();
    }

    @Test
    void deveDevolver404QuandoOTokenDoExternoNaoTemEntidadeId() {
        ContextoUtilizador semId = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, entidadeA.getNipc(), "ENTIDADE", null,
                null, null, null, TipoEntidade.CM, null);

        assertThatThrownBy(() -> autorizacaoService.verificarVisibilidade(semId, processoDaEntidadeA))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void devePassarCamada2QuandoAEntidadeDaSessaoEADoProcesso() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatCode(() -> autorizacaoService.verificarVisibilidade(contexto, processoDaEntidadeA)).doesNotThrowAnyException();
        assertThat(autorizacaoService.processoVisivel(contexto, processoDaEntidadeA)).isTrue();
    }

    @Test
    void devePassarAsDuasCamadasQuandoPermissaoAcaoExisteEAtiva() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(
                TipoEntidade.CM, procedimentoConfig)).thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(eq(procedimentoConfig), any())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(
                contexto, dinamicaDaEntidadeA, procedimentoConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver409QuandoOAnteriorObrigatorioNaoEstaPublicado() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem1 = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem2);
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 2))
                .thenReturn(List.of(ordem1));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, dinamica.getTentativa(), ordem1, EstadoProcesso.PUBLICADO))
                .thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem2))
                .isInstanceOf(SequenciamentoNaoCumpridoException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEQUENCIAMENTO_NAO_CUMPRIDO");
    }

    @Test
    void oAtoPontualPassaOSequenciamentoComOsAnterioresObrigatoriosPorPublicarEONormalContinuaBloqueado() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig decisao = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig discussao = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        InstrumentoProcedimentoConfig publicacao = procedimentoConfigComOrdem(dinamicaConfig, 3, true);
        InstrumentoProcedimentoConfig pontual = procedimentoConfigComOrdem(dinamicaConfig, 102, false);
        pontual.setAtoPontual(true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contextoPontual = contextoComPermissao(entidadeA, pontual);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, publicacao))
                .thenReturn(true);
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 3))
                .thenReturn(List.of(decisao, discussao));
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 102))
                .thenReturn(List.of(decisao, discussao, publicacao));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, dinamica.getTentativa(), decisao, EstadoProcesso.PUBLICADO))
                .thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(any(), any())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contextoPontual, dinamica, pontual))
                .doesNotThrowAnyException();
        assertThat(autorizacaoService.procedimentoDisponivel(dinamica, pontual, List.of())).isTrue();
        verify(instrumentoProcedimentoConfigRepository, never())
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 102);
        verify(regraRepository, never()).findByInstrumentoProcedimentoConfigAndAtivoTrue(pontual);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contextoPontual, dinamica, publicacao))
                .isInstanceOf(SequenciamentoNaoCumpridoException.class);
        assertThat(autorizacaoService.procedimentoDisponivel(dinamica, publicacao, List.of())).isFalse();
    }

    @Test
    void devePassarQuandoOAnteriorObrigatorioEstaPublicado() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem1 = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem2);
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 2))
                .thenReturn(List.of(ordem1));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, dinamica.getTentativa(), ordem1, EstadoProcesso.PUBLICADO))
                .thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(ordem2, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem2))
                .doesNotThrowAnyException();
    }

    @Test
    void deveExigirOAnteriorPublicadoNaTentativaCorrenteENaoNumaAnterior() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem1 = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ReflectionTestUtils.setField(dinamica, "tentativa", 2);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem2);
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 2))
                .thenReturn(List.of(ordem1));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, 1, ordem1, EstadoProcesso.PUBLICADO))
                .thenReturn(true);
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, 2, ordem1, EstadoProcesso.PUBLICADO))
                .thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem2))
                .isInstanceOf(SequenciamentoNaoCumpridoException.class);
    }

    @Test
    void deveContarSoOsProcessosEmCursoDaTentativaCorrente() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig decisao = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ReflectionTestUtils.setField(dinamica, "tentativa", 2);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, decisao);
        when(processoRepository.findEmCursoDaTentativa(dinamica, 1, EstadoProcesso.TERMINAIS))
                .thenReturn(List.of(processoEm(dinamica, decisao, EstadoProcesso.ENVIADO_DGT)));
        when(processoRepository.findEmCursoDaTentativa(dinamica, 2, EstadoProcesso.TERMINAIS)).thenReturn(List.of());
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 1))
                .thenReturn(List.of());
        when(motorRegrasService.avaliarElegibilidadeProcedimento(decisao, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, decisao))
                .doesNotThrowAnyException();
        verify(processoRepository, never()).findEmCursoDaTentativa(dinamica, 1, EstadoProcesso.TERMINAIS);
    }

    @Test
    void naoDeveBloquearQuandoOAnteriorNaoObrigatorioNaoEstaPublicado() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem3 = procedimentoConfigComOrdem(dinamicaConfig, 3, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem3);
        // A query real (`findBy...ObrigatorioTrue`) já exclui os opcionais — o de ordem 2 nunca é devolvido.
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 3))
                .thenReturn(List.of());
        when(motorRegrasService.avaliarElegibilidadeProcedimento(ordem3, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem3))
                .doesNotThrowAnyException();

        verify(processoRepository, never()).existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(any(), anyInt(), any(), any());
    }

    @Test
    void deveDevolver409QuandoSequenciamentoCumpridoMasRegraAditivaFalha() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig config = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, config);
        Regra regraAditiva = Regra.paraProcedimento(config);
        regraAditiva.setOperador("AND");
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(config)).thenReturn(Optional.of(regraAditiva));
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 1))
                .thenReturn(List.of());
        when(motorRegrasService.avaliarElegibilidadeProcedimento(config, dinamica.getInstrumento().getId())).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, config))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasFieldOrPropertyWithValue("codigo", "ESTADO_INVALIDO");
    }

    @Test
    void deveDevolver409QuandoRegraAditivaPassaMasSequenciamentoNaoCumprido() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem1 = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem2);
        Regra regraAditiva = Regra.paraProcedimento(ordem2);
        regraAditiva.setOperador("AND");
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(ordem2)).thenReturn(Optional.of(regraAditiva));
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 2))
                .thenReturn(List.of(ordem1));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(dinamica, dinamica.getTentativa(), ordem1, EstadoProcesso.PUBLICADO))
                .thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem2))
                .isInstanceOf(SequenciamentoNaoCumpridoException.class);

        verify(motorRegrasService, never()).avaliarElegibilidadeProcedimento(any(), any());
    }

    @Test
    void devePassarQuandoRegraSubstituiSequenciamentoEPassaMesmoComSequenciamentoNaoCumprido() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig ordem2 = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, ordem2);
        Regra regraSubstitui = Regra.paraProcedimento(ordem2);
        regraSubstitui.setOperador("AND");
        regraSubstitui.setSubstituiSequenciamento(true);
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(ordem2)).thenReturn(Optional.of(regraSubstitui));
        when(motorRegrasService.avaliarElegibilidadeProcedimento(ordem2, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, ordem2))
                .doesNotThrowAnyException();

        verifyNoInteractions(instrumentoProcedimentoConfigRepository);
        verify(processoRepository, never()).existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(any(), anyInt(), any(), any());
    }

    @Test
    void devePassarSemBloqueioDoMotorDeRegrasQuandoNaoHaRegraLigada() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig config = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, config);
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(config)).thenReturn(Optional.empty());
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 1))
                .thenReturn(List.of());
        when(motorRegrasService.avaliarElegibilidadeProcedimento(config, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, config))
                .doesNotThrowAnyException();

        verify(motorRegrasService).avaliarElegibilidadeProcedimento(config, dinamica.getInstrumento().getId());
    }

    @Test
    void devePassarParaProcedimentoUnicoSemNenhumAnteriorNemRegraConfigurada() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig unico = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, unico);
        when(instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 1))
                .thenReturn(List.of());
        when(motorRegrasService.avaliarElegibilidadeProcedimento(unico, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, unico))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver409ProcessoEmCursoQuandoHaProcessoEmCursoNaDinamica() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig discussao = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig publicacao = procedimentoConfigComOrdem(dinamicaConfig, 2, true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, publicacao);
        Processo enviado = processoEm(dinamica, discussao, EstadoProcesso.ENVIADO_DGT);
        when(processoRepository.findEmCursoDaTentativa(dinamica, dinamica.getTentativa(), EstadoProcesso.TERMINAIS)).thenReturn(List.of(enviado));

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, publicacao))
                .isInstanceOf(ProcessoEmCursoException.class)
                .hasFieldOrPropertyWithValue("codigo", "PROCESSO_EM_CURSO")
                .hasMessage("Já existe um processo em curso nesta dinâmica.");

        verifyNoInteractions(regraRepository, instrumentoProcedimentoConfigRepository, motorRegrasService);
    }

    @Test
    void naoDeveRecusarPorProcessoEmCursoQuandoOCandidatoEAtoPontual() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig discussao = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig pontual = procedimentoConfigComOrdem(dinamicaConfig, 2, false);
        pontual.setAtoPontual(true);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, pontual);
        when(processoRepository.findEmCursoDaTentativa(dinamica, dinamica.getTentativa(), EstadoProcesso.TERMINAIS))
                .thenReturn(List.of(processoEm(dinamica, discussao, EstadoProcesso.EM_VALIDACAO_DGT)));
        when(motorRegrasService.avaliarElegibilidadeProcedimento(pontual, dinamica.getInstrumento().getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, pontual))
                .doesNotThrowAnyException();
    }

    @Test
    void deveConsultarOsProcessosEmCursoForaDosEstadosTerminais() {
        Dinamica dinamica = dinamicaDaEntidadeA;
        Processo rejeitado = processoEm(dinamica, procedimentoConfig, EstadoProcesso.REJEITADO_DGT);
        when(processoRepository.findEmCursoDaTentativa(dinamica, dinamica.getTentativa(), EstadoProcesso.TERMINAIS)).thenReturn(List.of(rejeitado));

        assertThat(autorizacaoService.processosEmCurso(dinamica)).containsExactly(rejeitado);

        assertThat(EstadoProcesso.TERMINAIS).containsExactlyInAnyOrder(EstadoProcesso.PUBLICADO, EstadoProcesso.ANULADO);
    }

    @Test
    void processoEmCursoBloqueiaSoQuandoHaProcessoEmCursoECandidatoNaoEAtoPontual() {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig normal = procedimentoConfigComOrdem(dinamicaConfig, 1, true);
        InstrumentoProcedimentoConfig pontual = procedimentoConfigComOrdem(dinamicaConfig, 2, false);
        pontual.setAtoPontual(true);
        List<Processo> emCurso = List.of(processoEm(dinamicaDaEntidadeA, normal, EstadoProcesso.ENVIADO_DGT));

        assertThat(autorizacaoService.processoEmCursoBloqueia(List.of(), normal)).isFalse();
        assertThat(autorizacaoService.processoEmCursoBloqueia(emCurso, normal)).isTrue();
        assertThat(autorizacaoService.processoEmCursoBloqueia(emCurso, pontual)).isFalse();
    }

    @Test
    void procedimentoDisponivelEVerificarPossibilidadeDeAgirDaoARespostaIgualSemProcessoEmCurso() {
        verificarMesmaRespostaEmAmbosOsCaminhos(false, false, true);
    }

    @Test
    void procedimentoDisponivelEVerificarPossibilidadeDeAgirDaoARespostaIgualComProcessoEmCurso() {
        verificarMesmaRespostaEmAmbosOsCaminhos(true, false, false);
    }

    @Test
    void procedimentoDisponivelEVerificarPossibilidadeDeAgirDaoARespostaIgualComCandidatoAtoPontual() {
        verificarMesmaRespostaEmAmbosOsCaminhos(true, true, true);
    }

    private void verificarMesmaRespostaEmAmbosOsCaminhos(boolean comProcessoEmCurso, boolean candidatoAtoPontual,
                                                         boolean esperadoDisponivel) {
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig anterior = procedimentoConfigComOrdem(dinamicaConfig, 1, false);
        InstrumentoProcedimentoConfig candidato = procedimentoConfigComOrdem(dinamicaConfig, 2, false);
        candidato.setAtoPontual(candidatoAtoPontual);
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", utilizadorA);
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, candidato);
        List<Processo> emCurso = comProcessoEmCurso
                ? List.of(processoEm(dinamica, anterior, EstadoProcesso.ENVIADO_DGT)) : List.of();
        when(processoRepository.findEmCursoDaTentativa(dinamica, dinamica.getTentativa(), EstadoProcesso.TERMINAIS)).thenReturn(emCurso);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(candidato, dinamica.getInstrumento().getId())).thenReturn(true);

        boolean disponivel = autorizacaoService.procedimentoDisponivel(dinamica, candidato, emCurso);
        boolean podeCriar;
        try {
            autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, candidato);
            podeCriar = true;
        } catch (ProcessoEmCursoException e) {
            podeCriar = false;
        }

        assertThat(disponivel).isEqualTo(esperadoDisponivel);
        assertThat(podeCriar).isEqualTo(esperadoDisponivel);
    }

    private Processo processoEm(Dinamica dinamica, InstrumentoProcedimentoConfig config, EstadoProcesso estado) {
        Processo processo = new Processo(dinamica, config, utilizadorA, null);
        processo.setEstado(estado);
        return processo;
    }

    @Test
    void naoDeveAvaliarCamada4QuandoCamada3Falha() {
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(
                TipoEntidade.CM, procedimentoConfig)).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(
                contexto, dinamicaDaEntidadeA, procedimentoConfig))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(regraRepository, instrumentoProcedimentoConfigRepository, processoRepository, motorRegrasService);
    }

    @Test
    void deveDevolver403QuandoEntidadeTentaEditarProcessoCriadoPorOutroUtilizador() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        ContextoUtilizador contextoB = contextoExterno(utilizadorB.getId(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarAutoria(contextoB, processo))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveDevolver403EmQualquerEstadoNaoSoEmSubmissao() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        processo.setEstado(EstadoProcesso.REJEITADO_DGT);
        ContextoUtilizador contextoB = contextoExterno(utilizadorB.getId(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarAutoria(contextoB, processo))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void devePassarCamada3bQuandoOProprioAutorAge() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        ContextoUtilizador contextoA = contextoExterno(utilizadorA.getId(), entidadeA, "ENTIDADE");

        assertThatCode(() -> autorizacaoService.verificarAutoria(contextoA, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403AoUtilizadorPcmQueNaoEAutorNemTemOProcessoAtribuido() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        ContextoUtilizador contextoPcm = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");

        assertThatThrownBy(() -> autorizacaoService.verificarAutoria(contextoPcm, processo))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void naoDeveBloquearOUtilizadorPcmNoProcessoValidadoDgtQueLheFoiAtribuido() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processo.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador contextoPcm = contextoExterno(utilizadorB.getId(), entidadeC, "PCM");

        assertThatCode(() -> autorizacaoService.verificarAutoria(contextoPcm, processo)).doesNotThrowAnyException();
    }

    @Test
    void naoDeveAplicarCamada3bQuandoMasterValidaOuRejeita() {
        Processo processo = new Processo(null, procedimentoConfig, utilizadorA, null);
        ContextoUtilizador contextoAdmin = contextoInterno(utilizadorB.getId(), PerfilInterno.MASTER, "DGT");

        assertThatCode(() -> autorizacaoService.verificarAutoria(contextoAdmin, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403QuandoAtoDeSegundaSerieNaoConstaDeAtosAutorizados() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_H"), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(999L, null, true)));
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atos, "H");

        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo))
                .isInstanceOf(TipoAtoNaoAutorizadoException.class)
                .hasFieldOrPropertyWithValue("codigo", "TIPO_ATO_NAO_AUTORIZADO");
    }

    @Test
    void devePassarCamada3cQuandoAtoDeSegundaSerieEParteConstamDaAdesao() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_H"), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(222L, null, true)));
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atos, "H");

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403QuandoParteDaAdesaoNaoCoincideComASerieSegundaC() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_C"), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(222L, null, true)));
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atos, "H");

        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo))
                .isInstanceOf(ParteDrNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "PARTE_DR_NAO_AUTORIZADA");
    }

    @Test
    void deveCompararIdentificadorDaPrimeiraSerieNuncaDaSegundaParaSerieDrPrimeira() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("PRIMEIRA"), utilizadorA, null);
        // incm_act_type_id_serie_1 != incm_act_type_id_serie_2 — só a 1.ª pode passar aqui.
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atosComApenasSerie2 = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(222L, null, true)));
        ContextoUtilizador contextoSoSerie2 = contextoExternoComAtos(entidadeA, atosComApenasSerie2, null);

        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(contextoSoSerie2, processo))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        ClaimsUtilizador.AtosAutorizados atosComSerie1 = new ClaimsUtilizador.AtosAutorizados(List.of(111L), List.of());
        ContextoUtilizador contextoComSerie1 = contextoExternoComAtos(entidadeA, atosComSerie1, null);

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contextoComSerie1, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void deveVerificarContraSerie1ParaProcedimentoPrimeiraMesmoNumInstrumentoPlano() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("PRIMEIRA"), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(111L), List.of());
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atos, null);

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void naoDeveAplicarCamada3cParaUtilizadorInternoDaDgt() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_H"), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ContextoUtilizador contextoDgt = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contextoDgt, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void semTipoAtoNaoVerificaOTipoMasVerificaAParteDaAdesao() {
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(),
                List.of(new ClaimsUtilizador.AtoAutorizado(999L, null, true)));
        Processo daParteH = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_H"), utilizadorA, null);
        Processo daParteC = new Processo(null, procedimentoConfigComSerieDr("SEGUNDA_C"), utilizadorA, null);
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atos, "H");

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, daParteH)).doesNotThrowAnyException();
        assertThat(autorizacaoService.podePublicar(contexto, daParteH)).isTrue();
        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, daParteC))
                .isInstanceOf(ParteDrNaoAutorizadaException.class);
    }

    @Test
    void naoDeveAplicarCamada3cSemSerieNoProcedimentoNemNoProcesso() {
        Processo processo = new Processo(null, procedimentoConfigComSerieDr(null), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));
        ClaimsUtilizador.AtosAutorizados atosVazios = new ClaimsUtilizador.AtosAutorizados(List.of(), List.of());
        ContextoUtilizador contexto = contextoExternoComAtos(entidadeA, atosVazios, null);

        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo))
                .doesNotThrowAnyException();
    }

    @Test
    void semSerieNoProcedimentoACamada3cUsaASerieEscolhidaNoProcesso() {
        ClaimsUtilizador.AtosAutorizados atos = new ClaimsUtilizador.AtosAutorizados(List.of(111L),
                List.of(new ClaimsUtilizador.AtoAutorizado(222L, null, true)));
        ContextoUtilizador daParteC = contextoExternoComAtos(entidadeA, atos, "C");
        Processo processo = new Processo(null, procedimentoConfigComSerieDr(null), utilizadorA, null);
        processo.setTipoAto(tipoAtoComIds(111L, 222L));

        processo.setSerieDr("SEGUNDA_H");
        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(daParteC, processo))
                .isInstanceOf(ParteDrNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "PARTE_DR_NAO_AUTORIZADA");

        processo.setSerieDr("SEGUNDA_C");
        assertThatCode(() -> autorizacaoService.verificarAutorizacaoPublicacao(daParteC, processo)).doesNotThrowAnyException();

        processo.setSerieDr("PRIMEIRA");
        processo.setTipoAto(tipoAtoComIds(333L, 222L));
        assertThatThrownBy(() -> autorizacaoService.verificarAutorizacaoPublicacao(daParteC, processo))
                .isInstanceOf(TipoAtoNaoAutorizadoException.class);
    }

    @Test
    void deveDevolver403NaCriacaoQuandoAtorNaoResolvido() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador contextoSemAtor = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(contextoSemAtor, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolver403NaCriacaoQuandoTecnicoComAtorDgt() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(tecnico, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolver403NaCriacaoQuandoAtorSemPermissaoAcaoParaOTipoDeInstrumento() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(permissaoAcaoRepository
                .existeNoProcedimentoInicialDoTipo(
                        TipoEntidade.CM, tipoInstrumento)).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void devePassarNaCriacaoQuandoAtorTemPermissaoAcaoParaOTipoDeInstrumento() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        when(permissaoAcaoRepository
                .existeNoProcedimentoInicialDoTipo(
                        TipoEntidade.CM, tipoInstrumento)).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403NoInicioDeDinamicaQuandoAtorNaoResolvido() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador contextoSemAtor = contextoInterno(UUID.randomUUID(), PerfilInterno.VISITANTE, null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contextoSemAtor, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository, motorRegrasService);
    }

    @Test
    void deveDevolver403NoInicioDeDinamicaQuandoTecnicoComAtorDgt() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador tecnico = contextoInterno(UUID.randomUUID(), PerfilInterno.TECNICO, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(tecnico, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository, motorRegrasService);
    }

    @Test
    void deveDevolver403NoInicioDeDinamicaQuandoAtorSemPermissaoAcaoParaADinamica() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        comProcedimentoInicial(config, TipoEntidade.CM, false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(motorRegrasService);
    }

    @Test
    void deveDevolver409NoInicioDeDinamicaQuandoConfigNaoPertenceAoTipoDoInstrumento() {
        TipoInstrumento tipoDoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        TipoInstrumento outroTipo = new TipoInstrumento("REN", "Reserva Ecológica Nacional", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoDoInstrumento);
        InstrumentoDinamicaConfig configDeOutroTipo = new InstrumentoDinamicaConfig(outroTipo, null);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        comProcedimentoInicial(configDeOutroTipo, TipoEntidade.CM, true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(eq(configDeOutroTipo), any())).thenReturn(true);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, configDeOutroTipo))
                .isInstanceOf(DinamicaNaoElegivelException.class)
                .hasFieldOrPropertyWithValue("codigo", "DINAMICA_NAO_ELEGIVEL");
    }

    @Test
    void deveDevolver409NoInicioDeDinamicaQuandoMotorDeRegrasRecusa() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        comProcedimentoInicial(config, TipoEntidade.CM, true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(config, instrumento.getId())).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config))
                .isInstanceOf(DinamicaNaoElegivelException.class);
    }

    @Test
    void devePassarNoInicioDeDinamicaQuandoTudoOk() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");
        comProcedimentoInicial(config, TipoEntidade.CM, true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(config, instrumento.getId())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config))
                .doesNotThrowAnyException();
    }

    @Test
    void deveDevolver403NaCriacaoQuandoOTipoDaEntidadeNaoTemLinhaParaOTipoDeInstrumento() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.CCDR_DR);
        when(permissaoAcaoRepository
                .existeNoProcedimentoInicialDoTipo(
                        TipoEntidade.CCDR_DR, tipoInstrumento)).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEM_PERMISSAO")
                .hasMessage("Tipo da entidade da sessão sem permissão para o tipo de Instrumento.");
    }

    @Test
    void deveDevolver403NaCriacaoQuandoASessaoNaoTemTipoSemConsultarPermissaoAcao() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(contexto, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão para o tipo de Instrumento.");

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolverOProcedimentoAtivoDeMenorOrdemComoProcedimentoInicial() {
        InstrumentoDinamicaConfig config = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig segundo = procedimentoConfigComOrdem(config, 2, true);
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(config))
                .thenReturn(Optional.of(segundo));

        assertThat(autorizacaoService.procedimentoInicial(config)).containsSame(segundo);
    }

    @Test
    void deveUsarOProcedimentoAtivoSeguinteQuandoOPrimeiroEstaInativoParaPermitirIniciar() {
        InstrumentoDinamicaConfig config = dinamicaConfigGenerico();
        InstrumentoProcedimentoConfig primeiroInativo = procedimentoConfigComOrdem(config, 1, true);
        primeiroInativo.setAtivo(false);
        InstrumentoProcedimentoConfig segundoAtivo = procedimentoConfigComOrdem(config, 2, true);
        // A consulta só devolve ativos: com o 1.º inativo, o Procedimento inicial é o de ordem 2.
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(config))
                .thenReturn(Optional.of(segundoAtivo));
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, segundoAtivo))
                .thenReturn(true);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, primeiroInativo))
                .thenReturn(true);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThat(autorizacaoService.podeIniciarDinamica(contexto, config)).isTrue();
        verify(permissaoAcaoRepository, never())
                .existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), eq(primeiroInativo));
    }

    @Test
    void deveRecusarIniciarADinamicaQuandoOTipoSoTemLinhaNumProcedimentoPosterior() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        InstrumentoProcedimentoConfig inicial = comProcedimentoInicial(config, TipoEntidade.CCDR_DR, false);
        InstrumentoProcedimentoConfig constituicaoCc = procedimentoConfigComOrdem(config, 2, true);
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CCDR_DR, constituicaoCc))
                .thenReturn(true);
        ContextoUtilizador ccdr = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.CCDR_DR);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(ccdr, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEM_PERMISSAO")
                .hasMessage("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica.");

        verify(permissaoAcaoRepository).existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CCDR_DR, inicial);
        verify(permissaoAcaoRepository, never())
                .existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CCDR_DR, constituicaoCc);
        verifyNoInteractions(motorRegrasService);
    }

    @Test
    void deveRecusarIniciarADinamicaSemProcedimentosAtivos() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        when(instrumentoProcedimentoConfigRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(config))
                .thenReturn(Optional.empty());
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(motorRegrasService);
    }

    @Test
    void deveRecusarCriarOInstrumentoDaCcdrSemLinhaNoProcedimentoInicialMesmoComLinhaNaCc() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador ccdr = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.CCDR_DR);
        when(permissaoAcaoRepository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CCDR_DR, tipoInstrumento)).thenReturn(false);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(ccdr, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão para o tipo de Instrumento.");

        verify(permissaoAcaoRepository, never())
                .existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), any());
    }

    @Test
    void deveRegistarNasRecusasDaCamada3SoOUtilizadorIdEAOperacaoSemNifNemNome() {
        Logger logger = (Logger) LoggerFactory.getLogger(AutorizacaoService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
            Instrumento instrumentoDoTipo = new Instrumento();
            ReflectionTestUtils.setField(instrumentoDoTipo, "tipoInstrumento", tipoInstrumento);
            InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
            comProcedimentoInicial(config, TipoEntidade.CCDR_DR, false);
            UUID utilizadorId = UUID.randomUUID();
            ContextoUtilizador ccdr = contextoExterno(utilizadorId, entidadeA, "ENTIDADE", TipoEntidade.CCDR_DR);

            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(ccdr, tipoInstrumento))
                    .isInstanceOf(AcaoNaoAutorizadaException.class);
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(ccdr, instrumentoDoTipo, config))
                    .isInstanceOf(AcaoNaoAutorizadaException.class);

            assertThat(logs.list).hasSize(2);
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(mensagem -> assertThat(mensagem)
                            .contains("utilizadorId=" + utilizadorId)
                            .doesNotContain(entidadeA.getNipc())
                            .doesNotContain("Câmara Municipal A"));
            assertThat(logs.list.get(0).getFormattedMessage()).startsWith("Camada 3 recusou criar o instrumento");
            assertThat(logs.list.get(1).getFormattedMessage()).startsWith("Camada 3 recusou iniciar a dinâmica");
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void deveDevolver403NaCriacaoQuandoMasterInternoSemConsultarPermissaoAcao() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(master, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolver403NoInicioDeDinamicaQuandoMasterInternoSemConsultarPermissaoAcao() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador master = contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT");

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(master, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica.");

        verifyNoInteractions(permissaoAcaoRepository, motorRegrasService);
    }

    @Test
    void deveDevolver403NoInicioDeDinamicaQuandoASessaoNaoTemTipoSemConsultarPermissaoAcao() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        ContextoUtilizador contexto = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", null);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(contexto, instrumento, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica.");

        verifyNoInteractions(permissaoAcaoRepository, motorRegrasService);
    }

    // A PCM nunca cria (FR-153), mesmo com a linha `PCM` que os Procedimentos de 1.ª série passam a ter.

    private static final String MSG_PCM_NAO_CRIA = "A PCM não cria Instrumentos, Dinâmicas nem processos.";

    private ContextoUtilizador contextoPcmComLinhasPcm(InstrumentoProcedimentoConfig config) {
        when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.PCM, config))
                .thenReturn(true);
        when(permissaoAcaoRepository.existeNoProcedimentoInicialDoTipo(eq(TipoEntidade.PCM), any())).thenReturn(true);
        return contextoExterno(utilizadorB.getId(), entidadeC, "PCM");
    }

    @Test
    void aPcmRecebe403AoCriarInstrumentoMesmoComALinhaPcm() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PNPOT", "Programa Nacional", "PROGRAMA");
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(procedimentoConfig);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(pcm, tipoInstrumento))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEM_PERMISSAO")
                .hasMessage(MSG_PCM_NAO_CRIA);
        verify(permissaoAcaoRepository, never()).existeNoProcedimentoInicialDoTipo(any(), any());
    }

    @Test
    void aPcmRecebe403AoIniciarDinamicaMesmoComALinhaPcmNoProcedimentoInicial() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PNPOT", "Programa Nacional", "PROGRAMA");
        Instrumento instrumentoDoTipo = new Instrumento();
        ReflectionTestUtils.setField(instrumentoDoTipo, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumentoDoTipo, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        InstrumentoProcedimentoConfig inicial = comProcedimentoInicial(config, TipoEntidade.PCM, true);
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(inicial);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(pcm, instrumentoDoTipo, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEM_PERMISSAO")
                .hasMessage(MSG_PCM_NAO_CRIA);
        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(pcm, config))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage(MSG_PCM_NAO_CRIA);
        assertThat(autorizacaoService.podeIniciarDinamica(pcm, config)).isFalse();
        verifyNoInteractions(motorRegrasService);
    }

    @Test
    void aPcmRecebe403AoCriarProcessoMesmoComALinhaPcmNoProcedimento() {
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(procedimentoConfig);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(pcm, dinamicaDaEntidadeB, procedimentoConfig))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasFieldOrPropertyWithValue("codigo", "SEM_PERMISSAO")
                .hasMessage(MSG_PCM_NAO_CRIA);
        verify(permissaoAcaoRepository, never()).existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(any(), any());
        verifyNoInteractions(motorRegrasService);
    }

    @Test
    void recusarCriacaoPelaPcmSoRecusaAPcm() {
        assertThatThrownBy(() -> autorizacaoService.recusarCriacaoPelaPcm(contextoExterno(UUID.randomUUID(), entidadeC, "PCM")))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage(MSG_PCM_NAO_CRIA);
        assertThatCode(() -> autorizacaoService.recusarCriacaoPelaPcm(contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE")))
                .doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.recusarCriacaoPelaPcm(contextoInterno(UUID.randomUUID(), PerfilInterno.MASTER, "DGT")))
                .doesNotThrowAnyException();
        assertThat(autorizacaoService.tipoEntidadeQueCria(contextoExterno(UUID.randomUUID(), entidadeC, "PCM"))).isEmpty();
        assertThat(autorizacaoService.tipoEntidadeQueCria(contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE")))
                .contains(TipoEntidade.CM);
    }

    @Test
    void aCamaraComPermissaoContinuaACriarInstrumentoDinamicaEProcesso() {
        TipoInstrumento tipoInstrumento = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        Instrumento instrumentoDoTipo = new Instrumento();
        ReflectionTestUtils.setField(instrumentoDoTipo, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumentoDoTipo, "tipoInstrumento", tipoInstrumento);
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
        comProcedimentoInicial(config, TipoEntidade.CM, true);
        when(motorRegrasService.avaliarElegibilidadeDinamica(config, instrumentoDoTipo.getId())).thenReturn(true);
        when(permissaoAcaoRepository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).thenReturn(true);
        ContextoUtilizador camara = contextoComPermissao(entidadeA, procedimentoConfig);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(eq(procedimentoConfig), any())).thenReturn(true);

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeCriar(camara, tipoInstrumento)).doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(camara, instrumentoDoTipo, config))
                .doesNotThrowAnyException();
        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(camara, dinamicaDaEntidadeA, procedimentoConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void aPcmContinuaAAgirNoProcessoQueLheFoiAtribuidoComALinhaPcm() {
        processoDaEntidadeB.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoDaEntidadeB.setPcmAtribuidoA(utilizadorB);
        ContextoUtilizador pcm = contextoPcmComLinhasPcm(procedimentoConfig);

        assertThatCode(() -> autorizacaoService.verificarPermissaoDeAgir(pcm, processoDaEntidadeB)).doesNotThrowAnyException();
    }

    @Test
    void deveRegistarARecusaDaPcmEmWarnComOUtilizadorIdEAOperacaoSemNifNemNome() {
        Logger logger = (Logger) LoggerFactory.getLogger(AutorizacaoService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            TipoInstrumento tipoInstrumento = new TipoInstrumento("PNPOT", "Programa Nacional", "PROGRAMA");
            InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, null);
            ContextoUtilizador pcm = contextoPcmComLinhasPcm(procedimentoConfig);

            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeCriar(pcm, tipoInstrumento))
                    .isInstanceOf(AcaoNaoAutorizadaException.class);
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeIniciarDinamica(pcm, config))
                    .isInstanceOf(AcaoNaoAutorizadaException.class);
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(pcm, dinamicaDaEntidadeB, procedimentoConfig))
                    .isInstanceOf(AcaoNaoAutorizadaException.class);

            assertThat(logs.list).hasSize(3)
                    .allSatisfy(evento -> assertThat(evento.getLevel()).isEqualTo(Level.WARN));
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly(
                            "A PCM não cria | utilizadorId=" + utilizadorB.getId() + " operacao=criar instrumento",
                            "A PCM não cria | utilizadorId=" + utilizadorB.getId() + " operacao=iniciar dinamica",
                            "A PCM não cria | utilizadorId=" + utilizadorB.getId() + " operacao=criar processo")
                    .allSatisfy(mensagem -> assertThat(mensagem)
                            .doesNotContain(entidadeC.getNipc())
                            .doesNotContain("Entidade C")
                            .doesNotContain("Utilizador B"));
        } finally {
            logger.detachAppender(logs);
        }
    }

    /** Medidas Preventivas: Publicação (final), Prorrogação, um ato pontual e um Procedimento opcional repetível depois do final. */
    private record MedidasPreventivas(InstrumentoDinamicaConfig config, InstrumentoProcedimentoConfig publicacao,
                                      InstrumentoProcedimentoConfig prorrogacao, InstrumentoProcedimentoConfig pontual,
                                      InstrumentoProcedimentoConfig repetivel, Dinamica dinamica) {
    }

    private MedidasPreventivas medidasPreventivas(EstadoDinamica estado, boolean comRepetivel) {
        InstrumentoDinamicaConfig config = dinamicaConfigGenerico();
        ReflectionTestUtils.setField(config, "id", 50L);
        InstrumentoProcedimentoConfig publicacao = procedimentoComId(config, 501L, 1, true);
        InstrumentoProcedimentoConfig prorrogacao = procedimentoComId(config, 502L, 2, false);
        InstrumentoProcedimentoConfig pontual = procedimentoComId(config, 503L, 101, false);
        pontual.setAtoPontual(true);
        InstrumentoProcedimentoConfig repetivel = procedimentoComId(config, 504L, 3, false);
        repetivel.setRepetivel(true);
        Dinamica dinamica = new Dinamica(instrumento, config, "Medidas preventivas", utilizadorA);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        dinamica.setEstado(estado);
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(anyCollection())).thenReturn(comRepetivel
                ? List.of(publicacao, prorrogacao, repetivel, pontual) : List.of(publicacao, prorrogacao, pontual));
        return new MedidasPreventivas(config, publicacao, prorrogacao, pontual, repetivel, dinamica);
    }

    private InstrumentoProcedimentoConfig procedimentoComId(InstrumentoDinamicaConfig config, long id, int ordem, boolean obrigatorio) {
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigComOrdem(config, ordem, obrigatorio);
        ReflectionTestUtils.setField(procedimento, "id", id);
        return procedimento;
    }

    private void comProcessos(Processo... processos) {
        when(processoRepository.findByDinamicaInAndInstrumentoProcedimentoConfigIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(processos));
    }

    private Processo processoEm(Dinamica dinamica, InstrumentoProcedimentoConfig config, EstadoProcesso estado, int ocorrencia) {
        Processo processo = processoEm(dinamica, config, estado);
        processo.setOcorrencia(ocorrencia);
        return processo;
    }

    @Test
    void posterioresAoFinalNumaDinamicaEmCursoVemVaziosSemConsultarNada() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.EM_CURSO, false);

        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).isEmpty();
        verify(instrumentoProcedimentoConfigRepository, never()).listarAtivosPorConfigs(anyCollection());
        verifyNoInteractions(processoRepository);
    }

    @Test
    void posterioresAoFinalNumaDinamicaTerminadaSemProrrogacaoFeitaSoTrazemAProrrogacaoSemOAtoPontual() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        comProcessos(processoEm(mp.dinamica(), mp.publicacao(), EstadoProcesso.PUBLICADO));

        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.prorrogacao());
    }

    @Test
    void posterioresAoFinalSaiAProrrogacaoFeitaOuEmCursoEVoltaDepoisDeAnulada() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);

        for (EstadoProcesso estado : List.of(EstadoProcesso.ENVIADO_DGT, EstadoProcesso.VALIDADO_DGT, EstadoProcesso.REJEITADO_DGT,
                EstadoProcesso.PUBLICADO)) {
            comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), estado));
            assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).as(estado.name()).isEmpty();
        }
        comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.ANULADO));
        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.prorrogacao());
        // Em `EM_SUBMISSAO` retoma-se no assistente (tentativaEmCurso): continua oferecida, a criação dá 409 PROCESSO_EM_CURSO.
        comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.EM_SUBMISSAO));
        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.prorrogacao());
    }

    @Test
    void posterioresAoFinalContamAUltimaOcorrenciaDaTentativaCorrente() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        Processo daTentativaAnterior = processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.ENVIADO_DGT, 1);
        ReflectionTestUtils.setField(daTentativaAnterior, "tentativa", 0);

        comProcessos(daTentativaAnterior);
        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.prorrogacao());

        comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.ENVIADO_DGT, 2),
                processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.ANULADO, 1));
        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).isEmpty();
    }

    @Test
    void posterioresAoFinalOferecemOutraVezORepetivelComOAnteriorPublicadoENaoONaoRepetivel() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, true);
        comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.PUBLICADO),
                processoEm(mp.dinamica(), mp.repetivel(), EstadoProcesso.PUBLICADO));

        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.repetivel());

        comProcessos(processoEm(mp.dinamica(), mp.repetivel(), EstadoProcesso.PUBLICADO, 1),
                processoEm(mp.dinamica(), mp.repetivel(), EstadoProcesso.ENVIADO_DGT, 2));
        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica())).containsExactly(mp.prorrogacao());
    }

    @Test
    void posterioresAoFinalVazioSemOpcionaisDepoisDoFinal() {
        InstrumentoDinamicaConfig config = dinamicaConfigGenerico();
        ReflectionTestUtils.setField(config, "id", 60L);
        InstrumentoProcedimentoConfig decisao = procedimentoComId(config, 601L, 1, true);
        InstrumentoProcedimentoConfig opcionalAntes = procedimentoComId(config, 602L, 2, false);
        InstrumentoProcedimentoConfig alteracao = procedimentoComId(config, 603L, 3, true);
        InstrumentoProcedimentoConfig pontual = procedimentoComId(config, 604L, 101, false);
        pontual.setAtoPontual(true);
        Dinamica alteracaoPdm = new Dinamica(instrumento, config, "Alteração do PDM", utilizadorA);
        ReflectionTestUtils.setField(alteracaoPdm, "id", UUID.randomUUID());
        alteracaoPdm.setEstado(EstadoDinamica.TERMINADA);
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(anyCollection()))
                .thenReturn(List.of(decisao, opcionalAntes, alteracao, pontual));

        assertThat(autorizacaoService.procedimentosPosterioresAoFinal(alteracaoPdm)).isEmpty();
        verifyNoInteractions(processoRepository);
    }

    @Test
    void posterioresAoFinalPorDinamicaLeemTudoNumaConsultaDeConfiguracoesEOutraDeProcessos() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        Dinamica outra = new Dinamica(instrumento, mp.config(), "Outras medidas preventivas", utilizadorA);
        ReflectionTestUtils.setField(outra, "id", UUID.randomUUID());
        outra.setEstado(EstadoDinamica.TERMINADA);
        Dinamica emCurso = new Dinamica(instrumento, mp.config(), "Em curso", utilizadorA);
        ReflectionTestUtils.setField(emCurso, "id", UUID.randomUUID());
        comProcessos(processoEm(outra, mp.prorrogacao(), EstadoProcesso.PUBLICADO));

        Map<UUID, List<InstrumentoProcedimentoConfig>> resultado = autorizacaoService.procedimentosPosterioresAoFinalPorDinamica(
                List.of(mp.dinamica(), outra, emCurso));

        assertThat(resultado).containsOnlyKeys(mp.dinamica().getId());
        assertThat(resultado.get(mp.dinamica().getId())).containsExactly(mp.prorrogacao());
        verify(instrumentoProcedimentoConfigRepository).listarAtivosPorConfigs(anyCollection());
        verify(processoRepository).findByDinamicaInAndInstrumentoProcedimentoConfigIn(anyCollection(), anyCollection());
    }

    /** O que a criação pede para a Prorrogação passar: linha da Câmara, a Publicação publicada e elegível. */
    private ContextoUtilizador camaraQuePodeProrrogar(MedidasPreventivas mp) {
        ContextoUtilizador contexto = contextoComPermissao(entidadeA, mp.prorrogacao());
        for (InstrumentoProcedimentoConfig config : List.of(mp.publicacao(), mp.pontual())) {
            when(permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, config))
                    .thenReturn(true);
        }
        when(instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(mp.config(), 2))
                .thenReturn(List.of(mp.publicacao()));
        when(processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(mp.dinamica(),
                mp.dinamica().getTentativa(), mp.publicacao(), EstadoProcesso.PUBLICADO)).thenReturn(true);
        when(motorRegrasService.avaliarElegibilidadeProcedimento(any(), any())).thenReturn(true);
        return contexto;
    }

    @Test
    void numaDinamicaTerminadaCriaSeAProrrogacaoESoEla() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        ContextoUtilizador contexto = camaraQuePodeProrrogar(mp);
        comProcessos(processoEm(mp.dinamica(), mp.publicacao(), EstadoProcesso.PUBLICADO));

        assertThatCode(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, mp.dinamica(), mp.prorrogacao()))
                .doesNotThrowAnyException();
        for (InstrumentoProcedimentoConfig outro : List.of(mp.publicacao(), mp.pontual())) {
            assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, mp.dinamica(), outro))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Esta dinâmica está terminada.")
                    .hasFieldOrPropertyWithValue("codigo", "ESTADO_INVALIDO");
        }
    }

    @Test
    void numaDinamicaTerminadaAProrrogacaoJaEnviadaRecusaCom409EstadoInvalido() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        ContextoUtilizador contexto = camaraQuePodeProrrogar(mp);
        comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), EstadoProcesso.ENVIADO_DGT));

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(contexto, mp.dinamica(), mp.prorrogacao()))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void numaDinamicaTerminadaACamada3VemAntesDoEstado() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        ContextoUtilizador assembleia = contextoExterno(UUID.randomUUID(), entidadeA, "ENTIDADE", TipoEntidade.AM);

        assertThatThrownBy(() -> autorizacaoService.verificarPossibilidadeDeAgir(assembleia, mp.dinamica(), mp.prorrogacao()))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(instrumentoProcedimentoConfigRepository, never()).listarAtivosPorConfigs(anyCollection());
    }

    @Test
    void numaDinamicaTerminadaOsPosterioresEACriacaoDaoAMesmaResposta() {
        MedidasPreventivas mp = medidasPreventivas(EstadoDinamica.TERMINADA, false);
        ContextoUtilizador contexto = camaraQuePodeProrrogar(mp);

        for (EstadoProcesso estadoDaProrrogacao : List.of(EstadoProcesso.ANULADO, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.PUBLICADO)) {
            comProcessos(processoEm(mp.dinamica(), mp.prorrogacao(), estadoDaProrrogacao));
            List<InstrumentoProcedimentoConfig> posteriores = autorizacaoService.procedimentosPosterioresAoFinal(mp.dinamica());
            for (InstrumentoProcedimentoConfig candidato : List.of(mp.publicacao(), mp.prorrogacao(), mp.pontual())) {
                boolean podeCriar;
                try {
                    autorizacaoService.verificarPossibilidadeDeAgir(contexto, mp.dinamica(), candidato);
                    podeCriar = true;
                } catch (EstadoInvalidoException e) {
                    podeCriar = false;
                }
                assertThat(podeCriar).as(estadoDaProrrogacao + " / " + candidato.getId()).isEqualTo(posteriores.contains(candidato));
            }
        }
    }
}
