package com.conkord.saigt.db;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada contexto de teste tem a sua H2: com uma H2 partilhada, o create-drop de um contexto despejado da cache
 * apagava as tabelas dos contextos ainda vivos.
 */
@SpringBootTest
@ActiveProfiles("test")
class BaseDadosH2PorContextoTest {

    private static final String URL_POR_CONTEXTO =
            "jdbc:h2:mem:saigt-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12};MODE=PostgreSQL";

    @Autowired
    private DataSource dataSource;

    @Test
    void should_nameDatabasePerContext_when_contextStarts() throws SQLException {
        assertThat(urlDe(dataSource)).matches(URL_POR_CONTEXTO).doesNotContain("DB_CLOSE_DELAY");
    }

    @Test
    void should_createSchemaSqlObjects_when_contextStarts() {
        assertObjetosDoSchemaSql(new JdbcTemplate(dataSource));
    }

    @Test
    void should_useDifferentDatabase_when_anotherContextStarts() throws SQLException {
        String urlDesteContexto = urlDe(dataSource);

        contextoSoComDataSource().run(contexto -> {
            DataSource outra = contexto.getBean(DataSource.class);
            assertThat(urlDe(outra)).matches(URL_POR_CONTEXTO).isNotEqualTo(urlDesteContexto);
            assertObjetosDoSchemaSql(new JdbcTemplate(outra));
        });
    }

    @Test
    void should_dropDatabase_when_contextCloses() {
        AtomicReference<String> url = new AtomicReference<>();

        contextoSoComDataSource().run(contexto -> url.set(urlDe(contexto.getBean(DataSource.class))));

        // Sem DB_CLOSE_DELAY, abrir o mesmo URL depois do fecho cria uma BD nova, vazia.
        SingleConnectionDataSource reaberta = new SingleConnectionDataSource(url.get(), "sa", "", true);
        try {
            assertThat(contar(new JdbcTemplate(reaberta), "TABLES", "TABLE_NAME", "SHEDLOCK")).isZero();
        } finally {
            reaberta.destroy();
        }
    }

    /** Outro contexto, com o application-test.properties real, só com o DataSource e o schema.sql. */
    private static ApplicationContextRunner contextoSoComDataSource() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class, SqlInitializationAutoConfiguration.class))
                .withInitializer(contexto -> {
                    contexto.getEnvironment().getPropertySources()
                            .addFirst(new PropertiesPropertySource("application-test", propriedadesDeTeste()));
                    RandomValuePropertySource.addToEnvironment(contexto.getEnvironment());
                });
    }

    private static Properties propriedadesDeTeste() {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-test.properties"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String urlDe(DataSource dataSource) throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getJdbcUrl();
    }

    private static void assertObjetosDoSchemaSql(JdbcTemplate jdbc) {
        assertThat(contar(jdbc, "SEQUENCES", "SEQUENCE_NAME", "SEQ_INSTRUMENTO_NUMERO_SEQUENCIAL")).isOne();
        assertThat(contar(jdbc, "ROUTINES", "ROUTINE_NAME", "PG_ADVISORY_XACT_LOCK")).isOne();
        assertThat(contar(jdbc, "TABLES", "TABLE_NAME", "SHEDLOCK")).isOne();
    }

    private static int contar(JdbcTemplate jdbc, String vista, String coluna, String nome) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA." + vista + " WHERE UPPER(" + coluna + ") = ?",
                Integer.class, nome);
        return total == null ? 0 : total;
    }
}
