package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import com.conkord.saigt.service.snit.RegistoSnitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta à INCM todos os Processos enviados e ainda sem resultado. Sem transação à volta do ciclo: cada
 * consulta tem a sua, e um Processo que falha fica no mesmo estado e volta a ser consultado na execução seguinte. O
 * registo no SNIT e a comunicação à PCGT de um Processo publicado correm depois do commit da consulta, cada um à
 * parte; uma falha num deles não muda o Processo nem impede o outro.
 */
@Component
public class ConsultaIncmJob {

    private static final Logger log = LoggerFactory.getLogger(ConsultaIncmJob.class);

    private final ProcessoRepository processoRepository;
    private final ConsultaIncmService consultaIncmService;
    private final RegistoSnitService registoSnitService;
    private final ComunicacaoPcgtService comunicacaoPcgtService;

    public ConsultaIncmJob(ProcessoRepository processoRepository, ConsultaIncmService consultaIncmService,
                           RegistoSnitService registoSnitService, ComunicacaoPcgtService comunicacaoPcgtService) {
        this.processoRepository = processoRepository;
        this.consultaIncmService = consultaIncmService;
        this.registoSnitService = registoSnitService;
        this.comunicacaoPcgtService = comunicacaoPcgtService;
    }

    public void executar() {
        List<UUID> ids = processoRepository.findIdsParaConsultaIncm();
        Map<DesfechoConsultaIncm, Integer> contagem = new EnumMap<>(DesfechoConsultaIncm.class);
        int excecoes = 0;
        for (UUID processoId : ids) {
            DesfechoConsultaIncm desfecho;
            try {
                desfecho = consultaIncmService.consultar(processoId);
            } catch (RuntimeException e) {
                excecoes++;
                log.error("Falha na consulta à INCM, segue para o processo seguinte | processoId={}", processoId, e);
                continue;
            }
            contagem.merge(desfecho, 1, Integer::sum);
            if (desfecho == DesfechoConsultaIncm.PUBLICADO) {
                registarNoSnit(processoId);
                comunicarAPcgt(processoId);
            }
        }
        // FALHA_TECNICA conta como falha: tal como a exceção, não se sabe o resultado e repete-se na execução seguinte.
        log.info("Consulta à INCM concluída | job=consulta-incm total={} publicados={} anulados={} semAlteracao={} falhas={}",
                ids.size(), contagem.getOrDefault(DesfechoConsultaIncm.PUBLICADO, 0),
                contagem.getOrDefault(DesfechoConsultaIncm.ANULADO_INCM, 0),
                contagem.getOrDefault(DesfechoConsultaIncm.SEM_ALTERACAO, 0),
                contagem.getOrDefault(DesfechoConsultaIncm.FALHA_TECNICA, 0) + excecoes);
    }

    private void registarNoSnit(UUID processoId) {
        try {
            registoSnitService.registarProcedimentoFinal(processoId, new ReferenciaChamada(processoId, null, null));
        } catch (RuntimeException e) {
            log.error("Falha no registo no SNIT, o processo continua publicado | processoId={}", processoId, e);
        }
    }

    private void comunicarAPcgt(UUID processoId) {
        try {
            comunicacaoPcgtService.comunicarPublicacao(processoId);
        } catch (RuntimeException e) {
            log.error("Falha na comunicação à PCGT, o processo continua publicado | processoId={}", processoId, e);
        }
    }
}
