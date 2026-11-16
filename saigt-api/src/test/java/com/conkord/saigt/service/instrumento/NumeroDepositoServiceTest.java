package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.MunicipioCodigoDeposito;
import com.conkord.saigt.persistence.core.MunicipioCodigoDepositoRepository;
import com.conkord.saigt.persistence.core.SerieNumeroDeposito;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.SequenciaNumeroDepositoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NumeroDepositoServiceTest {

    private static final UUID INSTRUMENTO_ID = UUID.randomUUID();
    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    private final InstrumentoProcedimentoConfigRepository configRepository = mock(InstrumentoProcedimentoConfigRepository.class);
    private final InstrumentoConcelhoRepository concelhoRepository = mock(InstrumentoConcelhoRepository.class);
    private final MunicipioCodigoDepositoRepository municipioRepository = mock(MunicipioCodigoDepositoRepository.class);
    private final SequenciaNumeroDepositoRepository sequenciaRepository = mock(SequenciaNumeroDepositoRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final NumeroDepositoService service = new NumeroDepositoService(configRepository, concelhoRepository,
            municipioRepository, sequenciaRepository, auditoriaService, RELOGIO);

    private final Processo processo = mock(Processo.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final TipoDinamica tipoDinamica = mock(TipoDinamica.class);
    private final InstrumentoDinamicaConfig dinamicaConfig = mock(InstrumentoDinamicaConfig.class);
    private final InstrumentoProcedimentoConfig procedimentoFinal = mock(InstrumentoProcedimentoConfig.class);

    @BeforeEach
    void processoDoProcedimentoFinalDeUmPdmDeSintra() {
        Instrumento instrumento = mock(Instrumento.class);
        Dinamica dinamica = mock(Dinamica.class);
        when(instrumento.getId()).thenReturn(INSTRUMENTO_ID);
        when(instrumento.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(tipoInstrumento.getCodigo()).thenReturn("PDM");
        when(tipoInstrumento.getSerieNumeroDeposito()).thenReturn(SerieNumeroDeposito.GERAL);
        when(tipoDinamica.getCodigoIdus()).thenReturn("02");
        when(dinamicaConfig.getTipoDinamica()).thenReturn(tipoDinamica);
        when(dinamica.getInstrumento()).thenReturn(instrumento);
        when(dinamica.instrumentoDeInicio()).thenReturn(Optional.of(instrumento));
        when(dinamicaConfig.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(dinamicaConfig);
        when(procedimentoFinal.getId()).thenReturn(211L);
        when(processo.getId()).thenReturn(PROCESSO_ID);
        when(processo.getDinamica()).thenReturn(dinamica);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(procedimentoFinal);
        when(processo.getOrigemDados()).thenReturn("NATIVO");
        when(configRepository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(dinamicaConfig))
                .thenReturn(Optional.of(procedimentoFinal));
        when(sequenciaRepository.proximoSequencial(any(), anyInt())).thenReturn(1);
        concelhos("1111");
        municipios(new MunicipioCodigoDeposito("1111", "03", "03.11.11"));
    }

    private void concelhos(String... dtccs) {
        // Construídos antes do when(): o construtor lê o id do Instrumento, que é um mock.
        Instrumento instrumento = processo.getDinamica().getInstrumento();
        List<InstrumentoConcelho> lista = Arrays.stream(dtccs).map(dtcc -> new InstrumentoConcelho(instrumento, dtcc)).toList();
        when(concelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(INSTRUMENTO_ID)).thenReturn(lista);
    }

    private void municipios(MunicipioCodigoDeposito... municipios) {
        when(municipioRepository.findAllById(any())).thenReturn(List.of(municipios));
    }

    @Test
    void deveComporONumeroComOCodigoDoConcelhoESiglaDinamicaAnoESequencialERegistarAuditoria() {
        service.atribuirNaPublicacao(processo);

        verify(processo).setNumeroDeposito("03.11.11/PDM/02/2026/1");
        verify(sequenciaRepository).proximoSequencial(SerieNumeroDeposito.GERAL, 2026);
        verify(auditoriaService).registarSistema("PROCESSO", PROCESSO_ID.toString(), "ATRIBUIR_NUMERO_DEPOSITO",
                Map.of("numeroDeposito", "03.11.11/PDM/02/2026/1"));
    }

    @Test
    void deveUsarOSequencialSeguinteSemZerosAEsquerda() {
        when(sequenciaRepository.proximoSequencial(SerieNumeroDeposito.GERAL, 2026)).thenReturn(12);

        service.atribuirNaPublicacao(processo);

        verify(processo).setNumeroDeposito("03.11.11/PDM/02/2026/12");
    }

    @Test
    void deveTirarOSequencialDaSerieRenQuandoOTipoDeInstrumentoEDaRen() {
        when(tipoInstrumento.getCodigo()).thenReturn("REN");
        when(tipoInstrumento.getSerieNumeroDeposito()).thenReturn(SerieNumeroDeposito.REN);

        service.atribuirNaPublicacao(processo);

        verify(sequenciaRepository).proximoSequencial(SerieNumeroDeposito.REN, 2026);
        verify(processo).setNumeroDeposito("03.11.11/REN/02/2026/1");
    }

    @Test
    void deveNeutralizarODicoComVariosConcelhosDaMesmaRegiao() {
        concelhos("1105", "1111");
        municipios(new MunicipioCodigoDeposito("1105", "03", "03.11.05"),
                new MunicipioCodigoDeposito("1111", "03", "03.11.11"));

        service.atribuirNaPublicacao(processo);

        verify(processo).setNumeroDeposito("03.00.00/PDM/02/2026/1");
    }

    @Test
    void deveNeutralizarTambemARegiaoComConcelhosDeRegioesDiferentes() {
        concelhos("0501", "1111");
        municipios(new MunicipioCodigoDeposito("0501", "02", "02.05.01"),
                new MunicipioCodigoDeposito("1111", "03", "03.11.11"));

        service.atribuirNaPublicacao(processo);

        verify(processo).setNumeroDeposito("00.00.00/PDM/02/2026/1");
    }

    @Test
    void deveNeutralizarARegiaoQuandoOInstrumentoNaoTemConcelhos() {
        concelhos();

        service.atribuirNaPublicacao(processo);

        verify(processo).setNumeroDeposito("00.00.00/PDM/02/2026/1");
    }

    @Test
    void naoDeveAtribuirNemGastarSequencialForaDoProcedimentoFinal() {
        InstrumentoProcedimentoConfig decisaoInicio = mock(InstrumentoProcedimentoConfig.class);
        when(decisaoInicio.getId()).thenReturn(209L);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(decisaoInicio);

        service.atribuirNaPublicacao(processo);

        verifySemNumero();
    }

    @Test
    void naoDeveAtribuirAUmRegistoExterno() {
        when(processo.getOrigemDados()).thenReturn("REGISTO_EXTERNO");

        service.atribuirNaPublicacao(processo);

        verifySemNumero();
    }

    @Test
    void naoDeveMudarONumeroJaAtribuido() {
        when(processo.getNumeroDeposito()).thenReturn("03.11.11/PDM/02/2026/1");

        service.atribuirNaPublicacao(processo);

        verifySemNumero();
    }

    @Test
    void naoDeveAtribuirNemGastarSequencialSemOCodigoDoConcelho() {
        municipios();

        service.atribuirNaPublicacao(processo);

        verifySemNumero();
    }

    @Test
    void naoDeveAtribuirNemGastarSequencialSemOCodigoDaDinamica() {
        when(tipoDinamica.getCodigoIdus()).thenReturn(null);

        service.atribuirNaPublicacao(processo);

        verifySemNumero();
    }

    private void verifySemNumero() {
        verify(processo, never()).setNumeroDeposito(anyString());
        verifyNoInteractions(sequenciaRepository, auditoriaService);
    }
}
