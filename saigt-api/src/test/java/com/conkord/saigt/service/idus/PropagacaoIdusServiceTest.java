package com.conkord.saigt.service.idus;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PropagacaoIdusServiceTest {

    private static final String NUMERO_DEPOSITO = "03.11.11/PDM/02/2026/7";
    private static final String IDUS_DINAMICA = "01.1199.01.001002.02.00003.20261110";
    private static final UUID PROCESSO_FINAL = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000003");
    private static final UUID PROCESSO_DECISAO = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000001");
    private static final UUID PROCESSO_DP = UUID.fromString("0b6a3c1e-0000-4c00-8000-000000000002");

    private ClienteSnit clienteSnit;
    private ComunicacaoPcgtService comunicacaoPcgt;
    private PropagacaoIdusGeoconformService propagacaoGeoconform;
    private PropagacaoIdusService servico;

    @BeforeEach
    void iniciar() {
        clienteSnit = mock(ClienteSnit.class);
        comunicacaoPcgt = mock(ComunicacaoPcgtService.class);
        propagacaoGeoconform = mock(PropagacaoIdusGeoconformService.class);
        servico = new PropagacaoIdusService(clienteSnit, comunicacaoPcgt, propagacaoGeoconform);
    }

    private static EntradaEmVigorRegistada nova(Long ssaigtId, String idusDinamica) {
        return new EntradaEmVigorRegistada(NUMERO_DEPOSITO, true, PROCESSO_FINAL, ssaigtId, idusDinamica,
                List.of(PROCESSO_DECISAO, PROCESSO_DP, PROCESSO_FINAL));
    }

    private void snitResponde(ResultadoChamadaExterna<DepositoRecebidoSnit> resultado) {
        when(clienteSnit.atualizarIdusDeposito(anyLong(), anyString(), any())).thenReturn(resultado);
    }

    @Test
    void deveEnviarOIdusDaDinamicaAoSnitEOIdusDeCadaProcessoAPcgt(CapturedOutput output) {
        snitResponde(new ResultadoChamadaExterna<>(200, null, null, 5));

        servico.propagar(nova(42L, IDUS_DINAMICA));

        ArgumentCaptor<ReferenciaChamada> referencia = ArgumentCaptor.forClass(ReferenciaChamada.class);
        InOrder ordem = inOrder(clienteSnit, comunicacaoPcgt, propagacaoGeoconform);
        ordem.verify(clienteSnit).atualizarIdusDeposito(eq(42L), eq(IDUS_DINAMICA), referencia.capture());
        ordem.verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DECISAO);
        ordem.verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DP);
        ordem.verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        ordem.verify(propagacaoGeoconform).propagar(any());
        assertThat(referencia.getValue().processoId()).isEqualTo(PROCESSO_FINAL);
        assertThat(output).contains("IDUS da dinâmica enviado ao SNIT").contains("ssaigtId=42");
    }

    @Test
    void deveContinuarParaAPcgtERegistarUmErroQuandoOSnitFalha(CapturedOutput output) {
        snitResponde(new ResultadoChamadaExterna<>(409, null, FalhaTecnicaExterna.ERRO_HTTP, 5));

        servico.propagar(nova(42L, IDUS_DINAMICA));

        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DECISAO);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DP);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("ERROR").contains("Propagação do IDUS ao SNIT falhou, sem nova tentativa")
                .contains("httpStatus=409").contains("falha=ERRO_HTTP").contains("idusDinamica=" + IDUS_DINAMICA);
    }

    @Test
    void deveContinuarQuandoOClienteDoSnitLancaUmaExcecao(CapturedOutput output) {
        when(clienteSnit.atualizarIdusDeposito(anyLong(), anyString(), any()))
                .thenThrow(new IllegalStateException("falha inesperada"));

        assertThatCode(() -> servico.propagar(nova(42L, IDUS_DINAMICA))).doesNotThrowAnyException();

        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("Propagação do IDUS ao SNIT falhou").contains("erro=IllegalStateException")
                .doesNotContain("falha inesperada");
    }

    @Test
    void deveSeguirParaOsProcessosSeguintesQuandoAPcgtFalhaNumDeles(CapturedOutput output) {
        snitResponde(new ResultadoChamadaExterna<>(200, null, null, 5));
        doThrow(new IllegalStateException("x")).when(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DP);

        assertThatCode(() -> servico.propagar(nova(42L, IDUS_DINAMICA))).doesNotThrowAnyException();

        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("Propagação do IDUS à PCGT falhou, sem nova tentativa")
                .contains("processoId=" + PROCESSO_DP);
    }

    @Test
    void naoDeveChamarOSnitSemIdusDaDinamicaMasDeveAtualizarAPcgt(CapturedOutput output) {
        servico.propagar(nova(42L, null));

        verifyNoInteractions(clienteSnit);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DECISAO);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("WARN").contains("IDUS da dinâmica não enviado ao SNIT, não foi gerado")
                .contains("numeroDeposito=" + NUMERO_DEPOSITO);
    }

    @Test
    void naoDeveChamarOSnitSemNumeroDoProcessoNumerico(CapturedOutput output) {
        servico.propagar(nova(null, IDUS_DINAMICA));

        verifyNoInteractions(clienteSnit);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("ERROR").contains("não tem n.º numérico")
                .contains("numeroDeposito=" + NUMERO_DEPOSITO);
    }

    @Test
    void deveEnviarAoSnitMesmoQuandoAPcgtFalhaEmTodosOsProcessos(CapturedOutput output) {
        snitResponde(new ResultadoChamadaExterna<>(200, null, null, 5));
        doThrow(new IllegalStateException("x")).when(comunicacaoPcgt).comunicarPublicacao(any());

        assertThatCode(() -> servico.propagar(nova(42L, IDUS_DINAMICA))).doesNotThrowAnyException();

        verify(clienteSnit).atualizarIdusDeposito(eq(42L), eq(IDUS_DINAMICA), any());
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DECISAO);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_DP);
        verify(comunicacaoPcgt).comunicarPublicacao(PROCESSO_FINAL);
        assertThat(output).contains("IDUS da dinâmica enviado ao SNIT")
                .contains("sistema=PCGT numeroDeposito=" + NUMERO_DEPOSITO);
    }

    @Test
    void naoDevePropagarUmaRepeticao() {
        servico.propagar(EntradaEmVigorRegistada.repetida(NUMERO_DEPOSITO, PROCESSO_FINAL));

        verifyNoInteractions(clienteSnit);
        verify(comunicacaoPcgt, never()).comunicarPublicacao(any());
        verifyNoInteractions(propagacaoGeoconform);
    }

    @Test
    void deveIrAoGeoconformMesmoQuandoOSnitEAPcgtFalham() {
        snitResponde(new ResultadoChamadaExterna<>(null, null, FalhaTecnicaExterna.TIMEOUT, 5));
        doThrow(new IllegalStateException("x")).when(comunicacaoPcgt).comunicarPublicacao(any());
        EntradaEmVigorRegistada registo = nova(42L, IDUS_DINAMICA);

        servico.propagar(registo);

        verify(propagacaoGeoconform).propagar(registo);
    }

    @Test
    void deveRegistarUmErroSemOMotivoQuandoAPropagacaoAoGeoconformLanca(CapturedOutput output) {
        snitResponde(new ResultadoChamadaExterna<>(200, null, null, 5));
        doThrow(new IllegalStateException("detalhe interno")).when(propagacaoGeoconform).propagar(any());

        assertThatCode(() -> servico.propagar(nova(42L, IDUS_DINAMICA))).doesNotThrowAnyException();

        assertThat(output).contains("ERROR").contains("Propagação do IDUS ao GeoConform falhou, sem nova tentativa")
                .contains("sistema=GEOCONFORM").contains("erro=IllegalStateException")
                .doesNotContain("detalhe interno");
    }
}
