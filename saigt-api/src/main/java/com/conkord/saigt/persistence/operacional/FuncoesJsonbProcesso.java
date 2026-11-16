package com.conkord.saigt.persistence.operacional;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Funções SQL do PostgreSQL que o JPA Criteria não alcança. Registadas pelo
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 */
public class FuncoesJsonbProcesso implements FunctionContributor {

    /**
     * {@code motivos_alteracao_contem(dadosFormulario, json)}: os motivos do formulário contêm os de {@code json}
     * (um array). Usa o operador {@code @>} sobre a mesma expressão do índice {@code idx_processo_motivos_alteracao};
     * {@code jsonb_contains(...)} daria o mesmo resultado sem usar o índice.
     */
    public static final String MOTIVOS_ALTERACAO_CONTEM = "motivos_alteracao_contem";

    @Override
    public void contributeFunctions(FunctionContributions contribuicoes) {
        contribuicoes.getFunctionRegistry().registerPattern(MOTIVOS_ALTERACAO_CONTEM,
                "((?1 -> 'motivosAlteracao') @> cast(?2 as jsonb))",
                contribuicoes.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN));
    }
}
