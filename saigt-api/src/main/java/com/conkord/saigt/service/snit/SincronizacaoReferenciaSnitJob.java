package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sincroniza as tabelas de referência do SNIT uma a uma, cada uma na sua transação. Uma lista que falha (SNIT
 * indisponível, circuito aberto) fica com a réplica anterior e as seguintes sincronizam na mesma.
 */
@Component
public class SincronizacaoReferenciaSnitJob {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoReferenciaSnitJob.class);

    private final SincronizacaoReferenciaSnitService service;

    public SincronizacaoReferenciaSnitJob(SincronizacaoReferenciaSnitService service) {
        this.service = service;
    }

    public void executar() {
        int falhas = 0;
        boolean concelhosSincronizados = false;
        for (ListaReferenciaSnit lista : ListaReferenciaSnit.values()) {
            try {
                service.sincronizar(lista);
                concelhosSincronizados |= lista == ListaReferenciaSnit.MUNICIPALITIES;
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha na sincronização da réplica do SNIT, a lista fica como estava | lista={}", lista, e);
            }
        }
        if (concelhosSincronizados) {
            try {
                service.avisarDicoSemConcelho();
            } catch (RuntimeException e) {
                log.error("Falha na verificação dos DICO das entidades contra a réplica do SNIT", e);
            }
        }
        log.info("Sincronização da réplica do SNIT concluída | job=sincronizacao-referencia-snit listas={} falhas={}",
                ListaReferenciaSnit.values().length, falhas);
    }
}
