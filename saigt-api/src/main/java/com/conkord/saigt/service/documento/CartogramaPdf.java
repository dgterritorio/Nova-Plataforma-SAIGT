package com.conkord.saigt.service.documento;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDF do cartograma das folhas (EF v1.5 §3.1.2): a imagem do cartograma em cima e, por baixo, cada planta com os
 * TIFF das suas folhas, sem extensão. Sem estado e sem armazenamento, para se testar só com bytes.
 */
public final class CartogramaPdf {

    private static final float MARGEM = 50;
    private static final float ALTURA_MAXIMA_IMAGEM = 460;
    private static final float CORPO = 11;
    private static final float TITULO = 12;
    private static final float ENTRELINHA = 1.45f;

    private CartogramaPdf() {
    }

    public record Planta(String nome, List<String> tiffs) {
    }

    /** `extensao` é a do cartograma carregado: um PDF entra pela primeira página, o resto (JPG, PNG) como imagem. */
    public static byte[] gerar(byte[] cartograma, String extensao, List<Planta> plantas) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;
            float y = PDRectangle.A4.getHeight() - MARGEM;
            PDPageContentStream conteudo = new PDPageContentStream(documento, pagina);
            try {
                y = desenharCartograma(documento, conteudo, cartograma, extensao, largura, y);
                y -= 2 * CORPO;
                for (Planta planta : plantas) {
                    List<Linha> linhas = new ArrayList<>();
                    quebrar(planta.nome(), negrito, TITULO, largura).forEach(texto -> linhas.add(new Linha(texto, negrito, TITULO)));
                    planta.tiffs().forEach(tiff -> linhas.add(new Linha(codificavel(semExtensao(tiff), normal), normal, CORPO)));
                    float alturaBloco = linhas.stream().map(linha -> linha.tamanho() * ENTRELINHA).reduce(0f, Float::sum);
                    // Um bloco não se parte entre páginas, para o nome da planta ficar junto dos seus TIFF.
                    if (y - alturaBloco < MARGEM) {
                        conteudo.close();
                        pagina = new PDPage(PDRectangle.A4);
                        documento.addPage(pagina);
                        conteudo = new PDPageContentStream(documento, pagina);
                        y = PDRectangle.A4.getHeight() - MARGEM;
                    }
                    for (Linha linha : linhas) {
                        y -= linha.tamanho() * ENTRELINHA;
                        escrever(conteudo, linha, y);
                    }
                    y -= CORPO;
                }
            } finally {
                conteudo.close();
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private record Linha(String texto, PDType1Font fonte, float tamanho) {
    }

    private static float desenharCartograma(PDDocument documento, PDPageContentStream conteudo, byte[] cartograma,
                                            String extensao, float largura, float topo) throws IOException {
        if ("pdf".equals(extensao.toLowerCase(Locale.ROOT))) {
            try (PDDocument origem = Loader.loadPDF(cartograma)) {
                if (origem.getNumberOfPages() == 0) {
                    throw new IOException("O PDF do cartograma não tem páginas.");
                }
                PDFormXObject forma = new LayerUtility(documento).importPageAsForm(origem, 0);
                PDRectangle caixa = forma.getBBox();
                float escala = escala(caixa.getWidth(), caixa.getHeight(), largura);
                float altura = caixa.getHeight() * escala;
                conteudo.saveGraphicsState();
                conteudo.transform(Matrix.getTranslateInstance(MARGEM - caixa.getLowerLeftX() * escala,
                        topo - altura - caixa.getLowerLeftY() * escala));
                conteudo.transform(Matrix.getScaleInstance(escala, escala));
                conteudo.drawForm(forma);
                conteudo.restoreGraphicsState();
                return topo - altura;
            }
        }
        PDImageXObject imagem;
        try {
            imagem = PDImageXObject.createFromByteArray(documento, cartograma, "cartograma." + extensao);
        } catch (IllegalArgumentException e) {
            // O PDFBox recusa assim um formato de imagem que não reconhece; para o utilizador é um ficheiro ilegível.
            throw new IOException("Formato de imagem não reconhecido no cartograma.", e);
        }
        float escala = escala(imagem.getWidth(), imagem.getHeight(), largura);
        float altura = imagem.getHeight() * escala;
        conteudo.drawImage(imagem, MARGEM, topo - altura, imagem.getWidth() * escala, altura);
        return topo - altura;
    }

    private static float escala(float largura, float altura, float larguraDisponivel) {
        return Math.min(larguraDisponivel / largura, ALTURA_MAXIMA_IMAGEM / altura);
    }

    private static void escrever(PDPageContentStream conteudo, Linha linha, float y) throws IOException {
        conteudo.beginText();
        conteudo.setFont(linha.fonte(), linha.tamanho());
        conteudo.newLineAtOffset(MARGEM, y);
        conteudo.showText(linha.texto());
        conteudo.endText();
    }

    private static List<String> quebrar(String texto, PDType1Font fonte, float tamanho, float largura) throws IOException {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : codificavel(texto, fonte).split(" ")) {
            String tentativa = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (!atual.isEmpty() && fonte.getStringWidth(tentativa) / 1000 * tamanho > largura) {
                linhas.add(atual.toString());
                atual = new StringBuilder(palavra);
            } else {
                atual = new StringBuilder(tentativa);
            }
        }
        linhas.add(atual.toString());
        return linhas;
    }

    /** As fontes base do PDF só têm o Windows-1252: o resto (raro num nome de planta) passa a «?». */
    private static String codificavel(String texto, PDType1Font fonte) {
        StringBuilder resultado = new StringBuilder();
        texto.codePoints().forEach(c -> {
            String caracter = Character.toString(Character.isWhitespace(c) ? ' ' : c);
            try {
                fonte.encode(caracter);
                resultado.append(caracter);
            } catch (IOException | IllegalArgumentException e) {
                resultado.append('?');
            }
        });
        return resultado.toString();
    }

    private static String semExtensao(String tiff) {
        int ponto = tiff.lastIndexOf('.');
        return ponto > 0 ? tiff.substring(0, ponto) : tiff;
    }
}
