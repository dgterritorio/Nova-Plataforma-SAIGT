package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.operacional.Instrumento;

/** Tipologia da servidão e a Classe e o Domínio a que pertence, lidos pelo `pai` de cada nível. */
public record ClassificacaoSrupInstrumento(Nivel tipologia, Nivel classe, Nivel dominio) {

    public record Nivel(Long id, String descricao) {

        static Nivel de(ListaValores valor) {
            return valor == null ? null : new Nivel(valor.getId(), valor.getDescricao());
        }
    }

    /** `null` quando o Instrumento não tem tipologia. */
    public static ClassificacaoSrupInstrumento de(Instrumento instrumento) {
        ListaValores tipologia = instrumento.getTipologiaSrup();
        if (tipologia == null) {
            return null;
        }
        ListaValores classe = tipologia.getPai();
        ListaValores dominio = classe == null ? null : classe.getPai();
        return new ClassificacaoSrupInstrumento(Nivel.de(tipologia), Nivel.de(classe), Nivel.de(dominio));
    }
}
