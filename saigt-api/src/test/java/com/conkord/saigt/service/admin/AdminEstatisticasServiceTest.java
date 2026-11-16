package com.conkord.saigt.service.admin;

import com.conkord.saigt.dto.admin.EstatisticasAdminResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEstatisticasServiceTest {

    @Mock
    private ProcessoRepository processoRepository;
    @Mock
    private DinamicaRepository dinamicaRepository;
    @Mock
    private AuditoriaRepository auditoriaRepository;
    @Mock
    private UtilizadorRepository utilizadorRepository;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private AdminEstatisticasService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null, null, null, null, null, null, null, null);
    }

    @Test
    void deveCalcularAsQuatroMetricasAPartirDoRepositorio() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(processoRepository.count()).thenReturn(284L);
        when(processoRepository.countByEstado(EstadoProcesso.EM_VALIDACAO_DGT)).thenReturn(47L);
        when(processoRepository.countByEstadoIn(Set.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO))).thenReturn(23L);
        when(dinamicaRepository.countByEstadoAndDataCaducidadeBefore(eq(EstadoDinamica.EM_CURSO), any(LocalDate.class)))
                .thenReturn(1L);
        when(processoRepository.contarSubmissoesPorMes()).thenReturn(List.of());
        when(processoRepository.contarPorEstado()).thenReturn(List.of());
        when(auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of());

        EstatisticasAdminResponse resposta = service.obter();

        assertThat(resposta.totalProcessos()).isEqualTo(284L);
        assertThat(resposta.emAnalise()).isEqualTo(47L);
        assertThat(resposta.publicadosComSucesso()).isEqualTo(23L);
        assertThat(resposta.comPrazoExpirado()).isEqualTo(1L);
    }

    @Test
    void deveMapearAtividadeRecenteDaAuditoria() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(processoRepository.count()).thenReturn(0L);
        when(processoRepository.countByEstado(any())).thenReturn(0L);
        when(processoRepository.countByEstadoIn(any())).thenReturn(0L);
        when(dinamicaRepository.countByEstadoAndDataCaducidadeBefore(any(), any())).thenReturn(0L);
        when(processoRepository.contarSubmissoesPorMes()).thenReturn(List.of());
        when(processoRepository.contarPorEstado()).thenReturn(List.of());
        UUID utilizadorId = UUID.randomUUID();
        Auditoria auditoria = Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia("p1")
                .acao("SUBMETER")
                .estadoAntes("EM_SUBMISSAO")
                .estadoDepois("ENVIADO_DGT")
                .utilizadorId(utilizadorId)
                .build();
        when(auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of(auditoria));
        Utilizador utilizador = new Utilizador("Ana Ferreira", "ana.ferreira@dgt.pt", "AD_DGT");
        ReflectionTestUtils.setField(utilizador, "id", utilizadorId);
        when(utilizadorRepository.findAllById(Set.of(utilizadorId))).thenReturn(List.of(utilizador));

        EstatisticasAdminResponse resposta = service.obter();

        assertThat(resposta.atividadeRecente()).hasSize(1);
        assertThat(resposta.atividadeRecente().get(0).acao()).isEqualTo("SUBMETER");
        assertThat(resposta.atividadeRecente().get(0).utilizadorId()).isEqualTo(utilizadorId);
        assertThat(resposta.atividadeRecente().get(0).utilizadorNome()).isEqualTo("Ana Ferreira");
    }

    @Test
    void deveCairParaEmailQuandoUtilizadorAindaNaoTemNome() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(processoRepository.count()).thenReturn(0L);
        when(processoRepository.countByEstado(any())).thenReturn(0L);
        when(processoRepository.countByEstadoIn(any())).thenReturn(0L);
        when(dinamicaRepository.countByEstadoAndDataCaducidadeBefore(any(), any())).thenReturn(0L);
        when(processoRepository.contarSubmissoesPorMes()).thenReturn(List.of());
        when(processoRepository.contarPorEstado()).thenReturn(List.of());
        UUID utilizadorId = UUID.randomUUID();
        Auditoria auditoria = Auditoria.builder()
                .tipoOcorrencia("UTILIZADOR")
                .idOcorrencia(utilizadorId.toString())
                .acao("CRIAR")
                .utilizadorId(utilizadorId)
                .build();
        when(auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of(auditoria));
        Utilizador utilizador = new Utilizador(null, "pre.registado@dgt.pt");
        ReflectionTestUtils.setField(utilizador, "id", utilizadorId);
        when(utilizadorRepository.findAllById(Set.of(utilizadorId))).thenReturn(List.of(utilizador));

        EstatisticasAdminResponse resposta = service.obter();

        assertThat(resposta.atividadeRecente().get(0).utilizadorNome()).isEqualTo("pre.registado@dgt.pt");
    }

    @Test
    void deveRejeitarQuandoChamadorNaoTemPerfilDeLeituraAdministrativa() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(null));

        assertThatThrownBy(() -> service.obter()).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void devePivotarPublicadosPorEntidadeETipo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(processoRepository.count()).thenReturn(0L);
        when(processoRepository.countByEstado(any())).thenReturn(0L);
        when(processoRepository.countByEstadoIn(any())).thenReturn(0L);
        when(dinamicaRepository.countByEstadoAndDataCaducidadeBefore(any(), any())).thenReturn(0L);
        when(processoRepository.contarSubmissoesPorMes()).thenReturn(List.of());
        when(processoRepository.contarPorEstado()).thenReturn(List.of());
        when(auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of());

        ProcessoRepository.PublicadosPorEntidadeETipoProjecao lisboaPdm = mock(ProcessoRepository.PublicadosPorEntidadeETipoProjecao.class);
        when(lisboaPdm.getEntidadeNome()).thenReturn("CM Lisboa");
        when(lisboaPdm.getTipoInstrumentoCodigo()).thenReturn("PDM");
        when(lisboaPdm.getTotal()).thenReturn(2L);

        ProcessoRepository.PublicadosPorEntidadeETipoProjecao lisboaPp = mock(ProcessoRepository.PublicadosPorEntidadeETipoProjecao.class);
        when(lisboaPp.getEntidadeNome()).thenReturn("CM Lisboa");
        when(lisboaPp.getTipoInstrumentoCodigo()).thenReturn("PP");
        when(lisboaPp.getTotal()).thenReturn(14L);

        // Tipo fora das 4 colunas pivotadas — tem de continuar a contar para o total.
        ProcessoRepository.PublicadosPorEntidadeETipoProjecao lisboaPeot = mock(ProcessoRepository.PublicadosPorEntidadeETipoProjecao.class);
        when(lisboaPeot.getEntidadeNome()).thenReturn("CM Lisboa");
        when(lisboaPeot.getTipoInstrumentoCodigo()).thenReturn("PEOT");
        when(lisboaPeot.getTotal()).thenReturn(1L);

        ProcessoRepository.PublicadosPorEntidadeETipoProjecao portoPdm = mock(ProcessoRepository.PublicadosPorEntidadeETipoProjecao.class);
        when(portoPdm.getEntidadeNome()).thenReturn("CM Porto");
        when(portoPdm.getTipoInstrumentoCodigo()).thenReturn("PDM");
        when(portoPdm.getTotal()).thenReturn(1L);

        when(processoRepository.contarPublicadosPorEntidadeETipo(Set.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO)))
                .thenReturn(List.of(lisboaPdm, lisboaPp, lisboaPeot, portoPdm));

        ProcessoRepository.ContagemPorEntidadeProjecao lisboaCaducados = mock(ProcessoRepository.ContagemPorEntidadeProjecao.class);
        when(lisboaCaducados.getEntidadeNome()).thenReturn("CM Lisboa");
        when(lisboaCaducados.getTotal()).thenReturn(1L);
        when(processoRepository.contarCaducadosPorEntidade(Set.of(EstadoProcesso.ANULADO, EstadoProcesso.ANULADO_INCM)))
                .thenReturn(List.of(lisboaCaducados));

        ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao lisboaUltima = mock(ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao.class);
        when(lisboaUltima.getEntidadeNome()).thenReturn("CM Lisboa");
        when(lisboaUltima.getUltimaPublicacao()).thenReturn(LocalDate.of(2026, 3, 15));
        // `getEntidadeNome()` nunca é chamado para esta linha — o filtro de `null` corta primeiro.
        ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao portoSemData = mock(ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao.class);
        when(portoSemData.getUltimaPublicacao()).thenReturn(null);
        when(processoRepository.ultimaPublicacaoPorEntidade(Set.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO)))
                .thenReturn(List.of(lisboaUltima, portoSemData));

        EstatisticasAdminResponse resposta = service.obter();

        assertThat(resposta.publicadosPorEntidade()).hasSize(2);
        EstatisticasAdminResponse.PublicadosPorEntidade lisboa = resposta.publicadosPorEntidade().get(0);
        assertThat(lisboa.entidade()).isEqualTo("CM Lisboa");
        assertThat(lisboa.pdm()).isEqualTo(2L);
        assertThat(lisboa.pp()).isEqualTo(14L);
        assertThat(lisboa.pu()).isZero();
        assertThat(lisboa.total()).isEqualTo(17L); // 2 + 14 + 1 (PEOT, fora das 4 colunas)
        assertThat(lisboa.caducados()).isEqualTo(1L);
        assertThat(lisboa.ultimaPublicacao()).isEqualTo(LocalDate.of(2026, 3, 15));

        EstatisticasAdminResponse.PublicadosPorEntidade porto = resposta.publicadosPorEntidade().get(1);
        assertThat(porto.entidade()).isEqualTo("CM Porto");
        assertThat(porto.pdm()).isEqualTo(1L);
        assertThat(porto.caducados()).isZero();
        assertThat(porto.ultimaPublicacao()).isNull();
    }

    @Test
    void deveRejeitarTecnico() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.obter()).isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(processoRepository);
    }
}
