package com.conkord.saigt.persistence.configuracao;

/** Se a submissão do Procedimento pergunta pela utilização do GeoConform. */
public enum PerguntaGeoconform {
    NAO_APLICAVEL,
    OBRIGATORIA,
    /** Só quando os elementos afetados do Processo incluem as Peças Gráficas. */
    SE_PECAS_GRAFICAS
}
