package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.DinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.DinamicaConfigResponse;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dinâmicas de cada tipo de Instrumento e Procedimentos de cada Dinâmica. Só `MASTER`, também nas leituras. Sem
 * `DELETE`: desativar retira a linha da oferta e do sequenciamento sem afetar Dinâmicas nem processos em curso. A
 * chave (o par de tipos) só muda enquanto não houver Dinâmicas ou processos dessa linha; os restantes campos mudam
 * sempre. Criar uma linha não cria permissões, itens documentais nem regras: configuram-se à parte.
 */
@Slf4j
@Service
public class ConfiguracaoEstruturaService {

    private static final String TIPO_OCORRENCIA = "CONFIGURACAO";
    private static final String RECURSO_DINAMICAS = "dinamicas-config";
    private static final String RECURSO_PROCEDIMENTOS = "procedimentos-config";

    private final InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final TipoDinamicaRepository tipoDinamicaRepository;
    private final TipoProcedimentoRepository tipoProcedimentoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final CatalogoFormularios catalogoFormularios;
    private final AuditoriaService auditoriaService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public ConfiguracaoEstruturaService(InstrumentoDinamicaConfigRepository dinamicaConfigRepository,
                                        InstrumentoProcedimentoConfigRepository procedimentoConfigRepository,
                                        TipoInstrumentoRepository tipoInstrumentoRepository,
                                        TipoDinamicaRepository tipoDinamicaRepository,
                                        TipoProcedimentoRepository tipoProcedimentoRepository,
                                        DinamicaRepository dinamicaRepository,
                                        ProcessoRepository processoRepository,
                                        CatalogoFormularios catalogoFormularios,
                                        AuditoriaService auditoriaService,
                                        AutorizacaoService autorizacaoService,
                                        ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.dinamicaConfigRepository = dinamicaConfigRepository;
        this.procedimentoConfigRepository = procedimentoConfigRepository;
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.tipoDinamicaRepository = tipoDinamicaRepository;
        this.tipoProcedimentoRepository = tipoProcedimentoRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.catalogoFormularios = catalogoFormularios;
        this.auditoriaService = auditoriaService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public Page<DinamicaConfigResponse> listarDinamicas(Long tipoInstrumentoId, Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_DINAMICAS);
        Page<InstrumentoDinamicaConfig> configs = dinamicaConfigRepository.pesquisar(tipoInstrumentoId, ativo, pagina);
        List<Long> ids = configs.getContent().stream().map(InstrumentoDinamicaConfig::getId).toList();
        Set<Long> emUso = ids.isEmpty() ? Set.of() : dinamicaRepository.configsComDinamicas(ids);
        return configs.map(config -> DinamicaConfigResponse.de(config, emUso.contains(config.getId())));
    }

    @Transactional
    public DinamicaConfigResponse criarDinamica(DinamicaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_DINAMICAS);
        TiposDaDinamica tipos = tiposDaDinamica(request);
        if (dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_Id(request.tipoInstrumentoId(),
                request.tipoDinamicaId())) {
            throw dinamicaDuplicada();
        }
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipos.instrumento(), tipos.dinamica());
        config.setAssociacaoInstrumentos(request.associacaoInstrumentos());
        config.setAtivo(request.ativo());
        config = dinamicaConfigRepository.save(config);

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("tipoInstrumentoId", String.valueOf(request.tipoInstrumentoId()));
        dados.put("tipoDinamicaId", String.valueOf(request.tipoDinamicaId()));
        dados.put("associacaoInstrumentos", request.associacaoInstrumentos().name());
        dados.put("ativo", String.valueOf(request.ativo()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_DINAMICAS, config.getId()), "CRIAR", atorId, dados);
        return DinamicaConfigResponse.de(config, false);
    }

    @Transactional
    public DinamicaConfigResponse atualizarDinamica(Long id, DinamicaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_DINAMICAS);
        InstrumentoDinamicaConfig config = dinamicaConfigRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Dinâmica não encontrada."));
        TiposDaDinamica tipos = tiposDaDinamica(request);
        boolean emUso = !dinamicaRepository.configsComDinamicas(List.of(id)).isEmpty();

        Map<String, String> alterados = new LinkedHashMap<>();
        boolean instrumentoMuda = !config.getTipoInstrumento().getId().equals(request.tipoInstrumentoId());
        boolean dinamicaMuda = !config.getTipoDinamica().getId().equals(request.tipoDinamicaId());
        if (instrumentoMuda || dinamicaMuda) {
            if (dinamicaConfigRepository.existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(
                    request.tipoInstrumentoId(), request.tipoDinamicaId(), id)) {
                throw dinamicaDuplicada();
            }
            if (emUso) {
                throw new ConfiguracaoEmUsoException();
            }
            if (instrumentoMuda) {
                config.setTipoInstrumento(tipos.instrumento());
                alterados.put("tipoInstrumentoId", String.valueOf(request.tipoInstrumentoId()));
            }
            if (dinamicaMuda) {
                config.setTipoDinamica(tipos.dinamica());
                alterados.put("tipoDinamicaId", String.valueOf(request.tipoDinamicaId()));
            }
        }
        if (config.getAssociacaoInstrumentos() != request.associacaoInstrumentos()) {
            config.setAssociacaoInstrumentos(request.associacaoInstrumentos());
            alterados.put("associacaoInstrumentos", request.associacaoInstrumentos().name());
        }
        if (config.isAtivo() != request.ativo()) {
            config.setAtivo(request.ativo());
            alterados.put("ativo", String.valueOf(request.ativo()));
        }

        dinamicaConfigRepository.save(config);
        auditarAtualizacao(RECURSO_DINAMICAS, id, atorId, alterados);
        return DinamicaConfigResponse.de(config, emUso);
    }

    @Transactional(readOnly = true)
    public Page<ProcedimentoConfigResponse> listarProcedimentos(Long instrumentoDinamicaConfigId, Boolean ativo,
                                                                Pageable pagina) {
        exigirMaster(RECURSO_PROCEDIMENTOS);
        Page<InstrumentoProcedimentoConfig> configs = procedimentoConfigRepository.pesquisar(
                instrumentoDinamicaConfigId, ativo, pagina);
        List<Long> ids = configs.getContent().stream().map(InstrumentoProcedimentoConfig::getId).toList();
        Set<Long> emUso = ids.isEmpty() ? Set.of() : processoRepository.configsComProcessos(ids);
        return configs.map(config -> resposta(config, emUso.contains(config.getId())));
    }

    @Transactional
    public ProcedimentoConfigResponse criarProcedimento(ProcedimentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_PROCEDIMENTOS);
        ReferenciasDoProcedimento referencias = referenciasDoProcedimento(request);
        if (procedimentoConfigRepository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_Id(
                request.instrumentoDinamicaConfigId(), request.tipoProcedimentoId())) {
            throw procedimentoDuplicado();
        }
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(referencias.dinamicaConfig(),
                referencias.tipoProcedimento(), request.ordem(), request.serieDr());
        config.setObrigatorio(request.obrigatorio());
        config.setTamanhoMaximoProcesso(request.tamanhoMaximoProcesso());
        config.setRepetivel(request.repetivel());
        config.setAtoPontual(request.atoPontual());
        config.setAtivo(request.ativo());
        config.setVisivelNaDinamica(Boolean.TRUE.equals(request.visivelNaDinamica()));
        config = procedimentoConfigRepository.save(config);

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("instrumentoDinamicaConfigId", String.valueOf(request.instrumentoDinamicaConfigId()));
        dados.put("tipoProcedimentoId", String.valueOf(request.tipoProcedimentoId()));
        dados.put("ordem", String.valueOf(request.ordem()));
        dados.put("obrigatorio", String.valueOf(request.obrigatorio()));
        dados.put("serieDr", request.serieDr());
        dados.put("tamanhoMaximoProcesso", String.valueOf(request.tamanhoMaximoProcesso()));
        dados.put("repetivel", String.valueOf(request.repetivel()));
        dados.put("atoPontual", String.valueOf(request.atoPontual()));
        dados.put("ativo", String.valueOf(request.ativo()));
        dados.put("visivelNaDinamica", String.valueOf(config.isVisivelNaDinamica()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_PROCEDIMENTOS, config.getId()), "CRIAR", atorId, dados);
        return resposta(config, false);
    }

    /**
     * Mudar a `serieDr` com processos ainda por publicar muda o tipo de ato desses processos: aceita-se, porque só a
     * chave fica protegida.
     */
    @Transactional
    public ProcedimentoConfigResponse atualizarProcedimento(Long id, ProcedimentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_PROCEDIMENTOS);
        InstrumentoProcedimentoConfig config = procedimentoConfigRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Procedimento não encontrada."));
        ReferenciasDoProcedimento referencias = referenciasDoProcedimento(request);
        boolean emUso = !processoRepository.configsComProcessos(List.of(id)).isEmpty();

        Map<String, String> alterados = new LinkedHashMap<>();
        boolean dinamicaMuda = !config.getInstrumentoDinamicaConfig().getId().equals(request.instrumentoDinamicaConfigId());
        boolean tipoMuda = !config.getTipoProcedimento().getId().equals(request.tipoProcedimentoId());
        if (dinamicaMuda || tipoMuda) {
            if (procedimentoConfigRepository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_IdAndIdNot(
                    request.instrumentoDinamicaConfigId(), request.tipoProcedimentoId(), id)) {
                throw procedimentoDuplicado();
            }
            if (emUso) {
                throw new ConfiguracaoEmUsoException();
            }
            if (dinamicaMuda) {
                config.setInstrumentoDinamicaConfig(referencias.dinamicaConfig());
                alterados.put("instrumentoDinamicaConfigId", String.valueOf(request.instrumentoDinamicaConfigId()));
            }
            if (tipoMuda) {
                config.setTipoProcedimento(referencias.tipoProcedimento());
                alterados.put("tipoProcedimentoId", String.valueOf(request.tipoProcedimentoId()));
            }
        }
        if (config.getOrdem() != request.ordem()) {
            config.setOrdem(request.ordem());
            alterados.put("ordem", String.valueOf(request.ordem()));
        }
        if (config.isObrigatorio() != request.obrigatorio()) {
            config.setObrigatorio(request.obrigatorio());
            alterados.put("obrigatorio", String.valueOf(request.obrigatorio()));
        }
        if (!Objects.equals(config.getSerieDr(), request.serieDr())) {
            config.setSerieDr(request.serieDr());
            alterados.put("serieDr", request.serieDr());
        }
        if (!Objects.equals(config.getTamanhoMaximoProcesso(), request.tamanhoMaximoProcesso())) {
            config.setTamanhoMaximoProcesso(request.tamanhoMaximoProcesso());
            alterados.put("tamanhoMaximoProcesso", String.valueOf(request.tamanhoMaximoProcesso()));
        }
        if (config.isRepetivel() != request.repetivel()) {
            config.setRepetivel(request.repetivel());
            alterados.put("repetivel", String.valueOf(request.repetivel()));
        }
        if (config.isAtoPontual() != request.atoPontual()) {
            config.setAtoPontual(request.atoPontual());
            alterados.put("atoPontual", String.valueOf(request.atoPontual()));
        }
        if (config.isAtivo() != request.ativo()) {
            config.setAtivo(request.ativo());
            alterados.put("ativo", String.valueOf(request.ativo()));
        }
        if (request.visivelNaDinamica() != null && config.isVisivelNaDinamica() != request.visivelNaDinamica()) {
            config.setVisivelNaDinamica(request.visivelNaDinamica());
            alterados.put("visivelNaDinamica", String.valueOf(request.visivelNaDinamica()));
        }

        procedimentoConfigRepository.save(config);
        auditarAtualizacao(RECURSO_PROCEDIMENTOS, id, atorId, alterados);
        return resposta(config, emUso);
    }

    private record TiposDaDinamica(TipoInstrumento instrumento, TipoDinamica dinamica) {
    }

    private record ReferenciasDoProcedimento(InstrumentoDinamicaConfig dinamicaConfig, TipoProcedimento tipoProcedimento) {
    }

    private TiposDaDinamica tiposDaDinamica(DinamicaConfigRequest request) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        TipoInstrumento instrumento = tipoInstrumentoRepository.findById(request.tipoInstrumentoId()).orElse(null);
        if (instrumento == null) {
            erros.add(new ValidacaoException.CampoErro("tipoInstrumentoId", "Tipo de Instrumento inexistente."));
        }
        TipoDinamica dinamica = tipoDinamicaRepository.findById(request.tipoDinamicaId()).orElse(null);
        if (dinamica == null) {
            erros.add(new ValidacaoException.CampoErro("tipoDinamicaId", "Tipo de Dinâmica inexistente."));
        }
        exigirSemErros(erros);
        return new TiposDaDinamica(instrumento, dinamica);
    }

    private ReferenciasDoProcedimento referenciasDoProcedimento(ProcedimentoConfigRequest request) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.findById(request.instrumentoDinamicaConfigId())
                .orElse(null);
        if (dinamicaConfig == null) {
            erros.add(new ValidacaoException.CampoErro("instrumentoDinamicaConfigId",
                    "Configuração de Dinâmica inexistente."));
        }
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.findById(request.tipoProcedimentoId()).orElse(null);
        if (tipoProcedimento == null) {
            erros.add(new ValidacaoException.CampoErro("tipoProcedimentoId", "Tipo de Procedimento inexistente."));
        }
        exigirSemErros(erros);
        return new ReferenciasDoProcedimento(dinamicaConfig, tipoProcedimento);
    }

    private static void exigirSemErros(List<ValidacaoException.CampoErro> erros) {
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Referência inexistente.", erros);
        }
    }

    private ProcedimentoConfigResponse resposta(InstrumentoProcedimentoConfig config, boolean emUso) {
        String formulario = catalogoFormularios.formularioDe(config.getTipoProcedimento())
                .map(FormularioFixo::name).orElse(null);
        return ProcedimentoConfigResponse.de(config, formulario, emUso);
    }

    private static ValidacaoException dinamicaDuplicada() {
        return new ValidacaoException("Configuração duplicada.", List.of(new ValidacaoException.CampoErro(
                "tipoDinamicaId", "Esta Dinâmica já está configurada para este tipo de Instrumento.")));
    }

    private static ValidacaoException procedimentoDuplicado() {
        return new ValidacaoException("Configuração duplicada.", List.of(new ValidacaoException.CampoErro(
                "tipoProcedimentoId", "Este Procedimento já está configurado nesta Dinâmica.")));
    }

    private void auditarAtualizacao(String recurso, Long id, UUID atorId, Map<String, String> alterados) {
        if (!alterados.isEmpty()) {
            auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(recurso, id), "ATUALIZAR", atorId, alterados);
        }
    }

    private static String ocorrencia(String recurso, Long id) {
        return recurso + ":" + id;
    }

    private UUID exigirMaster(String recurso) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        try {
            autorizacaoService.verificarPerfilMaster(contexto);
        } catch (AcaoNaoAutorizadaException e) {
            log.warn("Acesso recusado à configuração utilizadorId={} recurso={}", contexto.utilizadorId(), recurso);
            throw e;
        }
        return contexto.utilizadorId();
    }
}
