package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.DinamicaAssociavelResponse;
import com.conkord.saigt.exception.DinamicaAssociadaInvalidaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaAssociavel;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DinamicaAssociadaServiceTest {

    private static final PageRequest PAGINA = PageRequest.of(0, 20);

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final DinamicaAssociadaService service = new DinamicaAssociadaService(dinamicaRepository);

    private TipoInstrumento ren;
    private TipoInstrumento pdm;
    private TipoInstrumento pu;
    private Utilizador autor;

    @BeforeEach
    void setUp() {
        ren = tipo(1L, "REN", "SRUP");
        pdm = tipo(2L, "PDM", "PLANO");
        pu = tipo(3L, "PU", "PLANO");
        autor = new Utilizador("Autor", "autor@ccdr-teste.pt", "INCM");
        ReflectionTestUtils.setField(autor, "id", UUID.randomUUID());
    }

    @Test
    void deveAceitarAssociacaoSoComAConfiguracaoDaDinamicaEOsTiposAssociaveisDoInstrumento() {
        ren.getTiposAssociaveis().add(pdm);

        assertThat(service.aceitaAssociacao(processo(ren, true))).isTrue();
        assertThat(service.aceitaAssociacao(processo(ren, false))).as("Correção Material e Retificação").isFalse();
        assertThat(service.aceitaAssociacao(processo(pdm, true))).as("tipo sem tipos associáveis").isFalse();
    }

    @Test
    void deveRecusarAListaCom409NumProcessoQueNaoAceitaAssociacao() {
        ren.getTiposAssociaveis().add(pdm);
        Processo correcao = processo(ren, false);

        assertThatThrownBy(() -> service.listar(correcao, PAGINA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Este processo não admite associação a uma dinâmica.");
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveListarComOInstrumentoDoProcessoEOsTiposAssociaveis() {
        ren.getTiposAssociaveis().add(pdm);
        ren.getTiposAssociaveis().add(pu);
        Processo processo = processo(ren, true);
        UUID instrumentoRen = processo.getDinamica().getInstrumento().getId();
        Page<DinamicaAssociavel> pagina = new PageImpl<>(List.of(mock(DinamicaAssociavel.class)), PAGINA, 1);
        when(dinamicaRepository.listarAssociaveis(eq(instrumentoRen), anyCollection(), eq(PAGINA))).thenReturn(pagina);

        assertThat(service.listar(processo, PAGINA)).isSameAs(pagina);
        verify(dinamicaRepository).listarAssociaveis(eq(instrumentoRen),
                org.mockito.ArgumentMatchers.argThat(tipos -> Set.copyOf(tipos).equals(Set.of(2L, 3L))), eq(PAGINA));
    }

    @Test
    void deveDevolverAListaVaziaSemConsultarNumaDinamicaSemInstrumentoDeInicio() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        ReflectionTestUtils.setField(processo.getDinamica(), "instrumento", null);

        assertThat(service.listar(processo, PAGINA)).isEmpty();
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveAceitarOValorNuloEmQualquerProcesso() {
        assertThatCode(() -> service.verificar(processo(pdm, false), null)).doesNotThrowAnyException();
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveRecusarUmValorNumProcessoQueNaoAceitaAssociacao() {
        Processo processo = processo(pdm, true);
        UUID dinamicaId = UUID.randomUUID();

        assertThatThrownBy(() -> service.verificar(processo, dinamicaId))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveRecusarADinamicaDoProprioProcesso() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        UUID propria = processo.getDinamica().getId();

        assertThatThrownBy(() -> service.verificar(processo, propria))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);
        verify(dinamicaRepository, never()).existeAssociavel(any(), any(), any());
    }

    @Test
    void deveRecusarADinamicaInexistenteOuForaDaRegraSemAsDistinguir() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        UUID foraDaRegra = UUID.randomUUID();
        when(dinamicaRepository.existeAssociavel(any(), anyCollection(), eq(foraDaRegra))).thenReturn(false);

        assertThatThrownBy(() -> service.verificar(processo, foraDaRegra))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class)
                .hasMessage("A dinâmica indicada não está disponível para associação.");
    }

    @Test
    void deveRecusarNumaDinamicaSemInstrumentoDeInicio() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        ReflectionTestUtils.setField(processo.getDinamica(), "instrumento", null);
        UUID dinamicaId = UUID.randomUUID();

        assertThatThrownBy(() -> service.verificar(processo, dinamicaId))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);
    }

    @Test
    void deveAceitarUmaDinamicaAssociavel() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        UUID associavel = UUID.randomUUID();
        when(dinamicaRepository.existeAssociavel(processo.getDinamica().getInstrumento().getId(), List.of(2L), associavel))
                .thenReturn(true);

        assertThatCode(() -> service.verificar(processo, associavel)).doesNotThrowAnyException();
    }

    @Test
    void deveDevolverAAssociacaoGravadaComOPrimeiroInstrumentoAssociavelPorDesignacao() {
        ren.getTiposAssociaveis().add(pdm);
        Processo processo = processo(ren, true);
        assertThat(service.associada(processo)).isNull();

        Instrumento pdmB = instrumento(pdm, "PDM de B");
        Dinamica alteracao = new Dinamica(pdmB, config(pdm, "ALTERACAO", false), "Alteração do PDM", autor);
        ReflectionTestUtils.setField(alteracao, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(alteracao, "numeroDinamica", "D-1");
        alteracao.getInstrumentos().add(instrumento(pdm, "PDM de A"));
        alteracao.getInstrumentos().add(instrumento(pu, "PU de 0"));
        processo.setDinamicaAssociada(alteracao);

        DinamicaAssociavelResponse resposta = service.associada(processo);

        assertThat(resposta.dinamicaId()).isEqualTo(alteracao.getId());
        assertThat(resposta.numeroDinamica()).isEqualTo("D-1");
        assertThat(resposta.tipoDinamica()).isEqualTo("ALTERACAO");
        assertThat(resposta.estado()).isEqualTo("EM_CURSO");
        assertThat(resposta.instrumento().designacao()).isEqualTo("PDM de A");
        assertThat(resposta.instrumento().tipoInstrumento()).isEqualTo("PDM");
    }

    @Test
    void deveAceitarARespostaAPerguntaDoPmotSoNumProcessoQueAceitaAssociacao() {
        ren.getTiposAssociaveis().add(pdm);
        Processo delimitacao = processo(ren, true);
        Processo correcao = processo(ren, false);

        assertThatCode(() -> service.verificarResposta(delimitacao, "SIM", null)).doesNotThrowAnyException();
        assertThatCode(() -> service.verificarResposta(delimitacao, "NAO", null)).doesNotThrowAnyException();
        assertThatCode(() -> service.verificarResposta(correcao, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verificarResposta(correcao, "SIM", null))
                .isInstanceOfSatisfying(com.conkord.saigt.exception.ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getCampos()).extracting(com.conkord.saigt.exception.ValidacaoException.CampoErro::path)
                            .containsExactly("dadosFormulario.simultaneoComPmot");
                });
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveRecusarADinamicaAssociadaSemARespostaSim() {
        ren.getTiposAssociaveis().add(pdm);
        Processo delimitacao = processo(ren, true);
        UUID dinamicaId = UUID.randomUUID();

        assertThatCode(() -> service.verificarResposta(delimitacao, "SIM", dinamicaId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.verificarResposta(delimitacao, "NAO", dinamicaId))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);
        assertThatThrownBy(() -> service.verificarResposta(delimitacao, null, dinamicaId))
                .isInstanceOf(DinamicaAssociadaInvalidaException.class);
    }

    private Processo processo(TipoInstrumento tipo, boolean admiteAssociacaoIgt) {
        InstrumentoDinamicaConfig config = config(tipo, "DELIMITACAO", admiteAssociacaoIgt);
        Dinamica dinamica = new Dinamica(instrumento(tipo, "Instrumento do processo"), config, "Dinâmica do processo", autor);
        ReflectionTestUtils.setField(dinamica, "id", UUID.randomUUID());
        Processo processo = new Processo(dinamica,
                new InstrumentoProcedimentoConfig(config, new TipoProcedimento("DECISAO_INICIO", "Decisão"), 1, "SEGUNDA_C"),
                autor, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        return processo;
    }

    private static InstrumentoDinamicaConfig config(TipoInstrumento tipo, String tipoDinamica, boolean admiteAssociacaoIgt) {
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipo, new TipoDinamica(tipoDinamica, tipoDinamica));
        ReflectionTestUtils.setField(config, "admiteAssociacaoIgt", admiteAssociacaoIgt);
        return config;
    }

    private Instrumento instrumento(TipoInstrumento tipo, String designacao) {
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrumento, "tipoInstrumento", tipo);
        ReflectionTestUtils.setField(instrumento, "designacao", designacao);
        return instrumento;
    }

    private static TipoInstrumento tipo(long id, String codigo, String categoria) {
        TipoInstrumento tipo = new TipoInstrumento(codigo, codigo, categoria);
        ReflectionTestUtils.setField(tipo, "id", id);
        return tipo;
    }
}
