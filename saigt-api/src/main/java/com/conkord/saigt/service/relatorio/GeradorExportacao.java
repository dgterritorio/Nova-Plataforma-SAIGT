package com.conkord.saigt.service.relatorio;

import java.util.List;

/** Gera o ficheiro de uma tabela já montada: o gerador não sabe o que são processos. */
interface GeradorExportacao {

    FormatoExportacao formato();

    byte[] gerar(String titulo, List<String> cabecalho, List<List<String>> linhas);
}
