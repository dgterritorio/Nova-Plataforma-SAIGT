package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.DinamicasDisponiveisResponse;
import com.conkord.saigt.dto.instrumento.DinamicasDisponiveisResponse.AtoPublicado;
import com.conkord.saigt.dto.instrumento.DinamicasDisponiveisResponse.DinamicaSemIgtEmCurso;
import com.conkord.saigt.dto.instrumento.DinamicasDisponiveisResponse.InstrumentoAbrangido;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * As Dinâmicas de tipos `inicio_sem_igt` em curso com pelo menos um processo da entidade da sessão ou criadas pelo
 * utilizador da sessão (a entidade DGT e os perfis internos veem todas), com os Procedimentos disponíveis pelas mesmas
 * regras de `GET /api/v1/instrumentos/{id}/disponivel`. Nunca revela processos que a sessão não vê.
 */
@Service
public class DinamicasSemIgtDisponiveisService {

    /** Sem entidade na sessão, nenhum processo é «da entidade»: um id que não existe. */
    private static final long SEM_ENTIDADE = -1L;

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ProcedimentosDisponiveisService procedimentosDisponiveisService;

    public DinamicasSemIgtDisponiveisService(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                             InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                                             AutorizacaoService autorizacaoService,
                                             ContextoUtilizadorResolver contextoUtilizadorResolver,
                                             ProcedimentosDisponiveisService procedimentosDisponiveisService) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.procedimentosDisponiveisService = procedimentosDisponiveisService;
    }

    @Transactional(readOnly = true)
    public DinamicasDisponiveisResponse disponivel() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        List<Dinamica> dinamicas = contexto.temVisibilidadeTransversal()
                ? dinamicaRepository.listarSemIgtComEstado(EstadoDinamica.EM_CURSO)
                : dinamicaRepository.listarSemIgtDaSessaoComEstado(EstadoDinamica.EM_CURSO, contexto.utilizadorId(),
                        contexto.entidadeId() == null ? SEM_ENTIDADE : contexto.entidadeId());
        if (dinamicas.isEmpty()) {
            return new DinamicasDisponiveisResponse(List.of());
        }

        ProcedimentosDisponiveisService.Sessao sessao = procedimentosDisponiveisService.sessao(contexto);
        List<UUID> instrumentoIds = dinamicas.stream().flatMap(dinamica -> dinamica.getInstrumentos().stream())
                .map(Instrumento::getId).distinct().toList();
        Map<UUID, List<String>> concelhos = instrumentoIds.isEmpty() ? Map.of()
                : instrumentoConcelhoRepository.findByIdInstrumentoIdInOrderByIdMunicipioCodigo(instrumentoIds).stream()
                        .collect(Collectors.groupingBy(concelho -> concelho.getId().getInstrumentoId(),
                                Collectors.mapping(InstrumentoConcelho::getMunicipioCodigo, Collectors.toList())));

        return new DinamicasDisponiveisResponse(dinamicas.stream()
                .map(dinamica -> new DinamicaSemIgtEmCurso(dinamica.getId(), dinamica.getNumeroDinamica(),
                        dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                        dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(), dinamica.getDescritivo(),
                        dinamica.getEstado().name(), dinamica.getDataInicio(), dinamica.getDataCaducidade(),
                        instrumentos(dinamica, concelhos), atoPublicado(dinamica, contexto),
                        procedimentosDisponiveisService.deDinamica(dinamica, sessao)))
                .toList());
    }

    private static List<InstrumentoAbrangido> instrumentos(Dinamica dinamica, Map<UUID, List<String>> concelhos) {
        return dinamica.getInstrumentos().stream()
                .sorted(Comparator.comparing(Instrumento::getDesignacao, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Instrumento::getId))
                .map(instrumento -> new InstrumentoAbrangido(instrumento.getId(), instrumento.getDesignacao(),
                        instrumento.getTipoInstrumento().getCodigo(), concelhos.getOrDefault(instrumento.getId(), List.of())))
                .toList();
    }

    private AtoPublicado atoPublicado(Dinamica dinamica, ContextoUtilizador contexto) {
        return processoRepository.findByDinamica(dinamica).stream()
                .filter(processo -> processo.getEstado() == EstadoProcesso.PUBLICADO)
                .filter(processo -> autorizacaoService.processoVisivel(contexto, processo))
                .max(Comparator.comparing(Processo::getDataPublicacaoDr, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Processo::getOcorrencia))
                .map(processo -> new AtoPublicado(processo.getId(), processo.getNumeroDr(), processo.getDataPublicacaoDr()))
                .orElse(null);
    }
}
