package com.conkord.saigt.service.geoconform;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoVisibilidadeGeoconform;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Retira da lista de ativáveis do GeoConform, sem o apagar, o pedido de uma Dinâmica com GeoConform que caducou, e
 * repõe-no se a Dinâmica continuar. O processo no GeoConform é o da Decisão de início publicada, pelo IDUS corrente.
 * Os dados leem-se numa transação curta e a chamada corre fora dela; nada aqui altera a Dinâmica e uma falha fica só
 * no log, sem nova tentativa.
 */
@Slf4j
@Service
public class VisibilidadeGeoconformService {

    static final String MOTIVO_CADUCIDADE = "caducidade";
    private static final int NAO_ENCONTRADO = 404;

    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final ClienteGeoconform clienteGeoconform;
    private final TransactionTemplate transacaoLeitura;

    public VisibilidadeGeoconformService(DinamicaRepository dinamicaRepository, ProcessoRepository processoRepository,
                                         IdusProcessoRepository idusProcessoRepository,
                                         ClienteGeoconform clienteGeoconform,
                                         PlatformTransactionManager transactionManager) {
        this.dinamicaRepository = dinamicaRepository;
        this.processoRepository = processoRepository;
        this.idusProcessoRepository = idusProcessoRepository;
        this.clienteGeoconform = clienteGeoconform;
        // Chamado no afterCommit da caducidade: sem REQUIRES_NEW juntar-se-ia à transação já confirmada.
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transacaoLeitura.setReadOnly(true);
    }

    private record Alvo(UUID processoId, String idus) {
    }

    public void retirarPorCaducidade(UUID dinamicaId, LocalDate dataCaducidade) {
        Objects.requireNonNull(dinamicaId, "dinamicaId");
        alterar(dinamicaId, new PedidoVisibilidadeGeoconform(false, MOTIVO_CADUCIDADE, dataCaducidade), "retirar");
    }

    /**
     * Repõe o pedido na lista de ativáveis. O contrato não define o `motivo` nem a `data` da reposição: só vai
     * `visivel: true`.
     */
    public void repor(UUID dinamicaId) {
        Objects.requireNonNull(dinamicaId, "dinamicaId");
        alterar(dinamicaId, new PedidoVisibilidadeGeoconform(true, null, null), "repor");
    }

    private void alterar(UUID dinamicaId, PedidoVisibilidadeGeoconform pedido, String acao) {
        Optional<Alvo> encontrado = transacaoLeitura.execute(estado -> encontrar(dinamicaId, acao));
        if (encontrado == null || encontrado.isEmpty()) {
            return;
        }
        Alvo alvo = encontrado.get();
        ResultadoChamadaExterna<Void> resultado = clienteGeoconform.alterarVisibilidade(alvo.idus(), pedido,
                new ReferenciaChamada(alvo.processoId(), alvo.idus(), null));
        if (sucesso(resultado)) {
            log.info("Visibilidade do processo no GeoConform alterada — acao={} visivel={} dinamicaId={} idus={}", acao,
                    pedido.visivel(), dinamicaId, alvo.idus());
        } else if (resultado.falhaTecnica() == FalhaTecnicaExterna.ERRO_HTTP
                && Integer.valueOf(NAO_ENCONTRADO).equals(resultado.httpStatus())) {
            log.warn("Visibilidade do processo no GeoConform não alterada, processo desconhecido do GeoConform "
                    + "(nunca chegou a ser criado) — acao={} dinamicaId={} idus={}", acao, dinamicaId, alvo.idus());
        } else {
            log.error("Visibilidade do processo no GeoConform não alterada, sem nova tentativa — acao={} dinamicaId={} "
                            + "idus={} falha={} httpStatus={}", acao, dinamicaId, alvo.idus(), resultado.falhaTecnica(),
                    resultado.httpStatus());
        }
    }

    /** A única condição de negócio é `dinamica.com_geoconform`; nada depende do tipo de Instrumento. */
    private Optional<Alvo> encontrar(UUID dinamicaId, String acao) {
        Dinamica dinamica = dinamicaRepository.findById(dinamicaId).orElse(null);
        if (dinamica == null || !Boolean.TRUE.equals(dinamica.getComGeoconform())) {
            return Optional.empty();
        }
        Processo decisaoInicio = processoRepository
                .findFirstByDinamica_IdAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoAndEstadoOrderByOcorrenciaDesc(
                        dinamicaId, CriacaoProcessoGeoconformService.DECISAO_INICIO, EstadoProcesso.PUBLICADO)
                .orElse(null);
        if (decisaoInicio == null) {
            log.warn("Visibilidade do processo no GeoConform não alterada, Dinâmica sem Decisão de início publicada — "
                    + "acao={} dinamicaId={}", acao, dinamicaId);
            return Optional.empty();
        }
        String idus = idusProcessoRepository.findById(decisaoInicio.getId()).map(IdusProcesso::getIdus)
                .filter(valor -> !valor.isBlank()).orElse(null);
        if (idus == null) {
            log.error("Visibilidade do processo no GeoConform não alterada, Decisão de início sem IDUS — acao={} "
                    + "dinamicaId={} processoId={}", acao, dinamicaId, decisaoInicio.getId());
            return Optional.empty();
        }
        return Optional.of(new Alvo(decisaoInicio.getId(), idus));
    }

    private static boolean sucesso(ResultadoChamadaExterna<Void> resultado) {
        Integer status = resultado.httpStatus();
        return !resultado.temFalhaTecnica() && status != null && status >= 200 && status < 300;
    }
}
