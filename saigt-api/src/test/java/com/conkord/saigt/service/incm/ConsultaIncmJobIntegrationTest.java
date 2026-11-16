package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.GetActDetailsResponse;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import com.conkord.saigt.service.snit.RegistoSnitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Seleção dos Processos na BD e ciclo completo do job, com o {@link ClienteIncm} simulado. */
@SpringBootTest
class ConsultaIncmJobIntegrationTest extends ConsultaIncmServiceTestBase {

    private static final String ACT = "900000000000001";

    @MockBean
    private ClienteIncm clienteIncm;

    @MockBean
    private RegistoSnitService registoSnitService;

    @MockBean
    private ComunicacaoPcgtService comunicacaoPcgtService;

    @Autowired
    private ConsultaIncmJob job;

    @Test
    void deveSelecionarSoOsProcessosEnviadosOuEmValidacaoIncmComAtoAtribuido() {
        Processo emValidacao = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        Processo enviado = criarProcesso(EstadoProcesso.ENVIADO_INCM, ACT);
        Processo validadoDgt = criarProcesso(EstadoProcesso.VALIDADO_DGT, ACT);
        Processo publicado = criarProcesso(EstadoProcesso.PUBLICADO, ACT);
        Processo semAto = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, null);

        List<UUID> ids = processoRepository.findIdsParaConsultaIncm();

        assertThat(ids).contains(emValidacao.getId(), enviado.getId())
                .doesNotContain(validadoDgt.getId(), publicado.getId(), semAto.getId());
        assertThat(ids.indexOf(emValidacao.getId())).isLessThan(ids.indexOf(enviado.getId()));
    }

    @Test
    void devePassarAPublicadoUmProcessoEmValidacaoIncmCujoAtoFoiPublicado() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        when(clienteIncm.obterDetalhesAto(eq(processo.getId()), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, respostaPublicada(), null, 5L));

        job.executar();

        Processo atualizado = recarregar(processo);
        assertThat(atualizado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(atualizado.getNumeroDr()).isEqualTo("DR-2026-000001");
        assertThat(atualizado.getLinkDr()).isEqualTo("https://exemplo.pt/dr/2026/000001");
    }

    @Test
    void deveRegistarNoSnitDepoisDoCommitDaPublicacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        when(clienteIncm.obterDetalhesAto(eq(processo.getId()), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, respostaPublicada(), null, 5L));
        List<EstadoProcesso> estadoNaChamada = new ArrayList<>();
        doAnswer(invocacao -> {
            estadoNaChamada.add(recarregar(processo).getEstado());
            return null;
        }).when(registoSnitService).registarProcedimentoFinal(eq(processo.getId()), any());

        job.executar();

        assertThat(estadoNaChamada).containsExactly(EstadoProcesso.PUBLICADO);
    }

    @Test
    void deveComunicarAPcgtDepoisDoCommitDaPublicacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        when(clienteIncm.obterDetalhesAto(eq(processo.getId()), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, respostaPublicada(), null, 5L));
        List<EstadoProcesso> estadoNaChamada = new ArrayList<>();
        doAnswer(invocacao -> {
            estadoNaChamada.add(recarregar(processo).getEstado());
            return null;
        }).when(comunicacaoPcgtService).comunicarPublicacao(processo.getId());

        job.executar();

        assertThat(estadoNaChamada).containsExactly(EstadoProcesso.PUBLICADO);
    }

    private static GetActDetailsResponse respostaPublicada() {
        GetActDetailsResponse.Publication publicacao = new GetActDetailsResponse.Publication(
                "https://exemplo.pt/dr/2026/000001.pdf", "Aviso n.º 1/2026", "https://exemplo.pt/dr/2026/000001",
                "Diário da República n.º 206/2026", LocalDate.of(2026, 10, 24));
        return new GetActDetailsResponse(true, null, "SubmissionAccepted", null, null, "DR-2026-000001", null, null,
                "Publicado", List.of(), null, publicacao);
    }
}
