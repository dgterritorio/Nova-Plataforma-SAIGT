package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/** Chaves dos objetos no bucket (TR-105) e sanitização do nome do ficheiro. */
public final class CaminhoDocumento {

    static final int TAMANHO_MAXIMO_NOME = 20;
    private static final int TAMANHO_MAXIMO_EXTENSAO = 10;
    private static final String NOME_POR_OMISSAO = "documento";
    private static final String RAIZ = "processos";
    private static final String RAIZ_NOTIFICACOES = "notificacoes";
    private static final String PASTA_SUBSTITUIDOS = "SUBSTITUIDOS";
    private static final String PASTA_GEOCONFORM = "GEOCONFORM";

    private CaminhoDocumento() {
    }

    public static String privado(String numeroProcesso, CategoriaDocumento categoria, OrigemDocumento origem,
                                 UUID processoDocumentoId, String nomeOriginal) {
        return pasta(numeroProcesso, categoria.name(), origem) + processoDocumentoId + "-" + sanitizar(nomeOriginal);
    }

    /**
     * Caminho do ficheiro que substitui outro: `{processo_documento_id}-{revisao}-{nome}`. A revisão garante uma
     * chave diferente da atual mesmo com o mesmo nome, para nunca sobrescrever o objeto antes do commit.
     */
    public static String privadoSubstituto(String numeroProcesso, CategoriaDocumento categoria, OrigemDocumento origem,
                                           UUID processoDocumentoId, String revisao, String nomeOriginal) {
        return pasta(numeroProcesso, categoria.name(), origem) + processoDocumentoId + "-" + revisao + "-"
                + sanitizar(nomeOriginal);
    }

    /** Cópia que fica em `SUBSTITUIDOS/` quando um ficheiro é substituído, com a revisão que ele tinha. */
    public static String substituido(String numeroProcesso, OrigemDocumento origem, UUID processoDocumentoId,
                                     String revisao, String nomeOriginal) {
        return pasta(numeroProcesso, PASTA_SUBSTITUIDOS, origem) + processoDocumentoId + "-" + revisao + "-"
                + sanitizar(nomeOriginal);
    }

    public static String notificacao(UUID notificacaoId, UUID notificacaoDocumentoId, String nomeOriginal) {
        return RAIZ_NOTIFICACOES + "/" + notificacaoId + "/" + notificacaoDocumentoId + "-" + sanitizar(nomeOriginal);
    }

    private static String pasta(String numeroProcesso, String caixa, OrigemDocumento origem) {
        String subpasta = origem == OrigemDocumento.GEOCONFORM ? PASTA_GEOCONFORM + "/" : "";
        return RAIZ + "/" + numeroProcesso + "/" + caixa + "/" + subpasta;
    }

    /** Minúsculas, sem ponto; vazia quando o nome não tem extensão. */
    public static String extensao(String nomeOriginal) {
        int ponto = nomeOriginal.lastIndexOf('.');
        if (ponto < 0 || ponto == nomeOriginal.length() - 1) {
            return "";
        }
        return nomeOriginal.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Máx. {@value #TAMANHO_MAXIMO_NOME} caracteres com a extensão, só letras sem acento, dígitos, `-` e `_`.
     * Corta o nome, nunca a extensão.
     */
    public static String sanitizar(String nomeOriginal) {
        String semAcentos = Normalizer.normalize(nomeOriginal, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String extensao = limpar(extensao(semAcentos));
        if (extensao.length() > TAMANHO_MAXIMO_EXTENSAO) {
            extensao = extensao.substring(0, TAMANHO_MAXIMO_EXTENSAO);
        }
        int ponto = semAcentos.lastIndexOf('.');
        String base = limpar(ponto < 0 ? semAcentos : semAcentos.substring(0, ponto));
        if (base.isEmpty()) {
            base = NOME_POR_OMISSAO;
        }

        int maximoBase = extensao.isEmpty() ? TAMANHO_MAXIMO_NOME : TAMANHO_MAXIMO_NOME - extensao.length() - 1;
        if (base.length() > maximoBase) {
            base = limpar(base.substring(0, maximoBase));
        }
        return extensao.isEmpty() ? base : base + "." + extensao;
    }

    private static String limpar(String texto) {
        return texto.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
    }
}
