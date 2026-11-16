package com.conkord.saigt.service.idus;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.CorrespondenciaIdusGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoCorrecaoIdentificadoresGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoPublicacaoGeoconform;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PropagacaoIdusGeoconformServiceTest {

    private static final String NUMERO_DEPOSITO = "03.11.11/PDM/02/2026/7";
    private static final String IDUS_DINAMICA = "01.1199.01.012345.02.00003.20261110";
    private static final UUID PROCESSO_DECISAO = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000001");
    private static final UUID PROCESSO_DP = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000002");
    private static final UUID PROCESSO_FINAL = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000003");
    private static final UUID PROCESSO_SEM_MUDANCA = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000004");
    private static final String DECISAO_PROVISORIO = "01.1199.01.900001.02.90001.01.031.01.20260105";
    private static final String DECISAO_IGT_SNIT = "01.1199.01.012345.02.90001.01.031.01.20260105";
    private static final String DECISAO_DEFINITIVO = "01.1199.01.012345.02.00003.01.031.01.20260105";
    private static final String DP_PROVISORIO = "01.1199.01.012345.02.90001.01.027.01.20260410";
    private static final String DP_DEFINITIVO = "01.1199.01.012345.02.00003.01.027.01.20260410";
    private static final String FINAL_PROVISORIO = "01.1199.01.012345.02.90001.01.034.01.20261014";
    private static final String FINAL_DEFINITIVO = "01.1199.01.012345.02.00003.01.034.01.20261014";
    private static final LocalDate DATA_PUBLICACAO = LocalDate.of(2026, 10, 14);

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final IdusProcessoRepository idusRepository = mock(IdusProcessoRepository.class);
    private final IdusProcessoHistoricoRepository historicoRepository = mock(IdusProcessoHistoricoRepository.class);
    private final ClienteGeoconform cliente = mock(ClienteGeoconform.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final PropagacaoIdusGeoconformService servico = new PropagacaoIdusGeoconformService(processoRepository,
            idusRepository, historicoRepository, cliente, transacoes);

    private final Processo processoFinal = mock(Processo.class);
    private final TipoInstrumento tipoInstrumento = mock(TipoInstrumento.class);
    private final List<IdusProcessoHistorico> historico = new ArrayList<>();
    private final Dinamica dinamica = mock(Dinamica.class);

    @BeforeEach
    void dinamicaDePdmComTresProcessosAtualizados() {
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(dinamica.getComGeoconform()).thenReturn(true);
        InstrumentoDinamicaConfig config = mock(InstrumentoDinamicaConfig.class);
        when(processoRepository.findById(PROCESSO_FINAL)).thenReturn(Optional.of(processoFinal));
        when(processoFinal.getId()).thenReturn(PROCESSO_FINAL);
        when(processoFinal.getDinamica()).thenReturn(dinamica);
        when(dinamica.getInstrumentoDinamicaConfig()).thenReturn(config);
        when(config.getTipoInstrumento()).thenReturn(tipoInstrumento);
        when(tipoInstrumento.getCodigo()).thenReturn("PDM");
        when(processoFinal.getNumeroProcesso()).thenReturn("900003");
        when(processoFinal.getNumeroDr()).thenReturn("18452/2026");
        when(processoFinal.getLinkDr()).thenReturn("https://dre.exemplo.pt/18452-2026");
        when(processoFinal.getDataPublicacaoDr()).thenReturn(DATA_PUBLICACAO);
        List<Processo> processos = List.of(processo(PROCESSO_DECISAO, "900001"), processo(PROCESSO_DP, "900002"),
                processoFinal, processo(PROCESSO_SEM_MUDANCA, "900004"));
        when(processoRepository.findAllById(any())).thenReturn(processos);

        historico.add(linha(PROCESSO_DECISAO, SegmentoIdus.ID_IGT, DECISAO_PROVISORIO, DECISAO_IGT_SNIT));
        historico.add(linha(PROCESSO_DECISAO, SegmentoIdus.ORDEM_DINAMICA, DECISAO_IGT_SNIT, DECISAO_DEFINITIVO));
        historico.add(linha(PROCESSO_DP, SegmentoIdus.ORDEM_DINAMICA, DP_PROVISORIO, DP_DEFINITIVO));
        historico.add(linha(PROCESSO_FINAL, SegmentoIdus.ORDEM_DINAMICA, FINAL_PROVISORIO, FINAL_DEFINITIVO));
        when(historicoRepository.findByProcessoIdInAndEventoOrderByIdAsc(any(), eq(EventoIdus.ENTRADA_EM_VIGOR)))
                .thenReturn(historico);

        when(cliente.registarPublicacao(anyString(), any(), any())).thenReturn(ok());
        when(cliente.corrigirIdentificadores(any(), any())).thenReturn(ok());
    }

    private static Processo processo(UUID id, String numero) {
        Processo processo = mock(Processo.class);
        when(processo.getId()).thenReturn(id);
        when(processo.getNumeroProcesso()).thenReturn(numero);
        return processo;
    }

    private static IdusProcessoHistorico linha(UUID processoId, SegmentoIdus segmento, String anterior, String novo) {
        return IdusProcessoHistorico.builder().processoId(processoId).segmento(segmento).idusAnterior(anterior)
                .idusNovo(novo).evento(EventoIdus.ENTRADA_EM_VIGOR).registadoEm(Instant.EPOCH).correlationId("t").build();
    }

    private static ResultadoChamadaExterna<Void> ok() {
        return new ResultadoChamadaExterna<>(200, null, null, 5);
    }

    private static EntradaEmVigorRegistada registo(String idusDinamica) {
        return new EntradaEmVigorRegistada(NUMERO_DEPOSITO, true, PROCESSO_FINAL, 900003L, idusDinamica,
                List.of(PROCESSO_DECISAO, PROCESSO_DP, PROCESSO_FINAL, PROCESSO_SEM_MUDANCA));
    }

    @Test
    void deveRegistarAPublicacaoComOIdusQueOGeoconformConheceEOsQuatroCamposSemOIdusDaDinamica(CapturedOutput output) {
        servico.propagar(registo(IDUS_DINAMICA));

        ArgumentCaptor<PedidoPublicacaoGeoconform> pedido = ArgumentCaptor.forClass(PedidoPublicacaoGeoconform.class);
        ArgumentCaptor<ReferenciaChamada> referencia = ArgumentCaptor.forClass(ReferenciaChamada.class);
        verify(cliente).registarPublicacao(eq(FINAL_PROVISORIO), pedido.capture(), referencia.capture());
        assertThat(pedido.getValue()).isEqualTo(new PedidoPublicacaoGeoconform("900003", "18452/2026",
                "https://dre.exemplo.pt/18452-2026", DATA_PUBLICACAO));
        assertThat(referencia.getValue().processoId()).isEqualTo(PROCESSO_FINAL);
        assertThat(output).contains("Publicação registada no GeoConform").contains("idus=" + FINAL_PROVISORIO);
    }

    @Test
    void deveCorrigirDepoisDaPublicacaoOsIdusProvisoriosNumSoPedidoComPublicacaoFinal(CapturedOutput output) {
        servico.propagar(registo(IDUS_DINAMICA));

        ArgumentCaptor<PedidoCorrecaoIdentificadoresGeoconform> pedido =
                ArgumentCaptor.forClass(PedidoCorrecaoIdentificadoresGeoconform.class);
        InOrder ordem = inOrder(cliente);
        ordem.verify(cliente).registarPublicacao(anyString(), any(), any());
        ordem.verify(cliente).corrigirIdentificadores(pedido.capture(), any());
        assertThat(pedido.getValue().idusDinamica()).isEqualTo(IDUS_DINAMICA);
        assertThat(pedido.getValue().motivo()).isEqualTo("publicacao_final");
        assertThat(pedido.getValue().correspondencias()).containsExactly(
                new CorrespondenciaIdusGeoconform(DECISAO_PROVISORIO, DECISAO_DEFINITIVO, "900001"),
                new CorrespondenciaIdusGeoconform(DP_PROVISORIO, DP_DEFINITIVO, "900002"),
                new CorrespondenciaIdusGeoconform(FINAL_PROVISORIO, FINAL_DEFINITIVO, "900003"));
        assertThat(output).contains("IDUS da tentativa corrigidos no GeoConform").contains("correspondencias=3");
    }

    @Test
    void naoDeveIrAoGeoconformNumaDinamicaQueNaoEDePdm() {
        when(tipoInstrumento.getCodigo()).thenReturn("PIOT");

        servico.propagar(registo(IDUS_DINAMICA));

        verifyNoInteractions(cliente);
    }

    @Test
    void naoDeveIrAoGeoconformNumaDinamicaDePdmSemGeoconform() {
        when(dinamica.getComGeoconform()).thenReturn(false);

        servico.propagar(registo(IDUS_DINAMICA));

        verifyNoInteractions(cliente);
    }

    @Test
    void naoDeveIrAoGeoconformNumaDinamicaDePdmSemRespostaAPerguntaDoGeoconform() {
        when(dinamica.getComGeoconform()).thenReturn(null);

        servico.propagar(registo(IDUS_DINAMICA));

        verifyNoInteractions(cliente);
    }

    @Test
    void deveRegistarUmErroSemNovaTentativaECorrigirOsIdusQuandoAPublicacaoFalha(CapturedOutput output) {
        when(cliente.registarPublicacao(anyString(), any(), any()))
                .thenReturn(new ResultadoChamadaExterna<>(503, null, FalhaTecnicaExterna.ERRO_HTTP, 5));

        assertThatCode(() -> servico.propagar(registo(IDUS_DINAMICA))).doesNotThrowAnyException();

        verify(cliente, times(1)).registarPublicacao(anyString(), any(), any());
        verify(cliente, times(1)).corrigirIdentificadores(any(), any());
        assertThat(output).contains("ERROR").contains("Registo da publicação no GeoConform falhou, sem nova tentativa")
                .contains("sistema=GEOCONFORM").contains("httpStatus=503").contains("falha=ERRO_HTTP")
                .doesNotContain("18452/2026").doesNotContain("dre.exemplo.pt");
    }

    @Test
    void deveRegistarUmErroSemNovaTentativaQuandoACorrecaoFalha(CapturedOutput output) {
        when(cliente.corrigirIdentificadores(any(), any()))
                .thenReturn(new ResultadoChamadaExterna<>(null, null, FalhaTecnicaExterna.TIMEOUT, 5));

        assertThatCode(() -> servico.propagar(registo(IDUS_DINAMICA))).doesNotThrowAnyException();

        verify(cliente, times(1)).corrigirIdentificadores(any(), any());
        assertThat(output).contains("ERROR")
                .contains("Correção dos IDUS da tentativa no GeoConform falhou, sem nova tentativa")
                .contains("idusDinamica=" + IDUS_DINAMICA).contains("falha=TIMEOUT")
                .doesNotContain("\"correspondencias\"").doesNotContain(DP_PROVISORIO);
    }

    @Test
    void deveRegistarUmErroSemOMotivoQuandoOClienteLanca(CapturedOutput output) {
        when(cliente.registarPublicacao(anyString(), any(), any())).thenThrow(new IllegalStateException("detalhe"));
        when(cliente.corrigirIdentificadores(any(), any())).thenThrow(new IllegalStateException("detalhe"));

        assertThatCode(() -> servico.propagar(registo(IDUS_DINAMICA))).doesNotThrowAnyException();

        assertThat(output).contains("Registo da publicação no GeoConform falhou")
                .contains("Correção dos IDUS da tentativa no GeoConform falhou")
                .contains("erro=IllegalStateException").doesNotContain("detalhe");
    }

    @Test
    void naoDeveRegistarAPublicacaoSemOsDadosDoAtoMasDeveCorrigirOsIdus(CapturedOutput output) {
        when(processoFinal.getLinkDr()).thenReturn(" ");
        when(processoFinal.getDataPublicacaoDr()).thenReturn(null);

        servico.propagar(registo(IDUS_DINAMICA));

        verify(cliente, never()).registarPublicacao(anyString(), any(), any());
        verify(cliente).corrigirIdentificadores(any(), any());
        assertThat(output).contains("ERROR").contains("faltam dados da publicação")
                .contains("emFalta=[linkPublicacaoIncm, dataPublicacao]");
    }

    @Test
    void deveUsarOIdusAtualDoProcessoFinalQuandoNaoMudouNaEntradaEmVigor() {
        historico.removeIf(linha -> linha.getProcessoId().equals(PROCESSO_FINAL));
        IdusProcesso idus = mock(IdusProcesso.class);
        when(idus.getIdus()).thenReturn(FINAL_DEFINITIVO);
        when(idusRepository.findById(PROCESSO_FINAL)).thenReturn(Optional.of(idus));

        servico.propagar(registo(IDUS_DINAMICA));

        verify(cliente).registarPublicacao(eq(FINAL_DEFINITIVO), any(), any());
    }

    @Test
    void naoDeveCorrigirOsIdusSemIdusDaDinamica(CapturedOutput output) {
        servico.propagar(registo(null));

        verify(cliente).registarPublicacao(anyString(), any(), any());
        verify(cliente, never()).corrigirIdentificadores(any(), any());
        assertThat(output).contains("WARN").contains("o IDUS da dinâmica não foi gerado");
    }

    @Test
    void naoDeveCorrigirQuandoNenhumIdusMudou() {
        historico.clear();
        when(idusRepository.findById(PROCESSO_FINAL)).thenReturn(Optional.empty());

        servico.propagar(registo(IDUS_DINAMICA));

        verify(cliente, never()).corrigirIdentificadores(any(), any());
        verify(cliente, never()).registarPublicacao(anyString(), any(), any());
    }
}
