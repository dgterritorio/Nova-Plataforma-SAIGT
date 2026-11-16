package com.conkord.saigt.persistence.operacional;

/** Linha de {@link DinamicaRepository#listarAssociaveis}: a Dinâmica e o primeiro Instrumento associável que abrange. */
public interface DinamicaAssociavel {

    Dinamica getDinamica();

    Instrumento getInstrumento();
}
