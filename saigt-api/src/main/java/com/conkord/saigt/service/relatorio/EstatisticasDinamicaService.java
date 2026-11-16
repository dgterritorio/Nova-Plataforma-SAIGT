package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.EstatisticasDinamicaResponse;
import com.conkord.saigt.dto.relatorio.EstatisticasProcessoDinamicaResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository.TransicoesPorOcorrenciaProjecao;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EstatisticasDinamicaService {

    private static final String TIPO_OCORRENCIA_PROCESSO = "PROCESSO";
    private static final String MENSAGEM_NAO_ENCONTRADA = "Dinâmica não encontrada.";

    private static final Comparator<Processo> POR_NUMERO_PROCESSO = Comparator
            .comparing((Processo processo) -> processo.getNumeroProcesso().length())
            .thenComparing(Processo::getNumeroProcesso);

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public EstatisticasDinamicaService(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                       AuditoriaRepository auditoriaRepository,
                                       IdusProcessoRepository idusProcessoRepository,
                                       AutorizacaoService autorizacaoService,
                                       ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.idusProcessoRepository = idusProcessoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public EstatisticasDinamicaResponse obter(UUID dinamicaId) {
        Dinamica dinamica = dinamicaRepository.findById(dinamicaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MENSAGEM_NAO_ENCONTRADA));
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        // As Dinâmicas são visíveis a todos; os processos listados são só os que a sessão pode ver (Camada 2).
        List<Processo> processos = processoRepository.findByDinamica(dinamica).stream()
                .filter(processo -> autorizacaoService.processoVisivel(contexto, processo))
                .sorted(POR_NUMERO_PROCESSO)
                .toList();
        if (processos.isEmpty()) {
            return new EstatisticasDinamicaResponse(dinamica.getId(), 0, 0, null, null, List.of());
        }

        List<String> ids = processos.stream().map(processo -> processo.getId().toString()).toList();
        Map<String, Long> rejeicoes = contarTransicoes(ids, EstadoProcesso.REJEITADO_DGT);
        Map<String, Long> anulacoes = contarTransicoes(ids, EstadoProcesso.ANULADO_INCM);
        Map<UUID, Short> submissoes = idusProcessoRepository.findAllById(
                        processos.stream().map(Processo::getId).toList()).stream()
                .collect(Collectors.toMap(IdusProcesso::getProcessoId, IdusProcesso::getSubmissao));

        List<EstatisticasProcessoDinamicaResponse> linhas = processos.stream()
                .map(processo -> new EstatisticasProcessoDinamicaResponse(processo.getId(),
                        processo.getNumeroProcesso(),
                        processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo(),
                        processo.getOcorrencia(),
                        rejeicoes.getOrDefault(processo.getId().toString(), 0L).intValue(),
                        anulacoes.getOrDefault(processo.getId().toString(), 0L).intValue(),
                        ciclosResubmissao(submissoes.get(processo.getId())),
                        null))
                .toList();
        return agregar(dinamica.getId(), linhas);
    }

    private Map<String, Long> contarTransicoes(List<String> idsProcessos, EstadoProcesso estadoDepois) {
        return auditoriaRepository.contarTransicoesPorOcorrencia(TIPO_OCORRENCIA_PROCESSO, idsProcessos, estadoDepois.name())
                .stream()
                .collect(Collectors.toMap(TransicoesPorOcorrenciaProjecao::getIdOcorrencia,
                        TransicoesPorOcorrenciaProjecao::getTotal));
    }

    static Integer ciclosResubmissao(Short submissao) {
        if (submissao == null) {
            return null;
        }
        return Math.max(submissao - 1, 0);
    }

    static EstatisticasDinamicaResponse agregar(UUID dinamicaId, List<EstatisticasProcessoDinamicaResponse> linhas) {
        int rejeicoes = linhas.stream().mapToInt(EstatisticasProcessoDinamicaResponse::numeroRejeicoesDgt).sum();
        int anulacoes = linhas.stream().mapToInt(EstatisticasProcessoDinamicaResponse::numeroAnulacoesIncm).sum();
        List<Integer> ciclos = linhas.stream()
                .map(EstatisticasProcessoDinamicaResponse::numeroCiclosResubmissao)
                .filter(Objects::nonNull)
                .toList();
        Integer totalCiclos = ciclos.isEmpty() ? null : ciclos.stream().mapToInt(Integer::intValue).sum();
        return new EstatisticasDinamicaResponse(dinamicaId, rejeicoes, anulacoes, totalCiclos, null, linhas);
    }
}
