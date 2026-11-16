package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.exception.RedesDefesaInvalidasException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras do formulário `SGIFR`. A tradução programa → indicador do catálogo é a única coisa própria do SGIFR: as
 * redes aceites leem-se de `aplica_pra`/`aplica_psa`, nunca de códigos de rede escritos aqui.
 */
@Component
public class RegrasRedesDefesaSgifr {

    public static final String TEXTO_INTEGRAL = "TEXTO_INTEGRAL";
    private static final String PROGRAMA_PRA = "PRA";
    private static final String PROGRAMA_PSA = "PSA";
    private static final String CAMPO_TIPO_INFORMACAO = "tipoInformacao";
    private static final String MSG_TIPO_INFORMACAO = "O texto do ato deve ser integral.";

    private final SgifrRedeDefesaRepository sgifrRedeDefesaRepository;

    public RegrasRedesDefesaSgifr(SgifrRedeDefesaRepository sgifrRedeDefesaRepository) {
        this.sgifrRedeDefesaRepository = sgifrRedeDefesaRepository;
    }

    /** As redes que o programa do processo aceita, por `codigo`. */
    public List<SgifrRedeDefesa> aceites(Processo processo) {
        String programa = programa(processo);
        if (PROGRAMA_PRA.equals(programa)) {
            return sgifrRedeDefesaRepository.findByAplicaPraTrueOrderByCodigo();
        }
        if (PROGRAMA_PSA.equals(programa)) {
            return sgifrRedeDefesaRepository.findByAplicaPsaTrueOrderByCodigo();
        }
        return List.of();
    }

    public List<SgifrRedeDefesa> gravadas(Processo processo) {
        return processo.getRedesDefesa().stream().sorted(Comparator.comparing(SgifrRedeDefesa::getCodigo)).toList();
    }

    /** O texto do ato é sempre integral: um pedido com outro valor é recusado, e sem valor fica o integral. */
    public String tipoInformacao(String pedido) {
        if (pedido != null && !TEXTO_INTEGRAL.equals(pedido)) {
            throw new ValidacaoException(MSG_TIPO_INFORMACAO, List.of(new CampoErro(CAMPO_TIPO_INFORMACAO, MSG_TIPO_INFORMACAO)));
        }
        return TEXTO_INTEGRAL;
    }

    /**
     * No PRA as redes são sempre as aceites; no PSA são as do pedido (omitido = não mexe, vazio = nenhuma).
     * Uma rede fora das aceites recusa o pedido em qualquer dos programas, antes de gravar.
     */
    public void gravar(Processo processo, List<String> pedidas) {
        List<SgifrRedeDefesa> aceites = aceites(processo);
        if (pedidas != null) {
            verificarAceites(aceites, new HashSet<>(pedidas));
        }
        if (PROGRAMA_PRA.equals(programa(processo))) {
            substituir(processo, aceites);
        } else if (pedidas != null) {
            Set<String> codigos = new HashSet<>(pedidas);
            substituir(processo, aceites.stream().filter(rede -> codigos.contains(rede.getCodigo())).toList());
        }
    }

    /** Sobre o gravado, como validação de autoridade: o PRA sem redes fica com a que o programa aceita. */
    public void verificarNaSubmissao(Processo processo) {
        List<SgifrRedeDefesa> aceites = aceites(processo);
        if (PROGRAMA_PRA.equals(programa(processo))) {
            substituir(processo, aceites);
            return;
        }
        verificarAceites(aceites, processo.getRedesDefesa().stream().map(SgifrRedeDefesa::getCodigo).collect(Collectors.toSet()));
    }

    private static void verificarAceites(List<SgifrRedeDefesa> aceites, Set<String> codigos) {
        Set<String> permitidos = aceites.stream().map(SgifrRedeDefesa::getCodigo).collect(Collectors.toSet());
        if (!permitidos.containsAll(codigos)) {
            throw new RedesDefesaInvalidasException();
        }
    }

    private static void substituir(Processo processo, List<SgifrRedeDefesa> redes) {
        processo.getRedesDefesa().clear();
        processo.getRedesDefesa().addAll(redes);
    }

    private static String programa(Processo processo) {
        return processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo();
    }
}
