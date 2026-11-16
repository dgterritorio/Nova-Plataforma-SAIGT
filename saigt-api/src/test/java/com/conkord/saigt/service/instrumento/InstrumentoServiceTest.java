package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.dto.instrumento.CriarInstrumentoRequest;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.IgtSnit;
import com.conkord.saigt.integracao.snit.MunicipioIgtSnit;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.AmbitoTerritorial;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class InstrumentoServiceTest {

    private final InstrumentoRepository instrumentoRepository = mock(InstrumentoRepository.class);
    private final TipoInstrumentoRepository tipoInstrumentoRepository = mock(TipoInstrumentoRepository.class);
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository =
            mock(InstrumentoDinamicaConfigRepository.class);
    private final UtilizadorRepository utilizadorRepository = mock(UtilizadorRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final InstrumentoSnitService instrumentoSnitService = mock(InstrumentoSnitService.class);
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository = mock(InstrumentoConcelhoRepository.class);
    private final TerritorioLocalService territorioLocalService = mock(TerritorioLocalService.class);
    private final SnitReferenciaRepository snitReferenciaRepository = mock(SnitReferenciaRepository.class);
    private final ListaValoresRepository listaValoresRepository = mock(ListaValoresRepository.class);

    private static final List<String> SINTRA = List.of("1111");

    private final TransacoesRegistadas transacoes = new TransacoesRegistadas();

    private final InstrumentoService instrumentoService = new InstrumentoService(instrumentoRepository,
            tipoInstrumentoRepository, instrumentoDinamicaConfigRepository, utilizadorRepository,
            autorizacaoService, contextoUtilizadorResolver, auditoriaService, instrumentoSnitService,
            instrumentoConcelhoRepository, territorioLocalService, snitReferenciaRepository, listaValoresRepository,
            transacoes);

    private TipoInstrumento pdm;
    private TipoInstrumento pdim;
    private TipoInstrumento prot;
    private Entidade cmLisboa;
    private Utilizador ferreira;
    private ContextoUtilizador contextoExterno;
    private ContextoUtilizador contextoInterno;

    @BeforeEach
    void setUp() {
        pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        ReflectionTestUtils.setField(pdm, "id", 1L);
        pdm.setAmbitoTerritorial(AmbitoTerritorial.MUNICIPAL);
        pdim = new TipoInstrumento("PDIM", "Plano Diretor Intermunicipal", "PLANO");
        ReflectionTestUtils.setField(pdim, "id", 2L);
        pdim.setAmbitoTerritorial(AmbitoTerritorial.INTERMUNICIPAL);
        prot = new TipoInstrumento("PROT", "Programa Regional de Ordenamento do Território", "PROGRAMA");
        ReflectionTestUtils.setField(prot, "id", 3L);
        cmLisboa = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");
        ReflectionTestUtils.setField(cmLisboa, "id", 1L);
        ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        UUID utilizadorId = UUID.randomUUID();
        ReflectionTestUtils.setField(ferreira, "id", utilizadorId);
        contextoExterno = new ContextoUtilizador(utilizadorId, "INCM", null, "501234567", "ENTIDADE", null, null, null, null, TipoEntidade.CM, 1L);
        contextoInterno = new ContextoUtilizador(utilizadorId, "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null);

        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoExterno);
        when(tipoInstrumentoRepository.findById(1L)).thenReturn(Optional.of(pdm));
        when(tipoInstrumentoRepository.findById(2L)).thenReturn(Optional.of(pdim));
        when(tipoInstrumentoRepository.findById(3L)).thenReturn(Optional.of(prot));
        when(utilizadorRepository.findById(utilizadorId)).thenReturn(Optional.of(ferreira));
        when(territorioLocalService.concelhos()).thenReturn(List.of(
                new ItemTerritorio("1105", "Cascais"), new ItemTerritorio("1111", "Sintra")));
    }

    /** `saveAndFlush` mocado não gera o UUID que a BD geraria (GenerationType.UUID) — atribuído aqui como faria o Hibernate. */
    private Instrumento comIdGerado(Instrumento instrumento) {
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        return instrumento;
    }

    @Test
    void deveCriarInstrumentoComNumeroDaSequenciaEmTextoEUtilizadorDoTokenComoResponsavelECriadoPor() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(42L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));

        assertThat(criado.getNumero()).isEqualTo("42");
        assertThat(criado.getDesignacao()).isEqualTo("PDM de Lisboa");
        assertThat(criado.getResponsavel()).isEqualTo(ferreira);
        assertThat(criado.getCriadoPor()).isEqualTo(ferreira);
        verify(autorizacaoService).verificarPossibilidadeDeCriar(contextoExterno, pdm);
    }

    @Test
    void deveCriarInstrumentoDeIgtDoSnitComADesignacaoDoSnitEOIdentificadorDoIgt() {
        IgtSnit igt = new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force");
        when(instrumentoSnitService.obterParaAdocao("1001", pdm)).thenReturn(igt);
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "Designação do pedido", "1001"));

        assertThat(criado.getDesignacao()).isEqualTo("PDM de Sintra (SNIT)");
        assertThat(criado.getIdIgtSnit()).isEqualTo("1001");
        assertThat(criado.getTipoInstrumento()).isEqualTo(pdm);
        assertThat(criado.isEmVigor()).isTrue();
    }

    @Test
    void deveConsultarOSnitSemTransacaoAbertaEGravarNumaTransacao() {
        List<Boolean> transacaoAbertaNaConsulta = new java.util.ArrayList<>();
        List<Boolean> transacaoAbertaNaGravacao = new java.util.ArrayList<>();
        when(instrumentoSnitService.obterParaAdocao("1001", pdm)).thenAnswer(inv -> {
            transacaoAbertaNaConsulta.add(transacoes.aberta);
            return new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force");
        });
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> {
            transacaoAbertaNaGravacao.add(transacoes.aberta);
            return comIdGerado(inv.getArgument(0));
        });

        instrumentoService.criar(new CriarInstrumentoRequest(1L, "Designação do pedido", "1001"));

        assertThat(transacaoAbertaNaConsulta).containsExactly(false);
        assertThat(transacaoAbertaNaGravacao).containsExactly(true);
        assertThat(transacoes.confirmadas).isEqualTo(1);
    }

    @Test
    void deveDesfazerAGravacaoQuandoOsConcelhosDoIgtFalham() {
        when(instrumentoSnitService.obterParaAdocao("1001", pdm)).thenReturn(igtComConcelhos("1111"));
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> comIdGerado(inv.getArgument(0)));
        when(instrumentoConcelhoRepository.saveAll(any())).thenThrow(new IllegalStateException("falha"));

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "Designação do pedido", "1001")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transacoes.desfeitas).isEqualTo(1);
        assertThat(transacoes.confirmadas).isZero();
    }

    /** Regista se há transação aberta, sem base de dados. */
    private static final class TransacoesRegistadas implements org.springframework.transaction.PlatformTransactionManager {
        private boolean aberta;
        private int confirmadas;
        private int desfeitas;

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definicao) {
            aberta = true;
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus estado) {
            aberta = false;
            confirmadas++;
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus estado) {
            aberta = false;
            desfeitas++;
        }
    }

    private IgtSnit igtComConcelhos(String... dtcc) {
        List<MunicipioIgtSnit> concelhos = new java.util.ArrayList<>();
        for (String codigo : dtcc) {
            concelhos.add(new MunicipioIgtSnit(codigo, "Concelho " + codigo));
        }
        return new IgtSnit(1001L, "PDM", "PDM do SNIT", "in_force", concelhos);
    }

    @SuppressWarnings("unchecked")
    private List<String> adotarEObterConcelhosGravados(IgtSnit igt) {
        when(instrumentoSnitService.obterParaAdocao("1001", pdm)).thenReturn(igt);
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM", "1001"));

        ArgumentCaptor<List<InstrumentoConcelho>> gravados = ArgumentCaptor.forClass(List.class);
        verify(instrumentoConcelhoRepository).saveAll(gravados.capture());
        return gravados.getValue().stream().map(InstrumentoConcelho::getMunicipioCodigo).toList();
    }

    @Test
    void deveGravarUmaLinhaPorConcelhoDoIgtDoSnit() {
        assertThat(adotarEObterConcelhosGravados(igtComConcelhos("1111"))).containsExactly("1111");
    }

    @Test
    void deveGravarOsDoisConcelhosDeUmIgtIntermunicipalSemRepetidos() {
        assertThat(adotarEObterConcelhosGravados(igtComConcelhos("1111", "1105", " 1111 ")))
                .containsExactly("1111", "1105");
    }

    @Test
    void naoDeveGravarConcelhosQuandoOIgtNaoTemMunicipalitiesOuTemListaVazia() {
        assertThat(adotarEObterConcelhosGravados(new IgtSnit(1001L, "PDM", "PDM do SNIT", "in_force", null))).isEmpty();
    }

    @Test
    void deveIgnorarDtccForaDoFormatoGravarOsValidosERegistarAvisoComOIdIgt(CapturedOutput output) {
        assertThat(adotarEObterConcelhosGravados(igtComConcelhos("1111", "11A1", "111", "", "11111")))
                .containsExactly("1111");
        assertThat(output).contains("fora do formato", "idIgtSnit=1001", "dtcc=11A1");
    }

    @Test
    void deveIgnorarMunicipioSemDtcc() {
        IgtSnit igt = new IgtSnit(1001L, "PDM", "PDM do SNIT", "in_force",
                java.util.Arrays.asList(new MunicipioIgtSnit(null, "Sem código"), new MunicipioIgtSnit("1105", "Cascais")));
        assertThat(adotarEObterConcelhosGravados(igt)).containsExactly("1105");
    }

    @Test
    void deveLevarOsDtccGravadosNaAuditoriaDeCriacao() {
        adotarEObterConcelhosGravados(igtComConcelhos("1111", "1105"));

        verify(auditoriaService).registar(eq("INSTRUMENTO"), any(), eq("CRIACAO"), any(), any(), any(),
                eq(Map.of("dtcc", List.of("1111", "1105"))));
    }

    @Test
    void deveAuditarOsConcelhosIndicadosDeUmInstrumentoQueNaoVemDoSnit() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(8L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));

        verifyNoInteractions(instrumentoSnitService);
        verify(auditoriaService).registar(any(), any(), eq("CRIACAO"), any(), any(), any(), eq(Map.of("dtcc", SINTRA)));
    }

    @Test
    void devePropagarAFalhaAoGravarOsConcelhosParaOInstrumentoNaoFicarCriado() {
        when(instrumentoSnitService.obterParaAdocao("1001", pdm)).thenReturn(igtComConcelhos("1111"));
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));
        RuntimeException falha = new RuntimeException("falha ao gravar");
        when(instrumentoConcelhoRepository.saveAll(any())).thenThrow(falha);

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM", "1001")))
                .isSameAs(falha);
        verify(auditoriaService, never()).registar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveDar409QuandoOIndiceUnicoDoIgtRejeitaUmPedidoEmSimultaneo() {
        when(instrumentoSnitService.obterParaAdocao("1001", pdm))
                .thenReturn(new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force"));
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenThrow(violacaoDaRestricao("uq_instrumento_id_igt_snit"));

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM", "1001")))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessage("O IGT já existe na SAIGT.");
        verify(auditoriaService, never()).registar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void naoDeveTraduzirEm409AViolacaoDeOutraRestricao() {
        when(instrumentoSnitService.obterParaAdocao("1001", pdm))
                .thenReturn(new IgtSnit(1001L, "PDM", "PDM de Sintra (SNIT)", "in_force"));
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        DataIntegrityViolationException violacao = violacaoDaRestricao("uq_instrumento_numero");
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenThrow(violacao);

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM", "1001")))
                .isSameAs(violacao);
    }

    @Test
    void naoDeveTraduzirEm409AViolacaoDoIndiceQuandoOPedidoNaoTraiIgtDoSnit() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(7L);
        DataIntegrityViolationException violacao = violacaoDaRestricao("uq_instrumento_id_igt_snit");
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenThrow(violacao);

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA)))
                .isSameAs(violacao);
    }

    private static DataIntegrityViolationException violacaoDaRestricao(String restricao) {
        return new DataIntegrityViolationException("violação",
                new ConstraintViolationException("violação", new SQLException("duplicate key", "23505"), restricao));
    }

    @Test
    void deveAutorizarAntesDeConsultarOSnit() {
        doThrow(new AcaoNaoAutorizadaException("Sem permissão."))
                .when(autorizacaoService).verificarPossibilidadeDeCriar(contextoExterno, pdm);

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM", "1001")))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verifyNoInteractions(instrumentoSnitService);
    }

    @Test
    void naoDeveConsultarOSnitQuandoOPedidoNaoTemIdIgtSnit() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(8L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));

        assertThat(criado.getIdIgtSnit()).isNull();
        assertThat(criado.isEmVigor()).isFalse();
        verifyNoInteractions(instrumentoSnitService);
    }

    @Test
    void deveObterNumeroSequencialDoRepositorioACadaCriacaoNuncaCalculandoLocalmente() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(1L).thenReturn(2L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento primeiro = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));
        Instrumento segundo = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Cascais", SINTRA));

        assertThat(primeiro.getNumero()).isEqualTo("1");
        assertThat(segundo.getNumero()).isEqualTo("2");
    }

    @Test
    void deveAceitarDoisInstrumentosComAMesmaDesignacaoComNumerosDiferentes() {
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(1L).thenReturn(2L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento primeiro = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));
        Instrumento segundo = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));

        assertThat(primeiro.getDesignacao()).isEqualTo(segundo.getDesignacao());
        assertThat(primeiro.getNumero()).isNotEqualTo(segundo.getNumero());
    }

    @Test
    void deveDevolver403QuandoAtorNaoTemPermissaoAcaoParaOTipoDeInstrumento() {
        doThrow(new AcaoNaoAutorizadaException("Ator não autorizado a criar Instrumentos deste tipo."))
                .when(autorizacaoService).verificarPossibilidadeDeCriar(contextoExterno, pdm);
        CriarInstrumentoRequest request = new CriarInstrumentoRequest(1L, "2026/PDM/001", SINTRA);

        assertThatThrownBy(() -> instrumentoService.criar(request))
                .isInstanceOf(AcaoNaoAutorizadaException.class);

        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver404QuandoTipoInstrumentoNaoExiste() {
        when(tipoInstrumentoRepository.findById(99L)).thenReturn(Optional.empty());
        CriarInstrumentoRequest request = new CriarInstrumentoRequest(99L, "2026/PDM/001", SINTRA);

        assertThatThrownBy(() -> instrumentoService.criar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(autorizacaoService, never()).verificarPossibilidadeDeCriar(any(), any());
    }

    @Test
    void deveCriarInstrumentoSemEntidadeEAuditarSobAEntidadeDaSessao() {
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "2026/PDM/001", SINTRA));

        assertThat(Arrays.stream(Instrumento.class.getDeclaredFields()).map(Field::getName)).doesNotContain("entidade");
        verify(auditoriaService).registar(eq("INSTRUMENTO"), eq(criado.getId().toString()), eq("CRIACAO"), eq(ferreira.getId()),
                eq(cmLisboa.getId()), any(), any());
    }

    @Test
    void deveAuditarACriacaoSemEntidadeQuandoOUtilizadorEInterno() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoInterno);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "2026/PDM/001", SINTRA));

        verify(auditoriaService).registar(eq("INSTRUMENTO"), eq(criado.getId().toString()), eq("CRIACAO"), eq(ferreira.getId()),
                isNull(), any(), any());
    }

    @Test
    void deveDevolverOInstrumentoDeQualquerEntidadeSemVerificarVisibilidade() {
        UUID id = UUID.randomUUID();
        Instrumento instrumento = new Instrumento(pdm, "1", "2026/PDM/001", ferreira, ferreira);
        when(instrumentoRepository.findById(id)).thenReturn(Optional.of(instrumento));

        assertThat(instrumentoService.obter(id)).isSameAs(instrumento);
        verifyNoInteractions(autorizacaoService);
    }

    @Test
    void deveListarTodosOsInstrumentosParaQualquerSessao() {
        Pageable pageable = PageRequest.of(0, 20);
        Instrumento instrumento = new Instrumento(pdm, "1", "2026/PDM/001", ferreira, ferreira);
        when(instrumentoRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(instrumento)));

        assertThat(instrumentoService.listar(pageable).getContent()).containsExactly(instrumento);

        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoInterno);
        assertThat(instrumentoService.listar(pageable).getContent()).containsExactly(instrumento);
    }

    @Test
    void deveDevolver404NaConsultaQuandoInstrumentoNaoExiste() {
        UUID id = UUID.randomUUID();
        when(instrumentoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentoService.obter(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarTiposDeveDevolverApenasOsTiposComDinamicasConfiguradas() {
        when(instrumentoDinamicaConfigRepository.listarTiposInstrumentoConfigurados()).thenReturn(List.of(pdm));

        assertThat(instrumentoService.listarTipos()).containsExactly(pdm);
        verify(tipoInstrumentoRepository, never()).findByAtivoTrueOrderByCodigoAsc();
    }

    private Instrumento instrumentoGuardado(boolean emVigor) {
        Instrumento instrumento = comIdGerado(new Instrumento(pdm, "1", "2026/PDM/001", ferreira, ferreira));
        instrumento.setEmVigor(emVigor);
        when(instrumentoRepository.saveAndFlush(instrumento)).thenReturn(instrumento);
        return instrumento;
    }

    private Dinamica dinamicaTerminada(String codigoTipo, Instrumento instrumento) {
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(pdm, new TipoDinamica(codigoTipo, codigoTipo));
        Dinamica dinamica = new Dinamica(instrumento, config, "Dinâmica", ferreira);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        dinamica.setEstado(EstadoDinamica.TERMINADA);
        return dinamica;
    }

    private static Map<String, Object> dadosEmVigor(boolean antes, boolean depois, Dinamica dinamica) {
        return Map.of("emVigorAntes", antes, "emVigorDepois", depois, "dinamicaId", dinamica.getId());
    }

    @Test
    void devePorEmVigorQuandoTerminaElaboracaoRevisaoOuDelimitacaoEAuditar() {
        for (String codigo : List.of("ELABORACAO", "REVISAO", "DELIMITACAO")) {
            Instrumento instrumento = instrumentoGuardado(false);
            Dinamica dinamica = dinamicaTerminada(codigo, instrumento);

            instrumentoService.atualizarEmVigorPorDinamica(dinamica, ferreira.getId());

            assertThat(instrumento.isEmVigor()).as(codigo).isTrue();
            verify(auditoriaService).registar("INSTRUMENTO", instrumento.getId().toString(), "ATUALIZACAO_EM_VIGOR",
                    ferreira.getId(), null, null, dadosEmVigor(false, true, dinamica));
        }
    }

    @Test
    void deveRetirarEmVigorQuandoTerminaRevogacao() {
        Instrumento instrumento = instrumentoGuardado(true);
        Dinamica dinamica = dinamicaTerminada("REVOGACAO", instrumento);

        instrumentoService.atualizarEmVigorPorDinamica(dinamica, ferreira.getId());

        assertThat(instrumento.isEmVigor()).isFalse();
        verify(auditoriaService).registar("INSTRUMENTO", instrumento.getId().toString(), "ATUALIZACAO_EM_VIGOR",
                ferreira.getId(), null, null, dadosEmVigor(true, false, dinamica));
    }

    @Test
    void devePorEmVigorOutraVezQuandoUmaDelimitacaoTerminaDepoisDeUmaRevogacao() {
        Instrumento instrumento = instrumentoGuardado(false);
        Dinamica delimitacao = dinamicaTerminada("DELIMITACAO", instrumento);

        instrumentoService.atualizarEmVigorPorDinamica(delimitacao, ferreira.getId());

        assertThat(instrumento.isEmVigor()).isTrue();
    }

    @Test
    void naoDeveAlterarNemAuditarQuandoTerminaOutroTipoDeDinamica() {
        Instrumento instrumento = instrumentoGuardado(true);

        instrumentoService.atualizarEmVigorPorDinamica(dinamicaTerminada("ALTERACAO", instrumento), ferreira.getId());

        assertThat(instrumento.isEmVigor()).isTrue();
        verify(instrumentoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void deveSerIdempotenteQuandoOValorJaEOPretendido() {
        Instrumento emVigor = instrumentoGuardado(true);
        Instrumento semVigor = instrumentoGuardado(false);

        instrumentoService.atualizarEmVigorPorDinamica(dinamicaTerminada("ELABORACAO", emVigor), ferreira.getId());
        instrumentoService.atualizarEmVigorPorDinamica(dinamicaTerminada("REVOGACAO", semVigor), ferreira.getId());

        verify(instrumentoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void deveAuditarComoAcaoDoSistemaQuandoNaoHaUtilizador() {
        Instrumento instrumento = instrumentoGuardado(false);
        Dinamica dinamica = dinamicaTerminada("ELABORACAO", instrumento);

        instrumentoService.atualizarEmVigorPorDinamica(dinamica, null);

        assertThat(instrumento.isEmVigor()).isTrue();
        verify(auditoriaService).registarSistema("INSTRUMENTO", instrumento.getId().toString(), "ATUALIZACAO_EM_VIGOR",
                dadosEmVigor(false, true, dinamica));
        verify(auditoriaService, never()).registar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void naoDeveFazerNadaQuandoADinamicaNaoAbrangeInstrumentos() {
        Instrumento instrumento = instrumentoGuardado(false);
        Dinamica dinamica = dinamicaTerminada("ELABORACAO", instrumento);
        dinamica.getInstrumentos().clear();

        instrumentoService.atualizarEmVigorPorDinamica(dinamica, ferreira.getId());

        assertThat(instrumento.isEmVigor()).isFalse();
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveGuardarOsConcelhosIndicadosAoCriarUmInstrumentoNovo() {
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(
                new CriarInstrumentoRequest(2L, " PDIM de Lisboa ", List.of("1111", "1105")));

        verify(instrumentoConcelhoRepository).existeDesignacaoNosConcelhos(2L, "PDIM de Lisboa", List.of("1111", "1105"));
        ArgumentCaptor<List<InstrumentoConcelho>> concelhos = ArgumentCaptor.forClass(List.class);
        verify(instrumentoConcelhoRepository).saveAll(concelhos.capture());
        assertThat(concelhos.getValue()).extracting(InstrumentoConcelho::getMunicipioCodigo).containsExactly("1111", "1105");
        assertThat(concelhos.getValue()).allSatisfy(concelho -> assertThat(concelho.getInstrumento()).isSameAs(criado));
    }

    @Test
    void deveRecusarUmInstrumentoNovoSemConcelhosSemGravar() {
        for (List<String> concelhos : Arrays.asList(null, List.<String>of())) {
            assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", concelhos)))
                    .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                            .extracting(ValidacaoException.CampoErro::path).containsExactly("concelhos"));
        }
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarUmConcelhoRepetidoSemGravar() {
        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(2L, "PDIM de Lisboa", List.of("1111", "1105", "1111"))))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("concelhos", "Concelho repetido.")));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarMaisDoQueUmConcelhoNoTipoMunicipalSemGravar() {
        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", List.of("1111", "1105"))))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("concelhos"));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarUmSoConcelhoNoTipoIntermunicipalSemGravar() {
        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(2L, "PDIM de Lisboa", SINTRA)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("concelhos", "Indique o concelho ou os concelhos do instrumento.")));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveAceitarQualquerNumeroDeConcelhosNoTipoSemAmbitoDefinido() {
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        instrumentoService.criar(new CriarInstrumentoRequest(3L, "PROT Centro", List.of("1111", "1105")));
        instrumentoService.criar(new CriarInstrumentoRequest(3L, "PROT Sul", SINTRA));

        verify(instrumentoRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Instrumento.class));
    }

    @Test
    void deveRecusarUmConcelhoQueNaoEstaNaListaDaElaboracao() {
        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", List.of("9999"))))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("concelhos", "Concelho inexistente.")));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveValidarOConcelhoPelaReplicaDoSnitQuandoEstaTemConcelhos() {
        when(snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(true);
        when(snitReferenciaRepository.findByListaAndCodigoIn(ListaReferenciaSnit.MUNICIPALITIES, List.of("0412")))
                .thenReturn(List.of(municipio("0412", "VINHAIS", true)));
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class)))
                .thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        // Vinhais não tem entidade CM na lista local, mas está na réplica do SNIT.
        instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Vinhais", List.of("0412")));

        verify(instrumentoRepository).saveAndFlush(any(Instrumento.class));
        verify(territorioLocalService, never()).concelhos();
    }

    @Test
    void deveRecusarUmConcelhoQueNaoEstaNaReplicaDoSnitMesmoQueEstejaNaListaLocal() {
        when(snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(true);
        when(snitReferenciaRepository.findByListaAndCodigoIn(ListaReferenciaSnit.MUNICIPALITIES, SINTRA)).thenReturn(List.of());

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Sintra", SINTRA)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("concelhos", "Concelho inexistente.")));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveRecusarUmConcelhoInativoNaReplicaDoSnit() {
        when(snitReferenciaRepository.existsByLista(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(true);
        when(snitReferenciaRepository.findByListaAndCodigoIn(ListaReferenciaSnit.MUNICIPALITIES, SINTRA))
                .thenReturn(List.of(municipio("1111", "SINTRA", false)));

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Sintra", SINTRA)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("concelhos", "Concelho inexistente.")));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    private static SnitReferencia municipio(String dtcc, String nome, boolean ativo) {
        SnitReferencia municipio = new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\"}", java.time.Instant.now());
        municipio.setAtivo(ativo);
        return municipio;
    }

    @Test
    void deveDar409QuandoJaExisteUmInstrumentoDoMesmoTipoComAMesmaDesignacaoNumDosConcelhos() {
        when(instrumentoConcelhoRepository.existeDesignacaoNosConcelhos(1L, "PDM de Lisboa", SINTRA)).thenReturn(true);

        assertThatThrownBy(() -> instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA)))
                .isInstanceOfSatisfying(ReferenciaDuplicadaException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("DESIGNACAO_DUPLICADA"));
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    /** Código sem significado de propósito: a regra decide pela `categoria` do tipo, nunca pelo código. */
    private TipoInstrumento tipoServidao(long id, String codigo) {
        TipoInstrumento servidao = new TipoInstrumento(codigo, "Servidão de teste " + codigo, "SRUP");
        ReflectionTestUtils.setField(servidao, "id", id);
        when(tipoInstrumentoRepository.findById(id)).thenReturn(Optional.of(servidao));
        return servidao;
    }

    private Instrumento instrumentoExistente(TipoInstrumento tipo, String designacao) {
        Instrumento existente = comIdGerado(new Instrumento(tipo, "1", designacao, ferreira, ferreira));
        when(instrumentoRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        return existente;
    }

    @Test
    void deveGuardarAServidaoDeOrigemEAuditarOSeuIdQuandoOTipoEAOrigemSaoServidoes() {
        TipoInstrumento servidao = tipoServidao(40L, "ZXQ-1");
        Instrumento origem = instrumentoExistente(tipoServidao(41L, "ZXQ-2"), "Imóvel classificado");
        when(instrumentoRepository.proximoNumeroSequencial()).thenReturn(9L);
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(
                new CriarInstrumentoRequest(servidao.getId(), "Zona de proteção", null, SINTRA, null, origem.getId()));

        assertThat(criado.getInstrumentoOrigem()).isSameAs(origem);
        verify(auditoriaService).registar(eq("INSTRUMENTO"), any(), eq("CRIACAO"), any(), any(), any(),
                eq(Map.of("dtcc", SINTRA, "instrumentoOrigemId", origem.getId().toString())));
    }

    @Test
    void deveRecusarAServidaoDeOrigemNumTipoQueNaoEServidaoSemProcurarAOrigem() {
        UUID origemId = UUID.randomUUID();

        assertThatThrownBy(() -> instrumentoService.criar(
                new CriarInstrumentoRequest(1L, "PDM de Lisboa", null, SINTRA, null, origemId)))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> assertThat(e.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("instrumentoOrigemId", "Só uma servidão pode indicar uma servidão de origem.")));
        verify(instrumentoRepository, never()).findById(origemId);
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDarAMesmaRespostaParaUmaOrigemInexistenteEParaUmaQueNaoEServidao() {
        TipoInstrumento servidao = tipoServidao(42L, "ZXQ-3");
        UUID inexistente = UUID.randomUUID();
        when(instrumentoRepository.findById(inexistente)).thenReturn(Optional.empty());
        Instrumento plano = instrumentoExistente(pdm, "PDM de Sintra");

        for (UUID origemId : List.of(inexistente, plano.getId())) {
            assertThatThrownBy(() -> instrumentoService.criar(
                    new CriarInstrumentoRequest(servidao.getId(), "Zona de proteção", null, SINTRA, null, origemId)))
                    .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                        assertThat(e.getMessage()).isEqualTo("Payload inválido.");
                        assertThat(e.getCampos())
                                .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                                .containsExactly(tuple("instrumentoOrigemId", "Servidão de origem inválida."));
                    });
        }
        verify(instrumentoRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveDevolver403SemValidarAServidaoDeOrigem() {
        TipoInstrumento servidao = tipoServidao(43L, "ZXQ-4");
        doThrow(new AcaoNaoAutorizadaException("Ator não autorizado a criar Instrumentos deste tipo."))
                .when(autorizacaoService).verificarPossibilidadeDeCriar(contextoExterno, servidao);
        UUID origemId = UUID.randomUUID();

        assertThatThrownBy(() -> instrumentoService.criar(
                new CriarInstrumentoRequest(servidao.getId(), "Zona de proteção", null, SINTRA, null, origemId)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(instrumentoRepository, never()).findById(any());
    }

    @Test
    void naoDeveProcurarOrigemNemMudarAAuditoriaQuandoOPedidoNaoTrazServidaoDeOrigem() {
        when(instrumentoRepository.saveAndFlush(any(Instrumento.class))).thenAnswer(inv -> comIdGerado(inv.getArgument(0)));

        Instrumento criado = instrumentoService.criar(new CriarInstrumentoRequest(1L, "PDM de Lisboa", SINTRA));

        assertThat(criado.getInstrumentoOrigem()).isNull();
        verify(instrumentoRepository, never()).findById(any());
        verify(auditoriaService).registar(any(), any(), eq("CRIACAO"), any(), any(), any(), eq(Map.of("dtcc", SINTRA)));
    }
}
