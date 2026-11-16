package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Limpeza noturna do storage pelas intenções registadas antes de cada gravação, cópia ou apagamento: nunca lista o
 * bucket. Só trata intenções com mais da idade mínima, para não apanhar operações ainda em curso. Uma intenção
 * fica para a execução seguinte só quando o storage falha.
 */
@Service
public class LimpezaArmazenamentoService {

    private static final Logger log = LoggerFactory.getLogger(LimpezaArmazenamentoService.class);
    private static final String METRICA = "documento.limpeza.total";

    private final ArmazenamentoIntencaoRepository intencaoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final NotificacaoDocumentoRepository notificacaoDocumentoRepository;
    private final ArmazenamentoDocumentos armazenamento;
    private final MeterRegistry metricas;
    private final Duration idadeMinima;

    public LimpezaArmazenamentoService(ArmazenamentoIntencaoRepository intencaoRepository,
                                       ProcessoDocumentoRepository processoDocumentoRepository,
                                       NotificacaoDocumentoRepository notificacaoDocumentoRepository,
                                       ArmazenamentoDocumentos armazenamento, MeterRegistry metricas,
                                       @Value("${app.documentos.limpeza.idade-minima}") Duration idadeMinima) {
        this.intencaoRepository = intencaoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.notificacaoDocumentoRepository = notificacaoDocumentoRepository;
        this.armazenamento = armazenamento;
        this.metricas = metricas;
        this.idadeMinima = idadeMinima;
    }

    /** `agora` é parâmetro para os testes controlarem a idade das intenções. */
    public Map<ResultadoLimpeza, Integer> limpar(Instant agora) {
        Instant limite = agora.minus(idadeMinima);
        Map<ResultadoLimpeza, Integer> contagem = new EnumMap<>(ResultadoLimpeza.class);
        long ultimoId = 0;
        List<ArmazenamentoIntencao> lote;
        do {
            lote = intencaoRepository.findTop500ByRegistadaEmBeforeAndIdGreaterThanOrderByIdAsc(limite, ultimoId);
            Set<String> comLinha = caminhosComLinha(lote);
            for (ArmazenamentoIntencao intencao : lote) {
                ResultadoLimpeza resultado = tratar(intencao, comLinha);
                contagem.merge(resultado, 1, Integer::sum);
                metricas.counter(METRICA, "resultado", resultado.name().toLowerCase()).increment();
                ultimoId = intencao.getId();
            }
        } while (!lote.isEmpty());
        log.info("Limpeza do storage terminada | resultados={}", contagem);
        return contagem;
    }

    private Set<String> caminhosComLinha(List<ArmazenamentoIntencao> lote) {
        Set<String> chaves = new HashSet<>();
        lote.stream().filter(intencao -> intencao.getOperacao() != OperacaoArmazenamento.APAGAMENTO)
                .forEach(intencao -> chaves.add(intencao.getChaveObjeto()));
        if (chaves.isEmpty()) {
            return Set.of();
        }
        Set<String> comLinha = new HashSet<>(processoDocumentoRepository.findCaminhosExistentes(chaves));
        comLinha.addAll(notificacaoDocumentoRepository.findCaminhosExistentes(chaves));
        return comLinha;
    }

    /**
     * `APAGAMENTO` apaga mesmo com linha, porque o pedido de apagamento é explícito (por exemplo, os ficheiros de um
     * processo anulado por inatividade).
     */
    private ResultadoLimpeza tratar(ArmazenamentoIntencao intencao, Set<String> comLinha) {
        String chave = intencao.getChaveObjeto();
        if (intencao.getOperacao() != OperacaoArmazenamento.APAGAMENTO && comLinha.contains(chave)) {
            descartar(intencao);
            return ResultadoLimpeza.DESCARTADA;
        }
        try {
            if (armazenamento.versoes(chave).stream().anyMatch(VersaoObjeto::retida)) {
                log.warn("Objeto com versão retida não apagado | intencaoId={} operacao={} chave={}",
                        intencao.getId(), intencao.getOperacao(), chave);
                descartar(intencao);
                return ResultadoLimpeza.RETIDA;
            }
            armazenamento.remover(chave);
        } catch (RuntimeException e) {
            log.warn("Objeto não apagado, a intenção fica para a execução seguinte | intencaoId={} operacao={}",
                    intencao.getId(), intencao.getOperacao(), e);
            return ResultadoLimpeza.FALHADA;
        }
        descartar(intencao);
        return ResultadoLimpeza.APAGADA;
    }

    private void descartar(ArmazenamentoIntencao intencao) {
        intencaoRepository.deleteById(intencao.getId());
    }

    public enum ResultadoLimpeza {
        DESCARTADA,
        APAGADA,
        RETIDA,
        FALHADA
    }
}
