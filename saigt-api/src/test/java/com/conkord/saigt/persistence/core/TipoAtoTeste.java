package com.conkord.saigt.persistence.core;

/**
 * O tipo de ato que os cenários gravam no processo antes de o submeter: o processo nasce sem ele e a submissão exige-o.
 * Um só por base (H2 partilhada ou PostgreSQL de teste), reutilizado. Os tokens de teste sem `atos_autorizados` não o
 * verificam na Camada 3c; o `ActTypeId` da 2.ª série só existe pelo CHECK de `tipo_ato` (uma das séries obrigatória).
 */
public final class TipoAtoTeste {

    private static final String CODIGO = "ATO-FIXSUB";
    private static final long ACT_TYPE_SERIE_2 = 9_368_999L;

    private TipoAtoTeste() {
    }

    public static TipoAto paraSubmeter(TipoAtoRepository repositorio) {
        return repositorio.findAll().stream().filter(tipo -> CODIGO.equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> {
                    TipoAto tipoAto = new TipoAto(CODIGO, "Ato para submeter", 1);
                    tipoAto.setIncmActTypeIdSerie2(ACT_TYPE_SERIE_2);
                    return repositorio.saveAndFlush(tipoAto);
                });
    }
}
