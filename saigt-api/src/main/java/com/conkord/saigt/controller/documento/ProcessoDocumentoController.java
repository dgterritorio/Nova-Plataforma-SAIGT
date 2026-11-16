package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.documento.DescargaDocumentoResponse;
import com.conkord.saigt.dto.documento.DesbloquearDocumentoRequest;
import com.conkord.saigt.dto.documento.FiltroDocumentos;
import com.conkord.saigt.dto.documento.GravarObservacoesRequest;
import com.conkord.saigt.dto.documento.HistoricoDocumentalResponse;
import com.conkord.saigt.dto.documento.ObservacoesDocumentoResponse;
import com.conkord.saigt.dto.documento.ProcessoDocumentoListagemResponse;
import com.conkord.saigt.dto.documento.ProcessoDocumentoResponse;
import com.conkord.saigt.dto.documento.ReclassificarDocumentoRequest;
import com.conkord.saigt.dto.documento.VerificacaoDocumentalResponse;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.service.documento.ChecklistValidacaoService;
import com.conkord.saigt.service.documento.DescargaDocumentoService;
import com.conkord.saigt.service.documento.ObservacaoDocumentoService;
import com.conkord.saigt.service.documento.ProcessoDocumentoConsultaService;
import com.conkord.saigt.service.documento.ProcessoDocumentoService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
public class ProcessoDocumentoController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final ProcessoDocumentoService processoDocumentoService;
    private final ProcessoDocumentoConsultaService processoDocumentoConsultaService;
    private final ObservacaoDocumentoService observacaoDocumentoService;
    private final DescargaDocumentoService descargaService;
    private final LeitorUploadDocumento leitorUpload;
    private final ChecklistValidacaoService checklistValidacaoService;

    public ProcessoDocumentoController(ProcessoDocumentoService processoDocumentoService,
                                       ProcessoDocumentoConsultaService processoDocumentoConsultaService,
                                       ObservacaoDocumentoService observacaoDocumentoService,
                                       DescargaDocumentoService descargaService,
                                       LeitorUploadDocumento leitorUpload,
                                       ChecklistValidacaoService checklistValidacaoService) {
        this.processoDocumentoService = processoDocumentoService;
        this.processoDocumentoConsultaService = processoDocumentoConsultaService;
        this.observacaoDocumentoService = observacaoDocumentoService;
        this.descargaService = descargaService;
        this.leitorUpload = leitorUpload;
        this.checklistValidacaoService = checklistValidacaoService;
    }

    /** `categoria` e `estadoValidacao` chegam como texto para o valor inválido dar o 400 de campo e não um erro de conversão. */
    @GetMapping("/api/v1/processos/{id}/documentos")
    public PageResponse<ProcessoDocumentoListagemResponse> listar(
            @PathVariable UUID id,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Long documentoConfigId,
            @RequestParam(required = false) String estadoValidacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        validarPagina(page, size, erros);
        CategoriaDocumento categoriaFiltro = converter(CategoriaDocumento.class, "categoria", categoria, erros);
        EstadoValidacao estadoFiltro = converter(EstadoValidacao.class, "estadoValidacao", estadoValidacao, erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Parâmetros de listagem inválidos.", erros);
        }

        Page<ProcessoDocumentoListagemResponse> resultado = processoDocumentoConsultaService.listar(
                id, new FiltroDocumentos(categoriaFiltro, documentoConfigId, estadoFiltro), PageRequest.of(page, size));
        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    /** Sem paginação: são os processos de um só Procedimento. */
    @GetMapping("/api/v1/processos/{id}/documentos/historico")
    public HistoricoDocumentalResponse historico(@PathVariable UUID id) {
        return processoDocumentoConsultaService.historico(id);
    }

    @GetMapping("/api/v1/processos/{id}/documentos/verificacoes")
    public List<VerificacaoDocumentalResponse> verificacoes(@PathVariable UUID id) {
        return checklistValidacaoService.verificar(id);
    }

    /**
     * `documentoConfigId` omitido = documento extra, sem item pré-configurado. O corpo é lido em streaming pelo
     * {@link LeitorUploadDocumento}, que exige as partes de texto antes de `ficheiro`; `categoria` é validada no
     * serviço para o valor em falta ou inválido dar o 400 de campo.
     */
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = UploadDocumento.class)))
    @PostMapping(value = "/api/v1/processos/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessoDocumentoResponse> carregar(@PathVariable UUID id, HttpServletRequest pedido) {
        ProcessoDocumento documento = processoDocumentoService.carregar(id, leitorUpload.ler(pedido));
        return ResponseEntity.status(201).body(ProcessoDocumentoResponse.de(documento));
    }

    /** Mantém o id e o slot: o pedido só traz o ficheiro (§14.6). Um `REJEITADO` dá um documento novo (BL-DOC-026). */
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = SubstituicaoDocumento.class)))
    @PutMapping(value = "/api/v1/processos/{id}/documentos/{docId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessoDocumentoResponse substituir(@PathVariable UUID id, @PathVariable UUID docId,
                                                HttpServletRequest pedido) {
        return ProcessoDocumentoResponse.de(processoDocumentoService.substituir(id, docId, leitorUpload.ler(pedido)));
    }

    /** Só descreve o corpo no Swagger: o pedido não é ligado a este tipo. */
    @Schema(description = "As partes de texto vêm antes de `ficheiro`.")
    record UploadDocumento(
            @Schema(description = "PUBLICACAO ou DEPOSITO", requiredMode = Schema.RequiredMode.REQUIRED) String categoria,
            @Schema(description = "Omitido = documento sem item, só DEPOSITO") Long documentoConfigId,
            @Schema(description = "Repetível: o documentoConfigId de outro item de depósito obrigatório que o ficheiro "
                    + "também satisfaz") List<Long> tags,
            @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED) String ficheiro) {
    }

    @Schema(description = "Só a parte `ficheiro`.")
    record SubstituicaoDocumento(
            @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED) String ficheiro) {
    }

    @DeleteMapping("/api/v1/processos/{id}/documentos/{docId}")
    public ResponseEntity<Void> remover(@PathVariable UUID id, @PathVariable UUID docId) {
        processoDocumentoService.remover(id, docId);
        return ResponseEntity.noContent().build();
    }

    /** Corpo opcional: a falta dele dá o mesmo `DOC_JUSTIFICACAO_EM_FALTA` da justificação em branco. */
    @PatchMapping("/api/v1/processos/{id}/documentos/{docId}/desbloquear")
    public ProcessoDocumentoResponse desbloquear(@PathVariable UUID id, @PathVariable UUID docId,
                                                 @Valid @RequestBody(required = false) DesbloquearDocumentoRequest request) {
        String justificacao = request == null ? null : request.justificacao();
        return ProcessoDocumentoResponse.de(processoDocumentoService.desbloquear(id, docId, justificacao));
    }

    @PatchMapping("/api/v1/processos/{id}/documentos/{docId}/classificacao")
    public ProcessoDocumentoResponse reclassificar(@PathVariable UUID id, @PathVariable UUID docId,
                                                   @Valid @RequestBody ReclassificarDocumentoRequest request) {
        return ProcessoDocumentoResponse.de(processoDocumentoService.reclassificar(id, docId, request));
    }

    @PutMapping("/api/v1/processos/{id}/documentos/observacoes/outros")
    public ObservacoesDocumentoResponse gravarObservacoesOutros(@PathVariable UUID id,
                                                                @Valid @RequestBody GravarObservacoesRequest request) {
        return new ObservacoesDocumentoResponse(null, observacaoDocumentoService.gravarOutros(id, request.observacoes()));
    }

    @PutMapping("/api/v1/processos/{id}/documentos/observacoes/{documentoConfigId}")
    public ObservacoesDocumentoResponse gravarObservacoes(@PathVariable UUID id, @PathVariable Long documentoConfigId,
                                                          @Valid @RequestBody GravarObservacoesRequest request) {
        var observacao = observacaoDocumentoService.gravar(id, documentoConfigId, request.observacoes());
        return new ObservacoesDocumentoResponse(observacao.getId().getDocumentoConfigId(), observacao.getObservacoes());
    }

    /** O browser não envia o JWT ao seguir um link: pede-se o ticket aqui e navega-se para o URL devolvido. */
    @PostMapping("/api/v1/processos/{id}/documentos/{docId}/descarga")
    public DescargaDocumentoResponse pedirDescarga(@PathVariable UUID id, @PathVariable UUID docId) {
        return new DescargaDocumentoResponse(descargaService.emitir(id, docId));
    }

    /** Recusa, não ajusta: o mesmo critério dos restantes endpoints de processos. */
    private static void validarPagina(int page, int size, List<ValidacaoException.CampoErro> erros) {
        if (page < 0) {
            erros.add(new ValidacaoException.CampoErro("page", "Não pode ser negativo."));
        }
        if (size < 1 || size > TAMANHO_PAGINA_MAXIMO) {
            erros.add(new ValidacaoException.CampoErro("size", "Tem de estar entre 1 e " + TAMANHO_PAGINA_MAXIMO + "."));
        }
    }

    private static <E extends Enum<E>> E converter(Class<E> tipo, String campo, String valor,
                                                   List<ValidacaoException.CampoErro> erros) {
        if (valor == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            erros.add(new ValidacaoException.CampoErro(campo, "Valor desconhecido: " + valor + "."));
            return null;
        }
    }
}
