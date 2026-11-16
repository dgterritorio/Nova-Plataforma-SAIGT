package com.conkord.saigt.service.workflow;

import com.conkord.saigt.dto.workflow.PassoResponse;
import com.conkord.saigt.dto.workflow.WorkflowResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    @Mock
    private InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    @Mock
    private PermissaoAcaoRepository permissaoAcaoRepository;
    @Mock
    private ProcessoRepository processoRepository;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private WorkflowService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null, null, null, null, null, null, null, null);
    }

    private static InstrumentoDinamicaConfig configComId(long id, TipoInstrumento tipoInstrumento, TipoDinamica tipoDinamica) {
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica);
        ReflectionTestUtils.setField(config, "id", id);
        return config;
    }

    private static InstrumentoProcedimentoConfig passoComId(long id, InstrumentoDinamicaConfig config,
                                                             TipoProcedimento tipoProcedimento, int ordem) {
        InstrumentoProcedimentoConfig passo = new InstrumentoProcedimentoConfig(config, tipoProcedimento, ordem, "SEGUNDA_H");
        ReflectionTestUtils.setField(passo, "id", id);
        return passo;
    }

    @Test
    void deveComporOTituloComOCodigoDoTipoInstrumentoENomeDaDinamica() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        InstrumentoDinamicaConfig config = configComId(101L,
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), new TipoDinamica("ELABORACAO", "Elaboração"));
        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of(config));
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(List.of(101L))).thenReturn(List.of());
        when(permissaoAcaoRepository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(List.of())).thenReturn(List.of());
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any())).thenReturn(List.of());

        List<WorkflowResponse> workflows = service.listar();

        assertThat(workflows).hasSize(1);
        assertThat(workflows.get(0).id()).isEqualTo(101L);
        assertThat(workflows.get(0).titulo()).isEqualTo("PDM — Elaboração");
        assertThat(workflows.get(0).tipoInstrumento().codigo()).isEqualTo("PDM");
        assertThat(workflows.get(0).tipoInstrumento().nome()).isEqualTo("Plano Diretor Municipal");
    }

    @Test
    void deveDevolverListaDePassosVaziaQuandoADinamicaNaoTemProcedimentosConfigurados() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        InstrumentoDinamicaConfig config = configComId(102L,
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), new TipoDinamica("SUSPENSAO", "Suspensão"));
        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of(config));
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(List.of(102L))).thenReturn(List.of());
        when(permissaoAcaoRepository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(List.of())).thenReturn(List.of());
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any())).thenReturn(List.of());

        WorkflowResponse workflow = service.listar().get(0);

        assertThat(workflow.passos()).isEmpty();
        assertThat(workflow.numeroPassos()).isZero();
    }

    @Test
    void deveDevolverOsPassosOrdenadosComAtoresEPrazoNuloQuandoNaoConfigurado() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        InstrumentoDinamicaConfig config = configComId(101L,
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoProcedimentoConfig passo1 = passoComId(201L, config, new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"), 1);
        InstrumentoProcedimentoConfig passo2 = passoComId(202L, config, new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública"), 2);
        passo2.setObrigatorio(false);
        passo2.setPrazoDias(30);
        passo2.setTipoPrazo("LEGAL");

        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of(config));
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(List.of(101L))).thenReturn(List.of(passo1, passo2));
        when(permissaoAcaoRepository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(List.of(201L, 202L)))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.CM, passo1), new PermissaoAcao(TipoEntidade.DGT, passo1)));
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any())).thenReturn(List.of());

        WorkflowResponse workflow = service.listar().get(0);

        assertThat(workflow.numeroPassos()).isEqualTo(2);
        List<PassoResponse> passos = workflow.passos();
        assertThat(passos).extracting(PassoResponse::nome).containsExactly("Decisão de Início", "Discussão Pública");

        PassoResponse resposta1 = passos.get(0);
        assertThat(resposta1.obrigatorio()).isTrue();
        assertThat(resposta1.serieDr()).isEqualTo("SEGUNDA_H");
        assertThat(resposta1.atores()).containsExactlyInAnyOrder("CM", "DGT");
        assertThat(resposta1.prazoDias()).isNull();
        assertThat(resposta1.tipoPrazo()).isNull();

        PassoResponse resposta2 = passos.get(1);
        assertThat(resposta2.obrigatorio()).isFalse();
        assertThat(resposta2.prazoDias()).isEqualTo(30);
        assertThat(resposta2.tipoPrazo()).isEqualTo("LEGAL");
        assertThat(resposta2.atores()).isEmpty();
    }

    @Test
    void deveDevolverEstadoEProcessosAtivosPorWorkflow() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        InstrumentoDinamicaConfig config = configComId(103L,
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), new TipoDinamica("SUSPENSAO", "Suspensão"));
        config.setAtivo(false);
        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of(config));
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(List.of(103L))).thenReturn(List.of());
        when(permissaoAcaoRepository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(List.of())).thenReturn(List.of());
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any()))
                .thenReturn(List.of(contagem(103L, 4L)));

        WorkflowResponse workflow = service.listar().get(0);

        assertThat(workflow.ativo()).isFalse();
        assertThat(workflow.processosAtivos()).isEqualTo(4L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveExcluirSoOsEstadosTerminaisEContarOsRejeitadosComoAtivos() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of());
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any())).thenReturn(List.of());

        service.listar();

        ArgumentCaptor<Collection<EstadoProcesso>> excluidos = ArgumentCaptor.forClass(Collection.class);
        verify(processoRepository).contarProcessosAtivosPorConfigDinamica(excluidos.capture());
        assertThat(excluidos.getValue())
                .containsExactlyInAnyOrder(EstadoProcesso.PUBLICADO, EstadoProcesso.ANULADO)
                .doesNotContain(EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM);
    }

    @Test
    void deveDevolverZeroProcessosAtivosQuandoNaoHaContagemParaOWorkflow() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        InstrumentoDinamicaConfig config = configComId(104L,
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"), new TipoDinamica("ELABORACAO", "Elaboração"));
        when(instrumentoDinamicaConfigRepository.listarTodosOrdenados()).thenReturn(List.of(config));
        when(instrumentoProcedimentoConfigRepository.listarAtivosPorConfigs(List.of(104L))).thenReturn(List.of());
        when(permissaoAcaoRepository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(List.of())).thenReturn(List.of());
        when(processoRepository.contarProcessosAtivosPorConfigDinamica(any())).thenReturn(List.of());

        assertThat(service.listar().get(0).processosAtivos()).isZero();
    }

    @Test
    void deveRejeitarTecnico() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.listar()).isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(instrumentoDinamicaConfigRepository);
    }

    @Test
    void deveRejeitarQuandoChamadorNaoTemPerfilDeLeitura() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(null));

        assertThatThrownBy(() -> service.listar()).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    private static ProcessoRepository.ContagemProcessosAtivosProjecao contagem(long configId, long total) {
        return new ProcessoRepository.ContagemProcessosAtivosProjecao() {
            @Override
            public Long getConfigId() {
                return configId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
