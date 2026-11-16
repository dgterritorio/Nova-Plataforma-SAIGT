package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.service.documento.PedidoUpload;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorUploadDocumentoTest {

    private static final String FRONTEIRA = "----teste";
    private static final String CRLF = "\r\n";

    private final LeitorUploadDocumento leitor = new LeitorUploadDocumento();

    @Test
    void deveDevolverOsCamposDeTextoEUmStreamDoFicheiro() throws IOException {
        String corpo = texto("categoria", "PUBLICACAO") + texto("documentoConfigId", "7") + texto("tags", "a")
                + texto("tags", "b") + ficheiro("ficheiro", "plano.pdf", "conteudo") + fim();

        PedidoUpload pedido = leitor.ler(pedido(corpo));

        assertThat(pedido.categoria()).isEqualTo("PUBLICACAO");
        assertThat(pedido.documentoConfigId()).isEqualTo(7L);
        assertThat(pedido.tags()).containsExactly("a", "b");
        assertThat(pedido.nomeFicheiro()).isEqualTo("plano.pdf");
        assertThat(pedido.contentType()).isEqualTo("application/pdf");
        assertThat(new String(pedido.conteudo().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("conteudo");
        pedido.verificarFim().run();
    }

    @Test
    void deveDevolverPedidoSemConteudoQuandoNaoHaParteFicheiro() {
        PedidoUpload pedido = leitor.ler(pedido(texto("categoria", "DEPOSITO") + fim()));

        assertThat(pedido.conteudo()).isNull();
        assertThat(pedido.nomeFicheiro()).isNull();
        assertThat(pedido.categoria()).isEqualTo("DEPOSITO");
    }

    @Test
    void deveRecusarParteDeTextoDepoisDoFicheiro() throws IOException {
        String corpo = texto("categoria", "DEPOSITO") + ficheiro("ficheiro", "plano.pdf", "x")
                + texto("documentoConfigId", "7") + fim();

        PedidoUpload pedido = leitor.ler(pedido(corpo));
        pedido.conteudo().readAllBytes();

        assertThatThrownBy(() -> pedido.verificarFim().run())
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                                .containsExactly("ficheiro"));
    }

    @Test
    void deveRecusarDocumentoConfigIdQueNaoENumero() {
        String corpo = texto("documentoConfigId", "abc") + ficheiro("ficheiro", "plano.pdf", "x") + fim();

        assertThatThrownBy(() -> leitor.ler(pedido(corpo)))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                                .containsExactly("documentoConfigId"));
    }

    @Test
    void deveRecusarPedidoQueNaoEMultipart() {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/documentos");
        pedido.setContentType("application/json");

        assertThatThrownBy(() -> leitor.ler(pedido)).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void deveRecusarParteDeTextoDemasiadoLonga() {
        String corpo = texto("categoria", "x".repeat(2000)) + ficheiro("ficheiro", "plano.pdf", "x") + fim();

        assertThatThrownBy(() -> leitor.ler(pedido(corpo)))
                .isInstanceOfSatisfying(ValidacaoException.class,
                        e -> assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path)
                                .containsExactly("categoria"));
    }

    @Test
    void naoDeveCortarOFicheiroPorTamanhoPorqueOLimiteEDoItemEDoProcesso() throws IOException {
        String conteudo = "x".repeat(200_000);
        String corpo = texto("categoria", "DEPOSITO") + ficheiro("ficheiro", "grande.pdf", conteudo) + fim();

        try (InputStream lido = leitor.ler(pedido(corpo)).conteudo()) {
            assertThat(lido.readAllBytes()).hasSize(conteudo.length());
        }
    }

    private static MockHttpServletRequest pedido(String corpo) {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/v1/processos/x/documentos");
        pedido.setContentType("multipart/form-data; boundary=" + FRONTEIRA);
        pedido.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        return pedido;
    }

    private static String texto(String nome, String valor) {
        return "--" + FRONTEIRA + CRLF + "Content-Disposition: form-data; name=\"" + nome + "\"" + CRLF + CRLF
                + valor + CRLF;
    }

    private static String ficheiro(String nome, String nomeFicheiro, String conteudo) {
        return "--" + FRONTEIRA + CRLF + "Content-Disposition: form-data; name=\"" + nome + "\"; filename=\""
                + nomeFicheiro + "\"" + CRLF + "Content-Type: application/pdf" + CRLF + CRLF + conteudo + CRLF;
    }

    private static String fim() {
        return "--" + FRONTEIRA + "--" + CRLF;
    }
}
