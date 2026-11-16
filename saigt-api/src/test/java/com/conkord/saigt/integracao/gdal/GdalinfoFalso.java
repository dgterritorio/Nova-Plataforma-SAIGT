package com.conkord.saigt.integracao.gdal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Executável falso do `gdalinfo`, corrido como processo filho pelos testes do executor. */
public final class GdalinfoFalso {

    private GdalinfoFalso() {
    }

    public static void main(String[] argumentos) throws Exception {
        switch (argumentos[0]) {
            case "dormir" -> Thread.sleep(120_000);
            case "nao-reconhecido" -> {
                System.err.print(recurso("/gdal/nao-reconhecido.stderr.txt"));
                System.exit(1);
            }
            case "truncado" -> {
                System.err.print(recurso("/gdal/tiff-truncado.stderr.txt"));
                System.exit(1);
            }
            case "falha" -> {
                System.err.print("ERROR 1: HTTP response code: 403");
                System.exit(1);
            }
            case "json" -> System.out.print(recurso("/gdal/rgb-300dpi.json"));
            case "lixo" -> System.out.print("isto não é JSON");
            default -> System.exit(2);
        }
    }

    private static String recurso(String caminho) throws Exception {
        try (InputStream stream = GdalinfoFalso.class.getResourceAsStream(caminho)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
