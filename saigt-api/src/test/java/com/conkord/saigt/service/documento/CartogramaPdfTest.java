package com.conkord.saigt.service.documento;

import com.conkord.saigt.service.documento.CartogramaPdf.Planta;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartogramaPdfTest {

    private static final List<Planta> PLANTAS = List.of(
            new Planta("Planta de ordenamento – uso do solo", List.of("1199_Ord_Solo_A.tif", "1199_Ord_Solo_B.tiff")),
            new Planta("Planta de Condicionantes – RAN", List.of("1199_Cond_RAN_A.tif", "1199_Cond_RAN_B.tif")));

    static byte[] imagem(String formato) throws IOException {
        BufferedImage imagem = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, bytes);
        return bytes.toByteArray();
    }

    private static byte[] pdfDeUmaPagina() throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A5);
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.addRect(20, 20, 200, 100);
                conteudo.stroke();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            documento.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static List<PDXObject> objetosDaPrimeiraPagina(PDDocument pdf) throws IOException {
        PDPage pagina = pdf.getPage(0);
        List<PDXObject> objetos = new ArrayList<>();
        for (COSName nome : pagina.getResources().getXObjectNames()) {
            objetos.add(pagina.getResources().getXObject(nome));
        }
        return objetos;
    }

    @Test
    void deveTerAImagemDoCartogramaECadaPlantaComOsTiffDasFolhasSemExtensao() throws IOException {
        byte[] gerado = CartogramaPdf.gerar(imagem("png"), "png", PLANTAS);

        try (PDDocument pdf = Loader.loadPDF(gerado)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(objetosDaPrimeiraPagina(pdf)).hasSize(1).first().isInstanceOf(PDImageXObject.class);
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto.lines().map(String::strip).filter(linha -> !linha.isEmpty()).toList()).containsExactly(
                    "Planta de ordenamento – uso do solo", "1199_Ord_Solo_A", "1199_Ord_Solo_B",
                    "Planta de Condicionantes – RAN", "1199_Cond_RAN_A", "1199_Cond_RAN_B");
        }
    }

    @Test
    void deveAceitarOCartogramaEmJpg() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(CartogramaPdf.gerar(imagem("jpg"), "JPG", PLANTAS))) {
            assertThat(objetosDaPrimeiraPagina(pdf)).hasSize(1).first().isInstanceOf(PDImageXObject.class);
        }
    }

    @Test
    void comUmPdfSemPaginasDeveFalharComoFicheiroIlegivel() throws IOException {
        byte[] semPaginas;
        try (PDDocument documento = new PDDocument()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            documento.save(bytes);
            semPaginas = bytes.toByteArray();
        }

        assertThatThrownBy(() -> CartogramaPdf.gerar(semPaginas, "pdf", PLANTAS)).isInstanceOf(IOException.class);
    }

    @Test
    void comUmaImagemQueNaoEPngNemJpgDeveFalharComoFicheiroIlegivel() {
        byte[] texto = "não é uma imagem".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> CartogramaPdf.gerar(texto, "png", PLANTAS)).isInstanceOf(IOException.class);
    }

    @Test
    void comOCartogramaEmPdfDeveUsarAPrimeiraPagina() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(CartogramaPdf.gerar(pdfDeUmaPagina(), "pdf", PLANTAS))) {
            assertThat(objetosDaPrimeiraPagina(pdf)).hasSize(1).first().isInstanceOf(PDFormXObject.class);
            assertThat(new PDFTextStripper().getText(pdf)).contains("1199_Cond_RAN_B");
        }
    }

    @Test
    void comMuitasPlantasDevePassarParaOutraPaginaSemPartirUmaPlanta() throws IOException {
        List<Planta> muitas = IntStream.range(0, 40)
                .mapToObj(i -> new Planta("Planta " + i, List.of("folha_" + i + "_A.tif", "folha_" + i + "_B.tif")))
                .toList();

        try (PDDocument pdf = Loader.loadPDF(CartogramaPdf.gerar(imagem("png"), "png", muitas))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper leitor = new PDFTextStripper();
            for (int pagina = 1; pagina <= pdf.getNumberOfPages(); pagina++) {
                leitor.setStartPage(pagina);
                leitor.setEndPage(pagina);
                List<String> linhas = leitor.getText(pdf).lines().map(String::strip).filter(linha -> !linha.isEmpty()).toList();
                assertThat(linhas.size() % 3).as("página %d só com plantas inteiras", pagina).isZero();
            }
        }
    }

    @Test
    void deveTrocarPorInterrogacaoOQueAFonteNaoTem() throws IOException {
        byte[] gerado = CartogramaPdf.gerar(imagem("png"), "png", List.of(new Planta("Planta ✓ RAN", List.of("a.tif"))));

        try (PDDocument pdf = Loader.loadPDF(gerado)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Planta ? RAN");
        }
    }
}
