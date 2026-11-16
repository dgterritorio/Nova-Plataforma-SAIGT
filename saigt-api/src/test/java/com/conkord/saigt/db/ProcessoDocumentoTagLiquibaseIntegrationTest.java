package com.conkord.saigt.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O changeset da tabela das tags corrido em H2, sobre o schema do Hibernate sem a tabela (que o próprio changeset cria).
 * O mesmo changeset em PostgreSQL é verificado no {@link MasterLiquibasePostgresIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:saigt_tag_documento-${random.uuid};MODE=PostgreSQL")
class ProcessoDocumentoTagLiquibaseIntegrationTest {

    private static final String CHANGESET = "db/changelog/structure/2026-11-11-9-create-processo-documento-tag.xml";
    private static final String TABELA = "PROCESSO_DOCUMENTO_TAG";

    @Autowired
    private DataSource dataSource;

    @Test
    void deveCriarATabelaComAChaveCompostaAsDuasFkEOIndiceEApagaLaNoRollback() throws Exception {
        try (Connection ligacao = dataSource.getConnection()) {
            ligacao.createStatement().execute("DROP TABLE processo_documento_tag");
            Liquibase liquibase = liquibase(ligacao);

            atualizar(liquibase);

            DatabaseMetaData metadados = ligacao.getMetaData();
            assertThat(chavePrimaria(metadados)).containsExactly("PROCESSO_DOCUMENTO_ID", "DOCUMENTO_CONFIG_ID");
            Map<String, Short> regrasDeApagar = new HashMap<>();
            Map<String, String> referencias = new HashMap<>();
            try (ResultSet fks = metadados.getImportedKeys(null, null, TABELA)) {
                while (fks.next()) {
                    regrasDeApagar.put(fks.getString("FK_NAME"), fks.getShort("DELETE_RULE"));
                    referencias.put(fks.getString("FK_NAME"), fks.getString("PKTABLE_NAME"));
                }
            }
            assertThat(referencias).containsOnly(
                    Map.entry("FK_PROCESSO_DOCUMENTO_TAG_DOCUMENTO", "PROCESSO_DOCUMENTO"),
                    Map.entry("FK_PROCESSO_DOCUMENTO_TAG_CONFIG", "DOCUMENTO_CONFIG"));
            assertThat(regrasDeApagar.get("FK_PROCESSO_DOCUMENTO_TAG_DOCUMENTO"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);
            assertThat(regrasDeApagar.get("FK_PROCESSO_DOCUMENTO_TAG_CONFIG"))
                    .isNotEqualTo((short) DatabaseMetaData.importedKeyCascade);
            assertThat(indices(metadados)).contains("IDX_PROCESSO_DOCUMENTO_TAG_CONFIG");

            reverter(liquibase);

            try (ResultSet tabelas = metadados.getTables(null, null, TABELA, null)) {
                assertThat(tabelas.next()).isFalse();
            }
        }
    }

    private static List<String> chavePrimaria(DatabaseMetaData metadados) throws Exception {
        List<String> colunas = new ArrayList<>(List.of("", ""));
        try (ResultSet chave = metadados.getPrimaryKeys(null, null, TABELA)) {
            while (chave.next()) {
                assertThat(chave.getString("PK_NAME")).isEqualTo("PK_PROCESSO_DOCUMENTO_TAG");
                colunas.set(chave.getShort("KEY_SEQ") - 1, chave.getString("COLUMN_NAME"));
            }
        }
        return colunas;
    }

    private static List<String> indices(DatabaseMetaData metadados) throws Exception {
        List<String> nomes = new ArrayList<>();
        try (ResultSet indices = metadados.getIndexInfo(null, null, TABELA, false, false)) {
            while (indices.next()) {
                nomes.add(indices.getString("INDEX_NAME"));
            }
        }
        return nomes;
    }

    private static Liquibase liquibase(Connection ligacao) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(ligacao));
        // Liquibase#update/#rollback estão deprecated em favor da CommandScope API; mantidos por ser só o teste.
        @SuppressWarnings("deprecation")
        Liquibase liquibase = new Liquibase(CHANGESET, new ClassLoaderResourceAccessor(), database);
        return liquibase;
    }

    @SuppressWarnings("deprecation")
    private static void atualizar(Liquibase liquibase) throws Exception {
        liquibase.update("");
    }

    @SuppressWarnings("deprecation")
    private static void reverter(Liquibase liquibase) throws Exception {
        liquibase.rollback(1, "");
    }
}
