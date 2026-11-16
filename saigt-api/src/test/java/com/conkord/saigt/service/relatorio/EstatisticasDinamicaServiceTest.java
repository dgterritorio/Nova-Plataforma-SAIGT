package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.EstatisticasDinamicaResponse;
import com.conkord.saigt.dto.relatorio.EstatisticasProcessoDinamicaResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository.TransicoesPorOcorrenciaProjecao;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstatisticasDinamicaServiceTest {

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final AuditoriaRepository auditoriaRepository = mock(AuditoriaRepository.class);
    private final IdusProcessoRepository idusProcessoRepository = mock(IdusProcessoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final ContextoUtilizador contexto =
            new ContextoUtilizador(UUID.randomUUID(), "INCM", null, null, null, null, null, null, null, null, null);

    private final Dinamica dinamica = mock(Dinamica.class);
    private final UUID dinamicaId = UUID.randomUUID();

    private final EstatisticasDinamicaService service = new EstatisticasDinamicaService(dinamicaRepository,
            processoRepository, auditoriaRepository, idusProcessoRepository, autorizacaoService, contextoUtilizadorResolver);

    @BeforeEach
    void preparar() {
        when(dinamica.getId()).thenReturn(dinamicaId);
        when(dinamicaRepository.findById(dinamicaId)).thenReturn(Optional.of(dinamica));
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(autorizacaoService.processoVisivel(eq(contexto), any())).thenReturn(true);
        when(auditoriaRepository.contarTransicoesPorOcorrencia(any(), anyCollection(), any())).thenReturn(List.of());
        when(idusProcessoRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void deveSomarAsContagensDosProcessosEOrdenarPorNumeroDeProcesso() {
        Processo dez = processo("10", "APROVACAO", 1);
        Processo nove = processo("9", "DECISAO_INICIO", 2);
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(dez, nove));
        List<String> ids = List.of(nove.getId().toString(), dez.getId().toString());
        List<TransicoesPorOcorrenciaProjecao> rejeicoes = List.of(total(nove, 2), total(dez, 1));
        List<TransicoesPorOcorrenciaProjecao> anulacoes = List.of(total(dez, 1));
        when(auditoriaRepository.contarTransicoesPorOcorrencia("PROCESSO", ids, "REJEITADO_DGT")).thenReturn(rejeicoes);
        when(auditoriaRepository.contarTransicoesPorOcorrencia("PROCESSO", ids, "ANULADO_INCM")).thenReturn(anulacoes);

        EstatisticasDinamicaResponse resposta = service.obter(dinamicaId);

        assertThat(resposta.dinamicaId()).isEqualTo(dinamicaId);
        assertThat(resposta.numeroRejeicoesDgt()).isEqualTo(3);
        assertThat(resposta.numeroAnulacoesIncm()).isEqualTo(1);
        assertThat(resposta.numeroComunicacoes()).isNull();
        assertThat(resposta.processos()).extracting(EstatisticasProcessoDinamicaResponse::numeroProcesso)
                .containsExactly("9", "10");
        EstatisticasProcessoDinamicaResponse primeira = resposta.processos().get(0);
        assertThat(primeira.processoId()).isEqualTo(nove.getId());
        assertThat(primeira.tipoProcedimento()).isEqualTo("DECISAO_INICIO");
        assertThat(primeira.ocorrencia()).isEqualTo(2);
        assertThat(primeira.numeroRejeicoesDgt()).isEqualTo(2);
        assertThat(primeira.numeroAnulacoesIncm()).isZero();
        assertThat(primeira.numeroComunicacoes()).isNull();
        assertThat(resposta.processos().get(1).numeroAnulacoesIncm()).isEqualTo(1);
    }

    @Test
    void deveCalcularOsCiclosAPartirDoSubContadorSubmissao() {
        Processo tresSubmissoes = processo("1", "APROVACAO", 1);
        Processo semSubmissao = processo("2", "APROVACAO", 1);
        Processo semIdus = processo("3", "APROVACAO", 1);
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(tresSubmissoes, semSubmissao, semIdus));
        List<IdusProcesso> idus = List.of(idus(tresSubmissoes, 3), idus(semSubmissao, 0));
        when(idusProcessoRepository.findAllById(any())).thenReturn(idus);

        EstatisticasDinamicaResponse resposta = service.obter(dinamicaId);

        assertThat(resposta.processos()).extracting(EstatisticasProcessoDinamicaResponse::numeroCiclosResubmissao)
                .containsExactly(2, 0, null);
        assertThat(resposta.numeroCiclosResubmissao()).isEqualTo(2);
    }

    @Test
    void cicloAgregadoFicaNuloQuandoNenhumProcessoTemIdus() {
        List<Processo> processos = List.of(processo("1", "APROVACAO", 1));
        when(processoRepository.findByDinamica(dinamica)).thenReturn(processos);

        EstatisticasDinamicaResponse resposta = service.obter(dinamicaId);

        assertThat(resposta.numeroCiclosResubmissao()).isNull();
        assertThat(resposta.processos().get(0).numeroCiclosResubmissao()).isNull();
    }

    @Test
    void deveContarESomarSoOsProcessosVisiveis() {
        Processo daEntidadeA = processo("1", "APROVACAO", 1);
        Processo daEntidadeB = processo("2", "APROVACAO", 1);
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of(daEntidadeA, daEntidadeB));
        when(autorizacaoService.processoVisivel(contexto, daEntidadeB)).thenReturn(false);
        List<TransicoesPorOcorrenciaProjecao> rejeicoes = List.of(total(daEntidadeA, 1));
        when(auditoriaRepository.contarTransicoesPorOcorrencia("PROCESSO", List.of(daEntidadeA.getId().toString()),
                "REJEITADO_DGT")).thenReturn(rejeicoes);

        EstatisticasDinamicaResponse resposta = service.obter(dinamicaId);

        assertThat(resposta.processos()).extracting(EstatisticasProcessoDinamicaResponse::processoId)
                .containsExactly(daEntidadeA.getId());
        assertThat(resposta.numeroRejeicoesDgt()).isEqualTo(1);
    }

    @Test
    void semProcessosVisiveisDevolveTotaisAZeroSemConsultarAuditoria() {
        when(processoRepository.findByDinamica(dinamica)).thenReturn(List.of());

        EstatisticasDinamicaResponse resposta = service.obter(dinamicaId);

        assertThat(resposta.processos()).isEmpty();
        assertThat(resposta.numeroRejeicoesDgt()).isZero();
        assertThat(resposta.numeroCiclosResubmissao()).isNull();
        verify(auditoriaRepository, never()).contarTransicoesPorOcorrencia(any(), anyCollection(), any());
    }

    @Test
    void dinamicaInexistenteDaOMesmoErro() {
        UUID inexistente = UUID.randomUUID();
        when(dinamicaRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(inexistente))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Dinâmica não encontrada.");
        verify(processoRepository, never()).findByDinamica(any());
    }

    private static Processo processo(String numero, String codigoProcedimento, int ocorrencia) {
        TipoProcedimento tipoProcedimento = mock(TipoProcedimento.class);
        when(tipoProcedimento.getCodigo()).thenReturn(codigoProcedimento);
        InstrumentoProcedimentoConfig config = mock(InstrumentoProcedimentoConfig.class);
        when(config.getTipoProcedimento()).thenReturn(tipoProcedimento);
        Processo processo = mock(Processo.class);
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(processo.getNumeroProcesso()).thenReturn(numero);
        when(processo.getOcorrencia()).thenReturn(ocorrencia);
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(config);
        return processo;
    }

    private static IdusProcesso idus(Processo processo, int submissao) {
        UUID processoId = processo.getId();
        IdusProcesso idus = mock(IdusProcesso.class);
        when(idus.getProcessoId()).thenReturn(processoId);
        when(idus.getSubmissao()).thenReturn((short) submissao);
        return idus;
    }

    private static TransicoesPorOcorrenciaProjecao total(Processo processo, long total) {
        String id = processo.getId().toString();
        return new TransicoesPorOcorrenciaProjecao() {
            @Override
            public String getIdOcorrencia() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
