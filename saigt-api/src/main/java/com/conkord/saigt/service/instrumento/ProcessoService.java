package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.AtualizarDadosFormularioRequest;
import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.dto.instrumento.DesbloquearCampoRequest;
import com.conkord.saigt.dto.instrumento.DevolverDgtRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseRequest;
import com.conkord.saigt.dto.instrumento.ReatribuirAutorRequest;
import com.conkord.saigt.dto.instrumento.RegistoExternoProcessoRequest;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.dto.instrumento.ValidarProcessoRequest;
import com.conkord.saigt.dto.instrumento.VersaoProcessoRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.ElementosAfetadosEmFaltaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ItensPorReverException;
import com.conkord.saigt.exception.OpcaoGeoconformEmFaltaException;
import com.conkord.saigt.exception.PcmEstadoInvalidoException;
import com.conkord.saigt.exception.PcmUtilizadorInvalidoException;
import com.conkord.saigt.exception.ProcessoEmCursoException;
import com.conkord.saigt.exception.RedesDefesaInvalidasException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaInvalidaException;
import com.conkord.saigt.exception.TipoAtoNaoAutorizadoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.VersaoDesatualizadaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.documento.BloqueioDocumentosCorrecao;
import com.conkord.saigt.service.documento.CompletudeDocumentalService;
import com.conkord.saigt.service.documento.CopiaAnexosNotificacaoService;
import com.conkord.saigt.service.documento.MetadadosMotuService;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.conkord.saigt.service.documento.RetencaoRegistoExternoService;
import com.conkord.saigt.service.idus.IdusProcessoService;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen;
import com.conkord.saigt.service.notificacao.NotificacaoOrigemService;
import com.conkord.saigt.service.instrumento.formulario.RegrasRedesDefesaSgifr;
import com.conkord.saigt.service.instrumento.formulario.RegrasTerritorioSgifr;
import com.conkord.saigt.service.notificacao.PedidoApoioService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * "Processo" é sempre a tentativa, nunca o Instrumento. A autorização vem de
 * {@code AutorizacaoService#verificarPossibilidadeDeAgir} e, para o `tipoAtoId`, de
 * {@code AutorizacaoService#verificarAutorizacaoPublicacao}.
 */
@Service
public class ProcessoService {

    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_CRIACAO = "CRIACAO";
    private static final String ACAO_SUBMETER = "SUBMETER";
    private static final String ACAO_RESGATAR = "RESGATAR";
    private static final String ACAO_CORRIGIR = "CORRIGIR";
    private static final String ACAO_CAPTURAR = "CAPTURAR";
    private static final String ACAO_VALIDAR = "VALIDAR";
    private static final String ACAO_REJEITAR = "REJEITAR";
    private static final String ACAO_ANULAR = "ANULAR";
    private static final String ACAO_REATRIBUIR_AUTOR = "REATRIBUIR_AUTOR";
    private static final String ORIGEM_DADOS_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String ACAO_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String ACAO_ENVIAR_PCM = "ENVIAR_PCM";
    private static final String ACAO_DEVOLVER_DGT = "DEVOLVER_DGT";
    private static final String ACAO_ASSOCIAR_DINAMICA = "ASSOCIAR_DINAMICA";
    private static final String ACAO_DESBLOQUEAR_CAMPO = "DESBLOQUEAR_CAMPO";
    private static final String ACAO_ALTERAR_CAMPO = "ALTERAR_CAMPO";
    private static final String CAMPO_CAMPO = "campo";
    private static final String CAMPO_CAMPOS = "campos";
    private static final String CAMPO_VALOR_ANTIGO = "valorAntigo";
    private static final String CAMPO_VALOR_NOVO = "valorNovo";
    private static final String MSG_DESBLOQUEIO_FORA_DA_CORRECAO =
            "Só é possível desbloquear campos de um processo em correção.";
    private static final String MSG_CAMPO_FORA_DO_FORMULARIO = "Este campo não é do formulário inicial.";
    private static final String CAMPO_DINAMICA_ASSOCIADA_ANTERIOR = "dinamicaAssociadaIdAnterior";
    private static final String CAMPO_DINAMICA_ASSOCIADA = "dinamicaAssociadaId";
    private static final String SEGMENTO_SUBMETER = "submeter";
    private static final String SEGMENTO_RESGATAR = "resgatar";
    private static final String SEGMENTO_CORRIGIR = "corrigir";
    private static final String SEGMENTO_CAPTURAR = "capturar";
    private static final String SEGMENTO_VALIDAR = "validar";
    private static final String SEGMENTO_REJEITAR = "rejeitar";
    private static final String SEGMENTO_ANULAR = "anular";
    private static final String SEGMENTO_ENVIAR_INCM = "enviar-incm";
    private static final String SEGMENTO_ENVIAR_PCM = "enviar-pcm";
    private static final String SEGMENTO_DEVOLVER_DGT = "devolver-dgt";
    static final String MSG_RATIFICACAO_SEM_PCM = "Na Ratificação, envie o processo à PCM antes da publicação.";
    private static final String SEGMENTO_REATRIBUIR_AUTOR = "reatribuir-autor";
    private static final String SEGMENTO_DESBLOQUEAR_CAMPO = "desbloquear-campo";
    private static final String SERIE_DR_PRIMEIRA = "PRIMEIRA";
    private static final String SERIE_DR_SEGUNDA_H = "SEGUNDA_H";
    private static final String SERIE_DR_SEGUNDA_C = "SEGUNDA_C";
    private static final String CAMPO_MOTIVO = "motivo";
    private static final String CAMPO_NOTIFICACAO_ORIGEM_ID = "notificacaoOrigemId";
    private static final String CAMPO_DOCUMENTOS_COPIADOS = "documentosCopiados";
    private static final String CAMPO_DOCUMENTOS_BLOQUEADOS = "documentosBloqueados";
    private static final String CAMPO_NOVO_UTILIZADOR_ID = "novoUtilizadorId";
    private static final String CAMPO_UTILIZADOR_ID = "utilizadorId";
    private static final String MSG_PCM_SERIE_OU_ESTADO =
            "Só processos validados pela DGT de Procedimentos da 1.ª série podem ser enviados à PCM.";
    private static final String CAMPO_PEDIDO_APOIO_ID = "pedidoApoioId";
    private static final String MSG_PROCESSO_NAO_ENCONTRADO = "Processo não encontrado.";
    static final String MSG_PCM_SO_EM_VALIDADO_DGT = "A PCM só corrige o processo validado pela DGT que lhe foi atribuído.";
    private static final String MSG_TIPO_ATO_NAO_ENCONTRADO = "Tipo de Ato não encontrado.";
    private static final String MSG_CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    private static final String MSG_UTILIZADOR_NAO_ENCONTRADO = "Utilizador não encontrado.";
    private static final String MSG_ESTADO_NAO_TRANSFERIVEL = "O processo não pode ser transferido neste estado.";
    private static final String MSG_SEM_FORMULARIO = "Este procedimento ainda não tem formulário na aplicação.";
    private static final String MSG_INFORMACAO_BASE_NAO_PREVISTA = "Informação Base não prevista neste procedimento.";
    private static final String CAMPO_COM_GEOCONFORM = "comGeoconform";
    private static final String CAMPO_CONFIRMACOES = "confirmacoes";
    private static final String CAMPO_DECLARACAO_TERMINO = "declaracaoTermino";
    private static final String CAMPO_DINAMICAS_A_TERMINAR = "dinamicasATerminar";
    private static final String MSG_OPCAO_GEOCONFORM_EM_FALTA = "Indique se utiliza o GeoConform.";
    private static final String MSG_SEM_PERGUNTA_GEOCONFORM = "Este procedimento não pergunta pela utilização do GeoConform.";
    private static final String CAMPO_INFORMACAO_BASE_DELIBERACAO = "informacaoBase.deliberacao";
    private static final String CAMPO_INFORMACAO_BASE_TEXTO_ATO = "informacaoBase.textoAto";

    private static final Logger log = LoggerFactory.getLogger(ProcessoService.class);

    /** Os estados em que o owner age; nos restantes a transferência de autoria é recusada com 409. */
    static final Set<EstadoProcesso> ESTADOS_TRANSFERIVEIS = Set.copyOf(EnumSet.of(
            EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM));

    private final DinamicaRepository dinamicaRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final TipoAtoRepository tipoAtoRepository;
    private final ProcessoRepository processoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;
    private final DinamicaService dinamicaService;
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    private final EnvioIncmService envioIncmService;
    private final CompletudeDocumentalService completudeDocumentalService;
    private final ProcessoValidacaoService processoValidacaoService;
    private final ValidadorSubmissaoProcesso validadorSubmissaoProcesso;
    private final CatalogoFormularios catalogoFormularios;
    private final CaducidadeDinamicaService caducidadeDinamicaService;
    private final IdusProcessoService idusProcessoService;
    private final RejeicaoProcessoService rejeicaoProcessoService;
    private final MetadadosMotuService metadadosMotuService;
    private final EntidadeRepository entidadeRepository;
    private final BloqueioDocumentosCorrecao bloqueioDocumentosCorrecao;
    private final RetencaoDocumentosService retencaoDocumentosService;
    private final PedidoApoioService pedidoApoioService;
    private final ApplicationEventPublisher publicador;
    private final NotificacaoOrigemService notificacaoOrigemService;
    private final CopiaAnexosNotificacaoService copiaAnexosNotificacaoService;
    private final RetencaoRegistoExternoService retencaoRegistoExternoService;
    private final DinamicaAssociadaService dinamicaAssociadaService;
    private final DestinoProcessoService destinoProcessoService;
    private final AtosReferiveis atosReferiveis;
    private final RegrasRedesDefesaSgifr regrasRedesDefesaSgifr;
    private final RegrasFormularioRen regrasFormularioRen;
    private final RegrasTerritorioSgifr regrasTerritorioSgifr;

    public ProcessoService(DinamicaRepository dinamicaRepository,
                            InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                            TipoAtoRepository tipoAtoRepository,
                            ProcessoRepository processoRepository, UtilizadorRepository utilizadorRepository,
                            AutorizacaoService autorizacaoService,
                            ContextoUtilizadorResolver contextoUtilizadorResolver, AuditoriaService auditoriaService,
                            ObjectMapper objectMapper, DinamicaService dinamicaService,
                            UtilizadorEntidadeRepository utilizadorEntidadeRepository,
                            EnvioIncmService envioIncmService,
                            CompletudeDocumentalService completudeDocumentalService,
                            ProcessoValidacaoService processoValidacaoService,
                            ValidadorSubmissaoProcesso validadorSubmissaoProcesso,
                            CatalogoFormularios catalogoFormularios,
                            CaducidadeDinamicaService caducidadeDinamicaService,
                            IdusProcessoService idusProcessoService,
                            RejeicaoProcessoService rejeicaoProcessoService,
                            MetadadosMotuService metadadosMotuService,
                            EntidadeRepository entidadeRepository,
                            BloqueioDocumentosCorrecao bloqueioDocumentosCorrecao,
                            RetencaoDocumentosService retencaoDocumentosService,
                            PedidoApoioService pedidoApoioService,
                            ApplicationEventPublisher publicador,
                            NotificacaoOrigemService notificacaoOrigemService,
                            CopiaAnexosNotificacaoService copiaAnexosNotificacaoService,
                            RetencaoRegistoExternoService retencaoRegistoExternoService,
                            DinamicaAssociadaService dinamicaAssociadaService,
                            DestinoProcessoService destinoProcessoService,
                            AtosReferiveis atosReferiveis,
                            RegrasRedesDefesaSgifr regrasRedesDefesaSgifr,
                            RegrasTerritorioSgifr regrasTerritorioSgifr,
                            RegrasFormularioRen regrasFormularioRen) {
        this.dinamicaRepository = dinamicaRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.tipoAtoRepository = tipoAtoRepository;
        this.processoRepository = processoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
        this.objectMapper = objectMapper;
        this.dinamicaService = dinamicaService;
        this.utilizadorEntidadeRepository = utilizadorEntidadeRepository;
        this.envioIncmService = envioIncmService;
        this.completudeDocumentalService = completudeDocumentalService;
        this.processoValidacaoService = processoValidacaoService;
        this.validadorSubmissaoProcesso = validadorSubmissaoProcesso;
        this.catalogoFormularios = catalogoFormularios;
        this.caducidadeDinamicaService = caducidadeDinamicaService;
        this.idusProcessoService = idusProcessoService;
        this.rejeicaoProcessoService = rejeicaoProcessoService;
        this.metadadosMotuService = metadadosMotuService;
        this.entidadeRepository = entidadeRepository;
        this.bloqueioDocumentosCorrecao = bloqueioDocumentosCorrecao;
        this.retencaoDocumentosService = retencaoDocumentosService;
        this.pedidoApoioService = pedidoApoioService;
        this.publicador = publicador;
        this.notificacaoOrigemService = notificacaoOrigemService;
        this.copiaAnexosNotificacaoService = copiaAnexosNotificacaoService;
        this.retencaoRegistoExternoService = retencaoRegistoExternoService;
        this.dinamicaAssociadaService = dinamicaAssociadaService;
        this.destinoProcessoService = destinoProcessoService;
        this.atosReferiveis = atosReferiveis;
        this.regrasRedesDefesaSgifr = regrasRedesDefesaSgifr;
        this.regrasFormularioRen = regrasFormularioRen;
        this.regrasTerritorioSgifr = regrasTerritorioSgifr;
    }

    /** `estado`/`origemDados`/`versao` vêm dos defaults do campo. */
    @Transactional
    public Processo criar(UUID dinamicaId, CriarProcessoRequest request) {
        Dinamica dinamica = dinamicaRepository.findByIdParaCriarProcesso(dinamicaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dinâmica não encontrada."));
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();

        InstrumentoProcedimentoConfig config = instrumentoProcedimentoConfigRepository
                .findById(request.instrumentoProcedimentoConfigId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Procedimento não encontrada."));

        autorizacaoService.verificarPossibilidadeDeAgir(contexto, dinamica, config);
        Processo processoReferido = processoReferido(dinamica, config, request.processoReferidoId());

        // Opcional: sem ele o processo nasce sem tipo de ato, que a gravação e a submissão verificam (Camada 3c).
        TipoAto tipoAto = request.tipoAtoId() == null ? null : carregarTipoAto(request.tipoAtoId());

        Utilizador utilizador = utilizadorRepository.findById(contexto.utilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_UTILIZADOR_NAO_ENCONTRADO));

        Processo processo = new Processo(dinamica, config, utilizador, entidadeDaSessao(contexto));
        processo.setTipoAto(tipoAto);
        processo.setProcessoReferido(processoReferido);
        // Sem tipo de ato verifica só a parte da adesão: o `/disponivel` já não oferece o que a entidade não pode publicar.
        autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo);

        processo.setOcorrencia(proximaOcorrencia(dinamica, processo.getTentativa(), config));

        Processo guardado;
        try {
            guardado = processoRepository.saveAndFlush(processo);
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException("Já existe uma tentativa com este número para este Procedimento.");
        }
        idusProcessoService.criar(guardado);
        metadadosMotuService.criarSerie(guardado);

        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_CRIACAO,
                utilizador.getId(), guardado.getEntidade().getId(), null, null);

        return guardado;
    }

    /**
     * O ato visado é obrigatório e verificado nos Procedimentos `ato_pontual` e proibido nos outros (BL-PROC-037). Numa
     * Dinâmica terminada o ato pontual recusa-se, como no `/disponivel`, que só oferece as Dinâmicas em aberto.
     */
    private Processo processoReferido(Dinamica dinamica, InstrumentoProcedimentoConfig config, UUID processoReferidoId) {
        if (!config.isAtoPontual()) {
            if (processoReferidoId != null) {
                throw new ReferenciaInvalidaException();
            }
            return null;
        }
        if (dinamica.getEstado() == EstadoDinamica.TERMINADA) {
            throw new EstadoInvalidoException("A dinâmica está terminada.");
        }
        return atosReferiveis.verificar(processoReferidoId, dinamica);
    }

    private TipoAto carregarTipoAto(Long tipoAtoId) {
        return tipoAtoRepository.findById(tipoAtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_TIPO_ATO_NAO_ENCONTRADO));
    }

    /**
     * As opções do «Tipo de ato» do cabeçalho para a sessão: a mesma regra de `tiposAtoDisponiveis` do `/disponivel`
     * ({@link TiposAtoDisponiveis}), com o catálogo da série efetiva do processo.
     */
    public List<TipoAto> tiposAtoDisponiveis(Processo processo, ContextoUtilizador contexto) {
        return tiposAtoDisponiveis(processo.serieDrEfetiva(), contexto);
    }

    private List<TipoAto> tiposAtoDisponiveis(String serieDr, ContextoUtilizador contexto) {
        if (serieDr == null) {
            return List.of();
        }
        boolean primeira = SERIE_DR_PRIMEIRA.equals(serieDr);
        List<TipoAto> catalogo = primeira ? tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull()
                : tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull();
        return TiposAtoDisponiveis.filtrar(serieDr, contexto, primeira ? catalogo : List.of(), primeira ? List.of() : catalogo);
    }

    /**
     * Só um valor diferente do gravado se verifica: repetir o gravado (o ecrã reenvia-o com o rascunho) é aceite mesmo
     * que o claim tenha mudado, porque a submissão volta a correr a Camada 3c. Corre antes de qualquer escrita.
     */
    private TipoAto tipoAtoPedido(Processo processo, Long tipoAtoId, String serieDr, ContextoUtilizador contexto) {
        if (tipoAtoId == null || (processo.getTipoAto() != null && tipoAtoId.equals(processo.getTipoAto().getId()))) {
            return processo.getTipoAto();
        }
        TipoAto tipoAto = carregarTipoAto(tipoAtoId);
        boolean disponivel = tiposAtoDisponiveis(serieDr, contexto).stream()
                .anyMatch(candidato -> candidato.getId().equals(tipoAtoId));
        if (!disponivel) {
            log.warn("Tipo de ato recusado na gravação, fora dos disponíveis — processoId={}, tipoAtoId={}",
                    processo.getId(), tipoAtoId);
            throw new TipoAtoNaoAutorizadoException("Tipo de ato não autorizado ao utilizador pela INCM.");
        }
        return tipoAto;
    }

    /**
     * A série que fica depois da gravação: a pedida, só nos Procedimentos sem série configurada (nos outros um valor é
     * recusado, mesmo igual ao do Procedimento); omitida, a gravada.
     */
    private static String seriePedida(Processo processo, String serieDr) {
        if (serieDr == null) {
            return processo.serieDrEfetiva();
        }
        if (!processo.escolheSerieDr()) {
            throw new ValidacaoException(MSG_CAMPO_NAO_APLICAVEL,
                    List.of(new ValidacaoException.CampoErro(CamposFormularioInicial.SERIE_DR, MSG_CAMPO_NAO_APLICAVEL)));
        }
        return serieDr;
    }

    /**
     * Mudar de série limpa o tipo de ato gravado que deixa de estar entre os disponíveis da série nova (EF v1.5
     * l.1691: escolhida a série, só os tipos dela). Um tipo novo no mesmo pedido não se limpa: verifica-se, e fora
     * da lista recusa-se com 403 como sempre.
     */
    private boolean tipoAtoForaDaSerieNova(Processo processo, Long tipoAtoIdPedido, String serieDr,
                                           ContextoUtilizador contexto) {
        TipoAto gravado = processo.getTipoAto();
        if (gravado == null || Objects.equals(serieDr, processo.serieDrEfetiva())
                || (tipoAtoIdPedido != null && !tipoAtoIdPedido.equals(gravado.getId()))) {
            return false;
        }
        boolean fora = tiposAtoDisponiveis(serieDr, contexto).stream()
                .noneMatch(candidato -> candidato.getId().equals(gravado.getId()));
        if (fora) {
            log.info("Tipo de ato limpo com a mudança de série — processoId={}, serieDr={}", processo.getId(), serieDr);
        }
        return fora;
    }

    /** O processo pertence à entidade da sessão, pelo id da claim; sem ele (internos, token sem `entidade.id`) recusa. */
    private Entidade entidadeDaSessao(ContextoUtilizador contexto) {
        if (contexto.entidadeId() == null) {
            throw new AcaoNaoAutorizadaException("A sessão não tem entidade para criar o processo.");
        }
        return entidadeRepository.findById(contexto.entidadeId())
                .orElseThrow(() -> new AcaoNaoAutorizadaException("A entidade da sessão não existe."));
    }

    /**
     * Conta dentro da Tentativa: 1 no primeiro processo do Procedimento nela. Outro só depois de `ANULADO`, ou de
     * `PUBLICADO` num Procedimento `repetivel` ou ato pontual; `REJEITADO_DGT` e `ANULADO_INCM` corrigem-se no mesmo processo.
     */
    private int proximaOcorrencia(Dinamica dinamica, int tentativa, InstrumentoProcedimentoConfig config) {
        return processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                        dinamica, tentativa, config)
                .map(ultima -> {
                    boolean repeticaoPermitida = config.repeteDepoisDePublicado() && ultima.getEstado() == EstadoProcesso.PUBLICADO;
                    if (!repeticaoPermitida && !EstadoProcesso.PERMITEM_NOVA_TENTATIVA.contains(ultima.getEstado())) {
                        throw new ProcessoEmCursoException(config.isAtoPontual() ? "Já existe um processo em curso deste ato."
                                : "Já existe um processo em curso para este procedimento.");
                    }
                    return ultima.getOcorrencia() + 1;
                })
                .orElse(1);
    }

    /**
     * Procedimento publicado no DR fora da plataforma: nasce já em `PUBLICADO`, sem transições nem
     * sequenciamento (os anteriores podem não estar publicados). `criado_por` é o `MASTER` que regista:
     * não há autor real e a coluna não é nula. Com `notificacaoOrigemId`, resolve a notificação e copia os
     * anexos para o depósito na mesma transação; a retenção dos documentos fica para depois do commit.
     */
    @Transactional
    public ResultadoRegistoExterno registoExterno(UUID dinamicaId, RegistoExternoProcessoRequest request) {
        Dinamica dinamica = dinamicaRepository.findById(dinamicaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dinâmica não encontrada."));
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        autorizacaoService.verificarPerfilMaster(contexto);

        InstrumentoProcedimentoConfig config = instrumentoProcedimentoConfigRepository
                .findById(request.instrumentoProcedimentoConfigId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Configuração de Procedimento não encontrada."));
        Utilizador admin = utilizadorRepository.findById(contexto.utilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_UTILIZADOR_NAO_ENCONTRADO));

        Entidade entidade = entidadeRepository.findById(request.entidadeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entidade não encontrada."));
        // Verificada antes de gravar o processo: um 400, 404 ou 409 da notificação não consome o número de processo.
        Notificacao origem = request.notificacaoOrigemId() == null ? null
                : notificacaoOrigemService.carregarParaRegistoExterno(request.notificacaoOrigemId());
        Processo processo = new Processo(dinamica, config, admin, entidade);
        processo.setOcorrencia(proximaOcorrenciaSemGate(dinamica, processo.getTentativa(), config));
        processo.setEstado(EstadoProcesso.PUBLICADO);
        processo.setOrigemDados(ORIGEM_DADOS_REGISTO_EXTERNO);
        processo.setNumeroDr(request.numeroDr());
        processo.setDataPublicacaoDr(request.dataPublicacaoDr());

        Processo guardado;
        try {
            guardado = processoRepository.saveAndFlush(processo);
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException("Já existe uma tentativa com este número para este Procedimento.");
        }
        idusProcessoService.criar(guardado);
        idusProcessoService.registarPublicacaoIncm(guardado);

        int documentosCopiados = 0;
        if (origem != null) {
            notificacaoOrigemService.resolver(origem, guardado);
            documentosCopiados = copiaAnexosNotificacaoService.copiarParaDeposito(guardado, origem.getId());
        }
        retencaoRegistoExternoService.reterAposCommit(guardado.getId());

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_MOTIVO, request.motivo());
        if (origem != null) {
            dados.put(CAMPO_NOTIFICACAO_ORIGEM_ID, origem.getId().toString());
        }
        dados.put(CAMPO_DOCUMENTOS_COPIADOS, documentosCopiados);
        auditoriaService.registarTransicao(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_REGISTO_EXTERNO,
                TransicaoEstado.de(null, EstadoProcesso.PUBLICADO), contexto.utilizadorId(), entidade.getId(), dados);
        log.info("Registo externo | processoId={} notificacaoOrigemId={} documentosCopiados={}",
                guardado.getId(), origem == null ? null : origem.getId(), documentosCopiados);
        // Sem dados_formulario não há prazo: a data fica por calcular, com aviso no log.
        caducidadeDinamicaService.definirNaPublicacao(guardado);
        dinamicaService.verificarConclusaoAutomatica(dinamica);
        return new ResultadoRegistoExterno(guardado, documentosCopiados);
    }

    /** Sem o gate de {@link #proximaOcorrencia}: o registo externo não é uma tentativa do fluxo normal. */
    private int proximaOcorrenciaSemGate(Dinamica dinamica, int tentativa, InstrumentoProcedimentoConfig config) {
        return processoRepository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                        dinamica, tentativa, config)
                .map(ultima -> ultima.getOcorrencia() + 1)
                .orElse(1);
    }

    /**
     * O autor, em `EM_SUBMISSAO`, ou a PCM, no `VALIDADO_DGT` que lhe foi atribuído; nunca transiciona estado. Substitui os campos INCM e
     * `dados_formulario` (omitido = nulo) sem verificar obrigatoriedade, que só vale em `submeter`; a regra do
     * extrato e os valores de `elementosAfetados` e de `fundamentacaoRjigt` são a exceção, porque recusam um valor
     * e não a falta dele.
     */
    @Transactional
    public Processo atualizarDadosFormulario(UUID processoId, AtualizarDadosFormularioRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarParaEditar(processoId, contexto, request.versao());
        verificarEstadoPermiteEdicao(processo, contexto);
        FormularioFixo formulario = exigirFormulario(processo);
        boolean sgifr = formulario == FormularioFixo.SGIFR;
        boolean ren = formulario == FormularioFixo.REN;
        if (!sgifr) {
            verificarSemRedesDefesa(request.redesDefesa());
        }
        RegrasFormularioRen.CamposRen camposRen = new RegrasFormularioRen.CamposRen(request.motivoProcedimentoId(),
                request.diplomaCorrigido(), request.regiaoCodigo(), request.municipioCodigo());
        if (!ren) {
            RegrasFormularioRen.verificarNaoAplicavel(camposRen);
        }
        Map<String, Object> territorioSgifr =
                regrasTerritorioSgifr.verificar(formulario, processo, request.regiao(), request.cimAm());
        String tipoInformacao = tipoInformacao(processo, formulario, request.tipoInformacao());
        Map<String, Object> pedidos = CamposFormularioInicial.pedidos(request);
        CamposFormularioInicial.semPublicacao(formulario, pedidos);
        if (!validadorSubmissaoProcesso.pedeEntidadeInstrutora(processo)) {
            pedidos.remove(ValidadorSubmissaoProcesso.CAMPO_ENTIDADE_INSTRUTORA);
        }
        String serieDr = seriePedida(processo, request.serieDr());
        boolean limpaTipoAto = tipoAtoForaDaSerieNova(processo, request.tipoAtoId(), serieDr, contexto);
        if (limpaTipoAto) {
            pedidos.put(CamposFormularioInicial.TIPO_ATO_ID, null);
        }
        if (tipoInformacaoFixo(processo, formulario)) {
            pedidos.remove(CamposFormularioInicial.TIPO_INFORMACAO);
        }
        CamposFormularioInicial.verificarAlteracoes(processo, pedidos, objectMapper);
        List<CamposFormularioInicial.Alteracao> alteracoes =
                CamposFormularioInicial.alteracoesDesbloqueadas(processo, pedidos, objectMapper);
        TipoAto tipoAto = limpaTipoAto ? null : tipoAtoPedido(processo, request.tipoAtoId(), serieDr, contexto);
        verificarInformacaoBasePrevista(processo, request.informacaoBase());
        Map<String, Object> chavesRen = ren ? regrasFormularioRen.chavesAGravar(processo, camposRen) : Map.of();
        dinamicaAssociadaService.verificar(processo, request.dinamicaAssociadaId());
        dinamicaAssociadaService.verificarResposta(processo, request.simultaneoComPmot(), request.dinamicaAssociadaId());
        destinoProcessoService.verificarMudanca(processo, request.destinoPedido());

        // Antes do preenchimento: a regra do extrato lê o tipo de ato e a série gravados.
        if (processo.escolheSerieDr()) {
            processo.setSerieDr(serieDr);
        }
        processo.setTipoAto(tipoAto);
        processo.setSumario(request.sumario());
        processo.setDataAssinatura(request.dataAssinatura());
        processo.setNormaLegal(request.normaLegal());
        processo.setTipoInformacao(tipoInformacao);
        gravarPublicacao(processo, formulario, request.publicarSuplemento(), request.dataSuplemento());
        Processo preenchido = switch (formulario) {
            case INFORMACAO_GERAL, DISCUSSAO_PUBLICA, PUBLICACAO_ALTERACAO, SRUP, SUSPENSAO_MP_GOVERNO, PRORROGACAO_GOVERNO ->
                    preencherInformacaoGeral(processo, request, contexto);
            case RATIFICACAO -> preencherRatificacao(processo, request, contexto);
            case SUSPENSAO_CCDR -> preencherSuspensaoCcdr(processo, request, contexto);
            case SGIFR -> preencherSgifr(processo, request, contexto, territorioSgifr);
            case REN -> juntarDadosFormulario(preencherInformacaoGeral(processo, request, contexto), chavesRen);
        };
        gravarOpcaoGeoconform(preenchido, request.comGeoconform());
        gravarDinamicaAssociada(preenchido, request.dinamicaAssociadaId(), contexto);
        if (request.destinoPedido() != null) {
            preenchido.setDestino(request.destinoPedido());
        }
        preenchido.setCamposDesbloqueados(
                CamposFormularioInicial.semAsAlteradas(preenchido.getCamposDesbloqueados(), alteracoes));
        Processo guardado = salvar(preenchido);
        registarAlteracoesDesbloqueadas(guardado, contexto, alteracoes);
        return guardado;
    }

    /**
     * Só na correção (`EM_SUBMISSAO` depois da primeira submissão): acrescenta o campo, e as chaves do seu grupo,
     * aos desbloqueados, sem mudar o estado. O motivo vai só para a auditoria, nunca para o log (texto livre).
     */
    @Transactional
    public Processo desbloquearCampo(UUID processoId, DesbloquearCampoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAutor(processoId, contexto, request.versao());
        if (!emCorrecao(processo)) {
            throw new EstadoInvalidoException(MSG_DESBLOQUEIO_FORA_DA_CORRECAO);
        }
        List<String> grupo = CamposFormularioInicial.grupo(request.campo());
        if (grupo.isEmpty()) {
            log.warn("Desbloqueio recusado, campo fora do formulário inicial — processoId={}", processoId);
            throw new ValidacaoException(MSG_CAMPO_FORA_DO_FORMULARIO,
                    List.of(new ValidacaoException.CampoErro(CAMPO_CAMPO, MSG_CAMPO_FORA_DO_FORMULARIO)));
        }
        List<String> desbloqueados = new ArrayList<>(
                processo.getCamposDesbloqueados() == null ? List.of() : processo.getCamposDesbloqueados());
        grupo.stream().filter(chave -> !desbloqueados.contains(chave)).forEach(desbloqueados::add);
        processo.setCamposDesbloqueados(List.copyOf(desbloqueados));
        Processo guardado = salvar(processo);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_CAMPO, request.campo());
        dados.put(CAMPO_CAMPOS, grupo);
        dados.put(CAMPO_MOTIVO, request.motivo().strip());
        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_DESBLOQUEAR_CAMPO,
                contexto.utilizadorId(), guardado.getEntidade().getId(), null, dados);
        log.info("Campo desbloqueado — processoId={}, campos={}", guardado.getId(), grupo);
        return guardado;
    }

    private static boolean emCorrecao(Processo processo) {
        return processo.getEstado() == EstadoProcesso.EM_SUBMISSAO && processo.getDataSubmissao() != null;
    }

    /** Depois de `salvar`: um conflito de `versao` não deixa registos de uma gravação que não aconteceu. */
    private void registarAlteracoesDesbloqueadas(Processo processo, ContextoUtilizador contexto,
                                                 List<CamposFormularioInicial.Alteracao> alteracoes) {
        for (CamposFormularioInicial.Alteracao alteracao : alteracoes) {
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put(CAMPO_CAMPO, alteracao.campo());
            dados.put(CAMPO_VALOR_ANTIGO, alteracao.valorAntigo());
            dados.put(CAMPO_VALOR_NOVO, alteracao.valorNovo());
            auditoriaService.registar(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_ALTERAR_CAMPO,
                    contexto.utilizadorId(), processo.getEntidade().getId(), null, dados);
        }
        if (!alteracoes.isEmpty()) {
            log.info("Campos desbloqueados alterados e de novo bloqueados — processoId={}, campos={}", processo.getId(),
                    alteracoes.stream().map(CamposFormularioInicial.Alteracao::campo).toList());
        }
    }

    /** Já verificada; a auditoria só regista a mudança, com os ids e sem dados pessoais. */
    private void gravarDinamicaAssociada(Processo processo, UUID dinamicaAssociadaId, ContextoUtilizador contexto) {
        UUID anterior = idDinamicaAssociada(processo);
        if (Objects.equals(anterior, dinamicaAssociadaId)) {
            return;
        }
        processo.setDinamicaAssociada(dinamicaAssociadaId == null ? null : dinamicaRepository.getReferenceById(dinamicaAssociadaId));
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_DINAMICA_ASSOCIADA_ANTERIOR, anterior);
        dados.put(CAMPO_DINAMICA_ASSOCIADA, dinamicaAssociadaId);
        auditoriaService.registar(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_ASSOCIAR_DINAMICA,
                contexto.utilizadorId(), dados);
    }

    private static UUID idDinamicaAssociada(Processo processo) {
        return processo.getDinamicaAssociada() == null ? null : processo.getDinamicaAssociada().getId();
    }

    /** O SGIFR e as Dinâmicas com `tipo_informacao_fixo` impõem o valor; os outros formulários aceitam o pedido. */
    private String tipoInformacao(Processo processo, FormularioFixo formulario, String pedido) {
        return switch (formulario) {
            case SGIFR -> regrasRedesDefesaSgifr.tipoInformacao(pedido);
            case REN -> regrasFormularioRen.tipoInformacao(processo, pedido);
            default -> pedido;
        };
    }

    private boolean tipoInformacaoFixo(Processo processo, FormularioFixo formulario) {
        return formulario == FormularioFixo.SGIFR
                || (formulario == FormularioFixo.REN && regrasFormularioRen.temTipoInformacaoFixo(processo));
    }

    /** Acrescenta chaves a `dados_formulario` sem apagar as que a Informação Geral acabou de escrever. */
    private Processo juntarDadosFormulario(Processo processo, Map<String, Object> chaves) {
        Map<String, Object> dados = new LinkedHashMap<>();
        if (processo.getDadosFormulario() != null) {
            try {
                dados.putAll(objectMapper.readValue(processo.getDadosFormulario(),
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { }));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("dados_formulario ilegível.", e);
            }
        }
        dados.putAll(chaves);
        processo.setDadosFormulario(escreverDadosFormulario(dados));
        return processo;
    }

    /** As redes de defesa só existem no formulário `SGIFR`; num campo preenchido noutro formulário o pedido é recusado. */
    private void verificarSemRedesDefesa(List<String> redesDefesa) {
        if (redesDefesa != null && !redesDefesa.isEmpty()) {
            throw new ValidacaoException(MSG_CAMPO_NAO_APLICAVEL,
                    List.of(new ValidacaoException.CampoErro(RedesDefesaInvalidasException.CAMPO, MSG_CAMPO_NAO_APLICAVEL)));
        }
    }

    private Processo preencherSgifr(Processo processo, AtualizarDadosFormularioRequest request, ContextoUtilizador contexto,
                                    Map<String, Object> territorio) {
        Processo preenchido = preencherInformacaoGeral(processo, request, contexto, territorio);
        regrasRedesDefesaSgifr.gravar(preenchido, request.redesDefesa());
        return preenchido;
    }

    /** Depois dos `elementosAfetados` do mesmo pedido, de que a pergunta pode depender. Nulo deixa a resposta gravada. */
    private void gravarOpcaoGeoconform(Processo processo, Boolean comGeoconform) {
        if (comGeoconform == null) {
            return;
        }
        if (!validadorSubmissaoProcesso.exigeOpcaoGeoconform(processo)) {
            throw new ValidacaoException(MSG_SEM_PERGUNTA_GEOCONFORM,
                    List.of(new ValidacaoException.CampoErro(CAMPO_COM_GEOCONFORM, MSG_SEM_PERGUNTA_GEOCONFORM)));
        }
        processo.getDinamica().setComGeoconform(comGeoconform);
    }

    /** Corre antes de qualquer escrita no Processo: o pedido recusado não grava nenhum campo. O log não leva o conteúdo (texto livre). */
    private void verificarInformacaoBasePrevista(Processo processo, InformacaoBaseRequest informacaoBase) {
        if (informacaoBase == null || (informacaoBase.deliberacao() == null && informacaoBase.textoAto() == null)) {
            return;
        }
        Set<ElementoInformacaoBase> previstos = processoValidacaoService
                .elementosPrevistos(processo.getInstrumentoProcedimentoConfig());
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        if (informacaoBase.deliberacao() != null && !previstos.contains(ElementoInformacaoBase.DELIBERACAO)) {
            erros.add(new ValidacaoException.CampoErro(CAMPO_INFORMACAO_BASE_DELIBERACAO,
                    "Este procedimento não prevê Informação Base na Deliberação."));
        }
        if (informacaoBase.textoAto() != null && !previstos.contains(ElementoInformacaoBase.TEXTO_ATO)) {
            erros.add(new ValidacaoException.CampoErro(CAMPO_INFORMACAO_BASE_TEXTO_ATO,
                    "Este procedimento não prevê Informação Base no Texto do ato."));
        }
        if (!erros.isEmpty()) {
            log.warn("Informação Base recusada, elemento não previsto — processoId={}, campos={}", processo.getId(),
                    erros.stream().map(ValidacaoException.CampoErro::path).toList());
            throw new ValidacaoException(MSG_INFORMACAO_BASE_NAO_PREVISTA, erros);
        }
    }

    private Processo preencherInformacaoGeral(Processo processo, AtualizarDadosFormularioRequest request,
                                              ContextoUtilizador contexto) {
        return preencherInformacaoGeral(processo, request, contexto, Map.of());
    }

    /** A Informação Geral sem elementos afetados (o ecrã envia-os vazios), com a modalidade em `dados_formulario`. */
    private Processo preencherRatificacao(Processo processo, AtualizarDadosFormularioRequest request,
                                          ContextoUtilizador contexto) {
        Map<String, Object> chavesProprias = new LinkedHashMap<>();
        chavesProprias.put(CamposFormularioInicial.MODALIDADE_RATIFICACAO, request.modalidadeRatificacao());
        return preencherInformacaoGeral(processo, request, contexto, chavesProprias);
    }

    /** A Informação Geral sem elementos afetados, com a entidade emitente da lista das CCDR (recusada fora dela). */
    private Processo preencherSuspensaoCcdr(Processo processo, AtualizarDadosFormularioRequest request,
                                            ContextoUtilizador contexto) {
        validadorSubmissaoProcesso.verificarEntidadeEmitenteDaLista(processo, request.entidadeEmitente());
        return preencherInformacaoGeral(processo, request, contexto, Map.of());
    }

    private Processo preencherInformacaoGeral(Processo processo, AtualizarDadosFormularioRequest request,
                                              ContextoUtilizador contexto, Map<String, Object> chavesProprias) {
        List<String> fundamentacaoRjigt = validadorSubmissaoProcesso
                .verificarFundamentacaoRjigt(processo, request.fundamentacaoRjigt());
        validadorSubmissaoProcesso.verificarElementosAfetados(request.elementosAfetados());
        LocalizacaoRequest localizacao = validadorSubmissaoProcesso.verificarLocalizacao(processo, request.localizacao());
        List<MotivoAlteracaoRequest> motivosAlteracao = validadorSubmissaoProcesso.verificarMotivoAlteracao(processo,
                request.conformaHierarquiaSuperior(), request.motivosAlteracao());
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("informacaoBase", request.informacaoBase());
        dados.put("fundamentacaoRjigt", fundamentacaoRjigt);
        dados.put("introduzPlantaNova", request.introduzPlantaNova());
        dados.put("localizacao", localizacao);
        dados.put("conformaHierarquiaSuperior", request.conformaHierarquiaSuperior());
        dados.put("motivosAlteracao", motivosAlteracao);
        dados.put("simultaneoComPmot", request.simultaneoComPmot());
        dados.putAll(chavesProprias);
        processo.setDadosFormulario(escreverDadosFormulario(dados));
        processo.setContactoTramitacao(emBrancoComoNulo(request.contactoTramitacao()));
        processo.setEntidadeEmitente(emBrancoComoNulo(request.entidadeEmitente()));
        // Fora dos Programas o formulário não mostra o campo: um valor enviado não se guarda.
        processo.setEntidadeInstrutora(validadorSubmissaoProcesso.pedeEntidadeInstrutora(processo)
                ? emBrancoComoNulo(request.entidadeInstrutora()) : null);
        processo.setContactoPublicacao(emBrancoComoNulo(request.contactoPublicacao()));
        processo.setElementosAfetados(request.elementosAfetados() == null ? null
                : request.elementosAfetados().stream().distinct().toList());
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        return processo;
    }

    /**
     * `EM_SUBMISSAO` → `ENVIADO_DGT`. Os campos obrigatórios só são verificados aqui, nunca no rascunho.
     */
    @Transactional
    public Processo submeter(UUID processoId, SubmeterProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAutor(processoId, contexto, request.versao());
        verificarEstadoPermiteEdicao(processo);
        // Antes do formulário: depende da Dinâmica, não do Procedimento, e vale também nos que ainda não têm formulário.
        exigirInstrumentosAssociados(processo);
        FormularioFixo formulario = exigirFormulario(processo);
        autorizacaoService.verificarAutorizacaoPublicacao(contexto, processo);
        RegrasFormularioRen.CamposRen camposRen = new RegrasFormularioRen.CamposRen(request.motivoProcedimentoId(),
                request.diplomaCorrigido(), request.regiaoCodigo(), request.municipioCodigo());
        if (formulario != FormularioFixo.REN) {
            RegrasFormularioRen.verificarNaoAplicavel(camposRen);
        }
        String tipoInformacao = tipoInformacao(processo, formulario, request.tipoInformacao());
        Map<String, Object> pedidos = CamposFormularioInicial.pedidos(request);
        CamposFormularioInicial.semPublicacao(formulario, pedidos);
        if (tipoInformacaoFixo(processo, formulario)) {
            pedidos.remove(CamposFormularioInicial.TIPO_INFORMACAO);
        }
        CamposFormularioInicial.verificarAlteracoes(processo, pedidos, objectMapper);
        List<CamposFormularioInicial.Alteracao> alteracoes =
                CamposFormularioInicial.alteracoesDesbloqueadas(processo, pedidos, objectMapper);
        validadorSubmissaoProcesso.verificarConfirmacoes(formulario, request.confirmacoes());
        if (formulario == FormularioFixo.REN) {
            juntarDadosFormulario(processo, regrasFormularioRen.chavesAGravar(processo, camposRen));
        }
        // A Dinâmica associada pode ter terminado depois da gravação: o autor limpa ou troca a associação.
        dinamicaAssociadaService.verificar(processo, idDinamicaAssociada(processo));
        destinoProcessoService.verificarAdmitido(processo, processo.getDestino());

        processo.setSumario(request.sumario());
        processo.setDataAssinatura(request.dataAssinatura());
        processo.setNormaLegal(request.normaLegal());
        processo.setTipoInformacao(tipoInformacao);
        gravarPublicacao(processo, formulario, request.publicarSuplemento(), request.dataSuplemento());
        if (formulario == FormularioFixo.RATIFICACAO && request.modalidadeRatificacao() != null) {
            gravarChaveDadosFormulario(processo, CamposFormularioInicial.MODALIDADE_RATIFICACAO, request.modalidadeRatificacao());
        }

        Processo validado = switch (formulario) {
            case INFORMACAO_GERAL, DISCUSSAO_PUBLICA -> validarInformacaoGeral(processo, contexto);
            case PUBLICACAO_ALTERACAO -> validarPublicacaoAlteracao(processo, contexto);
            case SRUP -> validarCabecalhoComumSrup(processo, contexto);
            case RATIFICACAO -> validarRatificacao(processo, contexto);
            case SUSPENSAO_CCDR -> validarSuspensaoCcdr(processo, contexto);
            case SUSPENSAO_MP_GOVERNO, PRORROGACAO_GOVERNO -> validarGoverno(processo, contexto);
            case SGIFR -> validarSgifr(processo, contexto);
            case REN -> validarRen(processo, contexto);
        };
        validadorSubmissaoProcesso.verificarInformacaoBase(processo, formulario);
        List<Dinamica> dinamicasATerminar = validadorSubmissaoProcesso.verificarDeclaracaoTermino(processo,
                request.confirmacoes());
        boolean perguntaGeoconform = validadorSubmissaoProcesso.exigeOpcaoGeoconform(processo);
        Boolean comGeoconform = request.comGeoconform() == null && perguntaGeoconform
                ? processo.getDinamica().getComGeoconform()
                : request.comGeoconform();
        verificarOpcaoGeoconform(perguntaGeoconform, comGeoconform);
        // Antes da completude: sem GeoConform, a resposta deste pedido é que decide se se exigem Plantas e Metadados.
        if (perguntaGeoconform) {
            processo.getDinamica().setComGeoconform(comGeoconform);
        }
        processoValidacaoService.verificarRejeitadosCorrigidos(validado);
        completudeDocumentalService.verificar(validado);
        congelarIdentificadoresIncm(processo, contexto);

        EstadoProcesso estadoAntes = processo.getEstado();
        Instant dataSubmissao = Instant.now();
        processo.setEstado(EstadoProcesso.ENVIADO_DGT);
        processo.setDataSubmissao(dataSubmissao);
        // Cada correção pede os seus desbloqueios: os que ficaram por usar não passam à seguinte.
        processo.setCamposDesbloqueados(null);

        Processo guardado = salvar(processo);
        registarAlteracoesDesbloqueadas(guardado, contexto, alteracoes);
        idusProcessoService.registarSubmissao(guardado);
        Map<String, Object> dadosAuditoria = new LinkedHashMap<>();
        if (perguntaGeoconform) {
            dadosAuditoria.put(CAMPO_COM_GEOCONFORM, comGeoconform);
        }
        dadosAuditoria.put(CAMPO_CONFIRMACOES, catalogoFormularios.confirmacoesExigidas(formulario).stream().sorted().toList());
        if (!dinamicasATerminar.isEmpty()) {
            dadosAuditoria.put(CAMPO_DECLARACAO_TERMINO, true);
            dadosAuditoria.put(CAMPO_DINAMICAS_A_TERMINAR,
                    dinamicasATerminar.stream().map(dinamica -> dinamica.getId().toString()).toList());
        }
        registarAuditoriaTransicao(guardado, contexto, ACAO_SUBMETER, estadoAntes, EstadoProcesso.ENVIADO_DGT, dadosAuditoria);
        publicador.publishEvent(ProcessoTransitouEvento.de(guardado.getId(), estadoAntes, EstadoProcesso.ENVIADO_DGT));
        return guardado;
    }

    private void verificarOpcaoGeoconform(boolean perguntaGeoconform, Boolean comGeoconform) {
        if (perguntaGeoconform && comGeoconform == null) {
            throw new OpcaoGeoconformEmFaltaException(MSG_OPCAO_GEOCONFORM_EM_FALTA,
                    List.of(new ValidacaoException.CampoErro(CAMPO_COM_GEOCONFORM, MSG_OPCAO_GEOCONFORM_EM_FALTA)));
        }
        if (!perguntaGeoconform && comGeoconform != null) {
            throw new ValidacaoException(MSG_SEM_PERGUNTA_GEOCONFORM,
                    List.of(new ValidacaoException.CampoErro(CAMPO_COM_GEOCONFORM, MSG_SEM_PERGUNTA_GEOCONFORM)));
        }
    }

    private Processo validarInformacaoGeral(Processo processo, ContextoUtilizador contexto) {
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        exigirDadosIncmCompletos(processo, contexto);
        exigirElementosAfetados(processo);
        exigirEntidadesInstrutoraEEmitente(processo);
        exigirFundamentacaoRjigtValida(processo);
        exigirLocalizacaoEMotivoAlteracao(processo);
        return processo;
    }

    private void exigirLocalizacaoEMotivoAlteracao(Processo processo) {
        List<ValidacaoException.CampoErro> pendencias = new ArrayList<>(validadorSubmissaoProcesso.pendenciasLocalizacao(processo));
        pendencias.addAll(validadorSubmissaoProcesso.pendenciasMotivoAlteracao(processo));
        if (!pendencias.isEmpty()) {
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro()
                    : "O formulário tem campos em falta ou inválidos.", pendencias);
        }
    }

    /** O da Informação Geral sem os elementos afetados, que são só dos Planos; nada depende do tipo de Instrumento. */
    private Processo validarCabecalhoComumSrup(Processo processo, ContextoUtilizador contexto) {
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        exigirDadosIncmCompletos(processo, contexto);
        exigirEntidadesInstrutoraEEmitente(processo);
        exigirFundamentacaoRjigtValida(processo);
        exigirLocalizacaoEMotivoAlteracao(processo);
        return processo;
    }

    /** Os campos do formulário primeiro, numa só recusa `VALIDACAO`; depois os dados INCM (data de assinatura, sessão). */
    private Processo validarRatificacao(Processo processo, ContextoUtilizador contexto) {
        List<ValidacaoException.CampoErro> pendencias = validadorSubmissaoProcesso.pendenciasRatificacao(processo);
        if (!pendencias.isEmpty()) {
            log.warn("Submissão da Ratificação recusada, campos em falta — processoId={}, campos={}", processo.getId(),
                    pendencias.stream().map(ValidacaoException.CampoErro::path).toList());
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro()
                    : "O formulário tem campos em falta ou inválidos.", pendencias);
        }
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        exigirDadosIncmCompletos(processo, contexto);
        exigirFundamentacaoRjigtValida(processo);
        exigirLocalizacaoEMotivoAlteracao(processo);
        return processo;
    }

    /** Como a Ratificação: os campos do formulário numa só recusa `VALIDACAO`, depois o extrato e os dados INCM. */
    private Processo validarSuspensaoCcdr(Processo processo, ContextoUtilizador contexto) {
        List<ValidacaoException.CampoErro> pendencias = validadorSubmissaoProcesso.pendenciasSuspensaoCcdr(processo);
        if (!pendencias.isEmpty()) {
            log.warn("Submissão da Suspensão da CCDR recusada, campos em falta — processoId={}, campos={}", processo.getId(),
                    pendencias.stream().map(ValidacaoException.CampoErro::path).toList());
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro()
                    : "O formulário tem campos em falta ou inválidos.", pendencias);
        }
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        exigirDadosIncmCompletos(processo, contexto);
        exigirFundamentacaoRjigtValida(processo);
        exigirLocalizacaoEMotivoAlteracao(processo);
        return processo;
    }

    /** Como a Suspensão da CCDR: os campos do formulário numa só recusa `VALIDACAO`, depois o extrato e os dados INCM. */
    private Processo validarGoverno(Processo processo, ContextoUtilizador contexto) {
        List<ValidacaoException.CampoErro> pendencias = validadorSubmissaoProcesso.pendenciasGoverno(processo);
        if (!pendencias.isEmpty()) {
            log.warn("Submissão do formulário do Governo recusada, campos em falta — processoId={}, campos={}",
                    processo.getId(), pendencias.stream().map(ValidacaoException.CampoErro::path).toList());
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro()
                    : "O formulário tem campos em falta ou inválidos.", pendencias);
        }
        validadorSubmissaoProcesso.verificarRegraDoExtrato(processo, contexto);
        exigirDadosIncmCompletos(processo, contexto);
        exigirFundamentacaoRjigtValida(processo);
        exigirLocalizacaoEMotivoAlteracao(processo);
        return processo;
    }

    /**
     * A Prorrogação do Governo não pergunta pela «Publicação»: o pedido não conta e grava-se Diário, porque a INCM
     * exige o valor na 1.ª série (TR-173).
     */
    private static void gravarPublicacao(Processo processo, FormularioFixo formulario, Boolean publicarSuplemento,
                                         LocalDate dataSuplemento) {
        if (formulario == FormularioFixo.PRORROGACAO_GOVERNO) {
            processo.setPublicarSuplemento(false);
            processo.setDataSuplemento(null);
            return;
        }
        processo.setPublicarSuplemento(publicarSuplemento);
        processo.setDataSuplemento(dataSuplemento);
    }

    private Processo validarSgifr(Processo processo, ContextoUtilizador contexto) {
        regrasRedesDefesaSgifr.verificarNaSubmissao(processo);
        return validarInformacaoGeral(processo, contexto);
    }

    private Processo validarRen(Processo processo, ContextoUtilizador contexto) {
        validarInformacaoGeral(processo, contexto);
        regrasFormularioRen.verificarNaSubmissao(processo);
        return processo;
    }

    private Processo validarPublicacaoAlteracao(Processo processo, ContextoUtilizador contexto) {
        validarInformacaoGeral(processo, contexto);
        validadorSubmissaoProcesso.verificarPublicacaoAlteracao(processo);
        return processo;
    }

    private FormularioFixo exigirFormulario(Processo processo) {
        return catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento())
                .orElseThrow(() -> new EstadoInvalidoException(MSG_SEM_FORMULARIO));
    }

    private boolean temFormulario(Processo processo) {
        return catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento()).isPresent();
    }

    /** Só antes da captura pela DGT; a corrida com `capturar` é resolvida por optimistic locking. */
    @Transactional
    public Processo resgatar(UUID processoId, VersaoProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAutor(processoId, contexto, request.versao());
        if (processo.getEstado() != EstadoProcesso.ENVIADO_DGT) {
            throw new EstadoInvalidoException("Só é possível resgatar uma tentativa em ENVIADO_DGT.");
        }

        transitar(processo, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_SUBMISSAO);
        Processo guardado = salvar(processo);
        registarAuditoriaTransicao(guardado, contexto, ACAO_RESGATAR, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_SUBMISSAO, null);
        publicador.publishEvent(ProcessoTransitouEvento.de(guardado.getId(), EstadoProcesso.ENVIADO_DGT,
                EstadoProcesso.EM_SUBMISSAO));
        return guardado;
    }

    /**
     * Reabre o mesmo processo (id, número e ocorrência) para correção; a conclusão é o `submeter` de sempre.
     * O bloqueio dos ficheiros vem depois de `salvar`: é aí que um conflito de `versao` sai como
     * `VERSAO_DESATUALIZADA`; antes, o flush do update em lote levá-lo-ia por fora dessa tradução.
     */
    @Transactional
    public Processo corrigir(UUID processoId, VersaoProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAutor(processoId, contexto, request.versao());
        EstadoProcesso estadoAntes = processo.getEstado();
        if (estadoAntes != EstadoProcesso.REJEITADO_DGT && estadoAntes != EstadoProcesso.ANULADO_INCM) {
            throw new EstadoInvalidoException("Este processo não pode ser corrigido neste estado.");
        }

        transitar(processo, estadoAntes, EstadoProcesso.EM_SUBMISSAO);
        Processo guardado = salvar(processo);
        long documentosBloqueados = bloqueioDocumentosCorrecao.bloquearParaCorrecao(guardado, estadoAntes);
        registarAuditoriaTransicao(guardado, contexto, ACAO_CORRIGIR, estadoAntes, EstadoProcesso.EM_SUBMISSAO,
                Map.of(CAMPO_DOCUMENTOS_BLOQUEADOS, documentosBloqueados));
        publicador.publishEvent(ProcessoTransitouEvento.de(guardado.getId(), estadoAntes, EstadoProcesso.EM_SUBMISSAO));
        return guardado;
    }

    /** `entidadeId` nulo no audit: ação da DGT, não praticada "sob" a entidade (ver {@code Auditoria}). */
    @Transactional
    public Processo capturar(UUID processoId, VersaoProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAtorDgt(processoId, contexto, request.versao());
        transitar(processo, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_VALIDACAO_DGT);
        Processo guardado = salvar(processo);
        processoValidacaoService.criarItensInformacaoBase(guardado);
        processoValidacaoService.reabrirRejeitados(guardado);
        registarAuditoriaTransicaoDgt(guardado, contexto, ACAO_CAPTURAR, EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_VALIDACAO_DGT, null);
        publicador.publishEvent(ProcessoTransitouEvento.de(guardado.getId(), EstadoProcesso.ENVIADO_DGT,
                EstadoProcesso.EM_VALIDACAO_DGT));
        return guardado;
    }

    @Transactional
    public Processo validar(UUID processoId, VersaoProcessoRequest request) {
        return validar(processoId, ValidarProcessoRequest.soVersao(request.versao()));
    }

    /** Os campos opcionais do Resumo Validados gravam-se na mesma transação da passagem a `VALIDADO_DGT`. */
    @Transactional
    public Processo validar(UUID processoId, ValidarProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAtorDgt(processoId, contexto, request.versao());
        verificarEstado(processo, EstadoProcesso.EM_VALIDACAO_DGT);
        if (!processoValidacaoService.todosRevistos(processo)) {
            throw new ItensPorReverException();
        }
        processo.setAlteracaoPeot(processoValidacaoService.alteracaoPeotEscolhida(request.alteracaoPeotId()));
        processo.setNotasIncm(textoOuNulo(request.notasIncm()));
        processo.setNotasDgt(textoOuNulo(request.notasDgt()));
        processo.setRegUsoSolo(request.regUsoSolo());
        // Uma devolução da PCM numa passagem anterior não conta nesta; o histórico fica na auditoria.
        processo.setPcmDevolvidoEm(null);
        transitar(processo, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT);
        retencaoDocumentosService.reterDocumentosSemRetencao(processo, MomentoRetencao.ENTRADA_VALIDADO_DGT);
        Processo guardado = salvar(processo);
        registarAuditoriaTransicaoDgt(guardado, contexto, ACAO_VALIDAR, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT, null);
        publicador.publishEvent(ProcessoTransitouEvento.de(guardado.getId(), EstadoProcesso.EM_VALIDACAO_DGT,
                EstadoProcesso.VALIDADO_DGT));
        return guardado;
    }

    /**
     * «Confirmar Rejeição»: a recusa e os seus itens nascem do rascunho da validação, que é apagado. A entidade
     * corrige na mesma tentativa. As notas gerais ficam fora da auditoria.
     */
    @Transactional
    public Processo rejeitar(UUID processoId, RejeitarProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoAtorDgt(processoId, contexto, request.versao());
        verificarTransicao(processo, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.REJEITADO_DGT);
        List<ProcessoValidacaoRejeicao> rascunho = processoValidacaoService.rascunhoParaRecusa(processo);

        processo.setEstado(EstadoProcesso.REJEITADO_DGT);
        Processo guardado = salvar(processo);
        ProcessoRejeicao recusa = rejeicaoProcessoService.registarRecusaDgt(guardado,
                utilizadorRepository.getReferenceById(contexto.utilizadorId()), textoOuNulo(request.notasGerais()),
                rascunho);
        processoValidacaoService.apagarRascunho(guardado);
        registarAuditoriaTransicaoDgt(guardado, contexto, ACAO_REJEITAR, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.REJEITADO_DGT,
                Map.of("sequencia", recusa.getSequencia(), "itens", rascunho.size()));
        publicador.publishEvent(ProcessoTransitouEvento.rejeicaoDgt(guardado.getId(), recusa.getId()));
        return guardado;
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /**
     * Manual, de qualquer estado de {@link EstadoProcesso#ANULAVEIS}; depois do envio à INCM já não se anula.
     * Não mexe na Dinâmica, que continua em curso. O motivo em texto livre vai para `dados_alteracao`.
     */
    @Transactional
    public Processo anular(UUID processoId, AnularProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoMaster(processoId, contexto, request.versao());
        EstadoProcesso estadoAntes = processo.getEstado();
        if (!EstadoProcesso.ANULAVEIS.contains(estadoAntes)) {
            throw new EstadoInvalidoException("Transição não permitida a partir do estado atual (" + estadoAntes + ").");
        }

        transitar(processo, estadoAntes, EstadoProcesso.ANULADO);
        Processo guardado = salvar(processo);
        registarAuditoriaTransicaoDgt(guardado, contexto, ACAO_ANULAR, estadoAntes, EstadoProcesso.ANULADO,
                Map.of(CAMPO_MOTIVO, request.motivo()));
        publicador.publishEvent(ProcessoTransitouEvento.anulacaoDgt(guardado.getId(), estadoAntes, request.motivo()));
        return guardado;
    }

    /**
     * `MASTER`, sem motivo nem aprovação, só nos estados em que o owner age. A entidade do processo não muda:
     * o novo autor tem de lhe pertencer e passa a ser o único que edita e submete. Com `pedidoApoioId`, o pedido
     * de apoio fica resolvido na mesma transação; um erro em qualquer passo deixa tudo como estava.
     */
    @Transactional
    public Processo reatribuirAutor(UUID processoId, ReatribuirAutorRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = carregarComoMaster(processoId, contexto, request.versao());
        if (!ESTADOS_TRANSFERIVEIS.contains(processo.getEstado())) {
            throw new EstadoInvalidoException(MSG_ESTADO_NAO_TRANSFERIVEL);
        }

        Utilizador autorAnterior = processo.getCriadoPor();
        Utilizador novoAutor = utilizadorRepository.findById(request.novoUtilizadorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_UTILIZADOR_NAO_ENCONTRADO));
        validarNovoAutor(processo, autorAnterior, novoAutor);
        UUID pedidoApoioId = request.pedidoApoioId();
        if (pedidoApoioId != null) {
            pedidoApoioService.resolverPedidoApoio(processo, pedidoApoioId);
        }

        processo.setCriadoPor(novoAutor);
        Processo guardado = salvar(processo);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("autorAnteriorId", autorAnterior.getId());
        dados.put("novoAutorId", novoAutor.getId());
        if (pedidoApoioId != null) {
            dados.put(CAMPO_PEDIDO_APOIO_ID, pedidoApoioId);
        }
        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_REATRIBUIR_AUTOR,
                contexto.utilizadorId(), null, null, dados);
        if (pedidoApoioId != null) {
            log.info("Autoria do processo transferida — processoId={}, pedidoApoioId={}", guardado.getId(), pedidoApoioId);
        } else {
            log.info("Autoria do processo transferida — processoId={}", guardado.getId());
        }
        return guardado;
    }

    /**
     * O novo autor tem de pertencer à entidade do processo pelos vínculos da adesão INCM
     * (`utilizador_entidade`), nunca pela `auditoria`: um autor de outra entidade, da DGT ou a PCM
     * falharia a Camada 2 no próprio Processo, que ficaria órfão.
     */
    private void validarNovoAutor(Processo processo, Utilizador autorAnterior, Utilizador novoAutor) {
        if (novoAutor.getId().equals(autorAnterior.getId())) {
            throw new EstadoInvalidoException("O utilizador indicado já é o autor do Processo.");
        }
        Long entidadeId = processo.getEntidade().getId();
        if (!utilizadorEntidadeRepository.existsByIdUtilizadorIdAndIdEntidadeId(novoAutor.getId(), entidadeId)) {
            String erro = "O utilizador indicado não pertence à entidade do Instrumento.";
            throw new ValidacaoException(erro, List.of(new ValidacaoException.CampoErro(CAMPO_NOVO_UTILIZADOR_ID, erro)));
        }
    }

    /**
     * Só 1.ª série; as restantes vão direto à INCM. Não muda `processo.estado` (mantém `VALIDADO_DGT`). As regras
     * correm por esta ordem: ator, `versao`, série, estado, já atribuído, documentos e, por fim, o destinatário, que
     * tem de ter vínculo a uma entidade PCM ativa. A única condição de âmbito é a série efetiva do processo.
     */
    @Transactional
    public Processo enviarPcm(UUID processoId, EnviarPcmRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPossibilidadeDeEnviarPcm(contexto, processo);
        verificarVersao(processo, request.versao());
        destinoProcessoService.verificarEnvioIncm(processo);
        verificarProcessoProntoParaPcm(processo);

        UUID utilizadorId = request.utilizadorId();
        if (!utilizadorEntidadeRepository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(
                utilizadorId, TipoEntidade.PCM)) {
            throw new PcmUtilizadorInvalidoException(CAMPO_UTILIZADOR_ID);
        }
        Utilizador utilizadorPcm = utilizadorRepository.findById(utilizadorId)
                .orElseThrow(() -> new PcmUtilizadorInvalidoException(CAMPO_UTILIZADOR_ID));

        processo.setPcmAtribuidoA(utilizadorPcm);
        Processo guardado = salvar(processo);
        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_ENVIAR_PCM,
                contexto.utilizadorId(), null, null, Map.of("pcmAtribuidoAId", utilizadorPcm.getId()));
        log.info("Processo enviado à PCM — processoId={}", guardado.getId());
        publicador.publishEvent(new ProcessoEnviadoPcmEvento(guardado.getId(), utilizadorPcm.getId()));
        return guardado;
    }

    /**
     * Regras de «Enviar PCM» que não dependem do destinatário. Todos os documentos contam, não só os obrigatórios;
     * `pcm_atribuido_a` preenchido quer dizer que o processo está com a PCM e ainda não foi devolvido.
     */
    private void verificarProcessoProntoParaPcm(Processo processo) {
        if (!primeiraSerie(processo)) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.SERIE_NAO_PRIMEIRA, MSG_PCM_SERIE_OU_ESTADO);
        }
        if (processo.getEstado() != EstadoProcesso.VALIDADO_DGT) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.ESTADO_INVALIDO, MSG_PCM_SERIE_OU_ESTADO);
        }
        if (processo.getPcmAtribuidoA() != null) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.JA_ATRIBUIDO, "O processo já está com a PCM.");
        }
        if (!processoValidacaoService.todosDocumentosValidados(processo)) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.DOCUMENTOS_POR_VALIDAR,
                    "Existem documentos por validar.");
        }
    }

    /**
     * A PCM devolve à DGT o processo que lhe foi atribuído: o estado mantém-se `VALIDADO_DGT`, `pcm_atribuido_a`
     * limpa-se e `pcm_devolvido_em` fica com a hora da devolução (TR-114). As regras correm por esta ordem: ator,
     * visibilidade, Camada 3, `versao`, estado, atribuição e documentos obrigatórios. As observações vão só para a
     * auditoria.
     */
    @Transactional
    public Processo devolverDgt(UUID processoId, DevolverDgtRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPossibilidadeDeDevolverDgt(contexto, processo);
        verificarVersao(processo, request.versao());
        if (processo.getEstado() != EstadoProcesso.VALIDADO_DGT) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.ESTADO_INVALIDO,
                    "Só processos validados pela DGT podem ser devolvidos.");
        }
        if (processo.getPcmAtribuidoA() == null || !processo.getPcmAtribuidoA().getId().equals(contexto.utilizadorId())) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.NAO_ATRIBUIDO, "O processo não está atribuído à PCM.");
        }
        List<ValidacaoException.CampoErro> emFalta = completudeDocumentalService.camposEmFalta(processo);
        if (!emFalta.isEmpty()) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.DOCUMENTOS_OBRIGATORIOS_EM_FALTA,
                    "Falta anexar documentos obrigatórios.", emFalta);
        }

        processo.setPcmAtribuidoA(null);
        processo.setPcmDevolvidoEm(Instant.now());
        Processo guardado = salvar(processo);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("pcmAtribuidoAId", contexto.utilizadorId());
        dados.put("observacoes", textoOuNulo(request.observacoes()));
        auditoriaService.registar(TIPO_OCORRENCIA, guardado.getId().toString(), ACAO_DEVOLVER_DGT,
                contexto.utilizadorId(), null, null, dados);
        log.info("Processo devolvido à DGT pela PCM — processoId={}, utilizadorId={}", guardado.getId(), contexto.utilizadorId());
        return guardado;
    }

    private static boolean primeiraSerie(Processo processo) {
        return SERIE_DR_PRIMEIRA.equals(processo.serieDrEfetiva());
    }

    /**
     * `VALIDADO_DGT` → `EM_VALIDACAO_INCM` ou `ANULADO_INCM`, só pela `DGT`, em qualquer série. Aqui ficam só
     * a autorização, a `versao`, o estado de origem, o bloqueio enquanto o processo está com a PCM (o passo da PCM é
     * facultativo, TR-114) e os documentos por validar; a exposição dos documentos, o `SubmitNewAct`, as transições
     * e a auditoria são do {@link EnvioIncmService}, que corre nesta transação.
     */
    @Transactional
    public Processo enviarIncm(UUID processoId, VersaoProcessoRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPossibilidadeDeEnviarIncm(contexto, processo);
        verificarVersao(processo, request.versao());
        destinoProcessoService.verificarEnvioIncm(processo);
        verificarEstado(processo, EstadoProcesso.VALIDADO_DGT);
        if (processo.getPcmAtribuidoA() != null) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.JA_ATRIBUIDO, "O processo está com a PCM.");
        }
        // O documento que a PCM acrescenta fica por validar e iria no SubmitNewAct (FR-059).
        if (!processoValidacaoService.todosDocumentosValidados(processo)) {
            throw new PcmEstadoInvalidoException(PcmEstadoInvalidoException.DOCUMENTOS_POR_VALIDAR,
                    "Existem documentos por validar.");
        }
        if (aguardaPcmAntesDaPublicacao(processo)) {
            throw new EstadoInvalidoException(MSG_RATIFICACAO_SEM_PCM);
        }
        return envioIncmService.enviar(processoId, contexto.utilizadorId());
    }

    /** Autor em estado editável, num Procedimento com formulário: as mesmas condições de {@code atualizarDadosFormulario}. */
    public boolean editavel(Processo processo, ContextoUtilizador contexto) {
        return EstadoProcesso.EDITAVEIS.contains(processo.getEstado())
                && processo.getCriadoPor().getId().equals(contexto.utilizadorId())
                && temFormulario(processo);
    }

    /** A PCM no `VALIDADO_DGT` que lhe foi atribuído, com a linha `PCM` e um Procedimento com formulário. */
    public boolean editavelPelaPcm(Processo processo, ContextoUtilizador contexto) {
        return autorizacaoService.pcmPodeCorrigir(contexto, processo) && temFormulario(processo);
    }

    /**
     * Segmentos de `PATCH /processos/{id}/<acao>` que o chamador pode executar agora, calculados com as
     * mesmas origens de estado e verificações de autorização dos endpoints acima: uma ação listada não é
     * recusada por estado nem por permissão se for chamada logo a seguir com a mesma `versao`.
     */
    public List<String> acoesDisponiveis(Processo processo, ContextoUtilizador contexto) {
        List<String> acoes = new ArrayList<>();
        switch (processo.getEstado()) {
            case EM_SUBMISSAO -> {
                if (temFormulario(processo) && autorizacaoService.podeAgirComoAutor(contexto, processo)
                        && autorizacaoService.podePublicar(contexto, processo)) {
                    acoes.add(SEGMENTO_SUBMETER);
                }
                if (emCorrecao(processo) && autorizacaoService.podeAgirComoAutor(contexto, processo)) {
                    acoes.add(SEGMENTO_DESBLOQUEAR_CAMPO);
                }
            }
            case REJEITADO_DGT, ANULADO_INCM -> {
                if (autorizacaoService.podeAgirComoAutor(contexto, processo)) {
                    acoes.add(SEGMENTO_CORRIGIR);
                }
            }
            case ENVIADO_DGT -> {
                if (autorizacaoService.podeAgirComoAutor(contexto, processo)) {
                    acoes.add(SEGMENTO_RESGATAR);
                }
                if (autorizacaoService.podeAgirComoDgt(contexto, processo)) {
                    acoes.add(SEGMENTO_CAPTURAR);
                }
            }
            case EM_VALIDACAO_DGT -> {
                if (autorizacaoService.podeAgirComoDgt(contexto, processo)) {
                    acoes.add(SEGMENTO_VALIDAR);
                    acoes.add(SEGMENTO_REJEITAR);
                }
            }
            // Só para depósito não vai à INCM nem à PCM: fica em VALIDADO_DGT sem ação de envio.
            case VALIDADO_DGT -> {
                if (!DestinoProcessoService.soDeposito(processo) && processo.getPcmAtribuidoA() == null
                        && !aguardaPcmAntesDaPublicacao(processo) && autorizacaoService.podeEnviarIncm(contexto, processo)
                        && processoValidacaoService.todosDocumentosValidados(processo)) {
                    acoes.add(SEGMENTO_ENVIAR_INCM);
                }
                if (!DestinoProcessoService.soDeposito(processo) && podeEnviarPcm(processo, contexto)) {
                    acoes.add(SEGMENTO_ENVIAR_PCM);
                }
                if (autorizacaoService.podeDevolverDgt(contexto, processo)) {
                    acoes.add(SEGMENTO_DEVOLVER_DGT);
                }
            }
            default -> { }
        }
        boolean master = autorizacaoService.podeAgirComoMaster(contexto, processo);
        if (master && EstadoProcesso.ANULAVEIS.contains(processo.getEstado())) {
            acoes.add(SEGMENTO_ANULAR);
        }
        if (master && ESTADOS_TRANSFERIVEIS.contains(processo.getEstado())) {
            acoes.add(SEGMENTO_REATRIBUIR_AUTOR);
        }
        return acoes;
    }

    /**
     * Na Ratificação a DGT envia o processo à PCM antes da publicação e só faz o envio final à INCM depois de a PCM o
     * devolver nesta passagem por `VALIDADO_DGT` (EF v1.5, «Procedimentos publicados na 1.ª série»).
     */
    private boolean aguardaPcmAntesDaPublicacao(Processo processo) {
        InstrumentoProcedimentoConfig config = processo.getInstrumentoProcedimentoConfig();
        return processo.getPcmDevolvidoEm() == null && config != null && config.getTipoProcedimento() != null
                && catalogoFormularios.formularioDe(config.getTipoProcedimento()).orElse(null) == FormularioFixo.RATIFICACAO;
    }

    /** As regras de {@link #enviarPcm} que não dependem do destinatário escolhido. */
    private boolean podeEnviarPcm(Processo processo, ContextoUtilizador contexto) {
        return primeiraSerie(processo) && processo.getPcmAtribuidoA() == null
                && autorizacaoService.podeEnviarPcm(contexto, processo)
                && processoValidacaoService.todosDocumentosValidados(processo);
    }

    /** Visibilidade (404), tipo da entidade em `permissao_acao` (403) e autoria (403), comuns às ações do autor. */
    private Processo carregarComoAutor(UUID processoId, ContextoUtilizador contexto, long versao) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPermissaoDeAgir(contexto, processo);
        autorizacaoService.verificarAutoria(contexto, processo);
        verificarVersao(processo, versao);
        return processo;
    }

    /** As camadas de {@link #carregarComoAutor}, com a exceção da PCM a quem o processo está atribuído. */
    private Processo carregarParaEditar(UUID processoId, ContextoUtilizador contexto, long versao) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPermissaoDeEditar(contexto, processo);
        verificarVersao(processo, versao);
        return processo;
    }

    private Processo carregarComoAtorDgt(UUID processoId, ContextoUtilizador contexto, long versao) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPossibilidadeDeAgirComoDgt(contexto, processo);
        verificarVersao(processo, versao);
        return processo;
    }

    private Processo carregarComoMaster(UUID processoId, ContextoUtilizador contexto, long versao) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_PROCESSO_NAO_ENCONTRADO));
        autorizacaoService.verificarPossibilidadeDeAgirComoMaster(contexto, processo);
        verificarVersao(processo, versao);
        return processo;
    }

    /**
     * Antes de qualquer regra de estado: uma `versao` antiga tem de dar `VERSAO_DESATUALIZADA`, não
     * `ESTADO_INVALIDO`, para o cliente saber que deve recarregar. Depois da autorização, para não revelar
     * a versão a quem não pode agir.
     */
    private void verificarVersao(Processo processo, long versao) {
        if (processo.getVersao() != versao) {
            throw new VersaoDesatualizadaException();
        }
    }

    /**
     * Máquina de estados de {@link EstadoProcesso}, validada num único ponto. Cada ação parte de uma só
     * `origem`, mesmo quando o enum admite outras para o mesmo destino (`corrigir` e `anular` escolhem
     * antes a origem entre as que admitem). Fora da origem → 409.
     */
    private void verificarTransicao(Processo processo, EstadoProcesso origem, EstadoProcesso destino) {
        verificarEstado(processo, origem);
        if (!origem.podeTransitarPara(destino)) {
            throw new IllegalStateException("Transição fora da máquina de estados: " + origem + " -> " + destino);
        }
    }

    private void transitar(Processo processo, EstadoProcesso origem, EstadoProcesso destino) {
        verificarTransicao(processo, origem, destino);
        processo.setEstado(destino);
    }

    private void verificarEstado(Processo processo, EstadoProcesso estadoEsperado) {
        if (processo.getEstado() != estadoEsperado) {
            throw new EstadoInvalidoException("Transição não permitida a partir do estado atual (" + processo.getEstado() + ").");
        }
    }

    /**
     * Traduz a exceção de optimistic locking (`@Version`) para o 409 do domínio. `saveAndFlush` força a
     * verificação aqui, não só no commit.
     */
    private Processo salvar(Processo processo) {
        try {
            return processoRepository.saveAndFlush(processo);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new VersaoDesatualizadaException();
        }
    }

    private void verificarEstadoPermiteEdicao(Processo processo) {
        if (!EstadoProcesso.EDITAVEIS.contains(processo.getEstado())) {
            throw new EstadoInvalidoException("Só é possível editar ou submeter em EM_SUBMISSAO.");
        }
    }

    private void verificarEstadoPermiteEdicao(Processo processo, ContextoUtilizador contexto) {
        if (!autorizacaoService.correcaoPelaPcm(contexto, processo)) {
            verificarEstadoPermiteEdicao(processo);
        } else if (processo.getEstado() != EstadoProcesso.VALIDADO_DGT) {
            throw new EstadoInvalidoException(MSG_PCM_SO_EM_VALIDADO_DGT);
        }
    }

    private void exigirDadosIncmCompletos(Processo processo, ContextoUtilizador contexto) {
        List<ValidacaoException.CampoErro> pendencias = validadorSubmissaoProcesso.pendencias(processo, contexto);
        if (!pendencias.isEmpty()) {
            throw new DadosIncmEmFaltaException("Dados insuficientes para submissão à INCM.", pendencias);
        }
    }

    private static String emBrancoComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private void exigirInstrumentosAssociados(Processo processo) {
        validadorSubmissaoProcesso.pendenciaInstrumentosAssociados(processo).ifPresent(pendencia -> {
            throw new ValidacaoException(pendencia.erro(), List.of(pendencia));
        });
    }

    private void exigirElementosAfetados(Processo processo) {
        validadorSubmissaoProcesso.pendenciaElementosAfetados(processo).ifPresent(pendencia -> {
            throw new ElementosAfetadosEmFaltaException(pendencia.erro(), List.of(pendencia));
        });
    }

    private void exigirEntidadesInstrutoraEEmitente(Processo processo) {
        List<ValidacaoException.CampoErro> pendencias = new ArrayList<>();
        validadorSubmissaoProcesso.pendenciaEntidadeInstrutora(processo).ifPresent(pendencias::add);
        validadorSubmissaoProcesso.pendenciaEntidadeEmitente(processo).ifPresent(pendencias::add);
        if (!pendencias.isEmpty()) {
            throw new ValidacaoException(pendencias.size() == 1 ? pendencias.get(0).erro()
                    : "O formulário tem campos em falta ou inválidos.", pendencias);
        }
    }

    private void exigirFundamentacaoRjigtValida(Processo processo) {
        validadorSubmissaoProcesso.pendenciaFundamentacaoRjigt(processo).ifPresent(pendencia -> {
            throw new ValidacaoException(pendencia.erro(), List.of(pendencia));
        });
    }

    /**
     * Congela `incm_enrollment_id`/`dr_parte_id` do token desta submissão, regravados a cada
     * (re)submissão. `dr_parte_id` só na 2.ª série (`SEGUNDA_H`/`SEGUNDA_C`).
     */
    private void congelarIdentificadoresIncm(Processo processo, ContextoUtilizador contexto) {
        processo.setIncmEnrollmentId(contexto.entidadeIncmEnrollmentId());
        String serieDr = processo.serieDrEfetiva();
        if (SERIE_DR_SEGUNDA_H.equals(serieDr) || SERIE_DR_SEGUNDA_C.equals(serieDr)) {
            processo.setDrParteId(contexto.entidadeDrParteId());
        }
    }

    private void registarAuditoriaTransicao(Processo processo, ContextoUtilizador contexto, String acao,
                                             EstadoProcesso estadoAntes, EstadoProcesso estadoDepois, Object dados) {
        auditoriaService.registarTransicao(TIPO_OCORRENCIA, processo.getId().toString(), acao,
                TransicaoEstado.de(estadoAntes, estadoDepois), contexto.utilizadorId(), processo.getEntidade().getId(), dados);
    }

    /** `entidadeId` nulo — ação da DGT, nunca "praticada sob" a entidade do processo (ver {@code Auditoria}). */
    private void registarAuditoriaTransicaoDgt(Processo processo, ContextoUtilizador contexto, String acao,
                                                EstadoProcesso estadoAntes, EstadoProcesso estadoDepois, Object dados) {
        auditoriaService.registarTransicao(TIPO_OCORRENCIA, processo.getId().toString(), acao,
                TransicaoEstado.de(estadoAntes, estadoDepois), contexto.utilizadorId(), null, dados);
    }

    /** Substitui uma chave de `dados_formulario` e mantém as outras. */
    private void gravarChaveDadosFormulario(Processo processo, String chave, Object valor) {
        Map<String, Object> dados = new LinkedHashMap<>();
        String atuais = processo.getDadosFormulario();
        if (atuais != null && !atuais.isBlank()) {
            try {
                dados.putAll(objectMapper.readValue(atuais, new TypeReference<Map<String, Object>>() { }));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("dados_formulario ilegível.", e);
            }
        }
        dados.put(chave, valor);
        processo.setDadosFormulario(escreverDadosFormulario(dados));
    }

    private String escreverDadosFormulario(Map<String, Object> chaves) {
        Map<String, Object> dados = new LinkedHashMap<>();
        chaves.forEach((chave, valor) -> {
            if (valor != null) {
                dados.put(chave, valor);
            }
        });
        if (dados.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar dados_formulario.", e);
        }
    }
}
