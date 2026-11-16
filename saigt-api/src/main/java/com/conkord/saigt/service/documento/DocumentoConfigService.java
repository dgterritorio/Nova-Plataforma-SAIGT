package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.AtualizarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.CriarDocumentoConfigRequest;
import com.conkord.saigt.dto.documento.DocumentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfigFormatoIncoerenteException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.ExtensoesAceites;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Itens documentais (`documento_config`) de cada Procedimento. Leitura para qualquer sessão autenticada, escrita só
 * `MASTER`. Sem `DELETE`: desativa-se com `ativo=false`, o que retira o item da estrutura documental também nos
 * processos em curso. Um erro de validação não grava nada (a transação reverte).
 */
@Slf4j
@Service
public class DocumentoConfigService {

    private static final String TIPO_OCORRENCIA = "CONFIGURACAO";
    private static final String RECURSO = "documento-config";

    private final DocumentoConfigRepository documentoConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final AuditoriaService auditoriaService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ValidadorRegrasTiff validadorRegrasTiff;

    public DocumentoConfigService(DocumentoConfigRepository documentoConfigRepository,
                                  InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                  ProcessoDocumentoRepository processoDocumentoRepository,
                                  AuditoriaService auditoriaService,
                                  AutorizacaoService autorizacaoService,
                                  ContextoUtilizadorResolver contextoUtilizadorResolver,
                                  ValidadorRegrasTiff validadorRegrasTiff) {
        this.documentoConfigRepository = documentoConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.auditoriaService = auditoriaService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.validadorRegrasTiff = validadorRegrasTiff;
    }

    @Transactional(readOnly = true)
    public Page<DocumentoConfigResponse> listar(Long instrumentoProcedimentoConfigId, Boolean ativo, Pageable pagina) {
        if (instrumentoProcedimentoConfigId == null) {
            throw new ValidacaoException("Parâmetros inválidos.", List.of(new ValidacaoException.CampoErro(
                    "instrumentoProcedimentoConfigId", "Obrigatório.")));
        }
        Page<DocumentoConfig> itens = ativo == null
                ? documentoConfigRepository.findByInstrumentoProcedimentoConfig_Id(instrumentoProcedimentoConfigId, pagina)
                : documentoConfigRepository.findByInstrumentoProcedimentoConfig_IdAndAtivo(
                        instrumentoProcedimentoConfigId, ativo, pagina);
        return itens.map(DocumentoConfigResponse::de);
    }

    @Transactional
    public DocumentoConfigResponse criar(CriarDocumentoConfigRequest request) {
        UUID atorId = exigirMaster();
        InstrumentoProcedimentoConfig procedimento = instrumentoProcedimentoConfigRepository
                .findById(request.instrumentoProcedimentoConfigId())
                .orElseThrow(() -> new ValidacaoException("Procedimento inexistente.", List.of(
                        new ValidacaoException.CampoErro("instrumentoProcedimentoConfigId",
                                "O Procedimento não existe."))));
        if (documentoConfigRepository.existsByInstrumentoProcedimentoConfig_IdAndCodigo(
                procedimento.getId(), request.codigo())) {
            throw new ValidacaoException("Código duplicado.", List.of(new ValidacaoException.CampoErro("codigo",
                    "Já existe um item com este código neste Procedimento.")));
        }

        AtualizarDocumentoConfigRequest dados = request.comoAtualizacao();
        DocumentoConfig item = new DocumentoConfig(procedimento, request.codigo(), dados.nome(), dados.separador(),
                dados.categoria(), dados.tipoElemento(), dados.formato(),
                ExtensoesAceites.escrever(dados.extensoesAceites()), dados.ordem());
        aplicarCamposComuns(item, dados);
        item.setRegrasTiff(regrasAGravar(item, dados.regrasTiff(), RegrasTiff.inicialGeral()));
        validar(item, dados.regrasTiff());
        item = documentoConfigRepository.save(item);

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("codigo", item.getCodigo());
        campos.put("instrumentoProcedimentoConfigId", String.valueOf(procedimento.getId()));
        campos.putAll(estadoComum(item));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(item.getId()), "CRIAR", atorId, campos);
        return DocumentoConfigResponse.de(item);
    }

    @Transactional
    public DocumentoConfigResponse atualizar(Long id, AtualizarDocumentoConfigRequest request) {
        UUID atorId = exigirMaster();
        DocumentoConfig item = documentoConfigRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item documental não encontrado."));

        Map<String, String> antes = estadoComum(item);
        RegrasTiff regrasGravadas = item.getRegrasTiff();
        boolean mudaChaveDeArmazenamento = item.getCategoria() != request.categoria()
                || item.getTipoElemento() != request.tipoElemento();
        item.setCategoria(request.categoria());
        item.setTipoElemento(request.tipoElemento());
        item.setFormato(request.formato());
        aplicarCamposComuns(item, request);
        // Antes de validar: a consulta da Informação Base faz flush do item, e a BD recusa regras fora das peças gráficas.
        item.setRegrasTiff(regrasAGravar(item, request.regrasTiff(), regrasGravadas));
        validar(item, request.regrasTiff());
        if (mudaChaveDeArmazenamento && processoDocumentoRepository.existsByDocumentoConfig_Id(id)) {
            throw new ConfiguracaoEmUsoException();
        }
        documentoConfigRepository.save(item);

        Map<String, String> alterados = new LinkedHashMap<>();
        estadoComum(item).forEach((campo, valor) -> {
            if (!Objects.equals(antes.get(campo), valor)) {
                alterados.put(campo, valor);
            }
        });
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(id), "ATUALIZAR", atorId, alterados);
        return DocumentoConfigResponse.de(item);
    }

    private static void aplicarCamposComuns(DocumentoConfig item, AtualizarDocumentoConfigRequest dados) {
        item.setNome(dados.nome());
        item.setSeparador(dados.separador());
        item.setDescricao(dados.descricao());
        item.setExtensoesAceites(ExtensoesAceites.escrever(dados.extensoesAceites()));
        item.setObrigatorio(dados.obrigatorio());
        item.setOrdem(dados.ordem());
        item.setElementoInformacaoBase(dados.elementoInformacaoBase());
        item.setTamanhoMaximoFicheiro(dados.tamanhoMaximoFicheiro());
        item.setTamanhoMaximoTotal(dados.tamanhoMaximoTotal());
        item.setNumeroMaximoFicheiros(dados.numeroMaximoFicheiros());
        item.setLinkPublicoPermitido(dados.linkPublicoPermitido());
        item.setAtivo(dados.ativo());
        if (dados.regraNomenclatura() != null) {
            item.setRegraNomenclatura(dados.regraNomenclatura());
        }
    }

    /** Sem regras no pedido, ficam as anteriores; fora das peças gráficas, nenhumas (a BD recusa-as). */
    private static RegrasTiff regrasAGravar(DocumentoConfig item, RegrasTiff pedidas, RegrasTiff anteriores) {
        if (item.getTipoElemento() != TipoElemento.PECA_GRAFICA) {
            return null;
        }
        return pedidas != null ? pedidas : anteriores;
    }

    private void validar(DocumentoConfig item, RegrasTiff regrasPedidas) {
        RegrasExtensaoDocumento.validarConfig(item);
        validarFormato(item);
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        erroInformacaoBase(item).ifPresent(erros::add);
        erros.addAll(validadorRegrasTiff.erros(item.getTipoElemento(), regrasPedidas));
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Item documental inválido.", erros);
        }
    }

    /** BL-DOC-023, só da peça escrita: a publicação segue editável para a INCM, o depósito não editável. */
    private static void validarFormato(DocumentoConfig item) {
        if (item.getTipoElemento() != TipoElemento.PECA_ESCRITA) {
            return;
        }
        FormatoDocumento esperado = item.getCategoria() == CategoriaDocumento.PUBLICACAO
                ? FormatoDocumento.EDITAVEL : FormatoDocumento.NAO_EDITAVEL;
        if (item.getFormato() != esperado) {
            throw new ConfigFormatoIncoerenteException();
        }
    }

    /** Os itens ativos do mesmo separador do Procedimento partilham a Informação Base, ou todos nenhuma. */
    private Optional<ValidacaoException.CampoErro> erroInformacaoBase(DocumentoConfig item) {
        if (!item.isAtivo()) {
            return Optional.empty();
        }
        boolean conflito = documentoConfigRepository
                .findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(
                        item.getInstrumentoProcedimentoConfig().getId(), item.getSeparador())
                .stream()
                .filter(outro -> !Objects.equals(outro.getId(), item.getId()))
                .anyMatch(outro -> outro.getElementoInformacaoBase() != item.getElementoInformacaoBase());
        return conflito
                ? Optional.of(new ValidacaoException.CampoErro("elementoInformacaoBase",
                        "Os itens do mesmo separador têm de ter a mesma Informação Base."))
                : Optional.empty();
    }

    private static Map<String, String> estadoComum(DocumentoConfig item) {
        Map<String, String> estado = new LinkedHashMap<>();
        estado.put("nome", item.getNome());
        estado.put("separador", item.getSeparador());
        estado.put("descricao", item.getDescricao());
        estado.put("categoria", String.valueOf(item.getCategoria()));
        estado.put("tipoElemento", String.valueOf(item.getTipoElemento()));
        estado.put("formato", String.valueOf(item.getFormato()));
        estado.put("extensoesAceites", String.join(",", ExtensoesAceites.ler(item)));
        estado.put("obrigatorio", String.valueOf(item.isObrigatorio()));
        estado.put("ordem", String.valueOf(item.getOrdem()));
        estado.put("elementoInformacaoBase", String.valueOf(item.getElementoInformacaoBase()));
        estado.put("tamanhoMaximoFicheiro", String.valueOf(item.getTamanhoMaximoFicheiro()));
        estado.put("tamanhoMaximoTotal", String.valueOf(item.getTamanhoMaximoTotal()));
        estado.put("numeroMaximoFicheiros", String.valueOf(item.getNumeroMaximoFicheiros()));
        estado.put("linkPublicoPermitido", String.valueOf(item.isLinkPublicoPermitido()));
        estado.put("ativo", String.valueOf(item.isAtivo()));
        estado.put("regraNomenclatura", String.valueOf(item.getRegraNomenclatura()));
        estado.put("regrasTiff", String.valueOf(item.regrasTiffEfetivas().orElse(null)));
        return estado;
    }

    private static String ocorrencia(Long id) {
        return RECURSO + ":" + id;
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
