package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import com.conkord.saigt.exception.DescargaIndisponivelException;
import com.conkord.saigt.exception.DescargasEmExcessoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Descarga pelo link público da INCM ou da PCGT, sem autenticação além do token. Token desconhecido, link revogado e
 * nome errado dão o mesmo 404; os dois últimos ficam auditados como `RECUSADA`, porque se sabe a que link pertencem.
 */
@Service
public class DescargaLinkPublicoService {

    private static final Logger log = LoggerFactory.getLogger(DescargaLinkPublicoService.class);
    private static final String MSG_NAO_ENCONTRADO = "Documento não encontrado.";
    private static final String METRICA = "documento.descarga.publica.total";

    private final AuditoriaDescargaService auditoria;
    private final ArmazenamentoDocumentos armazenamento;
    private final LimiteDescargas limite;
    private final MeterRegistry metricas;
    private final Duration repetirApos;

    public DescargaLinkPublicoService(AuditoriaDescargaService auditoria, ArmazenamentoDocumentos armazenamento,
                                      LimiteDescargas limite, MeterRegistry metricas,
                                      DescargaProperties propriedades) {
        this.auditoria = auditoria;
        this.armazenamento = armazenamento;
        this.limite = limite;
        this.metricas = metricas;
        this.repetirApos = propriedades.retryAfter();
    }

    /** `nome` já vem descodificado do caminho e tem de ser exatamente o nome do ficheiro do documento. */
    public AlvoLinkPublico resolver(String token, String nome, String ip) {
        AlvoLinkPublico alvo = lerToken(token).flatMap(auditoria::localizarLink)
                .orElseThrow(() -> naoEncontrado(null));
        if (!alvo.ativo() || !alvo.documento().nomeFicheiro().equals(nome)) {
            auditoria.registar(alvo, ip, ResultadoDescarga.RECUSADA);
            throw naoEncontrado(ResultadoDescarga.RECUSADA);
        }
        return alvo;
    }

    /** Aceita a descarga ou recusa-a (429, 503, 404 do objeto), deixando a linha `RECUSADA`. */
    public DescargaEmCurso iniciar(AlvoLinkPublico alvo, String ip, long inicio, long comprimento) {
        UUID token = alvo.token();
        if (!limite.ocupar(token)) {
            auditoria.registar(alvo, ip, ResultadoDescarga.RECUSADA);
            contar(ResultadoDescarga.RECUSADA);
            throw new DescargasEmExcessoException();
        }
        Long descargaId = null;
        try {
            descargaId = auditoria.registar(alvo, ip, ResultadoDescarga.INICIADA);
            AlvoDescarga documento = alvo.documento();
            InputStream conteudo = armazenamento.abrir(documento.caminho(), documento.versaoObjeto(), inicio,
                    comprimento);
            return new DescargaEmCurso(conteudo, descargaId, auditoria, () -> limite.libertar(token));
        } catch (RecursoNaoEncontradoException e) {
            recusar(descargaId, token);
            throw naoEncontrado(null);
        } catch (StorageIndisponivelException e) {
            recusar(descargaId, token);
            log.warn("Storage indisponível ao iniciar a descarga pública | linkId={}", alvo.linkId());
            throw new DescargaIndisponivelException(repetirApos, e);
        } catch (RuntimeException e) {
            recusar(descargaId, token);
            throw e;
        }
    }

    public void terminar(DescargaEmCurso descarga, AlvoLinkPublico alvo) {
        ResultadoDescarga resultado = descarga.fechar();
        contar(resultado);
        log.info("Descarga pública | linkId={} destino={} resultado={} bytes={}", alvo.linkId(), alvo.destino(),
                resultado, descarga.bytesEnviados());
    }

    private void recusar(Long descargaId, UUID token) {
        try {
            if (descargaId != null) {
                auditoria.concluir(descargaId, ResultadoDescarga.RECUSADA, 0);
                contar(ResultadoDescarga.RECUSADA);
            }
        } finally {
            limite.libertar(token);
        }
    }

    private RecursoNaoEncontradoException naoEncontrado(ResultadoDescarga resultado) {
        if (resultado != null) {
            contar(resultado);
        }
        return new RecursoNaoEncontradoException(MSG_NAO_ENCONTRADO);
    }

    private static Optional<UUID> lerToken(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void contar(ResultadoDescarga resultado) {
        metricas.counter(METRICA, "resultado", resultado.name().toLowerCase()).increment();
    }
}
