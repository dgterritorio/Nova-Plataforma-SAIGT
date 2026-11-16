package com.conkord.saigt.controller.notificacao;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.notificacao.CriarMensagemNotificacaoRequest;
import com.conkord.saigt.dto.notificacao.CriarMensagemNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.CriarNotificacaoRequest;
import com.conkord.saigt.dto.notificacao.CriarNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.DocumentoNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.DownloadAnexoNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.NotificacaoDetalheResponse;
import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.dto.notificacao.RemocaoNotificacaoResponse;
import com.conkord.saigt.controller.documento.LeitorUploadDocumento;
import com.conkord.saigt.service.notificacao.NotificacaoAnexoService;
import com.conkord.saigt.service.notificacao.NotificacaoConsultaService;
import com.conkord.saigt.service.notificacao.NotificacaoCriacaoService;
import com.conkord.saigt.service.notificacao.NotificacaoEstadoService;
import com.conkord.saigt.service.notificacao.NotificacaoMensagemService;
import com.conkord.saigt.service.notificacao.NotificacaoReaberturaService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class NotificacaoController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final NotificacaoConsultaService notificacaoConsultaService;
    private final NotificacaoCriacaoService notificacaoCriacaoService;
    private final NotificacaoMensagemService notificacaoMensagemService;
    private final NotificacaoReaberturaService notificacaoReaberturaService;
    private final NotificacaoEstadoService notificacaoEstadoService;
    private final NotificacaoAnexoService notificacaoAnexoService;
    private final LeitorUploadDocumento leitorUpload;

    public NotificacaoController(NotificacaoConsultaService notificacaoConsultaService,
                                 NotificacaoCriacaoService notificacaoCriacaoService,
                                 NotificacaoMensagemService notificacaoMensagemService,
                                 NotificacaoReaberturaService notificacaoReaberturaService,
                                 NotificacaoEstadoService notificacaoEstadoService,
                                 NotificacaoAnexoService notificacaoAnexoService,
                                 LeitorUploadDocumento leitorUpload) {
        this.notificacaoConsultaService = notificacaoConsultaService;
        this.notificacaoCriacaoService = notificacaoCriacaoService;
        this.notificacaoMensagemService = notificacaoMensagemService;
        this.notificacaoReaberturaService = notificacaoReaberturaService;
        this.notificacaoEstadoService = notificacaoEstadoService;
        this.notificacaoAnexoService = notificacaoAnexoService;
        this.leitorUpload = leitorUpload;
    }

    @GetMapping("/api/v1/notificacoes")
    public PageResponse<NotificacaoResumoResponse> listar(
            @RequestParam(required = false) List<String> estado,
            @RequestParam(required = false) List<String> tipo,
            @RequestParam(required = false) UUID processoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        int tamanhoAplicado = Math.min(size, TAMANHO_PAGINA_MAXIMO);
        Page<NotificacaoResumoResponse> resultado = notificacaoConsultaService.listar(estado, tipo, processoId,
                desde, ate, PageRequest.of(page, tamanhoAplicado,
                        Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"))));

        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/api/v1/notificacoes/{id}")
    public NotificacaoDetalheResponse obter(@PathVariable UUID id) {
        return notificacaoConsultaService.obter(id);
    }

    @PostMapping("/api/v1/notificacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public CriarNotificacaoResponse criar(@RequestBody CriarNotificacaoRequest pedido) {
        return notificacaoCriacaoService.criar(pedido);
    }

    @PostMapping("/api/v1/notificacoes/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public CriarMensagemNotificacaoResponse responder(@PathVariable UUID id,
                                                      @Valid @RequestBody CriarMensagemNotificacaoRequest request) {
        return notificacaoMensagemService.responder(id, request.texto());
    }

    @PatchMapping("/api/v1/notificacoes/{id}/reabrir")
    public NotificacaoResumoResponse reabrir(@PathVariable UUID id) {
        return notificacaoReaberturaService.reabrir(id);
    }

    /** Um ficheiro por pedido, lido em streaming pelo {@link LeitorUploadDocumento}. */
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = UploadAnexo.class)))
    @PostMapping(value = "/api/v1/notificacoes/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoNotificacaoResponse anexar(@PathVariable UUID id, HttpServletRequest pedido) {
        return DocumentoNotificacaoResponse.de(notificacaoAnexoService.carregar(id, leitorUpload.ler(pedido)));
    }

    @PostMapping("/api/v1/notificacoes/{id}/documentos/{docId}/descarga")
    public DownloadAnexoNotificacaoResponse pedirDescarga(@PathVariable UUID id, @PathVariable UUID docId) {
        return new DownloadAnexoNotificacaoResponse(notificacaoAnexoService.emitirDescarga(id, docId));
    }

    @PatchMapping("/api/v1/notificacoes/{id}/ler")
    public NotificacaoResumoResponse ler(@PathVariable UUID id) {
        return notificacaoEstadoService.ler(id);
    }

    @PatchMapping("/api/v1/notificacoes/{id}/resolver")
    public NotificacaoResumoResponse resolver(@PathVariable UUID id) {
        return notificacaoEstadoService.resolver(id);
    }

    @PatchMapping("/api/v1/notificacoes/{id}/remover")
    public RemocaoNotificacaoResponse remover(@PathVariable UUID id) {
        return notificacaoEstadoService.remover(id);
    }

    /** Só descreve o corpo no Swagger: o pedido não é ligado a este tipo. */
    record UploadAnexo(@Schema(type = "string", format = "binary") String ficheiro) {
    }
}
