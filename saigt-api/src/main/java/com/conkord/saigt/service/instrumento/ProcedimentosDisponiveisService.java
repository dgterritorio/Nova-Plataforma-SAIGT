package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AtoReferivelResponse;
import com.conkord.saigt.dto.instrumento.DocumentoExigidoResponse;
import com.conkord.saigt.dto.instrumento.ProcedimentoDisponivelResponse;
import com.conkord.saigt.dto.instrumento.TentativaEmCursoResponse;
import com.conkord.saigt.dto.instrumento.TipoAtoDisponivelResponse;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.ExtensoesAceites;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Os `procedimentosDisponiveis` de uma Dinâmica em curso, partilhados por `GET /api/v1/instrumentos/{id}/disponivel` e
 * `GET /api/v1/dinamicas/disponivel`: Camada 3, sequenciamento, um processo em curso por Dinâmica e os tipos de ato pelo
 * claim. As regras vêm sempre de {@code AutorizacaoService}/{@code MotorRegrasService}, nunca reimplementadas aqui.
 */
@Component
public class ProcedimentosDisponiveisService {

    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final ProcessoRepository processoRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final TipoAtoRepository tipoAtoRepository;
    private final AutorizacaoService autorizacaoService;
    private final CatalogoFormularios catalogoFormularios;
    private final AtosReferiveis atosReferiveis;

    public ProcedimentosDisponiveisService(InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                           PermissaoAcaoRepository permissaoAcaoRepository, ProcessoRepository processoRepository,
                                           DocumentoConfigRepository documentoConfigRepository, TipoAtoRepository tipoAtoRepository,
                                           AutorizacaoService autorizacaoService, CatalogoFormularios catalogoFormularios,
                                           AtosReferiveis atosReferiveis) {
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.processoRepository = processoRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.tipoAtoRepository = tipoAtoRepository;
        this.autorizacaoService = autorizacaoService;
        this.catalogoFormularios = catalogoFormularios;
        this.atosReferiveis = atosReferiveis;
    }

    /** O que a sessão pode fazer num pedido: lido uma vez por pedido, nunca por Dinâmica nem por Procedimento (evita N+1). */
    public record Sessao(ContextoUtilizador contexto, Set<Long> configsAutorizados, List<TipoAto> catalogoSerie1,
                         List<TipoAto> catalogoSerie2) {
    }

    /**
     * Sem tipo de entidade na sessão, ou com a PCM, nenhum Procedimento pode ser autorizado nem Dinâmica iniciada
     * (mesma condição de {@code AutorizacaoService#verificarPossibilidadeDeIniciarDinamica}).
     */
    public Sessao sessao(ContextoUtilizador contexto) {
        Set<Long> configsAutorizados = autorizacaoService.tipoEntidadeQueCria(contexto)
                .map(permissaoAcaoRepository::findAllByTipoEntidadeAndAtivoTrue).orElse(List.of()).stream()
                .map(permissao -> permissao.getInstrumentoProcedimentoConfig().getId())
                .collect(Collectors.toSet());
        return new Sessao(contexto, configsAutorizados, tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull(),
                tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull());
    }

    /** Numa Dinâmica `TERMINADA`, só os Procedimentos posteriores ao final ({@code AutorizacaoService}), como na criação. */
    public List<ProcedimentoDisponivelResponse> deDinamica(Dinamica dinamica, Sessao sessao) {
        return dinamica.getEstado() == EstadoDinamica.TERMINADA
                ? deDinamica(dinamica, sessao, autorizacaoService.procedimentosPosterioresAoFinal(dinamica))
                : deDinamica(dinamica, sessao, null);
    }

    /**
     * Os `procedimentosDisponiveis` de cada Dinâmica `TERMINADA` que ainda tem Procedimentos posteriores ao final por fazer
     * e que a sessão pode iniciar, pelo id da Dinâmica; as que não oferecem nada à sessão ficam de fora.
     */
    public Map<UUID, List<ProcedimentoDisponivelResponse>> deDinamicasTerminadas(List<Dinamica> dinamicas, Sessao sessao) {
        Map<UUID, List<InstrumentoProcedimentoConfig>> posteriores = autorizacaoService.procedimentosPosterioresAoFinalPorDinamica(dinamicas);
        Map<UUID, List<ProcedimentoDisponivelResponse>> resultado = new LinkedHashMap<>();
        for (Dinamica dinamica : dinamicas) {
            List<InstrumentoProcedimentoConfig> admitidos = posteriores.getOrDefault(dinamica.getId(), List.of());
            List<ProcedimentoDisponivelResponse> procedimentos = admitidos.isEmpty() ? List.of() : deDinamica(dinamica, sessao, admitidos);
            if (!procedimentos.isEmpty()) {
                resultado.put(dinamica.getId(), procedimentos);
            }
        }
        return resultado;
    }

    /** `admitidos` nulo: todos os Procedimentos ativos da configuração são candidatos. */
    private List<ProcedimentoDisponivelResponse> deDinamica(Dinamica dinamica, Sessao sessao,
                                                            List<InstrumentoProcedimentoConfig> admitidos) {
        List<InstrumentoProcedimentoConfig> configs = instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamica.getInstrumentoDinamicaConfig());
        if (admitidos != null) {
            Set<Long> idsAdmitidos = admitidos.stream().map(InstrumentoProcedimentoConfig::getId).collect(Collectors.toSet());
            configs = configs.stream().filter(config -> idsAdmitidos.contains(config.getId())).toList();
        }

        Map<Long, Processo> ultimaTentativaPorConfig = ultimaTentativaPorConfig(dinamica, configs);
        Set<Long> bloqueadosPelaUltimaTentativa = configsBloqueadosPelaUltimaTentativa(ultimaTentativaPorConfig);
        List<Processo> processosEmCurso = autorizacaoService.processosEmCurso(dinamica);
        Set<Long> retomaveis = configsRetomaveis(processosEmCurso);
        ContextoUtilizador contexto = sessao.contexto();

        List<InstrumentoProcedimentoConfig> candidatos = configs.stream()
                .filter(config -> !bloqueadosPelaUltimaTentativa.contains(config.getId()))
                .filter(config -> sessao.configsAutorizados().contains(config.getId()))
                // O `POST` recusaria com `PARTE_DR_NAO_AUTORIZADA`: não se oferece o que a entidade não pode publicar.
                .filter(config -> TiposAtoDisponiveis.parteDrPublicavelPelaEntidade(config.getSerieDr(), contexto))
                // Retomar não cria processo: o Procedimento em `EM_SUBMISSAO` fica fora da regra do processo em curso.
                .filter(config -> autorizacaoService.procedimentoDisponivel(dinamica, config,
                        retomaveis.contains(config.getId()) ? List.of() : processosEmCurso))
                .toList();

        // Um ato pontual sem ato publicado que possa visar seria recusado no `POST` (REFERENCIA_INVALIDA): não se oferece.
        List<AtoReferivelResponse> referiveis = candidatos.stream().anyMatch(InstrumentoProcedimentoConfig::isAtoPontual)
                ? atosReferiveis.listar(dinamica).stream().map(AtoReferivelResponse::de).toList() : List.of();
        if (referiveis.isEmpty()) {
            candidatos = candidatos.stream().filter(config -> !config.isAtoPontual()).toList();
        }

        Map<Long, List<DocumentoConfig>> documentosPorConfig = documentoConfigRepository
                .findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(candidatos).stream()
                .collect(Collectors.groupingBy(documento -> documento.getInstrumentoProcedimentoConfig().getId()));

        return candidatos.stream()
                .map(config -> mapearProcedimento(config, contexto, documentosPorConfig.getOrDefault(config.getId(), List.of()),
                        sessao.catalogoSerie1(), sessao.catalogoSerie2(),
                        tentativaEmCurso(ultimaTentativaPorConfig.get(config.getId()), contexto),
                        config.isAtoPontual() ? referiveis : List.of()))
                .toList();
    }

    /**
     * O primeiro Procedimento (menor `ordem`) de uma Dinâmica ainda por iniciar, se o tipo da entidade o pode fazer; com os
     * mesmos documentos e tipos de ato que {@link #deDinamica} daria.
     */
    public Optional<ProcedimentoDisponivelResponse> procedimentoInicial(InstrumentoDinamicaConfig dinamicaConfig, Sessao sessao) {
        return instrumentoProcedimentoConfigRepository.findByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdem(dinamicaConfig)
                .stream().findFirst()
                .filter(config -> sessao.configsAutorizados().contains(config.getId()))
                .map(config -> mapearProcedimento(config, sessao.contexto(),
                        documentoConfigRepository.findByInstrumentoProcedimentoConfigInAndAtivoTrueOrderByOrdem(List.of(config)),
                        sessao.catalogoSerie1(), sessao.catalogoSerie2(), null, List.of()));
    }

    /**
     * O último processo de cada config na Tentativa corrente da Dinâmica — nunca uma consulta por Procedimento
     * (evita N+1). Os das Tentativas anteriores não contam: a nova segue o sequenciamento desde o início.
     */
    private Map<Long, Processo> ultimaTentativaPorConfig(Dinamica dinamica, List<InstrumentoProcedimentoConfig> configs) {
        Map<Long, Processo> ultimaPorConfig = new HashMap<>();
        for (Processo processo : processoRepository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
                dinamica, dinamica.getTentativa(), configs)) {
            ultimaPorConfig.merge(processo.getInstrumentoProcedimentoConfig().getId(), processo,
                    (atual, candidato) -> candidato.getOcorrencia() > atual.getOcorrencia() ? candidato : atual);
        }
        return ultimaPorConfig;
    }

    /**
     * Sai da lista o Procedimento cuja última tentativa não está anulada nem em `EM_SUBMISSAO`, salvo um
     * `repetivel` ou ato pontual com a última `PUBLICADO`: o `POST /dinamicas/{id}/processos` recusaria com 409
     * ({@code ProcessoService#proximaOcorrencia}). Em `EM_SUBMISSAO` continua candidato, com
     * `tentativaEmCurso` preenchido (retomar, não criar).
     */
    private Set<Long> configsBloqueadosPelaUltimaTentativa(Map<Long, Processo> ultimaTentativaPorConfig) {
        return ultimaTentativaPorConfig.entrySet().stream()
                .filter(entrada -> {
                    Processo ultima = entrada.getValue();
                    EstadoProcesso estado = ultima.getEstado();
                    boolean repetivelPublicado = ultima.getInstrumentoProcedimentoConfig().repeteDepoisDePublicado()
                            && estado == EstadoProcesso.PUBLICADO;
                    return !repetivelPublicado && !EstadoProcesso.PERMITEM_NOVA_TENTATIVA.contains(estado)
                            && estado != EstadoProcesso.EM_SUBMISSAO;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<Long> configsRetomaveis(List<Processo> processosEmCurso) {
        return processosEmCurso.stream()
                .filter(processo -> processo.getEstado() == EstadoProcesso.EM_SUBMISSAO)
                .map(processo -> processo.getInstrumentoProcedimentoConfig().getId())
                .collect(Collectors.toSet());
    }

    /**
     * `null` quando não há tentativa, ou a última foi anulada — só `EM_SUBMISSAO` marca o Procedimento como retomável —
     * ou quando o processo não é visível à sessão (Camada 2, {@code AutorizacaoService#processoVisivel}): nunca se expõe
     * a existência nem o id do processo de outra entidade.
     */
    private TentativaEmCursoResponse tentativaEmCurso(Processo ultimaTentativa, ContextoUtilizador contexto) {
        if (ultimaTentativa == null || ultimaTentativa.getEstado() != EstadoProcesso.EM_SUBMISSAO
                || !autorizacaoService.processoVisivel(contexto, ultimaTentativa)) {
            return null;
        }
        return new TentativaEmCursoResponse(ultimaTentativa.getId(), ultimaTentativa.getOcorrencia(), ultimaTentativa.getEstado().name());
    }

    private ProcedimentoDisponivelResponse mapearProcedimento(InstrumentoProcedimentoConfig config, ContextoUtilizador contexto,
                                                               List<DocumentoConfig> documentos,
                                                               List<TipoAto> catalogoSerie1, List<TipoAto> catalogoSerie2,
                                                               TentativaEmCursoResponse tentativaEmCurso,
                                                               List<AtoReferivelResponse> atosReferiveisDoProcedimento) {
        List<DocumentoExigidoResponse> documentosExigidos = documentos.stream()
                .map(documento -> new DocumentoExigidoResponse(documento.getId(), documento.getNome(),
                        documento.getCategoria().name(), documento.isObrigatorio(), ExtensoesAceites.ler(documento)))
                .toList();

        return new ProcedimentoDisponivelResponse(config.getId(), config.getTipoProcedimento().getCodigo(),
                config.getTipoProcedimento().getNome(), config.getOrdem(),
                config.isObrigatorio(), config.isAtoPontual(), config.getPrazoDias(), config.getSerieDr(),
                catalogoFormularios.formularioDe(config.getTipoProcedimento()).map(Enum::name).orElse(null), documentosExigidos,
                tiposAtoDisponiveis(config, contexto, catalogoSerie1, catalogoSerie2), tentativaEmCurso,
                atosReferiveisDoProcedimento);
    }

    /**
     * A regra de {@link TiposAtoDisponiveis}, com o catálogo lido uma vez por pedido. Sem série configurada (escolhida
     * no processo), todos os tipos de alguma série, cada um com as séries em que existe.
     */
    private List<TipoAtoDisponivelResponse> tiposAtoDisponiveis(InstrumentoProcedimentoConfig config, ContextoUtilizador contexto,
                                                                 List<TipoAto> catalogoSerie1, List<TipoAto> catalogoSerie2) {
        if (config.getSerieDr() == null) {
            return TiposAtoDisponiveis.comSeries(contexto, catalogoSerie1, catalogoSerie2);
        }
        return TiposAtoDisponiveis.paraResponse(
                TiposAtoDisponiveis.filtrar(config.getSerieDr(), contexto, catalogoSerie1, catalogoSerie2));
    }
}
