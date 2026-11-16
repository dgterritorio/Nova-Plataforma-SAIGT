package com.conkord.saigt.persistence.configuracao;

/** Compressões do GeoTIFF aceites, sempre sem perdas; as restantes são recusadas até a DGT dizer o contrário. */
public enum CompressaoTiff {
    NONE,
    LZW
}
