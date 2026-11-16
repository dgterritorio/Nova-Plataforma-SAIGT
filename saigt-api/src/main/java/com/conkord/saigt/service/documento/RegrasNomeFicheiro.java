package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import java.util.Optional;
import java.util.regex.Pattern;

/** Regras de nomenclatura do ficheiro carregado, `GERAL` ou `SGIFR` conforme o item. O nome de um ZIP não é validado. */
public final class RegrasNomeFicheiro {

    public static final String MSG_PONTO = "O nome do ficheiro só pode ter um ponto, antes da extensão.";
    public static final String MSG_CARACTERES =
            "O nome do ficheiro tem de ter no máximo 20 caracteres, sem espaços nem caracteres especiais.";
    public static final String MSG_SGIFR =
            "O nome tem de seguir o formato Nome_região_temática (máx. 20 caracteres, letras, números e _).";

    private static final int TAMANHO_MAXIMO = 20;
    private static final String EXTENSAO_ZIP = "zip";
    private static final Pattern PERMITIDO = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?");
    // Nome_região_temática: três segmentos não vazios antes da extensão.
    private static final Pattern PADRAO_SGIFR = Pattern.compile("[A-Za-z0-9]+_[A-Za-z0-9]+_[A-Za-z0-9]+(\\..+)?");

    private RegrasNomeFicheiro() {
    }

    /** {@link #primeiroErro(String, RegraNomenclatura)} com a regra `GERAL`. */
    public static Optional<String> primeiroErro(String nome) {
        return primeiroErro(nome, RegraNomenclatura.GERAL);
    }

    /** Mensagem da primeira regra que falha, pela ordem do ponto e depois dos caracteres e do comprimento. */
    public static Optional<String> primeiroErro(String nome, RegraNomenclatura regra) {
        if (EXTENSAO_ZIP.equals(CaminhoDocumento.extensao(nome))) {
            return Optional.empty();
        }
        return regra == RegraNomenclatura.SGIFR ? erroNomeSgifr(nome) : erroNome(nome);
    }

    private static Optional<String> erroNomeSgifr(String nome) {
        if (pontoInvalido(nome)) {
            return Optional.of(MSG_PONTO);
        }
        if (nome.length() > TAMANHO_MAXIMO || !PERMITIDO.matcher(nome).matches()
                || !PADRAO_SGIFR.matcher(nome).matches()) {
            return Optional.of(MSG_SGIFR);
        }
        return Optional.empty();
    }

    /** As mesmas regras sem a exceção do ZIP: um ZIP dentro de um ZIP é uma entrada como as outras. */
    public static Optional<String> erroNome(String nome) {
        if (pontoInvalido(nome)) {
            return Optional.of(MSG_PONTO);
        }
        if (nome.length() > TAMANHO_MAXIMO || !PERMITIDO.matcher(nome).matches()) {
            return Optional.of(MSG_CARACTERES);
        }
        return Optional.empty();
    }

    /** Mais de um ponto, ou um ponto que não separa o nome de uma extensão. */
    private static boolean pontoInvalido(String nome) {
        int primeiro = nome.indexOf('.');
        if (primeiro < 0) {
            return false;
        }
        return primeiro != nome.lastIndexOf('.') || primeiro == nome.length() - 1;
    }
}
