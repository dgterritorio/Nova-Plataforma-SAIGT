package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Retém, em momentos fixos do ciclo de vida, os ficheiros atuais do processo que ainda não têm retenção. Os momentos
 * são fixos, e não ficheiro a ficheiro, porque a validação da DGT é gravada de forma faseada e pode mudar até
 * terminar. Corre na transação da transição: se o storage falhar, a transição reverte.
 */
@Service
public class RetencaoDocumentosService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDocumentosService.class);
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_RETER = "RETER_DOCUMENTOS";
    private static final String PASTA_SUBSTITUIDOS = "/SUBSTITUIDOS/";

    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final ArmazenamentoDocumentos armazenamento;
    private final AuditoriaService auditoriaService;
    private final Clock relogio;
    private final int anos;

    public RetencaoDocumentosService(ProcessoDocumentoRepository processoDocumentoRepository,
                                     ArmazenamentoDocumentos armazenamento, AuditoriaService auditoriaService,
                                     Clock relogio, @Value("${app.documentos.retencao.anos}") int anos) {
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.armazenamento = armazenamento;
        this.auditoriaService = auditoriaService;
        this.relogio = relogio;
        this.anos = anos;
    }

    /** Idempotente: uma versão já retida não é tocada, por isso nunca se altera a data de uma retenção existente. */
    public int reterDocumentosSemRetencao(Processo processo, MomentoRetencao momento) {
        Instant ate = ZonedDateTime.now(relogio.withZone(ZoneOffset.UTC)).plusYears(anos).toInstant();
        String prefixo = "processos/" + processo.getNumeroProcesso() + "/";
        int retidos = 0;
        for (ProcessoDocumento documento : processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())) {
            String chave = documento.getCaminhoFicheiro();
            if (!chave.startsWith(prefixo) || chave.contains(PASTA_SUBSTITUIDOS)
                    || armazenamento.temRetencao(chave, documento.getVersaoObjeto())) {
                continue;
            }
            armazenamento.reter(chave, documento.getVersaoObjeto(), ate);
            retidos++;
        }
        auditoriaService.registarSistema(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_RETER,
                Map.of("momento", momento.name(), "ficheirosRetidos", retidos, "retidoAte", ate.toString()));
        log.info("Retenção dos documentos | processoId={} momento={} ficheirosRetidos={} retidoAte={}",
                processo.getId(), momento, retidos, ate);
        return retidos;
    }
}
