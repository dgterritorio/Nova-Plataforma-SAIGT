package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.EstatisticasProcessoResponse;
import com.conkord.saigt.dto.instrumento.HistoricoProcessoResponse;
import com.conkord.saigt.dto.instrumento.ItemRecusaResponse;
import com.conkord.saigt.dto.instrumento.TipoInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.GrupoPainelResponse;
import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.LocalizacaoResponse;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoResponse;
import com.conkord.saigt.dto.instrumento.PainelProcessosResponse;
import com.conkord.saigt.dto.instrumento.OpcaoListaComIdResponse;
import com.conkord.saigt.dto.instrumento.OpcaoListaResponse;
import com.conkord.saigt.dto.instrumento.DinamicaATerminarResponse;
import com.conkord.saigt.dto.instrumento.DinamicaAssociavelResponse;
import com.conkord.saigt.dto.instrumento.PendenciaSubmissaoResponse;
import com.conkord.saigt.dto.instrumento.PendenciaSubmissaoResponse.Pagina;
import com.conkord.saigt.dto.instrumento.ProcessoDetalheResponse;
import com.conkord.saigt.dto.instrumento.FormularioRenResponse;
import com.conkord.saigt.dto.instrumento.MotivoProcedimentoResponse;
import com.conkord.saigt.dto.instrumento.RedeDefesaResponse;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.dto.instrumento.ProcessoListagemResponse;
import com.conkord.saigt.dto.instrumento.RecusaResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.UltimaRejeicaoDgtResponse;
import com.conkord.saigt.dto.instrumento.UltimaRejeicaoIncmResponse;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ExportacaoLimiteExcedidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.FuncoesJsonbProcesso;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.SituacaoPcm;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leitura de Processos só com visibilidade, sem `ator`: `VISITANTE` nunca tem `ator` e por isso não
 * pode usar {@code ProcessoService}.
 */
@Service
public class ProcessoConsultaService {

    private static final String ATOR_DGT = "DGT";
    private static final String TIPO_OCORRENCIA_PROCESSO = "PROCESSO";
    private static final String ACAO_REJEITAR = "REJEITAR";
    private static final String ACAO_SUBMETER = "SUBMETER";
    private static final String CAMPO_MOTIVO_CODIGO = "motivoCodigo";
    private static final String CAMPO_MOTIVO_ID = "motivoId";
    private static final String CAMPO_MENSAGENS = "mensagens";
    private static final String CAMPO_DATA_ASSINATURA = "dataAssinatura";
    private static final Set<String> ACOES_REJEICAO_INCM = Set.of("SUBMISSAO_INCM_REJEITADA", "REJEICAO_INCM");
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");
    /** O painel só aplica o critério do grupo e o âmbito da entidade, sem os filtros da listagem. */
    private static final ProcessoFiltro FILTRO_VAZIO =
            new ProcessoFiltro(null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    private final ProcessoRepository processoRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ObjectMapper objectMapper;
    private final ListaValoresRepository listaValoresRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final ProcessoService processoService;
    private final ProcessoValidacaoService processoValidacaoService;
    private final ValidadorSubmissaoProcesso validadorSubmissaoProcesso;
    private final CompletudeDocumentalService completudeDocumentalService;
    private final CatalogoFormularios catalogoFormularios;
    private final TerritorioInstrumentoService territorioInstrumentoService;
    private final ProcessoRejeicaoRepository processoRejeicaoRepository;
    private final ProcessoRejeicaoItemRepository processoRejeicaoItemRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final SnitReferenciaRepository snitReferenciaRepository;
    private final AssociacaoInstrumentosService associacaoInstrumentosService;
    private final DinamicaAssociadaService dinamicaAssociadaService;
    private final DestinoProcessoService destinoProcessoService;
    private final RegrasRedesDefesaSgifr regrasRedesDefesaSgifr;
    private final RegrasFormularioRen regrasFormularioRen;
    private final RegrasTerritorioSgifr regrasTerritorioSgifr;

    public ProcessoConsultaService(ProcessoRepository processoRepository, AuditoriaRepository auditoriaRepository,
                                    AutorizacaoService autorizacaoService, ContextoUtilizadorResolver contextoUtilizadorResolver,
                                    ObjectMapper objectMapper, ListaValoresRepository listaValoresRepository,
                                    UtilizadorRepository utilizadorRepository, ProcessoService processoService,
                                    ProcessoValidacaoService processoValidacaoService,
                                    ValidadorSubmissaoProcesso validadorSubmissaoProcesso,
                                    CompletudeDocumentalService completudeDocumentalService,
                                    CatalogoFormularios catalogoFormularios,
                                    TerritorioInstrumentoService territorioInstrumentoService,
                                    ProcessoRejeicaoRepository processoRejeicaoRepository,
                                    ProcessoRejeicaoItemRepository processoRejeicaoItemRepository,
                                    IdusProcessoRepository idusProcessoRepository,
                                    SnitReferenciaRepository snitReferenciaRepository,
                                    AssociacaoInstrumentosService associacaoInstrumentosService,
                                    DinamicaAssociadaService dinamicaAssociadaService,
                                    DestinoProcessoService destinoProcessoService,
                                    RegrasRedesDefesaSgifr regrasRedesDefesaSgifr,
                                    RegrasTerritorioSgifr regrasTerritorioSgifr,
                                    RegrasFormularioRen regrasFormularioRen) {
        this.regrasRedesDefesaSgifr = regrasRedesDefesaSgifr;
        this.regrasTerritorioSgifr = regrasTerritorioSgifr;
        this.regrasFormularioRen = regrasFormularioRen;
        this.processoRepository = processoRepository;
        this.destinoProcessoService = destinoProcessoService;
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.associacaoInstrumentosService = associacaoInstrumentosService;
        this.dinamicaAssociadaService = dinamicaAssociadaService;
        this.auditoriaRepository = auditoriaRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.objectMapper = objectMapper;
        this.listaValoresRepository = listaValoresRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.processoService = processoService;
        this.processoValidacaoService = processoValidacaoService;
        this.validadorSubmissaoProcesso = validadorSubmissaoProcesso;
        this.completudeDocumentalService = completudeDocumentalService;
        this.catalogoFormularios = catalogoFormularios;
        this.territorioInstrumentoService = territorioInstrumentoService;
        this.processoRejeicaoRepository = processoRejeicaoRepository;
        this.processoRejeicaoItemRepository = processoRejeicaoItemRepository;
        this.idusProcessoRepository = idusProcessoRepository;
    }

    /**
     * Só os perfis internos veem todas as entidades; um utilizador externo, `PCM` incluído, só vê a da sua
     * sessão, mesmo que `filtro.entidadeId()` peça outra.
     */
    @Transactional(readOnly = true)
    public Page<ProcessoListagemResponse> listar(ProcessoFiltro filtro, Pageable pageable) {
        boolean perfilInterno = contextoUtilizadorResolver.resolver().perfilInterno() != null;
        Page<Processo> pagina = processoRepository.findAll(especificacaoDaListagem(filtro, "Parâmetros de filtro inválidos."), pageable);
        return new PageImpl<>(paraListagem(pagina.getContent(), perfilInterno), pagina.getPageable(), pagina.getTotalElements());
    }

    /** As linhas da listagem, com os concelhos de todos os Instrumentos lidos de uma vez. */
    private List<ProcessoListagemResponse> paraListagem(List<Processo> processos, boolean perfilInterno) {
        Set<UUID> instrumentoIds = processos.stream()
                .map(processo -> processo.getDinamica().instrumentoDeInicio())
                .flatMap(Optional::stream)
                .map(Instrumento::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<String>> nomesConcelhos = territorioInstrumentoService.nomesConcelhos(instrumentoIds);
        return processos.stream()
                .map(processo -> ProcessoListagemResponse.de(processo, perfilInterno, nomesConcelhos))
                .toList();
    }

    /**
     * O conjunto completo de {@link #listar}, sem paginação, pela ordem dada. Conta antes de carregar: acima de
     * `limite` recusa sem ler nenhuma linha.
     */
    @Transactional(readOnly = true)
    public List<ProcessoListagemResponse> listarParaExportacao(ProcessoFiltro filtro, Sort ordenacao, int limite) {
        Specification<Processo> especificacao = especificacaoDaListagem(filtro, "Valor inválido para o filtro grupo.");
        if (processoRepository.count(especificacao) > limite) {
            throw new ExportacaoLimiteExcedidoException(limite);
        }
        boolean perfilInterno = contextoUtilizadorResolver.resolver().perfilInterno() != null;
        return paraListagem(processoRepository.findAll(especificacao, ordenacao), perfilInterno);
    }

    private Specification<Processo> especificacaoDaListagem(ProcessoFiltro filtro, String mensagemGrupoIndisponivel) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        GrupoProcesso.Criterio criterio = filtro.grupo() == null ? null : filtro.grupo().criterio(contexto)
                .orElseThrow(() -> new ValidacaoException(mensagemGrupoIndisponivel, List.of(
                        new ValidacaoException.CampoErro("grupo", "Grupo não disponível para este utilizador: " + filtro.grupo() + "."))));

        return construirEspecificacao(filtro, escopoEntidade(contexto, filtro), criterio, contexto.utilizadorId(),
                municipiosDaNutII(filtro.nutsII()));
    }

    /** `null` sem filtro por NUTS II; lista vazia quando a réplica não tem municípios ativos nessa NUTS II. */
    private List<String> municipiosDaNutII(String nutsII) {
        return nutsII == null ? null : snitReferenciaRepository.findCodigosMunicipiosDaNut2(nutsII);
    }

    /**
     * Único sítio que resolve o âmbito do pedido, pelo id da entidade da claim e nunca pelo NIF. Os perfis internos e
     * a entidade de tipo `DGT` veem tudo e podem filtrar por `entidadeId`; os outros veem só a Camada 2.
     */
    private static EscopoEntidade escopoEntidade(ContextoUtilizador contexto, ProcessoFiltro filtro) {
        return new EscopoEntidade(contexto, contexto.temVisibilidadeTransversal() ? filtro.entidadeId() : null);
    }

    private record EscopoEntidade(ContextoUtilizador contexto, Long filtroEntidadeId) {
    }

    /**
     * Painel Principal (API-PROC-031): por grupo, o total e a página pedida, no mesmo âmbito de {@link #listar}.
     * Sem `grupo` devolve todos os grupos que se aplicam ao utilizador (externo: 4; perfil interno: 2); com
     * `grupo` só esse, e um grupo que não se aplica é um 400. O critério de cada grupo é o do {@link GrupoProcesso},
     * sem outros filtros.
     */
    @Transactional(readOnly = true)
    public PainelProcessosResponse painel(GrupoProcesso grupo, Pageable pagina) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        EscopoEntidade escopo = escopoEntidade(contexto, FILTRO_VAZIO);
        List<GrupoProcesso> grupos = grupo == null
                ? Arrays.stream(GrupoProcesso.values()).filter(g -> g.criterio(contexto).isPresent()).toList()
                : List.of(grupo);

        List<GrupoPainelResponse> resposta = grupos.stream().map(g -> {
            GrupoProcesso.Criterio criterio = g.criterio(contexto)
                    .orElseThrow(() -> new ValidacaoException("Parâmetros de filtro inválidos.", List.of(
                            new ValidacaoException.CampoErro("grupo", "Grupo não disponível para este utilizador: " + g + "."))));
            Specification<Processo> especificacao = construirEspecificacao(FILTRO_VAZIO, escopo, criterio,
                    contexto.utilizadorId(), null);
            Page<Processo> resultado = processoRepository.findAll(especificacao, pagina);
            return new GrupoPainelResponse(g, resultado.getTotalElements(),
                    paraListagem(resultado.getContent(), contexto.perfilInterno() != null));
        }).toList();
        return new PainelProcessosResponse(resposta);
    }

    @Transactional(readOnly = true)
    public Processo obterComVisibilidade(UUID processoId) {
        return carregarComVisibilidade(processoId);
    }

    private Processo carregarComVisibilidade(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        return processo;
    }

    /**
     * Só visibilidade para ler: um `VISITANTE` consulta sem poder agir. As ações disponíveis e `editavel`
     * dizem o que o chamador poderia fazer, sem o autorizar a fazê-lo.
     */
    @Transactional(readOnly = true)
    public ProcessoDetalheResponse detalhe(UUID processoId) {
        Processo processo = carregarComVisibilidade(processoId);
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        String formulario = catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento())
                .map(Enum::name).orElse(null);
        boolean sgifr = FormularioFixo.SGIFR.name().equals(formulario);
        TerritorioInstrumentoResponse territorio = territorioInstrumentoService.territorio(processo.getDinamica());
        Optional<IdusProcesso> idusProcesso = idusProcessoRepository.findById(processo.getId());
        String idus = idusProcesso.map(IdusProcesso::getIdus).orElse(null);
        int submissao = idusProcesso.map(registo -> (int) registo.getSubmissao())
                .orElseGet(() -> (int) auditoriaRepository.countByTipoOcorrenciaAndIdOcorrenciaAndAcao(
                        TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), ACAO_SUBMETER));
        LocalizacaoRequest localizacaoGravada = CamposFormularioInicial.lerLocalizacao(processo.getDadosFormulario(), objectMapper);
        List<MotivoAlteracaoRequest> motivosGravados = CamposFormularioInicial.lerMotivosAlteracao(processo.getDadosFormulario(), objectMapper);
        Map<String, String> descricoes = descricoesGravadas(localizacaoGravada, motivosGravados);
        LocalizacaoResponse localizacao = localizacaoResponse(localizacaoGravada, descricoes);
        List<MotivoAlteracaoResponse> motivosAlteracao = motivosGravados.stream()
                .map(motivo -> new MotivoAlteracaoResponse(motivo.tipo(), motivo.codigo(),
                        descricoes.get(chaveLista(motivo.tipo(), motivo.codigo()))))
                .toList();
        boolean editavel = processoService.editavel(processo, contexto);
        // A PCM edita o formulário e acrescenta documentos; o destino e a submissão continuam só do autor.
        boolean editavelPelaPcm = processoService.editavelPelaPcm(processo, contexto);
        boolean dataAssinaturaNoFormulario = validadorSubmissaoProcesso.dataAssinaturaNoFormulario(processo);
        return ProcessoDetalheResponse.de(processo, formulario, territorio, idus, submissao, ultimaRejeicaoDgt(processo), ultimaRejeicaoIncm(processo),
                dataAssinaturaNoFormulario, editavel || editavelPelaPcm, processoService.acoesDisponiveis(processo, contexto),
                editavel, editavel ? pendenciasSubmissao(processo, contexto, dataAssinaturaNoFormulario) : List.of(),
                validadorSubmissaoProcesso.exigeOpcaoGeoconform(processo),
                validadorSubmissaoProcesso.exigeElementosAfetados(processo),
                completudeDocumentalService.exigePlantasEMetadados(processo),
                processoValidacaoService.informacaoBase(processo), processoValidacaoService.todosRevistos(processo),
                CamposFormularioInicial.lerFundamentacaoRjigt(processo.getDadosFormulario(), objectMapper),
                validadorSubmissaoProcesso.opcoesFundamentacaoRjigt(processo).stream().map(OpcaoListaResponse::de).toList(),
                localizacao, CamposFormularioInicial.lerConformaHierarquiaSuperior(processo.getDadosFormulario(), objectMapper),
                motivosAlteracao,
                sgifr ? regrasRedesDefesaSgifr.gravadas(processo).stream().map(RedeDefesaResponse::de).toList() : null,
                sgifr ? regrasRedesDefesaSgifr.aceites(processo).stream().map(RedeDefesaResponse::de).toList() : null,
                FormularioFixo.REN.name().equals(formulario) ? formularioRen(processo) : null,
                regrasTerritorioSgifr.territorio(sgifr ? FormularioFixo.SGIFR : null, processo),
                processoValidacaoService.rejeicoesEmValidacao(processo, contexto),
                associacaoInstrumentosService.tiposAdmitidos(processo.getDinamica()).stream()
                        .map(TipoInstrumentoResponse::de).toList(),
                associacaoInstrumentosService.podeAssociar(contexto, processo),
                validadorSubmissaoProcesso.dinamicasATerminar(processo).stream().map(DinamicaATerminarResponse::de).toList(),
                dinamicaAssociadaService.aceitaAssociacao(processo), dinamicaAssociadaService.associada(processo),
                destinoProcessoService.destinoEditavel(processo, editavel),
                CamposFormularioInicial.lerModalidadeRatificacao(processo.getDadosFormulario(), objectMapper),
                TiposAtoDisponiveis.paraResponse(processoService.tiposAtoDisponiveis(processo, contexto)),
                atorDgt(contexto), opcoesAlteracaoPeot(contexto), processoValidacaoService.revalidacaoPcm(processo, contexto));
    }

    /** Os campos do Resumo Validados são da DGT: a entidade e a PCM não os leem. */
    private static boolean atorDgt(ContextoUtilizador contexto) {
        return ATOR_DGT.equals(contexto.ator());
    }

    private List<OpcaoListaComIdResponse> opcoesAlteracaoPeot(ContextoUtilizador contexto) {
        if (!atorDgt(contexto)) {
            return List.of();
        }
        return listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(TipoListaValores.ALTERACAO_PEOT.name()).stream()
                .map(OpcaoListaComIdResponse::de)
                .toList();
    }

    private FormularioRenResponse formularioRen(Processo processo) {
        RegrasFormularioRen.Gravados gravados = regrasFormularioRen.gravados(processo.getDadosFormulario());
        return new FormularioRenResponse(
                regrasFormularioRen.motivoGravado(processo).map(MotivoProcedimentoResponse::de).orElse(null),
                regrasFormularioRen.motivosDisponiveis().stream().map(MotivoProcedimentoResponse::de).toList(),
                regrasFormularioRen.pedeDiplomaCorrigido(processo),
                gravados.diplomaCorrigido(),
                regrasFormularioRen.tipoInformacaoFixo(processo),
                gravados.regiaoCodigo(),
                gravados.municipioCodigo());
    }

    private Map<String, String> descricoesGravadas(LocalizacaoRequest localizacao, List<MotivoAlteracaoRequest> motivos) {
        Set<String> tipos = new HashSet<>();
        Set<String> codigos = new HashSet<>();
        if (localizacao != null && localizacao.freguesia() != null) {
            tipos.add(TipoListaValores.FREGUESIA.name());
            codigos.add(localizacao.freguesia());
        }
        if (localizacao != null && localizacao.toponimoCodigo() != null) {
            tipos.add(TipoListaValores.TOPONIMO.name());
            codigos.add(localizacao.toponimoCodigo());
        }
        motivos.stream().filter(motivo -> motivo.tipo() != null && motivo.codigo() != null).forEach(motivo -> {
            tipos.add(motivo.tipo());
            codigos.add(motivo.codigo());
        });
        if (codigos.isEmpty()) {
            return Map.of();
        }
        return listaValoresRepository.findByTipoInAndCodigoIn(tipos, codigos).stream()
                .collect(Collectors.toMap(valor -> chaveLista(valor.getTipo(), valor.getCodigo()), ListaValores::getDescricao,
                        (primeira, outra) -> primeira));
    }

    private static LocalizacaoResponse localizacaoResponse(LocalizacaoRequest localizacao, Map<String, String> descricoes) {
        if (localizacao == null) {
            return null;
        }
        return new LocalizacaoResponse(localizacao.aplicavelTodaArea(),
                opcao(TipoListaValores.FREGUESIA.name(), localizacao.freguesia(), descricoes),
                opcao(TipoListaValores.TOPONIMO.name(), localizacao.toponimoCodigo(), descricoes),
                localizacao.toponimoTexto());
    }

    private static OpcaoListaResponse opcao(String tipo, String codigo, Map<String, String> descricoes) {
        return codigo == null ? null : new OpcaoListaResponse(codigo, descricoes.get(chaveLista(tipo, codigo)));
    }

    private static String chaveLista(String tipo, String codigo) {
        return tipo + ":" + codigo;
    }

    /**
     * O que `podeSubmeter` mede, com a página do assistente onde se corrige. A Camada 3c fica de fora: depende do tipo
     * de ato e da parte da sessão, e `submeter` recusa-a à parte. A data de assinatura é dos Documentos quando se indica
     * na Informação Base do Texto do Ato; sem esse bloco é do formulário. Os formulários que já pedem a data repetem a
     * mesma pendência, que só aparece uma vez.
     */
    private List<PendenciaSubmissaoResponse> pendenciasSubmissao(Processo processo, ContextoUtilizador contexto,
                                                                 boolean dataAssinaturaNoFormulario) {
        List<PendenciaSubmissaoResponse> pendencias = new ArrayList<>();
        Pagina paginaDataAssinatura = dataAssinaturaNoFormulario ? Pagina.FORMULARIO : Pagina.DOCUMENTOS;
        validadorSubmissaoProcesso.pendencias(processo, contexto).forEach(campo -> pendencias.add(PendenciaSubmissaoResponse.de(
                CAMPO_DATA_ASSINATURA.equals(campo.path()) ? paginaDataAssinatura : Pagina.FORMULARIO, campo)));
        FormularioFixo formulario = catalogoFormularios
                .formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento()).orElse(null);
        validadorSubmissaoProcesso.pendenciasInformacaoBase(processo, formulario)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.DOCUMENTOS, c)));
        List<Optional<CampoErro>> doFormulario = List.of(
                validadorSubmissaoProcesso.pendenciaInstrumentosAssociados(processo),
                validadorSubmissaoProcesso.pendenciaElementosAfetados(processo),
                validadorSubmissaoProcesso.pendenciaEntidadeInstrutora(processo),
                validadorSubmissaoProcesso.pendenciaEntidadeEmitente(processo),
                validadorSubmissaoProcesso.pendenciaFundamentacaoRjigt(processo));
        doFormulario.forEach(campo -> campo.ifPresent(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c))));
        validadorSubmissaoProcesso.pendenciasLocalizacao(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        validadorSubmissaoProcesso.pendenciasMotivoAlteracao(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        if (formulario == FormularioFixo.REN) {
            regrasFormularioRen.pendencias(processo)
                    .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        }
        validadorSubmissaoProcesso.pendenciasRatificacao(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        validadorSubmissaoProcesso.pendenciasSuspensaoCcdr(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        validadorSubmissaoProcesso.pendenciasGoverno(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.FORMULARIO, c)));
        completudeDocumentalService.pendencias(processo).forEach(item -> pendencias.add(new PendenciaSubmissaoResponse(
                Pagina.DOCUMENTOS, item.config().getCodigo(), "Falta carregar: " + item.config().getNome() + ".")));
        completudeDocumentalService.pendenciasPlantas(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.PLANTAS, c)));
        completudeDocumentalService.pendenciasMetadados(processo)
                .forEach(c -> pendencias.add(PendenciaSubmissaoResponse.de(Pagina.METADADOS, c)));
        pendencias.sort(Comparator.comparing(PendenciaSubmissaoResponse::pagina));
        return pendencias.stream().distinct().toList();
    }

    /**
     * A recusa DGT mais recente em `processo_rejeicao`. As rejeições anteriores a essa tabela só existem em
     * `auditoria.dados_alteracao` (`motivoId`/`motivoCodigo`), por isso lê-se aí quando o Processo não tem linha.
     * Nulo se nunca foi rejeitada.
     */
    private UltimaRejeicaoDgtResponse ultimaRejeicaoDgt(Processo processo) {
        return processoRejeicaoRepository
                .findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.DGT)
                .map(rejeicao -> new UltimaRejeicaoDgtResponse(null, null, rejeicao.getCriadoEm(),
                        rejeicao.getNotasGerais()))
                .orElseGet(() -> ultimaRejeicaoDgtDaAuditoria(processo));
    }

    private UltimaRejeicaoDgtResponse ultimaRejeicaoDgtDaAuditoria(Processo processo) {
        return auditoriaRepository
                .findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                        TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), ACAO_REJEITAR)
                .map(auditoria -> {
                    JsonNode dados = lerDadosAlteracao(auditoria.getDadosAlteracao());
                    JsonNode motivoId = dados.path(CAMPO_MOTIVO_ID);
                    String descricao = motivoId.canConvertToLong()
                            ? listaValoresRepository.findById(motivoId.asLong()).map(ListaValores::getDescricao).orElse(null)
                            : null;
                    return new UltimaRejeicaoDgtResponse(dados.path(CAMPO_MOTIVO_CODIGO).asText(null), descricao,
                            auditoria.getCriadoEm(), null);
                })
                .orElse(null);
    }

    /**
     * A recusa mais recente da INCM em `processo_rejeicao`. As recusas anteriores a essa tabela só existem em
     * `auditoria.dados_alteracao`, por isso lê-se aí quando o Processo não tem linha. Nulo se a INCM nunca recusou
     * o Processo.
     */
    private UltimaRejeicaoIncmResponse ultimaRejeicaoIncm(Processo processo) {
        return processoRejeicaoRepository
                .findFirstByProcesso_IdAndOrigemOrderBySequenciaDesc(processo.getId(), OrigemRejeicao.INCM)
                .map(rejeicao -> new UltimaRejeicaoIncmResponse(
                        List.of(rejeicao.getMotivoTexto().split(RejeicaoProcessoService.SEPARADOR_MENSAGENS)),
                        rejeicao.getCriadoEm()))
                .orElseGet(() -> ultimaRejeicaoIncmDaAuditoria(processo));
    }

    private UltimaRejeicaoIncmResponse ultimaRejeicaoIncmDaAuditoria(Processo processo) {
        return auditoriaRepository
                .findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
                        TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), ACOES_REJEICAO_INCM)
                .map(auditoria -> {
                    List<String> mensagens = new ArrayList<>();
                    for (JsonNode mensagem : lerDadosAlteracao(auditoria.getDadosAlteracao()).path(CAMPO_MENSAGENS)) {
                        if (mensagem.isTextual()) {
                            mensagens.add(mensagem.asText());
                        }
                    }
                    return new UltimaRejeicaoIncmResponse(List.copyOf(mensagens), auditoria.getCriadoEm());
                })
                .orElse(null);
    }

    private JsonNode lerDadosAlteracao(String dadosAlteracaoJson) {
        if (dadosAlteracaoJson == null) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(dadosAlteracaoJson);
        } catch (JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }

    /**
     * Camada 2 (fora da visibilidade → 404), Camada 3b (não autor → 403) e processo editável (→ 409), como a gravação
     * do formulário que vai receber a escolha. A lista não depende da entidade da sessão: segue só a regra BL-PROC-062.
     */
    @Transactional(readOnly = true)
    public Page<DinamicaAssociavelResponse> dinamicasAssociaveis(UUID processoId, Pageable pagina) {
        Processo processo = carregarComVisibilidade(processoId);
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        autorizacaoService.verificarAutoria(contexto, processo);
        if (!processoService.editavel(processo, contexto)) {
            throw new EstadoInvalidoException("Só é possível escolher a dinâmica associada num processo editável pelo autor.");
        }
        return dinamicaAssociadaService.listar(processo, pagina)
                .map(linha -> DinamicaAssociavelResponse.de(linha.getDinamica(), linha.getInstrumento()));
    }

    @Transactional(readOnly = true)
    public Page<HistoricoProcessoResponse> historico(UUID processoId, Pageable pageable) {
        Processo processo = carregarComVisibilidade(processoId);
        Page<Auditoria> entradas = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaOrderByCriadoEmDescIdDesc(
                TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), pageable);
        Set<UUID> utilizadorIds = entradas.stream().map(Auditoria::getUtilizadorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> nomes = utilizadorRepository.findAllById(utilizadorIds).stream()
                .collect(Collectors.toMap(Utilizador::getId, Utilizador::getNome));
        return entradas.map(entrada -> HistoricoProcessoResponse.de(entrada, nomes.get(entrada.getUtilizadorId())));
    }

    @Transactional(readOnly = true)
    public EstatisticasProcessoResponse estatisticas(UUID processoId, Integer submissao, LocalDate dataDe, LocalDate dataAte) {
        Processo processo = carregarComVisibilidade(processoId);
        List<Instant> submissoes = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                        TIPO_OCORRENCIA_PROCESSO, processo.getId().toString(), ACAO_SUBMETER).stream()
                .map(Auditoria::getCriadoEm)
                .toList();

        List<ProcessoRejeicao> recusas = processoRejeicaoRepository.findByProcesso_IdOrderBySequenciaAsc(processo.getId())
                .stream()
                .filter(recusa -> submissao == null || submissaoDa(recusa, submissoes) == submissao)
                .filter(recusa -> dentroDoIntervalo(recusa.getCriadoEm(), dataDe, dataAte))
                .toList();
        Map<Long, List<ItemRecusaResponse>> itensPorRecusa = recusas.isEmpty() ? Map.of()
                : processoRejeicaoItemRepository.findByRejeicao_IdInOrderByIdAsc(
                                recusas.stream().map(ProcessoRejeicao::getId).toList()).stream()
                        .collect(Collectors.groupingBy(item -> item.getRejeicao().getId(),
                                Collectors.mapping(ItemRecusaResponse::de, Collectors.toList())));

        return new EstatisticasProcessoResponse(submissoes.size(), recusas.stream()
                .map(recusa -> new RecusaResponse(recusa.getSequencia(), recusa.getOrigem(), submissaoDa(recusa, submissoes),
                        recusa.getCriadoEm(), recusa.getNotasGerais(), mensagensIncm(recusa),
                        itensPorRecusa.getOrDefault(recusa.getId(), List.of())))
                .toList());
    }

    private static int submissaoDa(ProcessoRejeicao recusa, List<Instant> submissoes) {
        return (int) submissoes.stream().filter(momento -> !momento.isAfter(recusa.getCriadoEm())).count();
    }

    private static List<String> mensagensIncm(ProcessoRejeicao recusa) {
        if (recusa.getOrigem() != OrigemRejeicao.INCM || recusa.getMotivoTexto() == null) {
            return List.of();
        }
        return List.of(recusa.getMotivoTexto().split(RejeicaoProcessoService.SEPARADOR_MENSAGENS));
    }

    private static boolean dentroDoIntervalo(Instant momento, LocalDate dataDe, LocalDate dataAte) {
        if (dataDe != null && momento.isBefore(dataDe.atStartOfDay(ZONE_LISBOA).toInstant())) {
            return false;
        }
        return dataAte == null || momento.isBefore(dataAte.plusDays(1).atStartOfDay(ZONE_LISBOA).toInstant());
    }

    private Specification<Processo> construirEspecificacao(ProcessoFiltro filtro, EscopoEntidade escopo,
                                                           GrupoProcesso.Criterio criterio, UUID utilizadorId,
                                                           List<String> municipiosDaNutII) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Processo, Dinamica> dinamica = root.join("dinamica");
            // LEFT: uma Dinâmica iniciada sem IGT não tem Instrumento de início e os processos dela também se listam.
            Join<Dinamica, Instrumento> instrumento = dinamica.join("instrumento", JoinType.LEFT);
            if (Long.class != query.getResultType()) {
                // Fetch evita N+1 em autorNome (processo.criadoPor.nome). Só na query de dados: na de
                // count falha com "owner ... not present in the select list".
                root.fetch("criadoPor", JoinType.INNER);
            }
            List<Predicate> predicados = new ArrayList<>();
            adicionarPredicadosInstrumento(predicados, filtro, dinamica, instrumento, cb);
            adicionarPredicadoEntidade(predicados, escopo, root, query, cb);
            adicionarPredicadosProcesso(predicados, filtro, root, cb);
            adicionarPredicadosConcelho(predicados, filtro, municipiosDaNutII, instrumento, query, cb);
            adicionarPredicadoMotivos(predicados, filtro, escopo, root, cb);
            adicionarPredicadoSituacaoPcm(predicados, filtro, escopo, root, cb);
            if (criterio != null) {
                predicados.add(predicadoDoGrupo(criterio, utilizadorId, root, cb));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static void adicionarPredicadosInstrumento(List<Predicate> predicados, ProcessoFiltro filtro,
                                                       Join<Processo, Dinamica> dinamica,
                                                       Join<Dinamica, Instrumento> instrumento, CriteriaBuilder cb) {
        if (filtro.designacaoInstrumento() != null) {
            // Parcial, sem distinguir maiúsculas/minúsculas — pesquisa "à medida que escreve" no frontend. A designação
            // mostrada é a do Instrumento de início ou, sem ele, o descritivo da Dinâmica.
            predicados.add(cb.like(cb.lower(cb.coalesce(instrumento.<String>get("designacao"), dinamica.<String>get("descritivo"))),
                    "%" + filtro.designacaoInstrumento().toLowerCase() + "%"));
        }
        if (filtro.numeroInstrumento() != null) {
            predicados.add(cb.equal(instrumento.get("numero"), filtro.numeroInstrumento()));
        }
        if (filtro.tipoInstrumentoId() != null) {
            predicados.add(cb.equal(dinamica.get("instrumentoDinamicaConfig").get("tipoInstrumento").get("id"),
                    filtro.tipoInstrumentoId()));
        }
    }

    /** A entidade do processo, nunca a do Instrumento (que não tem): a mesma regra de `processoVisivel`, em consulta. */
    private static void adicionarPredicadoEntidade(List<Predicate> predicados, EscopoEntidade escopo, Root<Processo> root,
                                                   CriteriaQuery<?> query, CriteriaBuilder cb) {
        ContextoUtilizador contexto = escopo.contexto();
        if (contexto.temVisibilidadeTransversal()) {
            if (escopo.filtroEntidadeId() != null) {
                predicados.add(cb.equal(root.get("entidade").get("id"), escopo.filtroEntidadeId()));
            }
            return;
        }
        List<Predicate> alternativas = new ArrayList<>();
        if (contexto.entidadeId() != null) {
            alternativas.add(cb.equal(root.get("entidade").get("id"), contexto.entidadeId()));
            Subquery<Long> naDinamica = query.subquery(Long.class);
            Root<Processo> daEntidade = naDinamica.from(Processo.class);
            naDinamica.select(cb.literal(1L)).where(cb.equal(daEntidade.get("dinamica"), root.get("dinamica")),
                    cb.equal(daEntidade.get("entidade").get("id"), contexto.entidadeId()));
            alternativas.add(cb.and(cb.isTrue(root.get("instrumentoProcedimentoConfig").get("visivelNaDinamica")),
                    cb.exists(naDinamica)));
        }
        if (contexto.pcm()) {
            alternativas.add(cb.and(cb.equal(root.get("estado"), EstadoProcesso.VALIDADO_DGT),
                    cb.equal(root.get("pcmAtribuidoA").get("id"), contexto.utilizadorId())));
        }
        predicados.add(alternativas.isEmpty() ? cb.disjunction() : cb.or(alternativas.toArray(new Predicate[0])));
    }

    private static void adicionarPredicadosProcesso(List<Predicate> predicados, ProcessoFiltro filtro, Root<Processo> root,
                                                    CriteriaBuilder cb) {
        if (filtro.estado() != null && !filtro.estado().isEmpty()) {
            // Um valor fora do enum não casa com nenhuma linha — zero resultados, não 400.
            List<EstadoProcesso> estados = Arrays.stream(EstadoProcesso.values())
                    .filter(estado -> filtro.estado().contains(estado.name()))
                    .toList();
            predicados.add(estados.isEmpty() ? cb.disjunction() : root.get("estado").in(estados));
        }
        if (filtro.numeroProcesso() != null) {
            predicados.add(cb.equal(root.get("numeroProcesso"), filtro.numeroProcesso()));
        }
        if (filtro.ano() != null) {
            predicados.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), LocalDate.of(filtro.ano(), 1, 1).atStartOfDay(ZONE_LISBOA).toInstant()));
            predicados.add(cb.lessThan(root.get("criadoEm"), LocalDate.of(filtro.ano() + 1, 1, 1).atStartOfDay(ZONE_LISBOA).toInstant()));
        }
        if (filtro.dataDe() != null) {
            predicados.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), filtro.dataDe().atStartOfDay(ZONE_LISBOA).toInstant()));
        }
        if (filtro.dataAte() != null) {
            predicados.add(cb.lessThan(root.get("criadoEm"), filtro.dataAte().plusDays(1).atStartOfDay(ZONE_LISBOA).toInstant()));
        }
        if (filtro.numeroDr() != null) {
            predicados.add(cb.equal(root.get("numeroDr"), filtro.numeroDr()));
        }
        if (filtro.incmActIdentifier() != null) {
            predicados.add(cb.equal(root.get("incmActIdentifier"), filtro.incmActIdentifier()));
        }
    }

    private static void adicionarPredicadosConcelho(List<Predicate> predicados, ProcessoFiltro filtro,
                                                    List<String> municipiosDaNutII, Join<Dinamica, Instrumento> instrumento,
                                                    CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (filtro.concelho() != null) {
            predicados.add(existeConcelho(instrumento, query, cb,
                    concelho -> cb.equal(concelho.get("id").get("municipioCodigo"), filtro.concelho())));
        }
        if (municipiosDaNutII != null) {
            // Sem municípios, `IN ()` não vai à base: o predicado é falso e a página vem vazia.
            predicados.add(municipiosDaNutII.isEmpty()
                    ? cb.disjunction()
                    : existeConcelho(instrumento, query, cb,
                            concelho -> concelho.get("id").get("municipioCodigo").in(municipiosDaNutII)));
        }
    }

    /**
     * Algum dos motivos pedidos, só para perfis internos (a monitorização é da DGT); para os outros o parâmetro é
     * ignorado. Cada motivo vai como parâmetro ligado (`[{"tipo":…,"codigo":…}]`), nunca concatenado no SQL.
     */
    private static void adicionarPredicadoMotivos(List<Predicate> predicados, ProcessoFiltro filtro, EscopoEntidade escopo,
                                                  Root<Processo> root, CriteriaBuilder cb) {
        if (filtro.motivosAlteracao() == null || filtro.motivosAlteracao().isEmpty()
                || escopo.contexto().perfilInterno() == null) {
            return;
        }
        // `cb.literal` seria escrito no SQL; `value` vai sempre como parâmetro.
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        Predicate[] alternativas = filtro.motivosAlteracao().stream()
                .map(motivo -> JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode()
                        .put("tipo", motivo.tipo()).put("codigo", motivo.codigo())).toString())
                .map(json -> cb.isTrue(cb.function(FuncoesJsonbProcesso.MOTIVOS_ALTERACAO_CONTEM, Boolean.class,
                        root.get("dadosFormulario"), hcb.value(json))))
                .toArray(Predicate[]::new);
        predicados.add(cb.or(alternativas));
    }

    /** Só para perfis internos, como os motivos; para os outros o parâmetro é ignorado. */
    private static void adicionarPredicadoSituacaoPcm(List<Predicate> predicados, ProcessoFiltro filtro, EscopoEntidade escopo,
                                                      Root<Processo> root, CriteriaBuilder cb) {
        if (filtro.situacoesPcm() == null || filtro.situacoesPcm().isEmpty() || escopo.contexto().perfilInterno() == null) {
            return;
        }
        Predicate[] alternativas = filtro.situacoesPcm().stream()
                .map(situacao -> predicadoSituacaoPcm(situacao, root, cb))
                .toArray(Predicate[]::new);
        predicados.add(cb.or(alternativas));
    }

    private static Predicate predicadoSituacaoPcm(SituacaoPcm situacao, Root<Processo> root, CriteriaBuilder cb) {
        return switch (situacao) {
            case COM_PCM -> cb.isNotNull(root.get("pcmAtribuidoA"));
            case DEVOLVIDO -> cb.and(cb.isNull(root.get("pcmAtribuidoA")), cb.isNotNull(root.get("pcmDevolvidoEm")));
            case SEM_PCM -> cb.and(cb.isNull(root.get("pcmAtribuidoA")), cb.isNull(root.get("pcmDevolvidoEm")));
        };
    }

    private static Predicate existeConcelho(Join<Dinamica, Instrumento> instrumento, CriteriaQuery<?> query,
                                            CriteriaBuilder cb, Function<Root<InstrumentoConcelho>, Predicate> criterio) {
        Subquery<Integer> subconsulta = query.subquery(Integer.class);
        Root<InstrumentoConcelho> concelho = subconsulta.from(InstrumentoConcelho.class);
        subconsulta.select(cb.literal(1)).where(
                cb.equal(concelho.get("id").get("instrumentoId"), instrumento.get("id")),
                criterio.apply(concelho));
        return cb.exists(subconsulta);
    }

    private static Predicate predicadoDoGrupo(GrupoProcesso.Criterio criterio, UUID utilizadorId, Root<Processo> root,
                                              CriteriaBuilder cb) {
        Predicate predicado = root.get("estado").in(criterio.estados());
        if (criterio.apenasProprios()) {
            predicado = cb.and(predicado, cb.equal(root.get("criadoPor").get("id"), utilizadorId));
        }
        if (criterio.excluiValidadosComPcm()) {
            predicado = cb.and(predicado, cb.or(cb.notEqual(root.get("estado"), EstadoProcesso.VALIDADO_DGT),
                    cb.isNull(root.get("pcmAtribuidoA"))));
        }
        if (criterio.incluiValidadosAtribuidos()) {
            Predicate atribuidos = cb.and(cb.equal(root.get("estado"), EstadoProcesso.VALIDADO_DGT),
                    cb.equal(root.get("pcmAtribuidoA").get("id"), utilizadorId));
            predicado = cb.or(predicado, atribuidos);
        }
        return predicado;
    }
}
