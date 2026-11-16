package com.conkord.saigt.service.relatorio;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Tabela simples em A4 horizontal, com o cabeçalho repetido em cada página, até haver design da DGT. */
@Component
class GeradorPdf implements GeradorExportacao {

    private static final Color FUNDO_CABECALHO = new Color(230, 230, 230);

    @Override
    public FormatoExportacao formato() {
        return FormatoExportacao.PDF;
    }

    @Override
    public byte[] gerar(String titulo, List<String> cabecalho, List<List<String>> linhas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        try {
            PdfWriter.getInstance(documento, saida);
            documento.addTitle(titulo);
            documento.open();
            documento.add(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            documento.add(new Paragraph(" "));

            PdfPTable tabela = new PdfPTable(cabecalho.size());
            tabela.setWidthPercentage(100);
            if (!linhas.isEmpty()) {
                // Uma tabela só com linhas de cabeçalho repetido não é desenhada: o conjunto vazio perdia o cabeçalho.
                tabela.setHeaderRows(1);
            }
            Font fonteCabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            for (String tituloColuna : cabecalho) {
                PdfPCell celula = new PdfPCell(new Phrase(tituloColuna, fonteCabecalho));
                celula.setBackgroundColor(FUNDO_CABECALHO);
                tabela.addCell(celula);
            }
            Font fonte = FontFactory.getFont(FontFactory.HELVETICA, 8);
            for (List<String> linha : linhas) {
                for (String valor : linha) {
                    tabela.addCell(new PdfPCell(new Phrase(valor == null ? "" : valor, fonte)));
                }
            }
            documento.add(tabela);
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha a gerar o PDF da exportação.", e);
        } finally {
            documento.close();
        }
        return saida.toByteArray();
    }
}
