package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.TiposProcessoResponse;
import com.conkord.saigt.dto.instrumento.TiposProcessoResponse.ConfiguracaoSemIgt;
import com.conkord.saigt.dto.instrumento.TiposProcessoResponse.TipoComAssociacaoIgt;
import com.conkord.saigt.dto.instrumento.TiposProcessoResponse.TipoSemAssociacaoIgt;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * O que a sessão pode criar. Sem IGT, só o que inicia, pela regra de {@link AutorizacaoService#podeIniciarDinamica}
 * (Procedimento inicial com `permissao_acao` ativa do tipo de entidade da sessão). Com IGT, também o que continua:
 * basta `permissao_acao` num Procedimento ativo da configuração, porque quem só age mais adiante (a CCDR na
 * Constituição da CC) escolhe o IGT de uma Dinâmica já em curso. A entidade DGT recebe todas. Sem códigos de Dinâmica
 * nem de tipo de entidade: decide `tipo_dinamica.inicio_sem_igt`.
 */
@Service
public class TiposProcessoService {

    private static final Logger log = LoggerFactory.getLogger(TiposProcessoService.class);
    private static final Set<String> MODULOS = Set.of("PLANO", "PROGRAMA", "SRUP");
    /** EF v1.5 §2.2.4: o grupo «Processo sem Associação a IGT» é do módulo Planos. */
    private static final String MODULO_DO_GRUPO_SEM_IGT = "PLANO";

    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public TiposProcessoService(InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                                InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                PermissaoAcaoRepository permissaoAcaoRepository, AutorizacaoService autorizacaoService,
                                ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    /** @param modulo `PLANO`, `PROGRAMA`, `SRUP` ou nulo (todos) */
    @Transactional(readOnly = true)
    public TiposProcessoResponse tiposProcesso(String modulo) {
        if (modulo != null && !MODULOS.contains(modulo)) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.",
                    List.of(new ValidacaoException.CampoErro("modulo", "Use PLANO, PROGRAMA ou SRUP.")));
        }
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != null || contexto.entidadeId() == null) {
            log.warn("Tipos de processo pedidos sem entidade na sessão | utilizadorId={}", contexto.utilizadorId());
            throw new AcaoNaoAutorizadaException("Só uma sessão com entidade pode criar processos.");
        }

        List<InstrumentoDinamicaConfig> configuracoes = configuracoesAtivas().stream()
                .filter(config -> modulo == null || modulo.equals(config.getTipoInstrumento().getCategoria()))
                .toList();
        Map<Long, List<InstrumentoProcedimentoConfig>> procedimentos = procedimentosPorConfig(configuracoes);
        Predicate<InstrumentoProcedimentoConfig> autorizado = autorizacao(contexto);

        List<TipoComAssociacaoIgt> comAssociacao = configuracoes.stream()
                .filter(config -> !config.getTipoDinamica().isInicioSemIgt())
                .filter(config -> procedimentos.getOrDefault(config.getId(), List.of()).stream().anyMatch(autorizado))
                .map(InstrumentoDinamicaConfig::getTipoInstrumento)
                .collect(Collectors.toMap(TipoInstrumento::getId, tipo -> tipo, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .map(tipo -> new TipoComAssociacaoIgt(tipo.getId(), tipo.getCodigo(), tipo.getNome()))
                .toList();

        List<TipoSemAssociacaoIgt> semAssociacao = modulo != null && !MODULO_DO_GRUPO_SEM_IGT.equals(modulo)
                ? List.of()
                : configuracoes.stream()
                        .filter(config -> config.getTipoDinamica().isInicioSemIgt())
                        .filter(config -> procedimentos.getOrDefault(config.getId(), List.of()).stream().findFirst()
                                .filter(autorizado).isPresent())
                        .collect(Collectors.groupingBy(InstrumentoDinamicaConfig::getTipoDinamica, LinkedHashMap::new,
                                Collectors.toList()))
                        .entrySet().stream()
                        .sorted(Map.Entry.comparingByKey((a, b) -> a.getId().compareTo(b.getId())))
                        .map(grupo -> semAssociacao(grupo.getKey(), grupo.getValue()))
                        .toList();

        return new TiposProcessoResponse(comAssociacao, semAssociacao);
    }

    /** As ativas, de tipos ativos; por tipo de Instrumento e de Dinâmica. */
    private List<InstrumentoDinamicaConfig> configuracoesAtivas() {
        return instrumentoDinamicaConfigRepository.listarTodosOrdenados().stream()
                .filter(InstrumentoDinamicaConfig::isAtivo)
                .filter(config -> config.getTipoInstrumento().isAtivo() && config.getTipoDinamica().isAtivo())
                .toList();
    }

    /** Os Procedimentos ativos de cada configuração, por `ordem`: o primeiro é o inicial. */
    private Map<Long, List<InstrumentoProcedimentoConfig>> procedimentosPorConfig(List<InstrumentoDinamicaConfig> configuracoes) {
        if (configuracoes.isEmpty()) {
            return Map.of();
        }
        return instrumentoProcedimentoConfigRepository
                .listarAtivosPorConfigs(configuracoes.stream().map(InstrumentoDinamicaConfig::getId).toList()).stream()
                .collect(Collectors.groupingBy(procedimento -> procedimento.getInstrumentoDinamicaConfig().getId()));
    }

    private Predicate<InstrumentoProcedimentoConfig> autorizacao(ContextoUtilizador contexto) {
        if (contexto.entidadeDgt()) {
            return procedimento -> true;
        }
        Set<Long> autorizados = autorizacaoService.tipoEntidadeQueCria(contexto)
                .map(permissaoAcaoRepository::findAllByTipoEntidadeAndAtivoTrue).orElse(List.of()).stream()
                .map(PermissaoAcao::getInstrumentoProcedimentoConfig)
                .map(InstrumentoProcedimentoConfig::getId)
                .collect(Collectors.toSet());
        return procedimento -> autorizados.contains(procedimento.getId());
    }

    private static TipoSemAssociacaoIgt semAssociacao(TipoDinamica tipo, List<InstrumentoDinamicaConfig> configuracoes) {
        return new TipoSemAssociacaoIgt(tipo.getId(), tipo.getCodigo(), tipo.getNome(), configuracoes.stream()
                .map(config -> new ConfiguracaoSemIgt(config.getId(), config.getTipoInstrumento().getId(),
                        config.getTipoInstrumento().getCodigo(), config.getTipoInstrumento().getNome(),
                        config.getAssociacaoInstrumentos().name()))
                .toList());
    }
}
