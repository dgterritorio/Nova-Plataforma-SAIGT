package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InatividadeProcessoJob {

    private static final Logger log = LoggerFactory.getLogger(InatividadeProcessoJob.class);

    private final ProcessoRepository processoRepository;
    private final InatividadeProcessoService inatividadeProcessoService;
    private final Clock relogio;

    public InatividadeProcessoJob(ProcessoRepository processoRepository,
                                  InatividadeProcessoService inatividadeProcessoService, Clock relogio) {
        this.processoRepository = processoRepository;
        this.inatividadeProcessoService = inatividadeProcessoService;
        this.relogio = relogio;
    }

    public void executar() {
        Instant agora = relogio.instant();
        List<UUID> ids = processoRepository.findIdsEmSubmissaoSemAtualizacaoDesde(
                inatividadeProcessoService.limiteDaConsulta(agora));
        Map<DesfechoInatividade, Integer> contagem = new EnumMap<>(DesfechoInatividade.class);
        int falhas = 0;
        for (UUID processoId : ids) {
            try {
                contagem.merge(inatividadeProcessoService.avaliar(processoId, agora), 1, Integer::sum);
            } catch (ObjectOptimisticLockingFailureException e) {
                // Gravação concorrente do utilizador: não é falha, o processo é reavaliado na execução seguinte.
                log.warn("Processo alterado durante a verificação de inatividade, fica para a execução seguinte "
                        + "| processoId={}", processoId);
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha na verificação de inatividade, segue para o processo seguinte | processoId={}",
                        processoId, e);
            }
        }
        log.info("Verificação de inatividade concluída | job=inatividade-processos total={} primeirosAvisos={} "
                        + "segundosAvisos={} anulados={} falhas={}",
                ids.size(), contagem.getOrDefault(DesfechoInatividade.PRIMEIRO_AVISO, 0),
                contagem.getOrDefault(DesfechoInatividade.SEGUNDO_AVISO, 0),
                contagem.getOrDefault(DesfechoInatividade.ANULADO, 0), falhas);
    }
}
