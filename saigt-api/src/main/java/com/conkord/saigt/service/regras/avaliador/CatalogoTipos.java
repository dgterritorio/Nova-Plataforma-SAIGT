package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.springframework.stereotype.Component;

/** Códigos referidos em {@code condicao.parametros}. Um tipo inativo continua a existir. */
@Component
public class CatalogoTipos {

    private final TipoDinamicaRepository tipoDinamicaRepository;
    private final TipoProcedimentoRepository tipoProcedimentoRepository;

    public CatalogoTipos(TipoDinamicaRepository tipoDinamicaRepository,
                         TipoProcedimentoRepository tipoProcedimentoRepository) {
        this.tipoDinamicaRepository = tipoDinamicaRepository;
        this.tipoProcedimentoRepository = tipoProcedimentoRepository;
    }

    public boolean existeTipoDinamica(String codigo) {
        return tipoDinamicaRepository.existsByCodigo(codigo);
    }

    public boolean existeTipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.existsByCodigo(codigo);
    }
}
