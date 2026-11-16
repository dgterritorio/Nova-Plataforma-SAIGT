package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ReferenciaInvalidaException;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Os atos que um ato pontual de «Outros atos e anexos» pode visar: os processos `PUBLICADO` da Dinâmica de Decisão de
 * início ou de Discussão pública (FR-135). Os de `REGISTO_EXTERNO` nascem `PUBLICADO` e contam (TR-152). A regra fica
 * só aqui: o `/disponivel` lista-os e a criação verifica-os.
 */
@Component
public class AtosReferiveis {

    /** Pelos códigos do catálogo `tipo_procedimento`, iguais em todos os tipos de Instrumento. */
    public static final Set<String> CODIGOS_PROCEDIMENTO_REFERIVEIS = Set.of("DECISAO_INICIO", "ABERTURA_DISCUSSAO_PUBLICA",
            "ABERTURA_DISCUSSAO_PUBLICA_RERAE", "ABERTURA_DISCUSSAO_PUBLICA_DL10");

    private final ProcessoRepository processoRepository;

    public AtosReferiveis(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    public List<Processo> listar(Dinamica dinamica) {
        return processoRepository.findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
                dinamica, EstadoProcesso.PUBLICADO, CODIGOS_PROCEDIMENTO_REFERIVEIS);
    }

    public Processo verificar(UUID processoReferidoId, Dinamica dinamica) {
        if (processoReferidoId == null) {
            throw new ReferenciaInvalidaException();
        }
        return processoRepository.findById(processoReferidoId)
                .filter(processo -> processo.getDinamica().getId().equals(dinamica.getId()))
                .filter(processo -> processo.getEstado() == EstadoProcesso.PUBLICADO)
                .filter(processo -> CODIGOS_PROCEDIMENTO_REFERIVEIS.contains(
                        processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo()))
                .orElseThrow(ReferenciaInvalidaException::new);
    }
}
