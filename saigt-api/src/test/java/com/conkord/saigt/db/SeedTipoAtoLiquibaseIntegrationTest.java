package com.conkord.saigt.db;

import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Mesmo padrão do {@link SeedPdmLiquibaseIntegrationTest}: Liquibase real só sobre os seeds de `tipo_ato`, contra o schema H2 do Hibernate. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:saigt_seed_tipo_ato-${random.uuid};MODE=PostgreSQL")
class SeedTipoAtoLiquibaseIntegrationTest {

    private static final String[] SEED_CHANGELOGS = {
            "db/changelog/data/2026-09-17-1-seed-tipo-ato.xml",
            "db/changelog/data/2026-10-23-1-seed-catalogo-15-tipos-ato.xml",
    };

    /** Nome, na ordem da lista da EF v1.5, e as séries em que o tipo existe. */
    private record TipoEsperado(String nome, boolean serie1, boolean serie2) {
    }

    private static final List<TipoEsperado> CATALOGO = List.of(
            new TipoEsperado("Anúncio", false, true),
            new TipoEsperado("Aviso", true, true),
            new TipoEsperado("Contrato", false, true),
            new TipoEsperado("Declaração", true, true),
            new TipoEsperado("Declaração de Retificação", true, true),
            new TipoEsperado("Decreto", true, false),
            new TipoEsperado("Decreto Legislativo Regional", true, false),
            new TipoEsperado("Decreto Regulamentar", true, false),
            new TipoEsperado("Decreto Regulamentar Regional", true, false),
            new TipoEsperado("Decreto-Lei", true, false),
            new TipoEsperado("Deliberação", false, true),
            new TipoEsperado("Despacho", true, true),
            new TipoEsperado("Edital", false, true),
            new TipoEsperado("Lei", true, false),
            new TipoEsperado("Portaria", true, true));

    /** Ids dos 8 tipos do seed original; `processo.tipo_ato_id` aponta para eles. */
    private static final Map<String, Long> IDS_ORIGINAIS = Map.of(
            "DL", 1L, "PORT", 2L, "RCM", 3L, "AVISO", 4L, "DESP", 5L, "DECL", 6L, "EDITAL", 7L, "REGUL", 8L);

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            for (String changelog : SEED_CHANGELOGS) {
                // Liquibase#update está deprecated em favor da CommandScope API; mantido por ser só o arranque do teste.
                @SuppressWarnings("deprecation")
                Liquibase liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database);
                liquibase.update("");
            }
        }
    }

    @Test
    void should_haveTheFifteenActiveTypesInListOrder_when_seeded() {
        List<TipoAto> ativos = jdbcTemplate.query("""
                SELECT nome, incm_act_type_id_serie_1, incm_act_type_id_serie_2
                FROM tipo_ato WHERE ativo = TRUE ORDER BY ordem""", (rs, linha) -> {
            TipoAto tipoAto = new TipoAto("-", rs.getString("nome"), linha);
            tipoAto.setIncmActTypeIdSerie1((Long) rs.getObject("incm_act_type_id_serie_1"));
            tipoAto.setIncmActTypeIdSerie2((Long) rs.getObject("incm_act_type_id_serie_2"));
            return tipoAto;
        });

        assertThat(ativos).extracting(TipoAto::getNome)
                .containsExactlyElementsOf(CATALOGO.stream().map(TipoEsperado::nome).toList());
        for (int i = 0; i < CATALOGO.size(); i++) {
            TipoEsperado esperado = CATALOGO.get(i);
            TipoAto lido = ativos.get(i);
            assertThat(lido.getIncmActTypeIdSerie1() != null)
                    .as("%s na 1.ª série", esperado.nome()).isEqualTo(esperado.serie1());
            assertThat(lido.getIncmActTypeIdSerie2() != null)
                    .as("%s na 2.ª série", esperado.nome()).isEqualTo(esperado.serie2());
        }
    }

    @Test
    void should_notRepeatActTypeIdWithinASerie_when_seeded() {
        List<TipoAto> todos = tipoAtoRepository.findAll();

        assertThat(todos.stream().map(TipoAto::getIncmActTypeIdSerie1).filter(Objects::nonNull))
                .doesNotHaveDuplicates();
        assertThat(todos.stream().map(TipoAto::getIncmActTypeIdSerie2).filter(Objects::nonNull))
                .doesNotHaveDuplicates();
    }

    @Test
    void should_deactivateRcmAndRegulamentoWithoutDeletingThem_when_seeded() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT codigo FROM tipo_ato WHERE ativo = FALSE ORDER BY codigo""", String.class))
                .containsExactly("RCM", "REGUL");

        IDS_ORIGINAIS.forEach((codigo, id) -> assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM tipo_ato WHERE codigo = ?", Long.class, codigo))
                .as("id de %s", codigo).isEqualTo(id));
    }

    @Test
    void should_offerOnlyFirstSerieTypes_when_catalogueOfSerie1() {
        assertThat(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie1IsNotNull())
                .extracting(TipoAto::getNome)
                .containsExactlyInAnyOrderElementsOf(nomesNaSerie(true));
    }

    @Test
    void should_offerOnlySecondSerieTypes_when_catalogueOfSerie2() {
        assertThat(tipoAtoRepository.findByAtivoTrueAndIncmActTypeIdSerie2IsNotNull())
                .extracting(TipoAto::getNome)
                .containsExactlyInAnyOrder("Anúncio", "Aviso", "Contrato", "Declaração", "Declaração de Retificação",
                        "Deliberação", "Despacho", "Edital", "Portaria");
        assertThat(nomesNaSerie(false)).hasSize(9);
    }

    private static List<String> nomesNaSerie(boolean primeira) {
        return CATALOGO.stream()
                .filter(tipo -> primeira ? tipo.serie1() : tipo.serie2())
                .map(TipoEsperado::nome)
                .toList();
    }
}
