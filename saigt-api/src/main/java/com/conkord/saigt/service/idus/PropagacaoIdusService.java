package com.conkord.saigt.service.idus;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Ordem: SNIT, PCGT, GeoConform. O IDUS da Dinâmica não vai à PCGT, que ainda não tem campo para ele. Corre fora da transação da entrada em vigor:
 * uma falha não reverte a atualização local nem impede os envios seguintes, e fica num `ERROR` para o reenvio manual.
 */
@Slf4j
@Service
public class PropagacaoIdusService {

    private final ClienteSnit clienteSnit;
    private final ComunicacaoPcgtService comunicacaoPcgtService;
    private final PropagacaoIdusGeoconformService propagacaoGeoconform;

    public PropagacaoIdusService(ClienteSnit clienteSnit, ComunicacaoPcgtService comunicacaoPcgtService,
                                 PropagacaoIdusGeoconformService propagacaoGeoconform) {
        this.clienteSnit = clienteSnit;
        this.comunicacaoPcgtService = comunicacaoPcgtService;
        this.propagacaoGeoconform = propagacaoGeoconform;
    }

    public void propagar(EntradaEmVigorRegistada registo) {
        Objects.requireNonNull(registo, "registo");
        if (!registo.nova()) {
            return;
        }
        propagarAoSnit(registo);
        for (UUID processoId : registo.processos()) {
            propagarAPcgt(registo, processoId);
        }
        propagarAoGeoconform(registo);
    }

    private void propagarAoGeoconform(EntradaEmVigorRegistada registo) {
        try {
            propagacaoGeoconform.propagar(registo);
        } catch (RuntimeException e) {
            log.error("Propagação do IDUS ao GeoConform falhou, sem nova tentativa | sistema=GEOCONFORM "
                    + "numeroDeposito={} processoId={} erro={}", registo.numeroDeposito(), registo.processoFinalId(),
                    e.getClass().getSimpleName());
        }
    }

    private void propagarAoSnit(EntradaEmVigorRegistada registo) {
        if (registo.idusDinamica() == null) {
            log.warn("IDUS da dinâmica não enviado ao SNIT, não foi gerado — numeroDeposito={} processoId={}",
                    registo.numeroDeposito(), registo.processoFinalId());
            return;
        }
        if (registo.ssaigtId() == null) {
            log.error("IDUS da dinâmica não enviado ao SNIT, o processo da publicação final não tem n.º numérico "
                    + "| sistema=SNIT numeroDeposito={} processoId={}", registo.numeroDeposito(),
                    registo.processoFinalId());
            return;
        }
        try {
            ResultadoChamadaExterna<DepositoRecebidoSnit> resultado = clienteSnit.atualizarIdusDeposito(
                    registo.ssaigtId(), registo.idusDinamica(),
                    new ReferenciaChamada(registo.processoFinalId(), registo.idusDinamica(), null));
            if (!resultado.temFalhaTecnica()) {
                log.info("IDUS da dinâmica enviado ao SNIT — numeroDeposito={} ssaigtId={} idusDinamica={} httpStatus={}",
                        registo.numeroDeposito(), registo.ssaigtId(), registo.idusDinamica(), resultado.httpStatus());
                return;
            }
            log.error("Propagação do IDUS ao SNIT falhou, sem nova tentativa | sistema=SNIT numeroDeposito={} "
                            + "ssaigtId={} idusDinamica={} httpStatus={} falha={}", registo.numeroDeposito(),
                    registo.ssaigtId(), registo.idusDinamica(), resultado.httpStatus(), resultado.falhaTecnica());
        } catch (RuntimeException e) {
            log.error("Propagação do IDUS ao SNIT falhou, sem nova tentativa | sistema=SNIT numeroDeposito={} "
                    + "ssaigtId={} idusDinamica={} erro={}", registo.numeroDeposito(), registo.ssaigtId(),
                    registo.idusDinamica(), e.getClass().getSimpleName());
        }
    }

    private void propagarAPcgt(EntradaEmVigorRegistada registo, UUID processoId) {
        try {
            comunicacaoPcgtService.comunicarPublicacao(processoId);
        } catch (RuntimeException e) {
            log.error("Propagação do IDUS à PCGT falhou, sem nova tentativa | sistema=PCGT numeroDeposito={} "
                    + "processoId={} erro={}", registo.numeroDeposito(), processoId, e.getClass().getSimpleName());
        }
    }
}
