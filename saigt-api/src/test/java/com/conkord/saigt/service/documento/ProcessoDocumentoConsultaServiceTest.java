package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.FiltroDocumentos;
import com.conkord.saigt.dto.documento.HistoricoDocumentalResponse;
import com.conkord.saigt.dto.documento.ProcessoDocumentoListagemResponse;
import com.conkord.saigt.dto.documento.ProcessoHistoricoDocumentalResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessoDocumentoConsultaServiceTest {

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final ProcessoDocumentoConsultaService service = new ProcessoDocumentoConsultaService(
            processoRepository, processoDocumentoRepository, autorizacaoService, contextoUtilizadorResolver);

    private final Processo processo = mock(Processo.class, RETURNS_DEEP_STUBS);
    private final UUID processoId = UUID.randomUUID();

    @Test
    void deveVerificarVisibilidadeAntesDeConsultar() {
        when(processo.getId()).thenReturn(processoId);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(processo));
        Pageable pagina = PageRequest.of(1, 20);
        FiltroDocumentos filtro = new FiltroDocumentos(CategoriaDocumento.PUBLICACAO, 7L, EstadoValidacao.POR_VALIDAR);
        when(processoDocumentoRepository.pesquisar(processoId, CategoriaDocumento.PUBLICACAO, 7L,
                EstadoValidacao.POR_VALIDAR, pagina)).thenReturn(new PageImpl<>(List.of(), pagina, 0));

        Page<ProcessoDocumentoListagemResponse> resultado = service.listar(processoId, filtro, pagina);

        assertThat(resultado.getContent()).isEmpty();
        InOrder ordem = inOrder(autorizacaoService, processoDocumentoRepository);
        ordem.verify(autorizacaoService).verificarVisibilidade(any(), any());
        ordem.verify(processoDocumentoRepository).pesquisar(processoId, CategoriaDocumento.PUBLICACAO, 7L,
                EstadoValidacao.POR_VALIDAR, pagina);
    }

    @Test
    void naoDevePermitirDescargaDeDocumentoDeOutroProcesso() {
        UUID documentoId = UUID.randomUUID();
        when(processo.getId()).thenReturn(processoId);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(processo));
        when(processoDocumentoRepository.findByIdAndProcesso_Id(documentoId, processoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validarAcessoDescarga(processoId, documentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(autorizacaoService).verificarVisibilidade(any(), any());
    }

    private Processo ocorrencia(UUID id, int numero, EstadoProcesso estado) {
        Processo p = mock(Processo.class, RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn(id);
        when(p.getOcorrencia()).thenReturn(numero);
        when(p.getEstado()).thenReturn(estado);
        when(p.getNumeroProcesso()).thenReturn("PROC-" + numero);
        return p;
    }

    private ProcessoDocumento documentoDe(Processo dono, String nome) {
        ProcessoDocumento d = mock(ProcessoDocumento.class, RETURNS_DEEP_STUBS);
        when(d.getId()).thenReturn(UUID.randomUUID());
        when(d.getProcesso()).thenReturn(dono);
        when(d.getNomeFicheiro()).thenReturn(nome);
        when(d.getCriadoPor().getId()).thenReturn(UUID.randomUUID());
        when(d.getCriadoPor().getNome()).thenReturn("Ana");
        return d;
    }

    @Test
    void deveDevolverOsProcessosDoProcedimentoPorOcorrenciaComOsSeusFicheiros() {
        UUID primeiroId = UUID.randomUUID();
        Processo primeiro = ocorrencia(primeiroId, 1, EstadoProcesso.PUBLICADO);
        when(primeiro.getNumeroDr()).thenReturn("123/2026");
        when(primeiro.getLinkDr()).thenReturn("https://dr.pt/123");
        Processo segundo = ocorrencia(processoId, 2, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(segundo));
        when(processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(any(), any()))
                .thenReturn(List.of(primeiro, segundo));
        List<ProcessoDocumento> documentos = List.of(documentoDe(primeiro, "a.pdf"), documentoDe(segundo, "c.pdf"),
                documentoDe(primeiro, "b.pdf"));
        when(processoDocumentoRepository.findByProcesso_IdInOrderByCriadoEmAsc(List.of(primeiroId, processoId)))
                .thenReturn(documentos);

        HistoricoDocumentalResponse resposta = service.historico(processoId);

        assertThat(resposta.processos()).extracting(ProcessoHistoricoDocumentalResponse::ocorrencia)
                .containsExactly(1, 2);
        assertThat(resposta.processos()).extracting(ProcessoHistoricoDocumentalResponse::atual)
                .containsExactly(false, true);
        assertThat(resposta.processos().get(0).numeroDr()).isEqualTo("123/2026");
        assertThat(resposta.processos().get(0).linkDr()).isEqualTo("https://dr.pt/123");
        assertThat(resposta.processos().get(0).documentos()).extracting(ProcessoDocumentoListagemResponse::nomeFicheiro)
                .containsExactly("a.pdf", "b.pdf");
        assertThat(resposta.processos().get(1).documentos()).extracting(ProcessoDocumentoListagemResponse::nomeFicheiro)
                .containsExactly("c.pdf");
        verify(processoDocumentoRepository).findByProcesso_IdInOrderByCriadoEmAsc(any());
    }

    @Test
    void deveDevolverUmSoProcessoMarcadoComoAtual() {
        Processo unico = ocorrencia(processoId, 1, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(unico));
        when(processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(any(), any()))
                .thenReturn(List.of(unico));
        when(processoDocumentoRepository.findByProcesso_IdInOrderByCriadoEmAsc(List.of(processoId)))
                .thenReturn(List.of());

        HistoricoDocumentalResponse resposta = service.historico(processoId);

        assertThat(resposta.processos()).singleElement().satisfies(p -> {
            assertThat(p.atual()).isTrue();
            assertThat(p.documentos()).isEmpty();
        });
    }

    @Test
    void deveIncluirOProcessoAnuladoComOEstadoDeCadaUm() {
        UUID anuladoId = UUID.randomUUID();
        Processo anulado = ocorrencia(anuladoId, 1, EstadoProcesso.ANULADO);
        Processo novo = ocorrencia(processoId, 2, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(anuladoId)).thenReturn(Optional.of(anulado));
        when(processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(any(), any()))
                .thenReturn(List.of(anulado, novo));
        when(processoDocumentoRepository.findByProcesso_IdInOrderByCriadoEmAsc(any())).thenReturn(List.of());

        HistoricoDocumentalResponse resposta = service.historico(anuladoId);

        assertThat(resposta.processos()).extracting(ProcessoHistoricoDocumentalResponse::estado)
                .containsExactly(EstadoProcesso.ANULADO, EstadoProcesso.EM_SUBMISSAO);
        assertThat(resposta.processos()).extracting(ProcessoHistoricoDocumentalResponse::atual)
                .containsExactly(true, false);
    }

    @Test
    void deveVerificarVisibilidadeAntesDeConsultarOHistorico() {
        Processo unico = ocorrencia(processoId, 1, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(unico));
        when(processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(any(), any()))
                .thenReturn(List.of(unico));
        when(processoDocumentoRepository.findByProcesso_IdInOrderByCriadoEmAsc(any())).thenReturn(List.of());

        service.historico(processoId);

        InOrder ordem = inOrder(autorizacaoService, processoRepository);
        ordem.verify(autorizacaoService).verificarVisibilidade(any(), any());
        ordem.verify(processoRepository).findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(any(), any());
    }

    @Test
    void naoDeveConsultarOHistoricoQuandoOProcessoNaoExiste() {
        when(processoRepository.findById(processoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historico(processoId)).isInstanceOf(RecursoNaoEncontradoException.class);

        verify(processoDocumentoRepository, never()).findByProcesso_IdInOrderByCriadoEmAsc(any());
    }

    @Test
    void naoDeveConsultarOsFicheirosQuandoAVisibilidadeFalha() {
        Processo unico = ocorrencia(processoId, 1, EstadoProcesso.EM_SUBMISSAO);
        when(processoRepository.findById(processoId)).thenReturn(Optional.of(unico));
        doThrow(new RecursoNaoEncontradoException("Processo não encontrado."))
                .when(autorizacaoService).verificarVisibilidade(any(), any());

        assertThatThrownBy(() -> service.historico(processoId)).isInstanceOf(RecursoNaoEncontradoException.class);

        verify(processoDocumentoRepository, never()).findByProcesso_IdInOrderByCriadoEmAsc(any());
    }

    @Test
    void naoDeveConsultarQuandoProcessoNaoExiste() {
        when(processoRepository.findById(processoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(processoId, FiltroDocumentos.SEM_FILTRO, PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(processoDocumentoRepository, never()).pesquisar(any(), any(), any(), any(), any());
    }
}
