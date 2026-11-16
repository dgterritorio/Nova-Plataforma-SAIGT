package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.FiltroDocumentos;
import com.conkord.saigt.dto.documento.HistoricoDocumentalResponse;
import com.conkord.saigt.dto.documento.ProcessoDocumentoListagemResponse;
import com.conkord.saigt.dto.documento.ProcessoHistoricoDocumentalResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Listagem e acesso à descarga dos documentos do Processo (§14.4, §14.6). São leituras: só Camadas 1 e 2 — a Camada 3 é
 * sobre agir, e um perfil de consulta sem ator (`VISITANTE`) tem de conseguir ler.
 */
@Service
public class ProcessoDocumentoConsultaService {

    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public ProcessoDocumentoConsultaService(ProcessoRepository processoRepository,
                                            ProcessoDocumentoRepository processoDocumentoRepository,
                                            AutorizacaoService autorizacaoService,
                                            ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    /** Só os ficheiros enviados; os slots por preencher vêm da configuração do Procedimento. */
    @Transactional(readOnly = true)
    public Page<ProcessoDocumentoListagemResponse> listar(UUID processoId, FiltroDocumentos filtro, Pageable pagina) {
        Processo processo = carregarComVisibilidade(processoId);
        return processoDocumentoRepository.pesquisar(processo.getId(), filtro.categoria(), filtro.documentoConfigId(),
                        filtro.estadoValidacao(), pagina)
                .map(ProcessoDocumentoListagemResponse::de);
    }

    /** Os processos são da mesma Dinâmica e Instrumento do pedido, por isso a visibilidade do pedido vale para todos. */
    @Transactional(readOnly = true)
    public HistoricoDocumentalResponse historico(UUID processoId) {
        Processo referencia = carregarComVisibilidade(processoId);
        List<Processo> processos = processoRepository.findByDinamicaAndInstrumentoProcedimentoConfigOrderByOcorrenciaAsc(
                referencia.getDinamica(), referencia.getInstrumentoProcedimentoConfig());

        Map<UUID, List<ProcessoDocumento>> documentosPorProcesso = processoDocumentoRepository
                .findByProcesso_IdInOrderByCriadoEmAsc(processos.stream().map(Processo::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(documento -> documento.getProcesso().getId()));

        return new HistoricoDocumentalResponse(processos.stream()
                .map(processo -> ProcessoHistoricoDocumentalResponse.de(processo,
                        processo.getId().equals(referencia.getId()),
                        documentosPorProcesso.getOrDefault(processo.getId(), List.of())))
                .toList());
    }

    @Transactional(readOnly = true)
    public void validarAcessoDescarga(UUID processoId, UUID documentoId) {
        Processo processo = carregarComVisibilidade(processoId);
        processoDocumentoRepository.findByIdAndProcesso_Id(documentoId, processo.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
    }

    private Processo carregarComVisibilidade(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        return processo;
    }
}
