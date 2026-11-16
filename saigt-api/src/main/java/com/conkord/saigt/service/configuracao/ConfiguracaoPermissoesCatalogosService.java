package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.PermissaoConfigRequest;
import com.conkord.saigt.dto.configuracao.PermissaoConfigResponse;
import com.conkord.saigt.dto.configuracao.RedeDefesaConfigRequest;
import com.conkord.saigt.dto.configuracao.RedeDefesaConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoAtoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoAtoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Permissões de criação por tipo de entidade, tipos de ato do DR e redes de defesa do SGIFR. Só `MASTER`, também nas
 * leituras; sem `DELETE`. A `permissao_acao` é lida em cada pedido pela autorização, por isso desativar uma linha
 * vale de imediato. O `codigo` de um tipo de ato ou de uma rede usados num processo não muda; os restantes campos
 * mudam sempre e valem para os pedidos seguintes.
 */
@Slf4j
@Service
public class ConfiguracaoPermissoesCatalogosService {

    private static final String TIPO_OCORRENCIA = "CONFIGURACAO";
    private static final String RECURSO_PERMISSOES = "permissoes";
    private static final String RECURSO_TIPOS_ATO = "tipos-ato";
    private static final String RECURSO_REDES_DEFESA = "redes-defesa";
    private static final String SEM_SERIE = "Indique pelo menos uma série.";

    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    private final TipoAtoRepository tipoAtoRepository;
    private final SgifrRedeDefesaRepository redeDefesaRepository;
    private final ProcessoRepository processoRepository;
    private final AuditoriaService auditoriaService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public ConfiguracaoPermissoesCatalogosService(PermissaoAcaoRepository permissaoAcaoRepository,
                                                  InstrumentoProcedimentoConfigRepository procedimentoConfigRepository,
                                                  TipoAtoRepository tipoAtoRepository,
                                                  SgifrRedeDefesaRepository redeDefesaRepository,
                                                  ProcessoRepository processoRepository,
                                                  AuditoriaService auditoriaService,
                                                  AutorizacaoService autorizacaoService,
                                                  ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.procedimentoConfigRepository = procedimentoConfigRepository;
        this.tipoAtoRepository = tipoAtoRepository;
        this.redeDefesaRepository = redeDefesaRepository;
        this.processoRepository = processoRepository;
        this.auditoriaService = auditoriaService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public Page<PermissaoConfigResponse> listarPermissoes(Long instrumentoDinamicaConfigId,
                                                          Long instrumentoProcedimentoConfigId,
                                                          TipoEntidade tipoEntidade, Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_PERMISSOES);
        return permissaoAcaoRepository.pesquisar(instrumentoDinamicaConfigId, instrumentoProcedimentoConfigId,
                tipoEntidade, ativo, pagina).map(PermissaoConfigResponse::de);
    }

    @Transactional
    public PermissaoConfigResponse criarPermissao(PermissaoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_PERMISSOES);
        InstrumentoProcedimentoConfig procedimento = procedimentoDaPermissao(request.instrumentoProcedimentoConfigId());
        if (permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidade(
                request.instrumentoProcedimentoConfigId(), request.tipoEntidade())) {
            throw permissaoDuplicada();
        }
        PermissaoAcao permissao = new PermissaoAcao(request.tipoEntidade(), procedimento);
        permissao.setAtivo(request.ativo());
        permissao = permissaoAcaoRepository.save(permissao);

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("instrumentoProcedimentoConfigId", String.valueOf(request.instrumentoProcedimentoConfigId()));
        dados.put("tipoEntidade", request.tipoEntidade().name());
        dados.put("ativo", String.valueOf(request.ativo()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_PERMISSOES, permissao.getId()), "CRIAR", atorId, dados);
        return PermissaoConfigResponse.de(permissao);
    }

    @Transactional
    public PermissaoConfigResponse atualizarPermissao(Long id, PermissaoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_PERMISSOES);
        PermissaoAcao permissao = permissaoAcaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada."));
        InstrumentoProcedimentoConfig procedimento = procedimentoDaPermissao(request.instrumentoProcedimentoConfigId());

        Map<String, String> alterados = new LinkedHashMap<>();
        boolean procedimentoMuda = !permissao.getInstrumentoProcedimentoConfig().getId()
                .equals(request.instrumentoProcedimentoConfigId());
        boolean tipoMuda = permissao.getTipoEntidade() != request.tipoEntidade();
        if (procedimentoMuda || tipoMuda) {
            if (permissaoAcaoRepository.existsByInstrumentoProcedimentoConfig_IdAndTipoEntidadeAndIdNot(
                    request.instrumentoProcedimentoConfigId(), request.tipoEntidade(), id)) {
                throw permissaoDuplicada();
            }
            if (procedimentoMuda) {
                permissao.setInstrumentoProcedimentoConfig(procedimento);
                alterados.put("instrumentoProcedimentoConfigId", String.valueOf(request.instrumentoProcedimentoConfigId()));
            }
            if (tipoMuda) {
                permissao.setTipoEntidade(request.tipoEntidade());
                alterados.put("tipoEntidade", request.tipoEntidade().name());
            }
        }
        if (permissao.isAtivo() != request.ativo()) {
            permissao.setAtivo(request.ativo());
            alterados.put("ativo", String.valueOf(request.ativo()));
        }

        permissaoAcaoRepository.save(permissao);
        auditarAtualizacao(RECURSO_PERMISSOES, id, atorId, alterados);
        return PermissaoConfigResponse.de(permissao);
    }

    @Transactional(readOnly = true)
    public Page<TipoAtoConfigResponse> listarTiposAto(Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_TIPOS_ATO);
        Page<TipoAto> tipos = ativo == null ? tipoAtoRepository.findAll(pagina) : tipoAtoRepository.findByAtivo(ativo, pagina);
        return tipos.map(TipoAtoConfigResponse::de);
    }

    @Transactional
    public TipoAtoConfigResponse criarTipoAto(TipoAtoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_ATO);
        exigirUmaSerie(request);
        if (tipoAtoRepository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        TipoAto tipo = new TipoAto(request.codigo(), request.nome(), request.ordem());
        tipo.setIncmActTypeIdSerie1(request.incmActTypeIdSerie1());
        tipo.setIncmActTypeIdSerie2(request.incmActTypeIdSerie2());
        tipo.setAtivo(request.ativo());
        tipo = tipoAtoRepository.save(tipo);

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("codigo", tipo.getCodigo());
        dados.put("nome", tipo.getNome());
        dados.put("incmActTypeIdSerie1", String.valueOf(tipo.getIncmActTypeIdSerie1()));
        dados.put("incmActTypeIdSerie2", String.valueOf(tipo.getIncmActTypeIdSerie2()));
        dados.put("ordem", String.valueOf(tipo.getOrdem()));
        dados.put("ativo", String.valueOf(tipo.isAtivo()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_TIPOS_ATO, tipo.getId()), "CRIAR", atorId, dados);
        return TipoAtoConfigResponse.de(tipo);
    }

    /** Um `ActTypeId` novo vale para os envios seguintes à INCM, que o leem do tipo; os atos publicados não mudam. */
    @Transactional
    public TipoAtoConfigResponse atualizarTipoAto(Long id, TipoAtoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_ATO);
        TipoAto tipo = tipoAtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de ato não encontrado."));
        exigirUmaSerie(request);

        Map<String, String> alterados = new LinkedHashMap<>();
        if (!tipo.getCodigo().equals(request.codigo())) {
            if (tipoAtoRepository.existsByCodigoAndIdNot(request.codigo(), id)) {
                throw codigoDuplicado();
            }
            if (processoRepository.existsByTipoAto_Id(id)) {
                throw new ConfiguracaoEmUsoException();
            }
            tipo.setCodigo(request.codigo());
            alterados.put("codigo", request.codigo());
        }
        if (!tipo.getNome().equals(request.nome())) {
            tipo.setNome(request.nome());
            alterados.put("nome", request.nome());
        }
        if (!Objects.equals(tipo.getIncmActTypeIdSerie1(), request.incmActTypeIdSerie1())) {
            tipo.setIncmActTypeIdSerie1(request.incmActTypeIdSerie1());
            alterados.put("incmActTypeIdSerie1", String.valueOf(request.incmActTypeIdSerie1()));
        }
        if (!Objects.equals(tipo.getIncmActTypeIdSerie2(), request.incmActTypeIdSerie2())) {
            tipo.setIncmActTypeIdSerie2(request.incmActTypeIdSerie2());
            alterados.put("incmActTypeIdSerie2", String.valueOf(request.incmActTypeIdSerie2()));
        }
        if (tipo.getOrdem() != request.ordem()) {
            tipo.setOrdem(request.ordem());
            alterados.put("ordem", String.valueOf(request.ordem()));
        }
        if (tipo.isAtivo() != request.ativo()) {
            tipo.setAtivo(request.ativo());
            alterados.put("ativo", String.valueOf(request.ativo()));
        }

        tipoAtoRepository.save(tipo);
        auditarAtualizacao(RECURSO_TIPOS_ATO, id, atorId, alterados);
        return TipoAtoConfigResponse.de(tipo);
    }

    @Transactional(readOnly = true)
    public Page<RedeDefesaConfigResponse> listarRedesDefesa(Pageable pagina) {
        exigirMaster(RECURSO_REDES_DEFESA);
        return redeDefesaRepository.findAll(pagina).map(RedeDefesaConfigResponse::de);
    }

    @Transactional
    public RedeDefesaConfigResponse criarRedeDefesa(RedeDefesaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_REDES_DEFESA);
        if (redeDefesaRepository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        SgifrRedeDefesa rede = redeDefesaRepository.save(new SgifrRedeDefesa(request.codigo(), request.nome(),
                request.aplicaPra(), request.aplicaPsa(), request.publicavel()));

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("codigo", rede.getCodigo());
        dados.put("nome", rede.getNome());
        dados.put("aplicaPra", String.valueOf(rede.isAplicaPra()));
        dados.put("aplicaPsa", String.valueOf(rede.isAplicaPsa()));
        dados.put("publicavel", String.valueOf(rede.isPublicavel()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_REDES_DEFESA, rede.getId()), "CRIAR", atorId, dados);
        return RedeDefesaConfigResponse.de(rede);
    }

    @Transactional
    public RedeDefesaConfigResponse atualizarRedeDefesa(Long id, RedeDefesaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_REDES_DEFESA);
        SgifrRedeDefesa rede = redeDefesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Rede de defesa não encontrada."));

        Map<String, String> alterados = new LinkedHashMap<>();
        if (!rede.getCodigo().equals(request.codigo())) {
            if (redeDefesaRepository.existsByCodigoAndIdNot(request.codigo(), id)) {
                throw codigoDuplicado();
            }
            if (redeDefesaRepository.existeEmProcesso(id)) {
                throw new ConfiguracaoEmUsoException();
            }
            rede.setCodigo(request.codigo());
            alterados.put("codigo", request.codigo());
        }
        if (!rede.getNome().equals(request.nome())) {
            rede.setNome(request.nome());
            alterados.put("nome", request.nome());
        }
        if (rede.isAplicaPra() != request.aplicaPra()) {
            rede.setAplicaPra(request.aplicaPra());
            alterados.put("aplicaPra", String.valueOf(request.aplicaPra()));
        }
        if (rede.isAplicaPsa() != request.aplicaPsa()) {
            rede.setAplicaPsa(request.aplicaPsa());
            alterados.put("aplicaPsa", String.valueOf(request.aplicaPsa()));
        }
        if (rede.isPublicavel() != request.publicavel()) {
            rede.setPublicavel(request.publicavel());
            alterados.put("publicavel", String.valueOf(request.publicavel()));
        }

        redeDefesaRepository.save(rede);
        auditarAtualizacao(RECURSO_REDES_DEFESA, id, atorId, alterados);
        return RedeDefesaConfigResponse.de(rede);
    }

    private InstrumentoProcedimentoConfig procedimentoDaPermissao(Long instrumentoProcedimentoConfigId) {
        return procedimentoConfigRepository.findById(instrumentoProcedimentoConfigId)
                .orElseThrow(() -> new ValidacaoException("Referência inexistente.", List.of(new ValidacaoException.CampoErro(
                        "instrumentoProcedimentoConfigId", "Configuração de Procedimento inexistente."))));
    }

    /** O `CHECK ck_tipo_ato_incm_serie` também o exige; verificar aqui evita o 500. */
    private static void exigirUmaSerie(TipoAtoConfigRequest request) {
        if (request.incmActTypeIdSerie1() == null && request.incmActTypeIdSerie2() == null) {
            throw new ValidacaoException(SEM_SERIE, List.of(
                    new ValidacaoException.CampoErro("incmActTypeIdSerie1", SEM_SERIE),
                    new ValidacaoException.CampoErro("incmActTypeIdSerie2", SEM_SERIE)));
        }
    }

    private static ValidacaoException permissaoDuplicada() {
        return new ValidacaoException("Permissão duplicada.", List.of(new ValidacaoException.CampoErro(
                "tipoEntidade", "Este tipo de entidade já tem permissão neste Procedimento.")));
    }

    private static ValidacaoException codigoDuplicado() {
        return new ValidacaoException("Código duplicado.", List.of(new ValidacaoException.CampoErro(
                "codigo", "Já existe um registo com este código.")));
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
