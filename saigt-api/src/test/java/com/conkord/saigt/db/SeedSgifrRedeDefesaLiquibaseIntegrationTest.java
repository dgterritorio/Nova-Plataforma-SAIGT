package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mesmo padrão do {@link SeedTipoAtoLiquibaseIntegrationTest}: Liquibase real só sobre o seed das redes, contra o schema H2 do Hibernate. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:saigt_seed_rede_defesa-${random.uuid};MODE=PostgreSQL")
class SeedSgifrRedeDefesaLiquibaseIntegrationTest {

    private static final String SEED = "db/changelog/data/2026-11-11-5-seed-sgifr-rede-defesa.xml";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SgifrRedeDefesaRepository repository;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            // Liquibase#update está deprecated em favor da CommandScope API; mantido por ser só o arranque do teste.
            @SuppressWarnings("deprecation")
            Liquibase liquibase = new Liquibase(SEED, new ClassLoaderResourceAccessor(), database);
            liquibase.update("");
        }
    }

    @Test
    void should_haveTheEightNetworksWithTheirIndicators_when_seeded() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT codigo || ':' || aplica_pra || ':' || aplica_psa || ':' || publicavel
                FROM sgifr_rede_defesa ORDER BY codigo""", String.class))
                .containsExactly("AEMGC:FALSE:TRUE:TRUE", "APPS:FALSE:TRUE:TRUE", "RPA:FALSE:TRUE:TRUE",
                        "RPFGC:TRUE:FALSE:TRUE", "RSFGC:FALSE:TRUE:TRUE", "RTFGC:FALSE:FALSE:FALSE",
                        "RVDI:FALSE:TRUE:TRUE", "RVF:FALSE:TRUE:TRUE");
        assertThat(repository.findByCodigo("RVDI")).get().extracting(SgifrRedeDefesa::getNome)
                .isEqualTo("Rede nacional de postos de vigia - rede de Vigilância e Deteção de Incêndios");
    }

    @Test
    void should_returnOnlyThePrimaryNetwork_when_networksOfPra() {
        assertThat(repository.findByAplicaPraTrueOrderByCodigo())
                .extracting(SgifrRedeDefesa::getCodigo).containsExactly("RPFGC");
    }

    @Test
    void should_returnTheSixPsaNetworksWithoutTertiaryNorPrimary_when_networksOfPsa() {
        assertThat(repository.findByAplicaPsaTrueOrderByCodigo())
                .extracting(SgifrRedeDefesa::getCodigo)
                .containsExactly("AEMGC", "APPS", "RPA", "RSFGC", "RVDI", "RVF")
                .doesNotContain("RTFGC", "RPFGC");
    }

    @Test
    void should_neverPublishNorApplyTheTertiaryNetwork_when_readFromRepository() {
        SgifrRedeDefesa terciaria = repository.findByCodigo("RTFGC").orElseThrow();

        assertThat(terciaria.isPublicavel()).isFalse();
        assertThat(terciaria.isAplicaPra()).isFalse();
        assertThat(terciaria.isAplicaPsa()).isFalse();
    }

    @Test
    void should_rejectDuplicateCode_when_inserted() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO sgifr_rede_defesa "
                + "(codigo, nome, aplica_pra, aplica_psa, publicavel) VALUES ('RVF', 'Duplicada', FALSE, TRUE, TRUE)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UQ_SGIFR_REDE_DEFESA_CODIGO");
    }
}
