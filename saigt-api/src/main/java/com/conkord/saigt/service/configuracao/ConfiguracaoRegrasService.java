package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.CondicaoConfigRequest;
import com.conkord.saigt.dto.configuracao.CondicaoConfigResponse;
import com.conkord.saigt.dto.configuracao.RegraConfigRequest;
import com.conkord.saigt.dto.configuracao.RegraConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.RegistoAvaliadores;
import com.conkord.saigt.service.regras.avaliador.Avaliador;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras do motor e as suas condições, em backoffice. Só `MASTER`, também nas leituras; sem `DELETE`. Cada pedido é uma
 * transação: um erro não grava nada. Os parâmetros passam pela mesma validação do arranque
 * ({@code AvaliadorRegistoValidator}), também nas condições inativas, para que uma regra gravada aqui nunca impeça o
 * arranque seguinte. O motor lê as regras a cada avaliação, por isso as mudanças valem logo.
 */
@Slf4j
@Service
public class ConfiguracaoRegrasService {

    static final String REGRA_MAL_CONFIGURADA = "Regra mal configurada.";
    static final String AVALIADOR_DESCONHECIDO = "Avaliador desconhecido.";
    static final String SEM_CONDICAO_ATIVA = "Indique pelo menos uma condição ativa.";
    static final String PROCEDIMENTO_COM_REGRA = "Este Procedimento já tem uma regra; edite a existente.";
    static final String ALVO_NAO_MUDA = "O alvo da regra não pode ser alterado; desative-a e crie outra.";
    static final String PARAMETROS_INVALIDOS = "Parâmetros inválidos.";
    static final String CONDICAO_DE_OUTRA_REGRA = "A condição não pertence a esta regra.";

    private static final String TIPO_OCORRENCIA = "CONFIGURACAO";
    private static final String RECURSO = "regras";
    private static final String CAMPO_DINAMICA = "instrumentoDinamicaConfigId";
    private static final String CAMPO_PROCEDIMENTO = "instrumentoProcedimentoConfigId";

    private final RegraRepository regraRepository;
    private final CondicaoRepository condicaoRepository;
    private final InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    private final RegistoAvaliadores registoAvaliadores;
    private final AuditoriaService auditoriaService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ObjectMapper objectMapper;

    public ConfiguracaoRegrasService(RegraRepository regraRepository, CondicaoRepository condicaoRepository,
                                     InstrumentoDinamicaConfigRepository dinamicaConfigRepository,
                                     InstrumentoProcedimentoConfigRepository procedimentoConfigRepository,
                                     RegistoAvaliadores registoAvaliadores, AuditoriaService auditoriaService,
                                     AutorizacaoService autorizacaoService,
                                     ContextoUtilizadorResolver contextoUtilizadorResolver, ObjectMapper objectMapper) {
        this.regraRepository = regraRepository;
        this.condicaoRepository = condicaoRepository;
        this.dinamicaConfigRepository = dinamicaConfigRepository;
        this.procedimentoConfigRepository = procedimentoConfigRepository;
        this.registoAvaliadores = registoAvaliadores;
        this.auditoriaService = auditoriaService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<RegraConfigResponse> listar(Long instrumentoDinamicaConfigId, Long instrumentoProcedimentoConfigId,
                                            Long dinamicaDosProcedimentosId, Boolean ativo, Pageable pagina) {
        exigirMaster();
        Page<Regra> regras = regraRepository.pesquisar(instrumentoDinamicaConfigId, instrumentoProcedimentoConfigId,
                dinamicaDosProcedimentosId, ativo, pagina);
        Map<Long, List<Condicao>> condicoesPorRegra = regras.isEmpty() ? Map.of()
                : condicaoRepository.findByRegraInOrderByGrupoAscIdAsc(regras.getContent()).stream()
                .collect(Collectors.groupingBy(condicao -> condicao.getRegra().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return regras.map(regra -> responder(regra, condicoesPorRegra.getOrDefault(regra.getId(), List.of())));
    }

    @Transactional
    public RegraConfigResponse criar(RegraConfigRequest request) {
        UUID atorId = exigirMaster();
        exigirExatamenteUmAlvo(request);
        Regra regra;
        if (request.instrumentoDinamicaConfigId() != null) {
            regra = Regra.paraDinamica(dinamicaConfig(request.instrumentoDinamicaConfigId()));
        } else {
            InstrumentoProcedimentoConfig procedimento = procedimentoConfig(request.instrumentoProcedimentoConfigId());
            if (regraRepository.existsByInstrumentoProcedimentoConfig_Id(procedimento.getId())) {
                throw new ValidacaoException(PROCEDIMENTO_COM_REGRA,
                        List.of(new CampoErro(CAMPO_PROCEDIMENTO, PROCEDIMENTO_COM_REGRA)));
            }
            regra = Regra.paraProcedimento(procedimento);
        }
        exigirSubstituicaoSoEmProcedimento(request);
        validarIdsDasCondicoes(request.condicoes(), Map.of());
        List<String> parametros = validarCondicoes(request);

        aplicarCampos(regra, request);
        regra = regraRepository.save(regra);
        List<Condicao> condicoes = gravarCondicoes(regra, request.condicoes(), parametros, Map.of());

        auditar(regra, condicoes, "CRIAR", atorId);
        return responder(regra, condicoes);
    }

    @Transactional
    public RegraConfigResponse atualizar(Long id, RegraConfigRequest request) {
        UUID atorId = exigirMaster();
        Regra regra = regraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra não encontrada."));
        exigirExatamenteUmAlvo(request);
        exigirMesmoAlvo(regra, request);
        exigirSubstituicaoSoEmProcedimento(request);
        Map<Long, Condicao> existentes = condicaoRepository.findByRegraInOrderByGrupoAscIdAsc(List.of(regra)).stream()
                .collect(Collectors.toMap(Condicao::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        validarIdsDasCondicoes(request.condicoes(), existentes);
        List<String> parametros = validarCondicoes(request);

        aplicarCampos(regra, request);
        regraRepository.save(regra);
        gravarCondicoes(regra, request.condicoes(), parametros, existentes);

        List<Condicao> condicoes = condicaoRepository.findByRegraInOrderByGrupoAscIdAsc(List.of(regra));
        auditar(regra, condicoes, "ATUALIZAR", atorId);
        return responder(regra, condicoes);
    }

    private static void exigirExatamenteUmAlvo(RegraConfigRequest request) {
        if ((request.instrumentoDinamicaConfigId() == null) == (request.instrumentoProcedimentoConfigId() == null)) {
            String erro = "Indique a configuração de Dinâmica ou a de Procedimento, só uma.";
            throw new ValidacaoException(REGRA_MAL_CONFIGURADA,
                    List.of(new CampoErro(CAMPO_DINAMICA, erro), new CampoErro(CAMPO_PROCEDIMENTO, erro)));
        }
    }

    /** O sequenciamento é dos Procedimentos (BL-PROC-003, TR-103). */
    private static void exigirSubstituicaoSoEmProcedimento(RegraConfigRequest request) {
        if (Boolean.TRUE.equals(request.substituiSequenciamento()) && request.instrumentoDinamicaConfigId() != null) {
            throw new ValidacaoException(REGRA_MAL_CONFIGURADA, List.of(new CampoErro("substituiSequenciamento",
                    "Só uma regra de Procedimento pode substituir o sequenciamento.")));
        }
    }

    private static void exigirMesmoAlvo(Regra regra, RegraConfigRequest request) {
        Long dinamicaGravada = regra.getInstrumentoDinamicaConfig() == null ? null : regra.getInstrumentoDinamicaConfig().getId();
        Long procedimentoGravado = regra.getInstrumentoProcedimentoConfig() == null ? null
                : regra.getInstrumentoProcedimentoConfig().getId();
        if (!Objects.equals(dinamicaGravada, request.instrumentoDinamicaConfigId())
                || !Objects.equals(procedimentoGravado, request.instrumentoProcedimentoConfigId())) {
            String campo = dinamicaGravada != null ? CAMPO_DINAMICA : CAMPO_PROCEDIMENTO;
            throw new ValidacaoException(ALVO_NAO_MUDA, List.of(new CampoErro(campo, ALVO_NAO_MUDA)));
        }
    }

    /** Um `id` repetido na lista também é recusado: a mesma condição não se atualiza duas vezes. */
    private static void validarIdsDasCondicoes(List<CondicaoConfigRequest> condicoes, Map<Long, Condicao> existentes) {
        List<CampoErro> erros = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < condicoes.size(); i++) {
            Long id = condicoes.get(i).id();
            if (id != null && (!existentes.containsKey(id) || !vistos.add(id))) {
                erros.add(new CampoErro("condicoes[" + i + "].id", CONDICAO_DE_OUTRA_REGRA));
            }
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException(CONDICAO_DE_OUTRA_REGRA, erros);
        }
    }

    /**
     * BL-PROC-045 e a validação de parâmetros de cada avaliador, todas de uma vez; depois, uma regra ativa sem condições
     * ativas, que o motor faria falhar sempre.
     *
     * @return os parâmetros de cada condição, como texto JSON
     */
    private List<String> validarCondicoes(RegraConfigRequest request) {
        List<CampoErro> erros = new ArrayList<>();
        List<String> parametros = new ArrayList<>();
        boolean avaliadorDesconhecido = false;
        for (int i = 0; i < request.condicoes().size(); i++) {
            CondicaoConfigRequest condicao = request.condicoes().get(i);
            String json = paraTexto(condicao.parametros());
            parametros.add(json);
            Optional<Avaliador> avaliador = registoAvaliadores.porCodigo(condicao.avaliador());
            if (avaliador.isEmpty()) {
                avaliadorDesconhecido = true;
                erros.add(new CampoErro("condicoes[" + i + "].avaliador", AVALIADOR_DESCONHECIDO));
                continue;
            }
            String caminho = "condicoes[" + i + "].parametros";
            avaliador.get().validarParametros(json).forEach(motivo -> erros.add(new CampoErro(caminho, motivo)));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException(avaliadorDesconhecido ? AVALIADOR_DESCONHECIDO : PARAMETROS_INVALIDOS, erros);
        }
        if (Boolean.TRUE.equals(request.ativo())
                && request.condicoes().stream().noneMatch(condicao -> Boolean.TRUE.equals(condicao.ativo()))) {
            throw new ValidacaoException(SEM_CONDICAO_ATIVA, List.of(new CampoErro("condicoes", SEM_CONDICAO_ATIVA)));
        }
        return parametros;
    }

    private static void aplicarCampos(Regra regra, RegraConfigRequest request) {
        regra.setOperador(request.operador());
        regra.setSubstituiSequenciamento(request.substituiSequenciamento());
        regra.setDescricao(request.descricao());
        regra.setAtivo(request.ativo());
    }

    /** A lista substitui a atual: as condições que não vêm passam a inativas, nunca se apagam. */
    private List<Condicao> gravarCondicoes(Regra regra, List<CondicaoConfigRequest> pedidas, List<String> parametros,
                                           Map<Long, Condicao> existentes) {
        List<Condicao> gravadas = new ArrayList<>();
        Set<Long> mantidas = new HashSet<>();
        for (int i = 0; i < pedidas.size(); i++) {
            CondicaoConfigRequest pedida = pedidas.get(i);
            Condicao condicao;
            if (pedida.id() != null) {
                condicao = existentes.get(pedida.id());
                mantidas.add(pedida.id());
                condicao.setAvaliador(pedida.avaliador());
                condicao.setParametros(parametros.get(i));
            } else {
                condicao = new Condicao(regra, pedida.avaliador(), parametros.get(i));
            }
            condicao.setGrupo(pedida.grupo());
            condicao.setAtivo(pedida.ativo());
            gravadas.add(condicao);
        }
        existentes.values().stream()
                .filter(condicao -> !mantidas.contains(condicao.getId()))
                .forEach(condicao -> condicao.setAtivo(false));
        return condicaoRepository.saveAll(gravadas);
    }

    private InstrumentoDinamicaConfig dinamicaConfig(Long id) {
        return dinamicaConfigRepository.findById(id).orElseThrow(() -> new ValidacaoException("Referência inexistente.",
                List.of(new CampoErro(CAMPO_DINAMICA, "Configuração de Dinâmica inexistente."))));
    }

    private InstrumentoProcedimentoConfig procedimentoConfig(Long id) {
        return procedimentoConfigRepository.findById(id).orElseThrow(() -> new ValidacaoException("Referência inexistente.",
                List.of(new CampoErro(CAMPO_PROCEDIMENTO, "Configuração de Procedimento inexistente."))));
    }

    /** Sem a `descricao`: texto livre que pode trazer dados pessoais. */
    private void auditar(Regra regra, List<Condicao> condicoes, String acao, UUID atorId) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("operador", regra.getOperador());
        dados.put("substituiSequenciamento", regra.isSubstituiSequenciamento());
        dados.put("ativo", regra.isAtivo());
        dados.put("condicoes", condicoes.stream().map(condicao -> {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("avaliador", condicao.getAvaliador());
            linha.put("parametros", paraArvore(condicao.getParametros()));
            linha.put("grupo", condicao.getGrupo());
            linha.put("ativo", condicao.isAtivo());
            return linha;
        }).toList());
        auditoriaService.registar(TIPO_OCORRENCIA, RECURSO + ":" + regra.getId(), acao, atorId, dados);
        log.info("Regra do motor gravada regraId={} acao={} condicoes={}", regra.getId(), acao, condicoes.size());
    }

    private RegraConfigResponse responder(Regra regra, List<Condicao> condicoes) {
        List<CondicaoConfigResponse> respostas = condicoes.stream()
                .sorted(Comparator.comparingInt((Condicao c) -> c.getGrupo()).thenComparing(Condicao::getId))
                .map(condicao -> new CondicaoConfigResponse(condicao.getId(), condicao.getAvaliador(),
                        paraArvore(condicao.getParametros()), condicao.getGrupo(), condicao.isAtivo()))
                .toList();
        return new RegraConfigResponse(regra.getId(),
                regra.getInstrumentoDinamicaConfig() == null ? null : regra.getInstrumentoDinamicaConfig().getId(),
                regra.getInstrumentoProcedimentoConfig() == null ? null : regra.getInstrumentoProcedimentoConfig().getId(),
                regra.isSubstituiSequenciamento(), regra.getOperador(), regra.getDescricao(), regra.isAtivo(), respostas);
    }

    private String paraTexto(JsonNode parametros) {
        try {
            return objectMapper.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("parametros não serializáveis", e);
        }
    }

    private JsonNode paraArvore(String parametros) {
        try {
            return objectMapper.readTree(parametros);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("condicao.parametros não é JSON", e);
        }
    }

    private UUID exigirMaster() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        try {
            autorizacaoService.verificarPerfilMaster(contexto);
        } catch (AcaoNaoAutorizadaException e) {
            log.warn("Acesso recusado à configuração utilizadorId={} recurso={}", contexto.utilizadorId(), RECURSO);
            throw e;
        }
        return contexto.utilizadorId();
    }
}
