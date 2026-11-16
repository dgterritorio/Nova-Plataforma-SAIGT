package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.TamanhoExcedidoException;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

final class LimitesDocumento {

    private static final Logger log = LoggerFactory.getLogger(LimitesDocumento.class);
    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final Limite SEM_LIMITE = new Limite(Long.MAX_VALUE, null, 0);

    enum Motivo { FICHEIRO, TOTAL_ITEM, NUMERO_FICHEIROS, TOTAL_PROCESSO }

    record Ocupacao(long somaItem, long ficheirosItem, long somaProcesso) {
    }

    record Limite(long bytes, Motivo motivo, long configurado) {
    }

    private final UUID processoId;
    private final Long tamanhoMaximoFicheiro;
    private final Long tamanhoMaximoTotal;
    private final Integer numeroMaximoFicheiros;
    private final Long tamanhoMaximoProcesso;

    LimitesDocumento(UUID processoId, DocumentoConfig item, Long tamanhoMaximoProcesso) {
        this.processoId = processoId;
        this.tamanhoMaximoFicheiro = item == null ? null : item.getTamanhoMaximoFicheiro();
        this.tamanhoMaximoTotal = item == null ? null : item.getTamanhoMaximoTotal();
        this.numeroMaximoFicheiros = item == null ? null : item.getNumeroMaximoFicheiros();
        this.tamanhoMaximoProcesso = tamanhoMaximoProcesso;
    }

    Limite limiteDaLeitura(Ocupacao ocupacao) {
        recusarSeItemCheio(ocupacao);
        Limite limite = SEM_LIMITE;
        if (tamanhoMaximoFicheiro != null) {
            limite = menor(limite, new Limite(tamanhoMaximoFicheiro, Motivo.FICHEIRO, tamanhoMaximoFicheiro));
        }
        if (tamanhoMaximoTotal != null) {
            limite = menor(limite, new Limite(tamanhoMaximoTotal - ocupacao.somaItem(), Motivo.TOTAL_ITEM,
                    tamanhoMaximoTotal));
        }
        if (tamanhoMaximoProcesso != null) {
            limite = menor(limite, new Limite(tamanhoMaximoProcesso - ocupacao.somaProcesso(), Motivo.TOTAL_PROCESSO,
                    tamanhoMaximoProcesso));
        }
        if (limite.bytes() <= 0) {
            throw excedido(limite);
        }
        return limite;
    }

    /** Depois de gravar, com a ocupação relida sob bloqueio: apanha o que dois uploads simultâneos somam. */
    void verificarAposGravar(Ocupacao ocupacao, long tamanhoBytes) {
        recusarSeItemCheio(ocupacao);
        if (tamanhoMaximoFicheiro != null && tamanhoBytes > tamanhoMaximoFicheiro) {
            throw excedido(new Limite(tamanhoMaximoFicheiro, Motivo.FICHEIRO, tamanhoMaximoFicheiro));
        }
        if (tamanhoMaximoTotal != null && ocupacao.somaItem() + tamanhoBytes > tamanhoMaximoTotal) {
            throw excedido(new Limite(tamanhoMaximoTotal - ocupacao.somaItem(), Motivo.TOTAL_ITEM, tamanhoMaximoTotal));
        }
        if (tamanhoMaximoProcesso != null && ocupacao.somaProcesso() + tamanhoBytes > tamanhoMaximoProcesso) {
            throw excedido(new Limite(tamanhoMaximoProcesso - ocupacao.somaProcesso(), Motivo.TOTAL_PROCESSO,
                    tamanhoMaximoProcesso));
        }
    }

    TamanhoExcedidoException excedido(Limite limite) {
        log.warn("Upload recusado por limite de tamanho — processoId={} motivo={} limite={}", processoId,
                limite.motivo(), limite.configurado());
        return new TamanhoExcedidoException(erro(limite));
    }

    private void recusarSeItemCheio(Ocupacao ocupacao) {
        if (numeroMaximoFicheiros != null && ocupacao.ficheirosItem() >= numeroMaximoFicheiros) {
            throw excedido(new Limite(0, Motivo.NUMERO_FICHEIROS, numeroMaximoFicheiros));
        }
    }

    private static Limite menor(Limite atual, Limite candidato) {
        return candidato.bytes() < atual.bytes() ? candidato : atual;
    }

    static String erro(Limite limite) {
        return switch (limite.motivo()) {
            case FICHEIRO -> "Limite por ficheiro: " + formatar(limite.configurado()) + ".";
            case TOTAL_ITEM -> "Limite total do item: " + formatar(limite.configurado()) + ".";
            case NUMERO_FICHEIROS -> "O item já tem o número máximo de ficheiros (" + limite.configurado() + ").";
            case TOTAL_PROCESSO -> "Limite total do processo: " + formatar(limite.configurado()) + ".";
        };
    }

    static String formatar(long bytes) {
        DecimalFormat formato = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-PT")));
        formato.setRoundingMode(RoundingMode.DOWN);
        if (bytes >= GB) {
            return formato.format((double) bytes / GB) + " GB";
        }
        if (bytes >= MB) {
            return formato.format((double) bytes / MB) + " MB";
        }
        if (bytes >= KB) {
            return formato.format((double) bytes / KB) + " KB";
        }
        return bytes + " bytes";
    }
}
