package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.DinamicaAssociavelResponse;
import com.conkord.saigt.exception.DinamicaAssociadaInvalidaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaAssociavel;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Associação de um processo à Dinâmica de um IGT em curso no mesmo município (BL-PROC-062). Os tipos vêm de
 * `tipo_instrumento_associavel` e as Dinâmicas que a admitem de `admite_associacao_igt`: nenhum código no Java.
 * Nunca bloqueia o envio do IGT associado à INCM.
 */
@Service
public class DinamicaAssociadaService {

    static final String MSG_SEM_ASSOCIACAO = "Este processo não admite associação a uma dinâmica.";
    static final String MSG_SEM_PERGUNTA_PMOT = "Este processo não pergunta se corre em simultâneo com a formação de PMOT.";
    private static final String RESPOSTA_SIM = "SIM";

    private static final Logger log = LoggerFactory.getLogger(DinamicaAssociadaService.class);

    private final DinamicaRepository dinamicaRepository;

    public DinamicaAssociadaService(DinamicaRepository dinamicaRepository) {
        this.dinamicaRepository = dinamicaRepository;
    }

    public boolean aceitaAssociacao(Processo processo) {
        InstrumentoDinamicaConfig config = processo.getDinamica().getInstrumentoDinamicaConfig();
        return config.isAdmiteAssociacaoIgt() && !config.getTipoInstrumento().getTiposAssociaveis().isEmpty();
    }

    /** Vazia numa Dinâmica sem Instrumento de início: sem concelhos, não há município comum. */
    public Page<DinamicaAssociavel> listar(Processo processo, Pageable pagina) {
        if (!aceitaAssociacao(processo)) {
            throw new EstadoInvalidoException(MSG_SEM_ASSOCIACAO);
        }
        Page<DinamicaAssociavel> resultado = processo.getDinamica().instrumentoDeInicio()
                .map(instrumento -> dinamicaRepository.listarAssociaveis(instrumento.getId(), tiposAssociaveis(processo),
                        pagina))
                .orElseGet(() -> Page.empty(pagina));
        log.debug("Dinâmicas associáveis — processoId={}, resultados={}", processo.getId(), resultado.getTotalElements());
        return resultado;
    }

    /** Nulo passa: a associação é opcional. */
    public void verificar(Processo processo, UUID dinamicaId) {
        if (dinamicaId == null) {
            return;
        }
        boolean disponivel = aceitaAssociacao(processo)
                && !dinamicaId.equals(processo.getDinamica().getId())
                && processo.getDinamica().instrumentoDeInicio()
                        .map(instrumento -> dinamicaRepository.existeAssociavel(instrumento.getId(),
                                tiposAssociaveis(processo), dinamicaId))
                        .orElse(false);
        if (!disponivel) {
            log.warn("Dinâmica associada recusada — processoId={}, dinamicaId={}", processo.getId(), dinamicaId);
            throw new DinamicaAssociadaInvalidaException();
        }
    }

    /**
     * A pergunta só existe onde a associação é admitida, e a Dinâmica só se indica com a resposta `SIM`
     * (BL-PROC-062; EF v1.5 l.1599-1600). Corre depois de {@link #verificar} e antes de qualquer escrita.
     */
    public void verificarResposta(Processo processo, String simultaneoComPmot, UUID dinamicaId) {
        if (dinamicaId != null && !RESPOSTA_SIM.equals(simultaneoComPmot)) {
            log.warn("Dinâmica associada recusada sem a resposta SIM — processoId={}, dinamicaId={}", processo.getId(), dinamicaId);
            throw new DinamicaAssociadaInvalidaException();
        }
        if (simultaneoComPmot != null && !aceitaAssociacao(processo)) {
            throw new ValidacaoException(MSG_SEM_PERGUNTA_PMOT,
                    List.of(new ValidacaoException.CampoErro(CamposFormularioInicial.SIMULTANEO_COM_PMOT, MSG_SEM_PERGUNTA_PMOT)));
        }
    }

    /**
     * A associação gravada, como na lista; nula sem associação. Fica como foi gravada mesmo que a Dinâmica tenha
     * entretanto terminado: só a submissão a volta a verificar.
     */
    public DinamicaAssociavelResponse associada(Processo processo) {
        Dinamica dinamica = processo.getDinamicaAssociada();
        if (dinamica == null) {
            return null;
        }
        Set<Long> tipos = Set.copyOf(tiposAssociaveis(processo));
        Instrumento instrumento = dinamica.getInstrumentos().stream()
                .filter(abrangido -> tipos.contains(abrangido.getTipoInstrumento().getId()))
                .min(Comparator.comparing(Instrumento::getDesignacao).thenComparing(Instrumento::getId))
                .orElse(null);
        return DinamicaAssociavelResponse.de(dinamica, instrumento);
    }

    private static List<Long> tiposAssociaveis(Processo processo) {
        return processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getTiposAssociaveis().stream()
                .map(TipoInstrumento::getId).toList();
    }
}
