package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Aviso ao utilizador de que o pedido foi enviado para a PCGT (BL-NOTIF-003 (e), BL-INT-006 passo 4): só na Decisão de
 * início de um PDM com GeoConform, depois de a PCGT aceitar o pedido. Cria-se uma só vez por processo: a nova
 * comunicação depois da entrada em vigor (atualização do IDUS) não volta a avisar.
 */
@Slf4j
@Service
public class AvisoPedidoPcgtService {

    static final String DECISAO_INICIO = "DECISAO_INICIO";
    static final String PDM = "PDM";
    static final String TITULO = "Pedido enviado para a PCGT";
    static final String TEXTO = "O pedido %s foi enviado para a PCGT. A partir deste momento será possível o "
            + "preenchimento de toda a documentação na plataforma.";

    private final ProcessoRepository processoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final AlertaProcessoService alertaProcessoService;

    public AvisoPedidoPcgtService(ProcessoRepository processoRepository,
                                  NotificacaoRepository notificacaoRepository,
                                  AlertaProcessoService alertaProcessoService) {
        this.processoRepository = processoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.alertaProcessoService = alertaProcessoService;
    }

    /** Transação própria: uma falha aqui não toca no processo nem na comunicação à PCGT já feita. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean avisarPedidoEnviado(UUID processoId) {
        Objects.requireNonNull(processoId, "processoId");
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null || !aplicavel(processo)) {
            return false;
        }
        if (notificacaoRepository.existsByProcesso_IdAndTitulo(processoId, TITULO)) {
            log.info("Aviso de pedido enviado para a PCGT já existe, não repetido — processoId={} numeroProcesso={}",
                    processoId, processo.getNumeroProcesso());
            return false;
        }
        alertaProcessoService.criarParaUtilizador(processo, processo.getCriadoPor(), TITULO,
                TEXTO.formatted(processo.getNumeroProcesso()));
        log.info("Aviso de pedido enviado para a PCGT criado — processoId={} numeroProcesso={}", processoId,
                processo.getNumeroProcesso());
        return true;
    }

    private static boolean aplicavel(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        return DECISAO_INICIO.equals(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo())
                && PDM.equals(dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo())
                && Boolean.TRUE.equals(dinamica.getComGeoconform());
    }
}
