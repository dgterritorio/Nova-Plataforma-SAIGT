package com.conkord.saigt.service.regras;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.regras.avaliador.Avaliador;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Só a álgebra de grupos/operador e o despacho; a lógica de negócio vive nos {@link Avaliador}es. */
@Service
public class MotorRegrasService {

    private static final String OPERADOR_OR = "OR";

    private final RegistoAvaliadores registoAvaliadores;
    private final CondicaoRepository condicaoRepository;
    private final RegraRepository regraRepository;

    public MotorRegrasService(RegistoAvaliadores registoAvaliadores, CondicaoRepository condicaoRepository,
                               RegraRepository regraRepository) {
        this.registoAvaliadores = registoAvaliadores;
        this.condicaoRepository = condicaoRepository;
        this.regraRepository = regraRepository;
    }

    /** Sem Regras configuradas passa (ex.: SRUP/ARU). */
    public boolean avaliarElegibilidadeDinamica(InstrumentoDinamicaConfig instrumentoDinamicaConfig, UUID instrumentoId) {
        return regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(instrumentoDinamicaConfig).stream()
                .allMatch(regra -> avaliar(regra, instrumentoId));
    }

    /** No máximo uma Regra ativa; sem Regra passa. */
    public boolean avaliarElegibilidadeProcedimento(InstrumentoProcedimentoConfig instrumentoProcedimentoConfig, UUID instrumentoId) {
        return regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(instrumentoProcedimentoConfig)
                .map(regra -> avaliar(regra, instrumentoId))
                .orElse(true);
    }

    /**
     * Sem Instrumento de partida (Dinâmica iniciada sem IGT): só contam as Regras cujas condições ativas não dependem
     * de um Instrumento; as outras avaliam-se quando os Instrumentos se associam.
     */
    public boolean avaliarElegibilidadeDinamicaSemInstrumento(InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        return regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(instrumentoDinamicaConfig).stream()
                .filter(this::naoDependeDeInstrumento)
                .allMatch(regra -> avaliar(regra, null));
    }

    /** Como {@link #avaliarElegibilidadeDinamicaSemInstrumento}, para a Regra do Procedimento. */
    public boolean avaliarElegibilidadeProcedimentoSemInstrumento(InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        return regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(instrumentoProcedimentoConfig)
                .filter(this::naoDependeDeInstrumento)
                .map(regra -> avaliar(regra, null))
                .orElse(true);
    }

    private boolean naoDependeDeInstrumento(Regra regra) {
        return condicaoRepository.findByRegraAndAtivoTrue(regra).stream()
                .noneMatch(condicao -> avaliadorDe(condicao).dependeDeInstrumento());
    }

    /**
     * AND dentro do grupo, {@code regra.operador} entre grupos. Grupos sem condições ativas não contam;
     * sem nenhum, a regra falha.
     */
    public boolean avaliar(Regra regra, UUID instrumentoId) {
        List<Condicao> condicoesAtivas = condicaoRepository.findByRegraAndAtivoTrue(regra);
        Map<Short, List<Condicao>> condicoesPorGrupo = condicoesAtivas.stream()
                .collect(Collectors.groupingBy(Condicao::getGrupo));
        if (condicoesPorGrupo.isEmpty()) {
            return false;
        }

        List<Boolean> resultadoPorGrupo = condicoesPorGrupo.values().stream()
                .map(condicoesDoGrupo -> condicoesDoGrupo.stream().allMatch(c -> avaliarCondicao(c, instrumentoId)))
                .toList();

        return OPERADOR_OR.equals(regra.getOperador())
                ? resultadoPorGrupo.stream().anyMatch(Boolean::booleanValue)
                : resultadoPorGrupo.stream().allMatch(Boolean::booleanValue);
    }

    private boolean avaliarCondicao(Condicao condicao, UUID instrumentoId) {
        return avaliadorDe(condicao).avaliar(instrumentoId, condicao.getParametros());
    }

    private Avaliador avaliadorDe(Condicao condicao) {
        return registoAvaliadores.porCodigo(condicao.getAvaliador())
                .orElseThrow(() -> new IllegalStateException("Avaliador sem implementação: " + condicao.getAvaliador()));
    }
}
