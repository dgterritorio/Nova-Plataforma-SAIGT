package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ConfigExtensaoNaoAceiteException;
import com.conkord.saigt.exception.ExtensaoNaoAceiteException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegrasExtensaoDocumentoTest {

    private static final String PUBLICACAO_ESCRITA =
            "[\"doc\",\"docx\",\"rtf\",\"odf\",\"odt\",\"xls\",\"xlsx\",\"csv\",\"ods\",\"pdf\",\"txt\"]";

    private static DocumentoConfig item(CategoriaDocumento categoria, TipoElemento tipoElemento, String extensoes) {
        FormatoDocumento formato = categoria == CategoriaDocumento.PUBLICACAO
                ? FormatoDocumento.EDITAVEL : FormatoDocumento.NAO_EDITAVEL;
        return new DocumentoConfig(null, "ITEM", "Item", "Item", categoria, tipoElemento, formato, extensoes, 1);
    }

    @Test
    void deveAceitarExtensaoDoItemQueEstaNaTabela() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, PUBLICACAO_ESCRITA);

        assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("regulamento.docx", item)).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarExtensaoForaDoItem() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, PUBLICACAO_ESCRITA);

        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro("script.exe", item))
                .isInstanceOf(ExtensaoNaoAceiteException.class)
                .hasMessage(RegrasExtensaoDocumento.MSG_ITEM)
                .satisfies(ex -> {
                    ExtensaoNaoAceiteException erro = (ExtensaoNaoAceiteException) ex;
                    assertThat(erro.getCodigo()).isEqualTo("DOC_EXTENSAO_NAO_ACEITE");
                    assertThat(erro.getCampos()).singleElement()
                            .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
                });
    }

    @Test
    void deveRecusarParaPublicacaoUmaExtensaoDoItemQueNaoEstaNaTabela() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, "[\"pdf\",\"jpg\"]");

        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro("foto.jpg", item))
                .isInstanceOf(ExtensaoNaoAceiteException.class)
                .hasMessage(RegrasExtensaoDocumento.MSG_PUBLICACAO);
    }

    @Test
    void deveVerificarAListaDoItemAntesDaTabela() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, "[\"pdf\"]");

        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro("foto.jpg", item))
                .hasMessage(RegrasExtensaoDocumento.MSG_ITEM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"planta.tif", "planta.gpkg"})
    void deveRecusarNaPublicacaoDePecaGraficaOQueNaoEZip(String nome) {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_GRAFICA, "[\"zip\"]");

        assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("plantas.zip", item)).doesNotThrowAnyException();
        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarFicheiro(nome, item))
                .isInstanceOf(ExtensaoNaoAceiteException.class);
    }

    @Test
    void naoDeveAplicarAColunaDePublicacaoNumItemDeDeposito() {
        DocumentoConfig item = item(CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, "[\"pdf\",\"jpg\",\"png\"]");

        assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("acta.jpg", item)).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarEmOutrosSoPelaListaDoItem() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.OUTROS, "[\"dwg\"]");

        assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("desenho.dwg", item)).doesNotThrowAnyException();
    }

    @Test
    void deveCompararSemMaiusculas() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, "[\"PDF\"]");

        assertThatCode(() -> RegrasExtensaoDocumento.validarFicheiro("REGULAMENTO.PDF", item)).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarConfiguracaoComExtensaoForaDaTabela() {
        DocumentoConfig item = item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, "[\"pdf\",\"jpg\"]");

        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarConfig(item))
                .isInstanceOf(ConfigExtensaoNaoAceiteException.class)
                .satisfies(ex -> {
                    ConfigExtensaoNaoAceiteException erro = (ConfigExtensaoNaoAceiteException) ex;
                    assertThat(erro.getCodigo()).isEqualTo("CONFIG_EXTENSAO_NAO_ACEITE");
                    assertThat(erro.getCampos()).singleElement().satisfies(campo -> {
                        assertThat(campo.path()).isEqualTo("extensoesAceites");
                        assertThat(campo.erro()).isEqualTo("Extensão não aceite para este tipo de documento.");
                    });
                });
    }

    @Test
    void deveRecusarConfiguracaoDeDepositoDePecaGraficaComTiffSolto() {
        DocumentoConfig item = item(CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\"tif\",\"tiff\",\"tfw\"]");

        assertThatThrownBy(() -> RegrasExtensaoDocumento.validarConfig(item))
                .isInstanceOf(ConfigExtensaoNaoAceiteException.class);
    }

    @Test
    void deveAceitarConfiguracaoDentroDaTabelaEItensOutros() {
        assertThatCode(() -> RegrasExtensaoDocumento.validarConfig(
                item(CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, PUBLICACAO_ESCRITA)))
                .doesNotThrowAnyException();
        assertThatCode(() -> RegrasExtensaoDocumento.validarConfig(
                item(CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, "[\".ZIP\"]")))
                .doesNotThrowAnyException();
        assertThatCode(() -> RegrasExtensaoDocumento.validarConfig(
                item(CategoriaDocumento.DEPOSITO, TipoElemento.OUTROS, "[\"dwg\"]")))
                .doesNotThrowAnyException();
    }
}
