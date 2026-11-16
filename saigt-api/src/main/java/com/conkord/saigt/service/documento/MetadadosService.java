package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.MetadadosProcesso;
import com.conkord.saigt.dto.documento.MetadadosResponse;
import com.conkord.saigt.dto.documento.MetadadosResponse.Responsavel;
import com.conkord.saigt.exception.MotuNaoAplicavelException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoMetadados;
import com.conkord.saigt.persistence.operacional.ProcessoMetadadosRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ficha de metadados (MOTU) do processo, nos mesmos procedimentos que a página das Plantas (sem GeoConform), como no
 * Figma da «Decisão de elaborar sem Geoconform». Os campos e os obrigatórios são os do Figma.
 */
@Service
public class MetadadosService {

    private static final String FUNCAO_RESPONSAVEL = "Produtor";
    private static final String PAIS = "Portugal";
    private static final String PREFIXO_CAMPO = "metadados.";

    /** Obrigatórios do Figma (marcados com *), pela ordem do ecrã. */
    private static final Map<String, Function<MetadadosProcesso, Object>> OBRIGATORIOS = new LinkedHashMap<>();

    static {
        OBRIGATORIOS.put("palavrasChave", MetadadosProcesso::palavrasChave);
        OBRIGATORIOS.put("resolucaoEspacialEscala", MetadadosProcesso::resolucaoEspacialEscala);
        OBRIGATORIOS.put("tituloAlternativo", MetadadosProcesso::tituloAlternativo);
        OBRIGATORIOS.put("resumo", MetadadosProcesso::resumo);
        OBRIGATORIOS.put("sistemaReferencia", MetadadosProcesso::sistemaReferencia);
        OBRIGATORIOS.put("cartografiaTipo", MetadadosProcesso::cartografiaTipo);
        OBRIGATORIOS.put("entidadeProprietaria", MetadadosProcesso::entidadeProprietaria);
        OBRIGATORIOS.put("dataEdicao", MetadadosProcesso::dataEdicao);
        OBRIGATORIOS.put("entidadeProdutora", MetadadosProcesso::entidadeProdutora);
        OBRIGATORIOS.put("atualizacaoComplemento", MetadadosProcesso::atualizacaoComplemento);
        OBRIGATORIOS.put("sistemaReferenciaNome", MetadadosProcesso::sistemaReferenciaNome);
        OBRIGATORIOS.put("exatidaoPosicional", MetadadosProcesso::exatidaoPosicional);
        OBRIGATORIOS.put("exatidaoTematica", MetadadosProcesso::exatidaoTematica);
    }

    private final ProcessoRepository processoRepository;
    private final ProcessoMetadadosRepository processoMetadadosRepository;
    private final EstruturaDocumentalService estruturaDocumentalService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ProcessoAlteravel processoAlteravel;
    private final ObjectMapper objectMapper;
    private final MetadadosMotuService metadadosMotuService;

    public MetadadosService(ProcessoRepository processoRepository, ProcessoMetadadosRepository processoMetadadosRepository,
                            EstruturaDocumentalService estruturaDocumentalService, AutorizacaoService autorizacaoService,
                            ContextoUtilizadorResolver contextoUtilizadorResolver, ProcessoAlteravel processoAlteravel,
                            ObjectMapper objectMapper, MetadadosMotuService metadadosMotuService) {
        this.metadadosMotuService = metadadosMotuService;
        this.processoRepository = processoRepository;
        this.processoMetadadosRepository = processoMetadadosRepository;
        this.estruturaDocumentalService = estruturaDocumentalService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.processoAlteravel = processoAlteravel;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MetadadosResponse obter(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        return resposta(processo, dados(processo));
    }

    @Transactional
    public MetadadosResponse gravar(UUID processoId, MetadadosProcesso dados) {
        Processo processo = processoAlteravel.obter(processoId, contextoUtilizadorResolver.resolver());
        if (metadadosMotuService.aplicaFichaMotu(processo)) {
            throw new MotuNaoAplicavelException("Neste Procedimento a ficha é a MOTU: grava-se em /metadados-motu.");
        }
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            throw new ValidacaoException("Este processo não tem ficha de metadados.",
                    List.of(new CampoErro("metadados", "Só sem GeoConform se preenche a ficha de metadados aqui.")));
        }
        String json = paraJson(dados);
        ProcessoMetadados metadados = processoMetadadosRepository.findById(processo.getId())
                .orElseGet(() -> new ProcessoMetadados(processo.getId(), json, Instant.now()));
        metadados.setDados(json);
        metadados.setAtualizadoEm(Instant.now());
        processoMetadadosRepository.saveAndFlush(metadados);
        return resposta(processo, dados);
    }

    @Transactional(readOnly = true)
    public List<CampoErro> pendencias(Processo processo) {
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            return List.of();
        }
        MetadadosProcesso dados = dados(processo);
        List<CampoErro> pendencias = new ArrayList<>();
        OBRIGATORIOS.forEach((campo, valor) -> {
            Object atual = valor.apply(dados);
            if (atual == null || (atual instanceof String texto && texto.isBlank())) {
                pendencias.add(new CampoErro(PREFIXO_CAMPO + campo, "Preencha este campo da ficha de metadados."));
            }
        });
        return pendencias;
    }

    /** O responsável é a entidade do Processo (OI-496), função «Produtor». */
    static Responsavel responsavel(Processo processo) {
        Entidade entidade = processo.getEntidade();
        return new Responsavel(entidade.getDescricaoSaigt(), FUNCAO_RESPONSAVEL, entidade.getEmail(),
                null, null, null, null, PAIS, entidade.getTelefone(), null);
    }

    private MetadadosResponse resposta(Processo processo, MetadadosProcesso dados) {
        return new MetadadosResponse(estruturaDocumentalService.exigePlantas(processo), responsavel(processo),
                processo.getDinamica().designacao(), dados);
    }

    private MetadadosProcesso dados(Processo processo) {
        return processoMetadadosRepository.findById(processo.getId())
                .map(metadados -> deJson(metadados.getDados()))
                .orElse(MetadadosProcesso.vazio());
    }

    private String paraJson(MetadadosProcesso dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a ficha de metadados.", e);
        }
    }

    private MetadadosProcesso deJson(String json) {
        try {
            return objectMapper.readValue(json, MetadadosProcesso.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha de metadados gravada ilegível.", e);
        }
    }
}
