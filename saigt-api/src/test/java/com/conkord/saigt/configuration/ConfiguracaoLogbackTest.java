package com.conkord.saigt.configuration;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** O log técnico sai só na saída padrão: sem ficheiros nem base de dados, logo sem retenção nem auditoria a partir dele. */
class ConfiguracaoLogbackTest {

    private static Document logbackSpring() throws Exception {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream xml = ConfiguracaoLogbackTest.class.getResourceAsStream("/logback-spring.xml")) {
            assertThat(xml).isNotNull();
            return fabrica.newDocumentBuilder().parse(xml);
        }
    }

    private static List<Element> elementos(Element raiz, String nome) {
        NodeList lista = raiz.getElementsByTagName(nome);
        List<Element> elementos = new ArrayList<>();
        for (int i = 0; i < lista.getLength(); i++) {
            elementos.add((Element) lista.item(i));
        }
        return elementos;
    }

    private static Element perfil(Document documento, String nome) {
        return elementos(documento.getDocumentElement(), "springProfile").stream()
                .filter(perfil -> nome.equals(perfil.getAttribute("name"))).findFirst().orElseThrow();
    }

    @Test
    void soDeveTerAppendersDaSaidaPadrao() throws Exception {
        List<Element> appenders = elementos(logbackSpring().getDocumentElement(), "appender");

        assertThat(appenders).isNotEmpty().allSatisfy(appender ->
                assertThat(appender.getAttribute("class")).isEqualTo("ch.qos.logback.core.ConsoleAppender"));
        assertThat(elementos(logbackSpring().getDocumentElement(), "file")).isEmpty();
    }

    @Test
    void porOmissaoDeveEscreverJsonComOEncoderDaAplicacao() throws Exception {
        Element porOmissao = perfil(logbackSpring(), "!local");

        assertThat(elementos(porOmissao, "encoder")).singleElement()
                .satisfies(encoder -> assertThat(encoder.getAttribute("class")).isEqualTo(EncoderLogJson.class.getName()));
        assertThat(elementos(porOmissao, "appender-ref")).singleElement()
                .satisfies(ref -> assertThat(ref.getAttribute("ref")).isEqualTo("CONSOLE_JSON"));
    }

    @Test
    void soOPerfilLocalDeveEscreverTexto() throws Exception {
        Element local = perfil(logbackSpring(), "local");

        assertThat(elementos(local, "encoder")).singleElement()
                .satisfies(encoder -> assertThat(encoder.getAttribute("class")).isEmpty());
        assertThat(elementos(local, "pattern")).singleElement()
                .satisfies(padrao -> assertThat(padrao.getTextContent()).contains("%X{apiTraceId:-}"));
    }

    @Test
    void asPropertiesNaoDevemTerFicheiroDeLogNemOPadraoDeTextoAntigo() throws Exception {
        Properties propriedades = new Properties();
        try (InputStream entrada = ConfiguracaoLogbackTest.class.getResourceAsStream("/application.properties")) {
            propriedades.load(entrada);
        }

        assertThat(propriedades.stringPropertyNames())
                .noneMatch(nome -> nome.startsWith("logging.file") || nome.startsWith("logging.pattern"));
        assertThat(propriedades.getProperty("spring.main.banner-mode")).isEqualTo("off");
    }
}
