package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import org.springframework.stereotype.Component;

/**
 * Processos a caducar: os que não estão em estado terminal, nas Dinâmicas em curso com o primeiro aviso de
 * caducidade já enviado. Só a Dinâmica caduca; os processos dela são os que ficam por concluir.
 */
@Component
public class ACaducarCalculador implements CalculadorKpi {

    private final DinamicaRepository dinamicaRepository;

    public ACaducarCalculador(DinamicaRepository dinamicaRepository) {
        this.dinamicaRepository = dinamicaRepository;
    }

    @Override
    public KpiCodigo codigo() {
        return KpiCodigo.A_CADUCAR;
    }

    @Override
    public ValorKpi calcular() {
        return new ValorKpi(dinamicaRepository.contarProcessosACaducar(), null);
    }
}
