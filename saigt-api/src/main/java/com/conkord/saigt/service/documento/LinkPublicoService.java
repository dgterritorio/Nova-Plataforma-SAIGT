package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.LinkPublicoNaoPermitidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Links públicos dos documentos para a INCM e a PCGT: um link ativo por documento e destino, sem validade, reutilizado
 * nos reenvios e revogado só na substituição ou na remoção do documento. Não há cópia do objeto.
 */
@Service
public class LinkPublicoService {

    private static final Logger log = LoggerFactory.getLogger(LinkPublicoService.class);
    static final String CAMINHO = "/api/v1/publico/";
    private static final Pattern TOKEN_NO_URL = Pattern.compile("(/api/v1/publico/)[^/\"\\s?]+");
    private static final String TOKEN_MASCARADO = "$1***";

    private final DocumentoLinkPublicoRepository linkRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final PlantaRepository plantaRepository;
    private final TransactionTemplate transacaoPropria;
    private final Clock relogio;
    private final String urlBase;

    public LinkPublicoService(DocumentoLinkPublicoRepository linkRepository,
                              ProcessoDocumentoRepository processoDocumentoRepository,
                              PlantaRepository plantaRepository, PlatformTransactionManager transactionManager,
                              Clock relogio, @Value("${app.publico.base-url}") String urlBase) {
        this.linkRepository = linkRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.plantaRepository = plantaRepository;
        this.transacaoPropria = new TransactionTemplate(transactionManager);
        this.transacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.relogio = relogio;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * Commit próprio, para o URL já existir quando o consumidor o abre: nunca na transação de quem vai fazer a
     * chamada externa. Um pedido concorrente que crie o mesmo link primeiro ganha, e este reutiliza-o.
     */
    public LinkPublico obterOuCriar(UUID processoDocumentoId, DestinoLinkPublico destino) {
        try {
            return transacaoPropria.execute(estado -> obterOuCriarNaTransacao(processoDocumentoId, destino));
        } catch (DataIntegrityViolationException e) {
            return transacaoPropria.execute(estado -> obterOuCriarNaTransacao(processoDocumentoId, destino));
        }
    }

    public static boolean permitido(ProcessoDocumento documento) {
        DocumentoConfig tipo = documento.getDocumentoConfig();
        return tipo != null && tipo.isLinkPublicoPermitido();
    }

    /**
     * Na transação de quem substitui ou remove o documento. Um link INCM revogado deixa de servir a imagem da planta,
     * que só volta a ter link no envio seguinte. A planta liga-se ao link pelo URL do JPEG.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revogar(UUID processoDocumentoId, MotivoRevogacaoLink motivo) {
        List<DocumentoLinkPublico> ativos = linkRepository.findByProcessoDocumento_IdAndRevogadoEmIsNull(processoDocumentoId);
        if (ativos.isEmpty()) {
            return;
        }
        ativos.forEach(link -> link.revogar(motivo, relogio.instant()));
        linkRepository.flush();
        ativos.stream().filter(link -> link.getDestino() == DestinoLinkPublico.INCM).forEach(link -> {
            String url = url(link.getToken(), link.getProcessoDocumento().getNomeFicheiro());
            plantaRepository.findByProcesso_Id(link.getProcessoDocumento().getProcesso().getId()).stream()
                    .filter(planta -> url.equals(planta.getLinkJpeg())).forEach(planta -> planta.setLinkJpeg(null));
        });
        log.info("Links públicos revogados | processoDocumentoId={} motivo={} total={}", processoDocumentoId, motivo,
                ativos.size());
    }

    /**
     * Antes de apagar o documento: os links saem com ele. Apagam-se pelo Hibernate, e não só pela FK, porque os
     * revogados ficam geridos nesta transação e o flush recusaria uma referência ao documento removido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apagarDoDocumento(UUID processoDocumentoId) {
        revogar(processoDocumentoId, MotivoRevogacaoLink.REMOCAO);
        linkRepository.deleteAll(linkRepository.findByProcessoDocumento_Id(processoDocumentoId));
    }

    /** `https://{saigt}/api/v1/publico/{token}/{nome}`, com o nome do ficheiro codificado como segmento de caminho. */
    public String url(UUID token, String nomeFicheiro) {
        return urlBase + CAMINHO + token + "/" + UriUtils.encodePathSegment(nomeFicheiro, StandardCharsets.UTF_8);
    }

    /** Troca o token de cada URL de link público por `***`, para os logs e para os payloads guardados. */
    public static String mascararTokens(String texto) {
        return texto == null ? null : TOKEN_NO_URL.matcher(texto).replaceAll(TOKEN_MASCARADO);
    }

    private LinkPublico obterOuCriarNaTransacao(UUID processoDocumentoId, DestinoLinkPublico destino) {
        ProcessoDocumento documento = processoDocumentoRepository.findById(processoDocumentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado."));
        if (!permitido(documento)) {
            throw new LinkPublicoNaoPermitidoException(documento.getNomeFicheiro());
        }
        DocumentoLinkPublico link = linkRepository
                .findByProcessoDocumento_IdAndDestinoAndRevogadoEmIsNull(processoDocumentoId, destino)
                .orElseGet(() -> criar(documento, destino));
        return new LinkPublico(link.getId(), processoDocumentoId, url(link.getToken(), documento.getNomeFicheiro()));
    }

    private DocumentoLinkPublico criar(ProcessoDocumento documento, DestinoLinkPublico destino) {
        DocumentoLinkPublico link = linkRepository.saveAndFlush(
                new DocumentoLinkPublico(documento, destino, UUID.randomUUID(), relogio.instant()));
        log.info("Link público criado | processoDocumentoId={} destino={} linkId={}", documento.getId(), destino,
                link.getId());
        return link;
    }

    /** O URL leva o token: não vai para logs nem para a auditoria sem {@link #mascararTokens}. */
    public record LinkPublico(UUID linkId, UUID processoDocumentoId, String url) {
    }
}
