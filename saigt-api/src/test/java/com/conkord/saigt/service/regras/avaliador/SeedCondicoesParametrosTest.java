package com.conkord.saigt.service.regras.avaliador;

import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Os seeds Liquibase não correm nos testes (H2 sem Liquibase): lê os {@code <insert>} dos XML e os CSV dos
 * {@code <loadData>} e valida as condições ativas contra os catálogos dos mesmos seeds, como o arranque faria em
 * Postgres.
 */
class SeedCondicoesParametrosTest {

    private static final String SEEDS = "classpath:db/changelog/data/*.xml";

    @Test
    void condicoesAtivasDosSeedsDevemTerParametrosValidos() throws Exception {
        List<Map<String, String>> inserts = lerInserts();
        Set<String> tiposDinamica = codigos(inserts, "tipo_dinamica");
        Set<String> tiposProcedimento = codigos(inserts, "tipo_procedimento");
        List<Map<String, String>> condicoes = inserts.stream()
                .filter(insert -> "condicao".equals(insert.get("tableName")))
                .filter(insert -> !"false".equals(insert.get("ativo")))
                .toList();
        Map<String, Avaliador> avaliadores = avaliadores(tiposDinamica, tiposProcedimento);

        List<String> invalidas = condicoes.stream()
                .flatMap(condicao -> avaliadores.get(condicao.get("avaliador"))
                        .validarParametros(condicao.get("parametros")).stream()
                        .map(motivo -> "condicao.id=" + condicao.get("id") + ": " + motivo))
                .toList();

        assertThat(condicoes).isNotEmpty();
        assertThat(invalidas).isEmpty();
    }

    private static Map<String, Avaliador> avaliadores(Set<String> tiposDinamica, Set<String> tiposProcedimento) {
        TipoDinamicaRepository tipoDinamicaRepository = mock(TipoDinamicaRepository.class);
        TipoProcedimentoRepository tipoProcedimentoRepository = mock(TipoProcedimentoRepository.class);
        when(tipoDinamicaRepository.existsByCodigo(anyString()))
                .thenAnswer(invocation -> tiposDinamica.contains(invocation.<String>getArgument(0)));
        when(tipoProcedimentoRepository.existsByCodigo(anyString()))
                .thenAnswer(invocation -> tiposProcedimento.contains(invocation.<String>getArgument(0)));
        CatalogoTipos catalogo = new CatalogoTipos(tipoDinamicaRepository, tipoProcedimentoRepository);
        DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
        ProcessoRepository processoRepository = mock(ProcessoRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        return Stream.of(
                        new OcorrenciaEmEstadoAvaliador(dinamicaRepository, processoRepository, catalogo, objectMapper),
                        new OcorrenciaNaoExisteAvaliador(dinamicaRepository, processoRepository,
                                mock(InstrumentoRepository.class), catalogo, objectMapper),
                        new InstrumentoEmVigorAvaliador(mock(InstrumentoRepository.class), objectMapper),
                        new DinamicaBloqueadaLegadoAvaliador(dinamicaRepository, catalogo, objectMapper))
                .collect(Collectors.toMap(Avaliador::codigo, Function.identity()));
    }

    private static Set<String> codigos(List<Map<String, String>> inserts, String tabela) {
        Set<String> codigos = new HashSet<>();
        inserts.stream()
                .filter(insert -> tabela.equals(insert.get("tableName")))
                .forEach(insert -> codigos.add(insert.get("codigo")));
        return codigos;
    }

    /** Cada {@code <insert>} como mapa coluna → valor, com a tabela em {@code tableName}. */
    private static List<Map<String, String>> lerInserts() throws Exception {
        List<Map<String, String>> inserts = new ArrayList<>();
        for (Resource seed : new PathMatchingResourcePatternResolver().getResources(SEEDS)) {
            try (InputStream xml = seed.getInputStream()) {
                Document documento = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml);
                NodeList elementos = documento.getElementsByTagName("insert");
                for (int i = 0; i < elementos.getLength(); i++) {
                    inserts.add(colunas((Element) elementos.item(i)));
                }
                NodeList cargas = documento.getElementsByTagName("loadData");
                for (int i = 0; i < cargas.getLength(); i++) {
                    inserts.addAll(linhasDoCsv((Element) cargas.item(i)));
                }
            }
        }
        return inserts;
    }

    /** Cada linha do CSV de um {@code <loadData>} como mapa coluna → valor; {@code NULL} sem aspas fica a nulo. */
    private static List<Map<String, String>> linhasDoCsv(Element carga) throws Exception {
        String texto;
        try (InputStream csv = new ClassPathResource(carga.getAttribute("file")).getInputStream()) {
            texto = new String(csv.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<List<String>> registos = new ArrayList<>();
        List<String> registo = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        boolean comAspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
                comAspas = true;
            } else if (c == ';' || c == '\n') {
                registo.add(!comAspas && "NULL".equals(campo.toString()) ? null : campo.toString());
                campo.setLength(0);
                comAspas = false;
                if (c == '\n') {
                    registos.add(registo);
                    registo = new ArrayList<>();
                }
            } else if (c != '\r') {
                campo.append(c);
            }
        }
        List<String> cabecalho = registos.get(0);
        List<Map<String, String>> linhas = new ArrayList<>();
        for (List<String> valores : registos.subList(1, registos.size())) {
            Map<String, String> colunas = new HashMap<>();
            colunas.put("tableName", carga.getAttribute("tableName"));
            for (int i = 0; i < cabecalho.size(); i++) {
                colunas.put(cabecalho.get(i), valores.get(i));
            }
            linhas.add(colunas);
        }
        return linhas;
    }

    private static Map<String, String> colunas(Element insert) {
        Map<String, String> colunas = new HashMap<>();
        colunas.put("tableName", insert.getAttribute("tableName"));
        NodeList elementos = insert.getElementsByTagName("column");
        for (int i = 0; i < elementos.getLength(); i++) {
            Element coluna = (Element) elementos.item(i);
            String valor = Stream.of("value", "valueBoolean", "valueNumeric")
                    .filter(coluna::hasAttribute)
                    .map(coluna::getAttribute)
                    .findFirst()
                    .orElse(null);
            colunas.put(coluna.getAttribute("name"), valor);
        }
        return colunas;
    }
}
