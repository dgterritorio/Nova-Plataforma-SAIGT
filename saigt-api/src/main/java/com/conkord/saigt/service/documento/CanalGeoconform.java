package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.Dinamica;

/** Canal de submissão gráfica da Dinâmica: com GeoConform, as peças gráficas vêm de lá e não se carregam. */
final class CanalGeoconform {

    private CanalGeoconform() {
    }

    /** Segue a Dinâmica, nunca o tipo de Instrumento; nulo vale falso. */
    static boolean obtidoDoGeoconform(Dinamica dinamica, DocumentoConfig item) {
        return item != null && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && Boolean.TRUE.equals(dinamica.getComGeoconform());
    }
}
