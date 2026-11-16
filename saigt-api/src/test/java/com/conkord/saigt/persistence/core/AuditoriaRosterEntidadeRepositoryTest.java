package com.conkord.saigt.persistence.core;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditoriaRosterEntidadeRepositoryTest extends PostgresTestcontainersBase {

    private static final long ENTIDADE_A = 9_101L;
    private static final long ENTIDADE_B = 9_102L;
    private static final UUID RITA = UUID.randomUUID();
    private static final UUID RUI = UUID.randomUUID();
    private static final UUID SARA = UUID.randomUUID();

    @Autowired
    private AuditoriaRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void registar(UUID utilizadorId, Long entidadeId, String instante) {
        jdbcTemplate.update("INSERT INTO auditoria (tipo_ocorrencia, id_ocorrencia, acao, utilizador_id, entidade_id, criado_em) "
                        + "VALUES ('PROCESSO', 'roster', 'SUBMETER', ?, ?, ?)",
                utilizadorId, entidadeId, Timestamp.from(Instant.parse(instante)));
    }

    @BeforeEach
    void preparar() {
        registar(RITA, ENTIDADE_A, "2026-09-05T09:00:00Z");
        registar(RITA, ENTIDADE_A, "2026-09-10T10:00:00Z");
        registar(RITA, ENTIDADE_B, "2026-09-20T10:00:00Z");
        registar(RUI, ENTIDADE_A, "2026-08-20T10:00:00Z");
        registar(SARA, ENTIDADE_B, "2026-09-02T10:00:00Z");
        registar(null, ENTIDADE_A, "2026-09-12T10:00:00Z");
    }

    @Test
    void soDevolveQuemTemAuditoriaNaEntidadeDesdeAData() {
        Instant primeiroDeSetembro = Instant.parse("2026-08-31T23:00:00Z");
        Instant quinzeDeSetembro = Instant.parse("2026-09-14T23:00:00Z");

        assertThat(repository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(ENTIDADE_A, primeiroDeSetembro)).containsExactly(RITA);
        assertThat(repository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(ENTIDADE_A, quinzeDeSetembro)).isEmpty();
        assertThat(repository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(ENTIDADE_B, primeiroDeSetembro))
                .containsExactlyInAnyOrder(RITA, SARA);
        assertThat(repository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(ENTIDADE_B, quinzeDeSetembro)).containsExactly(RITA);
        assertThat(repository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(ENTIDADE_A, Instant.parse("2026-08-01T00:00:00Z")))
                .containsExactlyInAnyOrder(RITA, RUI);
    }

    @Test
    void aUltimaAtuacaoEOMaisRecenteDoParUtilizadorEntidade() {
        Map<UUID, Instant> ultimaEmA = repository.findUltimaAtuacaoNaEntidadeEmLote(ENTIDADE_A, List.of(RITA, RUI, SARA)).stream()
                .collect(Collectors.toMap(AuditoriaRepository.UltimaAtuacaoProjecao::getUtilizadorId,
                        AuditoriaRepository.UltimaAtuacaoProjecao::getUltimaAtuacaoEm));

        assertThat(ultimaEmA).containsOnly(
                Map.entry(RITA, Instant.parse("2026-09-10T10:00:00Z")),
                Map.entry(RUI, Instant.parse("2026-08-20T10:00:00Z")));
    }
}
