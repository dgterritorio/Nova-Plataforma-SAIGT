package com.conkord.saigt.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A matriz de tipos de entidade por configuração de Procedimento (FR-189 e EF v1.5), calculada pelos códigos,
 * independente do SQL do seed: a primeira regra que se aplica decide e a DGT tem sempre linha. A PCM fica de fora,
 * só pode existir nas configurações da 1.ª série. O PROT é só da CCDR (EF v1.5, «IGT Programas»); a matriz inicial
 * do changeset 2026-11-11-4 dava-o também à SEC_ESTADO, até o 2026-11-13-15 a desativar.
 */
final class MatrizPermissaoAcao {

    private static final Set<String> MUNICIPAIS_E_INTERMUNICIPAIS = Set.of("PDM", "PU", "PP", "PDIM", "PUIM", "PPIM");
    private static final Set<String> DINAMICAS_DO_GOVERNO = Set.of("SUSPENSAO_GOVERNO", "MEDIDAS_PREVENTIVAS_GOVERNO", "RATIFICACAO");
    private static final Set<String> SERVIDOES_COM_REGRA_PROPRIA = Set.of("REN", "PRA", "PSA");

    private static final String CONFIGURACOES = "SELECT c.id, ti.codigo, ti.categoria, td.codigo, tp.codigo, c.serie_dr, "
            + "coalesce((SELECT string_agg(pa.tipo_entidade, ',' ORDER BY pa.tipo_entidade) FROM permissao_acao pa "
            + "WHERE pa.instrumento_procedimento_config_id = c.id AND pa.ativo AND pa.tipo_entidade <> 'PCM'), ''), "
            + "(SELECT count(*) FROM permissao_acao pa WHERE pa.instrumento_procedimento_config_id = c.id "
            + "AND pa.tipo_entidade = 'PCM') "
            + "FROM instrumento_procedimento_config c "
            + "JOIN instrumento_dinamica_config d ON d.id = c.instrumento_dinamica_config_id "
            + "JOIN tipo_instrumento ti ON ti.id = d.tipo_instrumento_id "
            + "JOIN tipo_dinamica td ON td.id = d.tipo_dinamica_id "
            + "JOIN tipo_procedimento tp ON tp.id = c.tipo_procedimento_id ORDER BY c.id";

    private MatrizPermissaoAcao() {
    }

    static Set<String> tiposEsperados(String tipoInstrumento, String categoria, String tipoDinamica, String tipoProcedimento) {
        return tiposEsperados(tipoInstrumento, categoria, tipoDinamica, tipoProcedimento, false);
    }

    private static Set<String> tiposEsperados(String tipoInstrumento, String categoria, String tipoDinamica,
                                              String tipoProcedimento, boolean matrizInicial) {
        Set<String> tipos = new TreeSet<>(regra(tipoInstrumento, categoria, tipoDinamica, tipoProcedimento, matrizInicial));
        tipos.add("DGT");
        return tipos;
    }

    private static List<String> regra(String tipoInstrumento, String categoria, String tipoDinamica, String tipoProcedimento,
                                      boolean matrizInicial) {
        if ("CONSTITUICAO_CC".equals(tipoProcedimento) || "SUSPENSAO_CCDR".equals(tipoDinamica)) {
            return List.of("CCDR_DR");
        }
        if (DINAMICAS_DO_GOVERNO.contains(tipoDinamica)) {
            return List.of("SEC_ESTADO");
        }
        if (MUNICIPAIS_E_INTERMUNICIPAIS.contains(tipoInstrumento)) {
            return List.of("CM");
        }
        return switch (tipoInstrumento) {
            case "PNPOT" -> List.of("SEC_ESTADO");
            case "PROT" -> matrizInicial ? List.of("SEC_ESTADO", "CCDR_DR") : List.of("CCDR_DR");
            case "PS" -> List.of("SEC_ESTADO", "ICNF", "APA");
            case "PEOT" -> List.of("SEC_ESTADO", "ICNF", "APA", "DGPC");
            case "PIMOT" -> List.of("CIM");
            case "PRA", "REN" -> List.of("CCDR_DR");
            case "PSA" -> List.of("AM", "CIM");
            default -> "SRUP".equals(categoria) && !SERVIDOES_COM_REGRA_PROPRIA.contains(tipoInstrumento)
                    ? List.of("CM", "SEC_ESTADO", "IP")
                    : List.of();
        };
    }

    /** Uma linha por configuração em desacordo com a matriz ou com PCM fora da 1.ª série; vazia quando tudo bate. */
    static List<String> desacordos(Connection ligacao) throws SQLException {
        return desacordos(ligacao, false);
    }

    /** Como {@link #desacordos(Connection)}, mas com a SEC_ESTADO no PROT, como a semeava o changeset 2026-11-11-4. */
    static List<String> desacordosDaMatrizInicial(Connection ligacao) throws SQLException {
        return desacordos(ligacao, true);
    }

    private static List<String> desacordos(Connection ligacao, boolean matrizInicial) throws SQLException {
        List<String> desacordos = new ArrayList<>();
        try (Statement sql = ligacao.createStatement(); ResultSet linha = sql.executeQuery(CONFIGURACOES)) {
            while (linha.next()) {
                String configuracao = "id=" + linha.getLong(1) + " " + linha.getString(2) + "/" + linha.getString(4)
                        + "/" + linha.getString(5);
                Set<String> esperado = tiposEsperados(linha.getString(2), linha.getString(3), linha.getString(4),
                        linha.getString(5), matrizInicial);
                String agregados = linha.getString(7);
                Set<String> encontrado = agregados.isEmpty() ? new TreeSet<>() : new TreeSet<>(Arrays.asList(agregados.split(",")));
                if (!esperado.equals(encontrado)) {
                    desacordos.add(configuracao + " esperado=" + esperado + " encontrado=" + encontrado);
                }
                if (linha.getLong(8) > 0 && !"PRIMEIRA".equals(linha.getString(6))) {
                    desacordos.add(configuracao + " tem PCM na série " + linha.getString(6));
                }
            }
        }
        return desacordos;
    }

    static int configuracoes(Connection ligacao) throws SQLException {
        try (Statement sql = ligacao.createStatement();
             ResultSet linha = sql.executeQuery("SELECT count(*) FROM instrumento_procedimento_config")) {
            linha.next();
            return linha.getInt(1);
        }
    }
}
