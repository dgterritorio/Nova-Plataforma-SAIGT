package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ConsumidorDescarga;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Cada método é uma transação curta: a descarga nunca mantém uma aberta enquanto envia os bytes. */
@Service
public class AuditoriaDescargaService {

    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final DocumentoDescargaRepository descargaRepository;
    private final DocumentoLinkPublicoRepository linkRepository;
    private final NotificacaoDocumentoRepository notificacaoDocumentoRepository;
    private final Clock relogio;

    public AuditoriaDescargaService(ProcessoDocumentoRepository processoDocumentoRepository,
                                    DocumentoDescargaRepository descargaRepository,
                                    DocumentoLinkPublicoRepository linkRepository,
                                    NotificacaoDocumentoRepository notificacaoDocumentoRepository, Clock relogio) {
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.descargaRepository = descargaRepository;
        this.linkRepository = linkRepository;
        this.notificacaoDocumentoRepository = notificacaoDocumentoRepository;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Optional<AlvoDescarga> localizar(UUID documentoId, UUID utilizadorId) {
        return processoDocumentoRepository.findById(documentoId).map(documento -> alvo(documento, utilizadorId));
    }

    @Transactional(readOnly = true)
    public Optional<AlvoDescarga> localizarAnexoNotificacao(UUID notificacaoDocumentoId, UUID utilizadorId) {
        return notificacaoDocumentoRepository.findById(notificacaoDocumentoId)
                .map(anexo -> alvo(anexo, utilizadorId));
    }

    @Transactional
    public Long registar(AlvoDescarga alvo, String ip, ResultadoDescarga resultado) {
        DocumentoDescarga descarga = alvo.tipo() == TipoTicketDescarga.ANEXO_NOTIFICACAO
                ? DocumentoDescarga.porAnexoNotificacao(alvo.documentoId(), alvo.utilizadorId(), ip, relogio.instant(),
                        resultado)
                : new DocumentoDescarga(ConsumidorDescarga.UTILIZADOR, alvo.processoId(), alvo.documentoId(),
                        alvo.utilizadorId(), ip, relogio.instant(), resultado);
        return descargaRepository.save(descarga).getId();
    }

    /** O link pelo token, ativo ou revogado: quem decide o 404 é o chamador. */
    @Transactional(readOnly = true)
    public Optional<AlvoLinkPublico> localizarLink(UUID token) {
        return linkRepository.findComDocumentoByToken(token).map(link -> new AlvoLinkPublico(link.getId(),
                link.getToken(), link.getDestino(), link.isAtivo(), alvo(link.getProcessoDocumento(), null)));
    }

    @Transactional
    public Long registar(AlvoLinkPublico alvo, String ip, ResultadoDescarga resultado) {
        ConsumidorDescarga consumidor = alvo.destino() == DestinoLinkPublico.INCM
                ? ConsumidorDescarga.INCM : ConsumidorDescarga.PCGT;
        return descargaRepository.save(DocumentoDescarga.porLinkPublico(consumidor, alvo.documento().processoId(),
                alvo.documento().documentoId(), alvo.linkId(), ip, relogio.instant(), resultado)).getId();
    }

    @Transactional
    public Long registarSnit(UUID processoId, UUID processoDocumentoId, UUID manifestoId, String ip,
                             ResultadoDescarga resultado) {
        return descargaRepository.save(DocumentoDescarga.porSnit(processoId, processoDocumentoId, manifestoId, ip,
                relogio.instant(), resultado)).getId();
    }

    @Transactional
    public void concluir(Long descargaId, ResultadoDescarga resultado, long bytesEnviados) {
        descargaRepository.findById(descargaId).ifPresent(descarga -> {
            descarga.setResultado(resultado);
            descarga.setBytesEnviados(bytesEnviados);
        });
    }

    private static AlvoDescarga alvo(ProcessoDocumento documento, UUID utilizadorId) {
        return new AlvoDescarga(documento.getId(), documento.getProcesso().getId(), utilizadorId,
                documento.getCaminhoFicheiro(), documento.getVersaoObjeto(), documento.getNomeFicheiro(),
                documento.getTamanhoBytes(), documento.getHashSha256(), TipoTicketDescarga.DOCUMENTO_PROCESSO);
    }

    private static AlvoDescarga alvo(NotificacaoDocumento anexo, UUID utilizadorId) {
        return new AlvoDescarga(anexo.getId(), null, utilizadorId, anexo.getCaminhoFicheiro(), null,
                anexo.getNomeFicheiro(), anexo.getTamanhoBytes(), null, TipoTicketDescarga.ANEXO_NOTIFICACAO);
    }
}
