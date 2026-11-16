package com.conkord.saigt.service.relatorio;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O nome do concelho como o ecrã o mostra (`formatarNomeConcelho` no saigt-ui), para o ficheiro exportado dizer o
 * mesmo que a listagem: a API devolve a designação do SNIT tal como vem («VILA NOVA DE GAIA»). Ao mudar a regra lá,
 * muda-se aqui.
 */
final class NomesConcelho {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final Set<String> PARTICULAS = Set.of("de", "da", "do", "das", "dos", "e", "o", "a");
    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+");

    private NomesConcelho() {
    }

    /** «VILA NOVA DE GAIA» → «Vila Nova de Gaia», «MONTEMOR-O-NOVO» → «Montemor-o-Novo». */
    static String formatar(String nome) {
        if (nome == null) {
            return "";
        }
        Matcher palavras = PALAVRA.matcher(nome.strip().toLowerCase(PT));
        StringBuilder resultado = new StringBuilder();
        boolean primeira = true;
        while (palavras.find()) {
            String palavra = palavras.group();
            String formatada = !primeira && PARTICULAS.contains(palavra)
                    ? palavra
                    : palavra.substring(0, 1).toUpperCase(PT) + palavra.substring(1);
            palavras.appendReplacement(resultado, Matcher.quoteReplacement(formatada));
            primeira = false;
        }
        palavras.appendTail(resultado);
        return resultado.toString();
    }
}
