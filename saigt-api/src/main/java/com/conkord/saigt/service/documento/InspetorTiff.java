package com.conkord.saigt.service.documento;

/**
 * Lê as características de um GeoTIFF dentro de um ZIP já gravado, sem o descarregar. Ficheiro que não é TIFF lança
 * {@link TiffIlegivelException}; qualquer falha técnica, {@link InspecaoTiffIndisponivelException}.
 */
public interface InspetorTiff {

    CaracteristicasTiff inspecionar(String chaveObjeto, String entradaZip);
}
