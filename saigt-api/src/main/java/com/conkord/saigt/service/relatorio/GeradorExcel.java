package com.conkord.saigt.service.relatorio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
class GeradorExcel implements GeradorExportacao {

    /** Em caracteres: uma designação longa não estica a coluna para lá disto. */
    private static final int LARGURA_MAXIMA = 60;

    @Override
    public FormatoExportacao formato() {
        return FormatoExportacao.EXCEL;
    }

    @Override
    public byte[] gerar(String titulo, List<String> cabecalho, List<List<String>> linhas) {
        try (XSSFWorkbook livro = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet folha = livro.createSheet(titulo);
            int[] larguras = new int[cabecalho.size()];
            escreverLinha(folha.createRow(0), cabecalho, estiloCabecalho(livro), larguras);
            for (int i = 0; i < linhas.size(); i++) {
                escreverLinha(folha.createRow(i + 1), linhas.get(i), null, larguras);
            }
            for (int coluna = 0; coluna < larguras.length; coluna++) {
                folha.setColumnWidth(coluna, (Math.min(larguras[coluna], LARGURA_MAXIMA) + 2) * 256);
            }
            folha.createFreezePane(0, 1);
            livro.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha a gerar o Excel da exportação.", e);
        }
    }

    private static void escreverLinha(Row linha, List<String> valores, CellStyle estilo, int[] larguras) {
        for (int coluna = 0; coluna < valores.size(); coluna++) {
            String valor = valores.get(coluna) == null ? "" : valores.get(coluna);
            Cell celula = linha.createCell(coluna);
            celula.setCellValue(valor);
            if (estilo != null) {
                celula.setCellStyle(estilo);
            }
            larguras[coluna] = Math.max(larguras[coluna], valor.length());
        }
    }

    private static CellStyle estiloCabecalho(XSSFWorkbook livro) {
        Font negrito = livro.createFont();
        negrito.setBold(true);
        CellStyle estilo = livro.createCellStyle();
        estilo.setFont(negrito);
        return estilo;
    }
}
