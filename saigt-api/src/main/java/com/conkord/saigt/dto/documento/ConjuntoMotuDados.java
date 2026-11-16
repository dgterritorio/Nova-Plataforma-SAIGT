package com.conkord.saigt.dto.documento;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Campos editáveis de um Conjunto de Dados Geográficos (MOTU, EF v1.5 §3.6.2). Grava-se como rascunho, com campos em
 * branco; os obrigatórios do tipo de cartografia escolhido só contam para `completo`. Os campos de um tipo só existem
 * com esse tipo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConjuntoMotuDados(
        @Size(max = 300) String tituloAlternativo,
        @Size(max = 4000) String resumo,
        @Size(max = 500) String palavrasChave,
        @Size(max = 50) String denominadorEscala,
        @Size(max = 200) String sistemaReferencia,
        @Size(max = 50) String codigoSistema,
        TipoCartografiaMotu cartografiaTipo,
        LocalDate dataEdicao,
        LocalDate dataAtualizacao,
        @Size(max = 300) String atualizacaoComplemento,
        @Size(max = 300) String entidadeProprietaria,
        @Size(max = 300) String entidadeProdutora,
        @Size(max = 200) String sistemaReferenciaNome,
        @Size(max = 50) String sistemaReferenciaCodigo,
        @Size(max = 100) String exatidaoPosicional,
        @Size(max = 100) String exatidaoTematica,
        @Size(max = 200) String serieCartograficaOficial,
        @Size(max = 100) String numeroHomologacao,
        LocalDate dataHomologacao,
        @Size(max = 300) String entidadeHomologacao
) implements DadosMotu {

    public static ConjuntoMotuDados vazio() {
        return new ConjuntoMotuDados(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** O que uma nova ficha herda da anterior: tudo menos o Título Alternativo. */
    public ConjuntoMotuDados semOTituloAlternativo() {
        return new ConjuntoMotuDados(null, resumo, palavrasChave, denominadorEscala, sistemaReferencia, codigoSistema,
                cartografiaTipo, dataEdicao, dataAtualizacao, atualizacaoComplemento, entidadeProprietaria,
                entidadeProdutora, sistemaReferenciaNome, sistemaReferenciaCodigo, exatidaoPosicional,
                exatidaoTematica, serieCartograficaOficial, numeroHomologacao, dataHomologacao, entidadeHomologacao);
    }

    /** Apaga os campos que só existem com outro tipo de cartografia. */
    public ConjuntoMotuDados semOsCamposDeOutroTipo() {
        boolean oficial = cartografiaTipo == TipoCartografiaMotu.SERIE_CARTOGRAFICA_OFICIAL;
        boolean homologada = cartografiaTipo == TipoCartografiaMotu.CARTOGRAFIA_HOMOLOGADA;
        return new ConjuntoMotuDados(tituloAlternativo, resumo, palavrasChave, denominadorEscala, sistemaReferencia,
                codigoSistema, cartografiaTipo, dataEdicao, dataAtualizacao, atualizacaoComplemento,
                entidadeProprietaria, entidadeProdutora, sistemaReferenciaNome, sistemaReferenciaCodigo,
                exatidaoPosicional, exatidaoTematica, oficial ? serieCartograficaOficial : null,
                homologada ? numeroHomologacao : null, homologada ? dataHomologacao : null,
                homologada ? entidadeHomologacao : null);
    }

    public boolean temCampoDoTipoOficial() {
        return preenchido(serieCartograficaOficial);
    }

    /** Todos os obrigatórios com valor, incluindo os do tipo de cartografia escolhido. */
    public boolean completa() {
        return camposEmFalta().isEmpty();
    }

    /** Os rótulos do ecrã dos obrigatórios sem valor, pela ordem da ficha. */
    public List<String> camposEmFalta() {
        List<String> emFalta = new ArrayList<>();
        exigir(emFalta, preenchido(tituloAlternativo), "Título Alternativo");
        exigir(emFalta, preenchido(resumo), "Resumo");
        exigir(emFalta, preenchido(palavrasChave), "Palavras-chave");
        exigir(emFalta, preenchido(denominadorEscala), "Denominador da Escala");
        exigir(emFalta, preenchido(sistemaReferencia), "Sistema de Referência");
        exigir(emFalta, dataEdicao != null, "Data de Edição");
        exigir(emFalta, preenchido(atualizacaoComplemento), "Complemento da Atualização");
        exigir(emFalta, preenchido(entidadeProprietaria), "Entidade Proprietária");
        exigir(emFalta, preenchido(entidadeProdutora), "Entidade Produtora");
        exigir(emFalta, preenchido(sistemaReferenciaNome), "Sistema de Referência (Nome)");
        exigir(emFalta, preenchido(sistemaReferenciaCodigo), "Sistema de Referência (Código)");
        exigir(emFalta, preenchido(exatidaoPosicional), "Exatidão Posicional");
        exigir(emFalta, preenchido(exatidaoTematica), "Exatidão Temática");
        exigir(emFalta, cartografiaTipo != null, "Tipo de Cartografia");
        if (cartografiaTipo == TipoCartografiaMotu.SERIE_CARTOGRAFICA_OFICIAL) {
            exigir(emFalta, preenchido(serieCartograficaOficial), "Série Cartográfica Oficial");
        } else if (cartografiaTipo == TipoCartografiaMotu.CARTOGRAFIA_HOMOLOGADA) {
            exigir(emFalta, preenchido(numeroHomologacao), "N.º da Homologação");
            exigir(emFalta, dataHomologacao != null, "Data da Homologação");
            exigir(emFalta, preenchido(entidadeHomologacao), "Entidade Responsável pela Homologação");
        }
        return emFalta;
    }

    private static void exigir(List<String> emFalta, boolean preenchido, String rotulo) {
        if (!preenchido) {
            emFalta.add(rotulo);
        }
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
