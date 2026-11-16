package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifica todos os dias a data de caducidade das Dinâmicas `EM_CURSO`. Sem transação à volta do ciclo: cada
 * Dinâmica tem a sua, e uma que falha fica como estava e volta a ser avaliada na execução seguinte.
 */
@Component
public class CaducidadeDinamicaJob {

    private static final Logger log = LoggerFactory.getLogger(CaducidadeDinamicaJob.class);
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");

    private final DinamicaRepository dinamicaRepository;
    private final CaducidadeDinamicaService caducidadeDinamicaService;

    public CaducidadeDinamicaJob(DinamicaRepository dinamicaRepository,
                                 CaducidadeDinamicaService caducidadeDinamicaService) {
        this.dinamicaRepository = dinamicaRepository;
        this.caducidadeDinamicaService = caducidadeDinamicaService;
    }

    public void executar() {
        LocalDate hoje = LocalDate.now(ZONE_LISBOA);
        // Margem de 3 dias: 31/03 menos um mês dá 28/02, por isso o limiar UM_MES chega até 3 dias depois de hoje + 1 mês.
        List<UUID> ids = dinamicaRepository.findIdsParaVerificacaoCaducidade(hoje.plusMonths(1).plusDays(3));
        Map<LimiarCaducidade, Integer> contagem = new EnumMap<>(LimiarCaducidade.class);
        int falhas = 0;
        for (UUID dinamicaId : ids) {
            try {
                caducidadeDinamicaService.avaliar(dinamicaId, hoje).ifPresent(limiar -> contagem.merge(limiar, 1, Integer::sum));
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha na verificação de caducidade, segue para a Dinâmica seguinte | dinamicaId={}", dinamicaId, e);
            }
        }
        log.info("Verificação de caducidade concluída | job=caducidade-dinamicas total={} umMes={} quinzeDias={} caducadas={} falhas={}",
                ids.size(), contagem.getOrDefault(LimiarCaducidade.UM_MES, 0),
                contagem.getOrDefault(LimiarCaducidade.QUINZE_DIAS, 0),
                contagem.getOrDefault(LimiarCaducidade.DATA, 0), falhas);
    }
}
