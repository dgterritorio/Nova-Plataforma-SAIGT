package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import com.conkord.saigt.exception.DescargaIndisponivelException;
import com.conkord.saigt.exception.DescargasEmExcessoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Descarga de documentos por ticket: emissão (utilizador com visibilidade) e envio (qualquer portador do ticket).
 * A linha de auditoria só existe quando o ticket identifica um documento; um ticket inválido não tem a quem ser
 * atribuído e dá 404 sem rasto.
 */
@Service
public class DescargaDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DescargaDocumentoService.class);
    private static final String MSG_NAO_ENCONTRADA = "Descarga não encontrada.";
    private static final String METRICA = "documento.descarga.total";

    private final ProcessoDocumentoConsultaService consultaService;
    private final TicketDescargaService ticketService;
    private final AuditoriaDescargaService auditoria;
    private final ArmazenamentoDocumentos armazenamento;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final MeterRegistry metricas;
    private final LimiteDescargas limite;
    private final Duration repetirApos;
    private final String urlBase;

    public DescargaDocumentoService(ProcessoDocumentoConsultaService consultaService,
                                    TicketDescargaService ticketService, AuditoriaDescargaService auditoria,
                                    ArmazenamentoDocumentos armazenamento,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver, MeterRegistry metricas,
                                    LimiteDescargas limite,
                                    DescargaProperties propriedades,
                                    @Value("${app.publico.base-url}") String urlBase) {
        this.consultaService = consultaService;
        this.ticketService = ticketService;
        this.auditoria = auditoria;
        this.armazenamento = armazenamento;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.metricas = metricas;
        this.limite = limite;
        this.repetirApos = propriedades.retryAfter();
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /** Camadas 1 e 2: sem visibilidade sobre o processo, ou documento de outro processo, é 404 e não há ticket. */
    public String emitir(UUID processoId, UUID documentoId) {
        consultaService.validarAcessoDescarga(processoId, documentoId);
        UUID utilizadorId = contextoUtilizadorResolver.resolver().utilizadorId();
        return url(TipoTicketDescarga.DOCUMENTO_PROCESSO, utilizadorId, documentoId);
    }

    public String emitirAnexoNotificacao(UUID notificacaoDocumentoId) {
        UUID utilizadorId = contextoUtilizadorResolver.resolver().utilizadorId();
        return url(TipoTicketDescarga.ANEXO_NOTIFICACAO, utilizadorId, notificacaoDocumentoId);
    }

    /** O mesmo 404 para ticket inválido, adulterado, expirado, de outra chave ou de um documento que já não existe. */
    public AlvoDescarga resolver(String ticket) {
        TicketDescarga validado = ticketService.validar(ticket)
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA));
        return (validado.tipo() == TipoTicketDescarga.ANEXO_NOTIFICACAO
                ? auditoria.localizarAnexoNotificacao(validado.documentoId(), validado.utilizadorId())
                : auditoria.localizar(validado.documentoId(), validado.utilizadorId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA));
    }

    private String url(TipoTicketDescarga tipo, UUID utilizadorId, UUID documentoId) {
        return urlBase + "/api/v1/descargas/" + ticketService.emitir(tipo, utilizadorId, documentoId);
    }

    /**
     * Aceita a descarga ou recusa-a (429, 503, 404 do objeto), deixando a linha `RECUSADA`. `inicio` e `comprimento`
     * já vêm resolvidos contra o tamanho do documento.
     */
    public DescargaEmCurso iniciar(AlvoDescarga alvo, String ip, long inicio, long comprimento) {
        if (!limite.ocupar()) {
            auditoria.registar(alvo, ip, ResultadoDescarga.RECUSADA);
            contar(ResultadoDescarga.RECUSADA);
            throw new DescargasEmExcessoException();
        }
        Long descargaId = null;
        try {
            descargaId = auditoria.registar(alvo, ip, ResultadoDescarga.INICIADA);
            InputStream conteudo = armazenamento.abrir(alvo.caminho(), alvo.versaoObjeto(), inicio, comprimento);
            return new DescargaEmCurso(conteudo, descargaId, auditoria, limite::libertar);
        } catch (RecursoNaoEncontradoException e) {
            recusar(descargaId);
            throw new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADA);
        } catch (StorageIndisponivelException e) {
            recusar(descargaId);
            log.warn("Storage indisponível ao iniciar a descarga do documento {}.", alvo.documentoId());
            throw new DescargaIndisponivelException(repetirApos, e);
        } catch (RuntimeException e) {
            recusar(descargaId);
            throw e;
        }
    }

    public void terminar(DescargaEmCurso descarga, UUID documentoId) {
        ResultadoDescarga resultado = descarga.fechar();
        contar(resultado);
        log.info("Descarga do documento {}: {}, {} bytes.", documentoId, resultado, descarga.bytesEnviados());
    }

    private void recusar(Long descargaId) {
        try {
            if (descargaId != null) {
                auditoria.concluir(descargaId, ResultadoDescarga.RECUSADA, 0);
                contar(ResultadoDescarga.RECUSADA);
            }
        } finally {
            limite.libertar();
        }
    }

    private void contar(ResultadoDescarga resultado) {
        metricas.counter(METRICA, "resultado", resultado.name().toLowerCase()).increment();
    }
}
