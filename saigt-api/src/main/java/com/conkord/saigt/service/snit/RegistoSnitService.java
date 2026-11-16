package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.PublicacaoRecebidaSnit;
import com.conkord.saigt.integracao.snit.PublicacaoSnit;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Regista no SNIT a publicação, o depósito e os ZIP das peças gráficas do procedimento final publicado, por esta
 * ordem (BL-INT-004). Cada passo só corre com o anterior aceite; sem nova tentativa automática (BL-INT-001). Não deve
 * correr dentro de uma transação: os pedidos montam-se numa transação curta de leitura e as chamadas correm fora dela.
 */
@Slf4j
@Service
public class RegistoSnitService {

    private static final String ORIGEM_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String MDC_CORRELATION_ID = "apiTraceId";

    private final ProcessoRepository processoRepository;
    private final NumeroDepositoService numeroDepositoService;
    private final MontadorPedidoSnit montador;
    private final ClienteSnit clienteSnit;
    private final ManifestoDepositoSnitService manifestoDepositoSnitService;
    private final IdusProcessoRepository idusProcessoRepository;
    private final TransactionTemplate transacaoLeitura;
    private final Clock relogio;

    public RegistoSnitService(ProcessoRepository processoRepository, NumeroDepositoService numeroDepositoService,
                              MontadorPedidoSnit montador, ClienteSnit clienteSnit,
                              ManifestoDepositoSnitService manifestoDepositoSnitService,
                              IdusProcessoRepository idusProcessoRepository,
                              PlatformTransactionManager transactionManager, Clock relogio) {
        this.processoRepository = processoRepository;
        this.numeroDepositoService = numeroDepositoService;
        this.montador = montador;
        this.clienteSnit = clienteSnit;
        this.manifestoDepositoSnitService = manifestoDepositoSnitService;
        this.idusProcessoRepository = idusProcessoRepository;
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
        this.relogio = relogio;
    }

    public void registarProcedimentoFinal(UUID processoId, ReferenciaChamada referencia) {
        Objects.requireNonNull(processoId, "processoId");
        Objects.requireNonNull(referencia, "referencia");
        Instant depositadoEm = relogio.instant();
        Optional<PedidosSnit> preparados = transacaoLeitura.execute(estado -> preparar(processoId, depositadoEm));
        if (preparados == null || preparados.isEmpty()) {
            return;
        }
        PedidosSnit pedidos = preparados.get();
        ReferenciaChamada referenciaComIdus = referencia.idus() != null || pedidos.idus() == null ? referencia
                : new ReferenciaChamada(referencia.processoId(), pedidos.idus(), referencia.actIdentifier());

        ResultadoChamadaExterna<PublicacaoRecebidaSnit> publicacao =
                clienteSnit.registarPublicacao(pedidos.publicacao(), referenciaComIdus);
        if (publicacao.temFalhaTecnica()) {
            avisarFalha("registarPublicacao", processoId, pedidos.numeroProcesso(), publicacao);
            return;
        }
        log.info("Publicação registada no SNIT — processoId={} numeroProcesso={} httpStatus={}", processoId,
                pedidos.numeroProcesso(), publicacao.httpStatus());
        if (pedidos.deposito() == null) {
            return;
        }

        ComunicacaoDepositoSnit comunicacao = manifestoDepositoSnitService.comunicarDeposito(processoId,
                pedidos.deposito(), pedidos.ficheiros(), referenciaComIdus);
        if (comunicacao.deposito().temFalhaTecnica()) {
            avisarFalha("registarDeposito", processoId, pedidos.numeroProcesso(), comunicacao.deposito());
            return;
        }
        log.info("Depósito registado no SNIT — processoId={} numeroProcesso={} httpStatus={} manifestoId={}",
                processoId, pedidos.numeroProcesso(), comunicacao.deposito().httpStatus(), comunicacao.manifestoId());
        if (comunicacao.ficheiros() == null) {
            return;
        }
        if (comunicacao.ficheiros().temFalhaTecnica()) {
            avisarFalha("registarFicheirosDeposito", processoId, pedidos.numeroProcesso(), comunicacao.ficheiros());
            return;
        }
        log.info("Ficheiros do depósito registados no SNIT — processoId={} numeroProcesso={} httpStatus={} ficheiros={}",
                processoId, pedidos.numeroProcesso(), comunicacao.ficheiros().httpStatus(), pedidos.ficheiros().size());
    }

    private Optional<PedidosSnit> preparar(UUID processoId, Instant depositadoEm) {
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null || processo.getEstado() != EstadoProcesso.PUBLICADO
                || ORIGEM_REGISTO_EXTERNO.equals(processo.getOrigemDados())
                || !numeroDepositoService.doProcedimentoFinal(processo)) {
            return Optional.empty();
        }
        Optional<PublicacaoSnit> publicacao = montador.montarPublicacao(processo);
        if (publicacao.isEmpty()) {
            return Optional.empty();
        }
        DepositoSnit deposito = montador.montarDeposito(processo, depositadoEm).orElse(null);
        List<FicheiroDepositoSnit> ficheiros = deposito == null ? List.of() : montador.montarFicheiros(processo);
        String idus = idusProcessoRepository.findById(processoId).map(IdusProcesso::getIdus).orElse(null);
        return Optional.of(new PedidosSnit(processo.getNumeroProcesso(), idus, publicacao.get(), deposito, ficheiros));
    }

    private static void avisarFalha(String passo, UUID processoId, String numeroProcesso,
                                    ResultadoChamadaExterna<?> resultado) {
        log.warn("Registo no SNIT falhou, sem nova tentativa | sistema=SNIT passo={} processoId={} numeroProcesso={} "
                        + "httpStatus={} falha={} correlationId={}", passo, processoId, numeroProcesso,
                resultado.httpStatus(), resultado.falhaTecnica(), MDC.get(MDC_CORRELATION_ID));
    }

    /** `deposito` é nulo quando falta um dado do depósito: a publicação regista-se sem ele. */
    private record PedidosSnit(String numeroProcesso, String idus, PublicacaoSnit publicacao, DepositoSnit deposito,
                               List<FicheiroDepositoSnit> ficheiros) {
    }
}
