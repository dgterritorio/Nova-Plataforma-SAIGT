package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.GravarValidacaoItensRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseResponse;
import com.conkord.saigt.dto.instrumento.ItemValidacaoRequest;
import com.conkord.saigt.dto.instrumento.ItemValidacaoResponse;
import com.conkord.saigt.dto.instrumento.RejeicaoEmValidacaoResponse;
import com.conkord.saigt.dto.instrumento.RejeicaoItemResponse;
import com.conkord.saigt.dto.instrumento.TipoItemValidacao;
import com.conkord.saigt.dto.instrumento.ValidacaoItensResponse;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.VersaoDesatualizadaException;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.SituacaoPcm;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.listavalores.ListaValoresService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registo da decisão da DGT sobre cada documento e cada elemento da Informação Base de um Processo. */
@Service
public class ProcessoValidacaoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessoValidacaoService.class);
    private static final String ATOR_DGT = "DGT";
    private static final String CAMPO_ITENS = "itens";
    private static final String MSG_SEM_ITENS_REJEITADOS = "Indique pelo menos um item rejeitado.";
    private static final Set<EstadoValidacao> ESTADOS_DOCUMENTO_RESOLVIDOS =
            Set.copyOf(EnumSet.of(EstadoValidacao.VALIDADO, EstadoValidacao.SUBSTITUIDO));
    private static final String MSG_REJEICAO_INCOMPLETA = "Indique a categoria, o motivo e as notas da rejeição.";

    private static final String CAMPO_ALTERACAO_PEOT_ID = "alteracaoPeotId";
    private static final String TIPO_OCORRENCIA_PROCESSO = "PROCESSO";
    private static final String ACAO_VALIDAR_ITENS_DEVOLVIDO_PCM = "VALIDAR_ITENS_DEVOLVIDO_PCM";
    private static final String MSG_SO_DOCUMENTOS_DA_PCM = "Em Validado DGT só se validam os documentos acrescentados pela PCM.";
    private static final String MSG_SO_VALIDAR_DEVOLVIDO = "Em Validado DGT o documento só pode ser validado.";
    private static final String MSG_NAO_PERTENCE = "Não pertence ao Processo.";

    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final ProcessoInformacaoBaseRepository processoInformacaoBaseRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final ProcessoValidacaoRejeicaoRepository processoValidacaoRejeicaoRepository;
    private final ListaValoresService listaValoresService;
    private final UtilizadorRepository utilizadorRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;

    public ProcessoValidacaoService(ProcessoRepository processoRepository,
                                    ProcessoDocumentoRepository processoDocumentoRepository,
                                    ProcessoInformacaoBaseRepository processoInformacaoBaseRepository,
                                    DocumentoConfigRepository documentoConfigRepository,
                                    ProcessoValidacaoRejeicaoRepository processoValidacaoRejeicaoRepository,
                                    ListaValoresService listaValoresService,
                                    UtilizadorRepository utilizadorRepository,
                                    AutorizacaoService autorizacaoService,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver,
                                    AuditoriaService auditoriaService) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.processoInformacaoBaseRepository = processoInformacaoBaseRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.processoValidacaoRejeicaoRepository = processoValidacaoRejeicaoRepository;
        this.listaValoresService = listaValoresService;
        this.utilizadorRepository = utilizadorRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
    }

    /** Idempotente: só cria os elementos esperados que ainda não existem. */
    @Transactional
    public void criarItensInformacaoBase(Processo processo) {
        Set<ElementoInformacaoBase> esperados = elementosEsperados(processo);
        if (esperados.isEmpty()) {
            return;
        }
        Set<ElementoInformacaoBase> existentes = processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())
                .stream().map(ProcessoInformacaoBase::getElemento).collect(Collectors.toSet());
        for (ElementoInformacaoBase elemento : ElementoInformacaoBase.values()) {
            if (esperados.contains(elemento) && !existentes.contains(elemento)) {
                processoInformacaoBaseRepository.save(new ProcessoInformacaoBase(processo, elemento));
            }
        }
    }

    /** Um elemento esperado sem linha conta como por rever. */
    @Transactional(readOnly = true)
    public boolean todosRevistos(Processo processo) {
        boolean documentosRevistos = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())
                .stream().filter(d -> d.getEstadoValidacao() != EstadoValidacao.SUBSTITUIDO)
                .allMatch(d -> d.getEstadoValidacao() == EstadoValidacao.VALIDADO);
        if (!documentosRevistos) {
            return false;
        }
        Map<ElementoInformacaoBase, EstadoValidacao> estados = processoInformacaoBaseRepository
                .findByProcesso_Id(processo.getId()).stream()
                .collect(Collectors.toMap(ProcessoInformacaoBase::getElemento, ProcessoInformacaoBase::getEstadoValidacao));
        return elementosEsperados(processo).stream().allMatch(e -> estados.get(e) == EstadoValidacao.VALIDADO);
    }

    /**
     * Todos os documentos do Processo, obrigatórios ou não, gravados como validados; os `SUBSTITUIDO` já saíram do
     * conjunto documental e não contam. Sem documentos, verdadeiro.
     */
    @Transactional(readOnly = true)
    public boolean todosDocumentosValidados(Processo processo) {
        return !processoDocumentoRepository.existsByProcessoIdAndEstadoValidacaoNotIn(processo.getId(),
                ESTADOS_DOCUMENTO_RESOLVIDOS);
    }

    /**
     * A DGT tem documentos acrescentados pela PCM por validar num processo devolvido: valida-os antes de o enviar à
     * INCM ou de novo à PCM, sem mudar o estado (TR-114).
     */
    @Transactional(readOnly = true)
    public boolean revalidacaoPcm(Processo processo, ContextoUtilizador contexto) {
        return ATOR_DGT.equals(contexto.ator()) && devolvidoPelaPcm(processo)
                && processoDocumentoRepository.existsByProcessoIdAndEstadoValidacao(processo.getId(), EstadoValidacao.POR_VALIDAR);
    }

    private static boolean devolvidoPelaPcm(Processo processo) {
        return processo.getEstado() == EstadoProcesso.VALIDADO_DGT && processo.situacaoPcm() == SituacaoPcm.DEVOLVIDO;
    }

    @Transactional(readOnly = true)
    public List<InformacaoBaseResponse> informacaoBase(Processo processo) {
        return processoInformacaoBaseRepository.findByProcesso_Id(processo.getId()).stream()
                .sorted(Comparator.comparing(ProcessoInformacaoBase::getElemento))
                .map(e -> new InformacaoBaseResponse(e.getId(), e.getElemento(), e.getEstadoValidacao()))
                .toList();
    }

    /**
     * «Alteração PEOT's» do Resumo Validados: um item ativo de `ALTERACAO_PEOT`, ou nulo quando não se escolheu.
     * Com a lista vazia o campo não aparece, e qualquer id é recusado com 400 `VALIDACAO`.
     */
    @Transactional(readOnly = true)
    public ListaValores alteracaoPeotEscolhida(Long id) {
        return id == null ? null : listaValoresService.obterAtivo(TipoListaValores.ALTERACAO_PEOT, id, CAMPO_ALTERACAO_PEOT_ID);
    }

    @Transactional(readOnly = true)
    public List<RejeicaoEmValidacaoResponse> rejeicoesEmValidacao(Processo processo, ContextoUtilizador contexto) {
        if (!ATOR_DGT.equals(contexto.ator()) || processo.getEstado() != EstadoProcesso.EM_VALIDACAO_DGT) {
            return List.of();
        }
        return processoValidacaoRejeicaoRepository.findByProcesso_Id(processo.getId()).stream()
                .map(RejeicaoEmValidacaoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcessoValidacaoRejeicao> rascunhoParaRecusa(Processo processo) {
        List<UUID> rejeitados = new ArrayList<>();
        processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).stream()
                .filter(documento -> documento.getEstadoValidacao() == EstadoValidacao.REJEITADO)
                .forEach(documento -> rejeitados.add(documento.getId()));
        processoInformacaoBaseRepository.findByProcesso_Id(processo.getId()).stream()
                .filter(elemento -> elemento.getEstadoValidacao() == EstadoValidacao.REJEITADO)
                .sorted(Comparator.comparing(ProcessoInformacaoBase::getElemento))
                .forEach(elemento -> rejeitados.add(elemento.getId()));
        if (rejeitados.isEmpty()) {
            throw new ValidacaoException(MSG_SEM_ITENS_REJEITADOS,
                    List.of(new ValidacaoException.CampoErro(CAMPO_ITENS, MSG_SEM_ITENS_REJEITADOS)));
        }

        Map<UUID, ProcessoValidacaoRejeicao> rascunho = new HashMap<>();
        processoValidacaoRejeicaoRepository.findByProcesso_Id(processo.getId())
                .forEach(linha -> rascunho.put(alvo(linha), linha));
        if (!rascunho.keySet().containsAll(rejeitados)) {
            throw new ValidacaoException(MSG_REJEICAO_INCOMPLETA,
                    List.of(new ValidacaoException.CampoErro(CAMPO_ITENS, MSG_REJEICAO_INCOMPLETA)));
        }

        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        List<ProcessoValidacaoRejeicao> linhas = new ArrayList<>();
        for (UUID itemId : rejeitados) {
            ProcessoValidacaoRejeicao linha = rascunho.get(itemId);
            String prefixo = CAMPO_ITENS + "[" + itemId + "].";
            verificarAtivo(TipoListaValores.CATEGORIA_REJEICAO_DGT, linha.getCategoria(), prefixo + "categoriaId", erros);
            verificarAtivo(TipoListaValores.MOTIVO_REJEICAO_DGT, linha.getMotivo(), prefixo + "motivoId", erros);
            linhas.add(linha);
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Valor de lista inválido.", erros);
        }
        return linhas;
    }

    /**
     * Numa nova ronda, o que a DGT rejeitou volta a estar por validar. Sem isto um elemento da Informação Base rejeitado
     * ficava rejeitado para sempre e a DGT não conseguia validar nem rejeitar o processo.
     */
    @Transactional
    public void reabrirRejeitados(Processo processo) {
        List<ProcessoDocumento> documentos = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())
                .stream().filter(documento -> documento.getEstadoValidacao() == EstadoValidacao.REJEITADO).toList();
        documentos.forEach(documento -> documento.setEstadoValidacao(EstadoValidacao.POR_VALIDAR));
        processoDocumentoRepository.saveAll(documentos);
        List<ProcessoInformacaoBase> elementos = processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())
                .stream().filter(elemento -> elemento.getEstadoValidacao() == EstadoValidacao.REJEITADO).toList();
        elementos.forEach(elemento -> elemento.setEstadoValidacao(EstadoValidacao.POR_VALIDAR));
        processoInformacaoBaseRepository.saveAll(elementos);
    }

    /** Na correção, cada ficheiro que a DGT rejeitou é substituído ou removido antes de voltar a submeter. */
    @Transactional(readOnly = true)
    public void verificarRejeitadosCorrigidos(Processo processo) {
        List<ValidacaoException.CampoErro> erros = processoDocumentoRepository
                .findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).stream()
                .filter(documento -> documento.getEstadoValidacao() == EstadoValidacao.REJEITADO)
                .map(documento -> new ValidacaoException.CampoErro("documentos[" + documento.getId() + "]",
                        "O ficheiro «" + documento.getNomeFicheiro() + "» foi rejeitado pela DGT: substitua-o ou remova-o."))
                .toList();
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Há ficheiros rejeitados pela DGT por corrigir.", erros);
        }
    }

    @Transactional
    public void apagarRascunho(Processo processo) {
        processoValidacaoRejeicaoRepository.deleteByProcesso_Id(processo.getId());
    }

    private void verificarAtivo(TipoListaValores tipo, ListaValores valor, String campo,
                                List<ValidacaoException.CampoErro> erros) {
        if (valor == null) {
            return;
        }
        try {
            listaValoresService.obterAtivo(tipo, valor.getId(), campo);
        } catch (ValidacaoException e) {
            erros.addAll(e.getCampos());
        }
    }

    /** Todos os itens ativos contam, mesmo os que o filtro por elementos afetados esconde na estrutura documental. */
    @Transactional(readOnly = true)
    public Set<ElementoInformacaoBase> elementosPrevistos(InstrumentoProcedimentoConfig config) {
        return documentoConfigRepository.findElementosInformacaoBaseAtivos(config);
    }

    private Set<ElementoInformacaoBase> elementosEsperados(Processo processo) {
        return elementosPrevistos(processo.getInstrumentoProcedimentoConfig());
    }

    /**
     * Tudo ou nada: o pedido só grava depois de todos os itens passarem. A `versao` do Processo não é
     * incrementada, porque a decisão sobre um item não altera o Processo.
     * Um item `REJEITADO` tem sempre a sua linha no rascunho e um `VALIDADO` nunca a tem.
     * Num `VALIDADO_DGT` devolvido pela PCM só se validam os documentos `POR_VALIDAR` (os que a PCM acrescentou) e o
     * estado não muda; com o processo com a PCM, ou sem passar por ela, é 409.
     */
    @Transactional
    public ValidacaoItensResponse gravar(UUID processoId, GravarValidacaoItensRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarPossibilidadeDeAgirComoDgt(contexto, processo);
        if (processo.getVersao() != request.versao()) {
            throw new VersaoDesatualizadaException();
        }
        boolean devolvidoPelaPcm = devolvidoPelaPcm(processo);
        if (processo.getEstado() != EstadoProcesso.EM_VALIDACAO_DGT && !devolvidoPelaPcm) {
            throw new EstadoInvalidoException(
                    "A validação dos itens só é possível em EM_VALIDACAO_DGT ou num processo validado devolvido pela PCM.");
        }

        List<ProcessoDocumento> documentos = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId)
                .stream().filter(d -> d.getEstadoValidacao() != EstadoValidacao.SUBSTITUIDO).toList();
        List<ProcessoInformacaoBase> elementos = processoInformacaoBaseRepository.findByProcesso_Id(processoId);
        Map<UUID, ProcessoDocumento> documentosPorId = documentos.stream()
                .collect(Collectors.toMap(ProcessoDocumento::getId, Function.identity()));
        Map<UUID, ProcessoInformacaoBase> elementosPorId = elementos.stream()
                .collect(Collectors.toMap(ProcessoInformacaoBase::getId, Function.identity()));

        if (devolvidoPelaPcm) {
            verificarItensDevolvidoPcm(request.itens(), documentosPorId, elementosPorId);
        }
        List<ListasRejeicao> listas = verificarItens(request.itens(), documentosPorId, elementosPorId);

        Map<UUID, ProcessoValidacaoRejeicao> rascunho = new HashMap<>();
        processoValidacaoRejeicaoRepository.findByProcesso_Id(processoId)
                .forEach(linha -> rascunho.put(alvo(linha), linha));
        Instant agora = Instant.now();
        int rejeitados = 0;
        for (int i = 0; i < request.itens().size(); i++) {
            ItemValidacaoRequest item = request.itens().get(i);
            if (item.tipo() == TipoItemValidacao.DOCUMENTO) {
                documentosPorId.get(item.itemId()).setEstadoValidacao(item.decisao());
            } else {
                elementosPorId.get(item.itemId()).setEstadoValidacao(item.decisao());
            }
            if (item.decisao() == EstadoValidacao.REJEITADO) {
                ProcessoValidacaoRejeicao linha = rascunho.computeIfAbsent(item.itemId(), id ->
                        item.tipo() == TipoItemValidacao.DOCUMENTO
                                ? ProcessoValidacaoRejeicao.doDocumento(documentosPorId.get(id))
                                : ProcessoValidacaoRejeicao.daInformacaoBase(elementosPorId.get(id)));
                linha.registar(listas.get(i).categoria(), listas.get(i).motivo(), item.notas().trim(),
                        utilizadorRepository.getReferenceById(contexto.utilizadorId()), agora);
                processoValidacaoRejeicaoRepository.save(linha);
                rejeitados++;
            } else {
                ProcessoValidacaoRejeicao linha = rascunho.remove(item.itemId());
                if (linha != null) {
                    processoValidacaoRejeicaoRepository.delete(linha);
                }
            }
        }
        processoDocumentoRepository.saveAll(documentos);
        processoInformacaoBaseRepository.saveAll(elementos);
        log.info("Validação de itens gravada: processo={} itens={} rejeitados={}", processoId,
                request.itens().size(), rejeitados);
        if (devolvidoPelaPcm) {
            auditoriaService.registar(TIPO_OCORRENCIA_PROCESSO, processoId.toString(), ACAO_VALIDAR_ITENS_DEVOLVIDO_PCM,
                    contexto.utilizadorId(), Map.of(CAMPO_ITENS, request.itens().stream().map(ItemValidacaoRequest::itemId).toList()));
        }

        return resposta(documentos, elementos, rascunho);
    }

    private static void verificarItensDevolvidoPcm(List<ItemValidacaoRequest> itens, Map<UUID, ProcessoDocumento> documentos,
                                                   Map<UUID, ProcessoInformacaoBase> elementos) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemValidacaoRequest item = itens.get(i);
            String prefixo = "itens[" + i + "].";
            ProcessoDocumento documento = item.tipo() == TipoItemValidacao.DOCUMENTO ? documentos.get(item.itemId()) : null;
            boolean pertence = documento != null || (item.tipo() == TipoItemValidacao.INFORMACAO_BASE
                    && elementos.containsKey(item.itemId()));
            if (!pertence) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "itemId", MSG_NAO_PERTENCE));
            } else if (documento == null || documento.getEstadoValidacao() != EstadoValidacao.POR_VALIDAR) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "itemId", MSG_SO_DOCUMENTOS_DA_PCM));
            }
            if (item.decisao() != EstadoValidacao.VALIDADO) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "decisao", MSG_SO_VALIDAR_DEVOLVIDO));
            }
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Itens de validação inválidos.", erros);
        }
    }

    private record ListasRejeicao(ListaValores categoria, ListaValores motivo) {
        private static final ListasRejeicao NENHUMAS = new ListasRejeicao(null, null);
    }

    private static UUID alvo(ProcessoValidacaoRejeicao linha) {
        return linha.getProcessoDocumento() != null
                ? linha.getProcessoDocumento().getId()
                : linha.getProcessoInformacaoBase().getId();
    }

    /**
     * Junta os erros de todos os itens antes de recusar e devolve, pela ordem do pedido, a categoria e o motivo de
     * cada item. Uma lista sem valores ativos não é exigida; as notas são-no sempre.
     */
    private List<ListasRejeicao> verificarItens(List<ItemValidacaoRequest> itens, Map<UUID, ProcessoDocumento> documentos,
                                                Map<UUID, ProcessoInformacaoBase> elementos) {
        boolean haRejeicoes = itens.stream().anyMatch(item -> item.decisao() == EstadoValidacao.REJEITADO);
        boolean exigeCategoria = haRejeicoes
                && listaValoresService.temValoresAtivos(TipoListaValores.CATEGORIA_REJEICAO_DGT);
        boolean exigeMotivo = haRejeicoes
                && listaValoresService.temValoresAtivos(TipoListaValores.MOTIVO_REJEICAO_DGT);
        String mensagemEmFalta = mensagemEmFalta(exigeCategoria, exigeMotivo);

        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        List<ListasRejeicao> listas = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemValidacaoRequest item = itens.get(i);
            String prefixo = "itens[" + i + "].";
            if (item.decisao() == EstadoValidacao.POR_VALIDAR) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "decisao",
                        "A decisão tem de ser VALIDADO ou REJEITADO."));
            }
            boolean pertence = item.tipo() == TipoItemValidacao.DOCUMENTO
                    ? documentos.containsKey(item.itemId())
                    : elementos.containsKey(item.itemId());
            if (!pertence) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "itemId", MSG_NAO_PERTENCE));
            }
            if (item.decisao() != EstadoValidacao.REJEITADO) {
                listas.add(ListasRejeicao.NENHUMAS);
                continue;
            }
            ListaValores categoria = valorDaLista(TipoListaValores.CATEGORIA_REJEICAO_DGT, item.categoriaId(),
                    exigeCategoria, prefixo + "categoriaId", mensagemEmFalta, erros);
            ListaValores motivo = valorDaLista(TipoListaValores.MOTIVO_REJEICAO_DGT, item.motivoId(),
                    exigeMotivo, prefixo + "motivoId", mensagemEmFalta, erros);
            if (item.notas() == null || item.notas().isBlank()) {
                erros.add(new ValidacaoException.CampoErro(prefixo + "notas", mensagemEmFalta));
            }
            listas.add(new ListasRejeicao(categoria, motivo));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Itens de validação inválidos.", erros);
        }
        return listas;
    }

    private ListaValores valorDaLista(TipoListaValores tipo, Long id, boolean exigido, String campo,
                                      String mensagemEmFalta, List<ValidacaoException.CampoErro> erros) {
        if (id == null) {
            if (exigido) {
                erros.add(new ValidacaoException.CampoErro(campo, mensagemEmFalta));
            }
            return null;
        }
        try {
            return listaValoresService.obterAtivo(tipo, id, campo);
        } catch (ValidacaoException e) {
            erros.addAll(e.getCampos());
            return null;
        }
    }

    private static String mensagemEmFalta(boolean exigeCategoria, boolean exigeMotivo) {
        if (exigeCategoria && exigeMotivo) {
            return "Indique a categoria, o motivo e as notas da rejeição.";
        }
        if (exigeCategoria) {
            return "Indique a categoria e as notas da rejeição.";
        }
        if (exigeMotivo) {
            return "Indique o motivo e as notas da rejeição.";
        }
        return "Indique as notas da rejeição.";
    }

    private static ValidacaoItensResponse resposta(List<ProcessoDocumento> documentos,
                                                   List<ProcessoInformacaoBase> elementos,
                                                   Map<UUID, ProcessoValidacaoRejeicao> rascunho) {
        List<ItemValidacaoResponse> itens = new ArrayList<>();
        documentos.forEach(d -> itens.add(new ItemValidacaoResponse(TipoItemValidacao.DOCUMENTO, d.getId(),
                d.getEstadoValidacao(), rejeicaoDe(d.getEstadoValidacao(), rascunho.get(d.getId())))));
        elementos.forEach(e -> itens.add(new ItemValidacaoResponse(TipoItemValidacao.INFORMACAO_BASE, e.getId(),
                e.getEstadoValidacao(), rejeicaoDe(e.getEstadoValidacao(), rascunho.get(e.getId())))));
        boolean todosRevistos = itens.stream().noneMatch(i -> i.estadoValidacao() == EstadoValidacao.POR_VALIDAR);
        return new ValidacaoItensResponse(itens, todosRevistos);
    }

    private static RejeicaoItemResponse rejeicaoDe(EstadoValidacao estado, ProcessoValidacaoRejeicao linha) {
        return estado == EstadoValidacao.REJEITADO && linha != null ? RejeicaoItemResponse.de(linha) : null;
    }
}
