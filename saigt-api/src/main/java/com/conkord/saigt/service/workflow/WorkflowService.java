package com.conkord.saigt.service.workflow;

import com.conkord.saigt.dto.instrumento.TipoInstrumentoResponse;
import com.conkord.saigt.dto.workflow.PassoResponse;
import com.conkord.saigt.dto.workflow.WorkflowResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cada workflow é uma `instrumento_dinamica_config`; os passos são as `instrumento_procedimento_config`
 * ativas por `ordem`, com os tipos de entidade de `permissao_acao`. Só leitura: a configuração vem por seed.
 * Agregações em lote, nunca uma consulta por workflow/passo (N+1).
 */
@Service
public class WorkflowService {

    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final ProcessoRepository processoRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public WorkflowService(InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                            InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                            PermissaoAcaoRepository permissaoAcaoRepository,
                            ProcessoRepository processoRepository,
                            ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.processoRepository = processoRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listar() {
        exigirMaster();

        List<InstrumentoDinamicaConfig> configs = instrumentoDinamicaConfigRepository.listarTodosOrdenados();
        List<Long> configIds = configs.stream().map(InstrumentoDinamicaConfig::getId).toList();

        Map<Long, List<InstrumentoProcedimentoConfig>> passosPorConfig = instrumentoProcedimentoConfigRepository
                .listarAtivosPorConfigs(configIds).stream()
                .collect(Collectors.groupingBy(passo -> passo.getInstrumentoDinamicaConfig().getId()));

        List<Long> passoIds = passosPorConfig.values().stream()
                .flatMap(List::stream)
                .map(InstrumentoProcedimentoConfig::getId)
                .toList();

        Map<Long, List<String>> atoresPorPasso = permissaoAcaoRepository
                .findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(passoIds).stream()
                .collect(Collectors.groupingBy(permissaoAcao -> permissaoAcao.getInstrumentoProcedimentoConfig().getId(),
                        Collectors.mapping(permissaoAcao -> permissaoAcao.getTipoEntidade().name(), Collectors.toList())));

        Map<Long, Long> processosAtivosPorConfig = processoRepository
                .contarProcessosAtivosPorConfigDinamica(EstadoProcesso.TERMINAIS).stream()
                .collect(Collectors.toMap(
                        ProcessoRepository.ContagemProcessosAtivosProjecao::getConfigId,
                        ProcessoRepository.ContagemProcessosAtivosProjecao::getTotal));

        return configs.stream()
                .map(config -> paraResponse(config, passosPorConfig, atoresPorPasso, processosAtivosPorConfig))
                .toList();
    }

    private WorkflowResponse paraResponse(InstrumentoDinamicaConfig config,
                                           Map<Long, List<InstrumentoProcedimentoConfig>> passosPorConfig,
                                           Map<Long, List<String>> atoresPorPasso,
                                           Map<Long, Long> processosAtivosPorConfig) {
        List<PassoResponse> passos = passosPorConfig.getOrDefault(config.getId(), List.of()).stream()
                .map(passo -> new PassoResponse(passo.getTipoProcedimento().getNome(), passo.getOrdem(),
                        passo.isObrigatorio(), passo.getSerieDr(), passo.getPrazoDias(), passo.getTipoPrazo(),
                        atoresPorPasso.getOrDefault(passo.getId(), List.of())))
                .toList();

        String titulo = config.getTipoInstrumento().getCodigo() + " — " + config.getTipoDinamica().getNome();
        long processosAtivos = processosAtivosPorConfig.getOrDefault(config.getId(), 0L);

        return new WorkflowResponse(config.getId(), titulo,
                TipoInstrumentoResponse.de(config.getTipoInstrumento()), config.isAtivo(), passos.size(), processosAtivos, passos);
    }

    private void exigirMaster() {
        if (contextoUtilizadorResolver.resolver().perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode consultar workflows.");
        }
    }
}
