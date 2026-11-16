package com.conkord.saigt.service.documento;

import com.conkord.saigt.service.documento.LeitorPlantas.Linha;
import com.conkord.saigt.service.documento.LeitorPlantas.Txt;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LeitorPlantasTest {

    private static Txt ler(String texto) throws IOException {
        return LeitorPlantas.lerTxt(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deveLerAsLinhasNoFormatoTipoNomeFolha() throws IOException {
        Txt txt = ler("Ordenamento; Planta de Ordenamento – Uso do Solo; A\r\nCondicionantes;Planta de Condicionantes – RAN;B\n");

        assertThat(txt.erros()).isEmpty();
        assertThat(txt.linhas()).containsExactly(
                new Linha(0, "Ordenamento", "Planta de Ordenamento – Uso do Solo", "A"),
                new Linha(1, "Condicionantes", "Planta de Condicionantes – RAN", "B"));
    }

    @Test
    void deveAceitarAFolhaNoFimDoNomeComoNoExemploDoFigma() throws IOException {
        Txt txt = ler("Ordenamento; Planta de Ordenamento – Uso do Solo - Folha A");

        assertThat(txt.linhas()).containsExactly(new Linha(0, "Ordenamento", "Planta de Ordenamento – Uso do Solo", "A"));
    }

    @Test
    void deveAceitarAFolhaEmBrancoEIgnorarLinhasVaziasEOBom() throws IOException {
        Txt txt = ler("﻿Ordenamento; Planta de Ordenamento;\n\n   \nCondicionantes; Planta de Condicionantes; 1");

        assertThat(txt.linhas()).containsExactly(
                new Linha(0, "Ordenamento", "Planta de Ordenamento", null),
                new Linha(1, "Condicionantes", "Planta de Condicionantes", "1"));
    }

    @Test
    void deveDizerQueLinhasNaoSeLeramComONumeroDaLinha() throws IOException {
        Txt txt = ler("Ordenamento; Planta; A\nsem separador\n;Sem tipo;A");

        assertThat(txt.linhas()).hasSize(1);
        assertThat(txt.erros()).containsExactly("Linha 2: use o formato «Tipo; Nome; Folha».",
                "Linha 3: use o formato «Tipo; Nome; Folha».");
    }

    @Test
    void deveLerUmTxtGravadoEmWindows1252() throws IOException {
        byte[] bytes = "Ordenamento; Planta de Ordenação; A".getBytes(Charset.forName("windows-1252"));

        Txt txt = LeitorPlantas.lerTxt(new ByteArrayInputStream(bytes));

        assertThat(txt.linhas()).containsExactly(new Linha(0, "Ordenamento", "Planta de Ordenação", "A"));
    }

    @Test
    void deveListarSoOsTiffDoZipSemPastasEPorOrdem() throws IOException {
        byte[] zip = zip("plantas/1199_Ord_B.TIF", "1199_Ord_A.tif", "1199_Ord_A.tfw", "leia-me.txt", "pasta/");

        assertThat(LeitorPlantas.tiffsDoZip(new ByteArrayInputStream(zip))).containsExactly("1199_Ord_A.tif", "1199_Ord_B.TIF");
    }

    @Test
    void deveExigirOCartogramaSoQuandoUmaPlantaTemMaisDeUmaFolha() {
        assertThat(PlantasService.exigeCartograma(List.of(new Linha(0, "Ordenamento", "Uso do Solo", "A"),
                new Linha(1, "Condicionantes", "RAN", "A")))).isFalse();
        assertThat(PlantasService.exigeCartograma(List.of(new Linha(0, "Ordenamento", "Uso do Solo", "A"),
                new Linha(1, "Ordenamento", "Uso do Solo", "B")))).isTrue();
    }

    @Test
    void deveCompararTiposSemAcentosNemMaiusculas() {
        assertThat(LeitorPlantas.normalizar(" Condicionantes ")).isEqualTo(LeitorPlantas.normalizar("condicionantes"));
        assertThat(LeitorPlantas.normalizar("Ordenação")).isEqualTo("ordenacao");
    }

    static byte[] zip(String... nomes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String nome : nomes) {
                zip.putNextEntry(new ZipEntry(nome));
                if (!nome.endsWith("/")) {
                    zip.write(new byte[] {1, 2, 3});
                }
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
