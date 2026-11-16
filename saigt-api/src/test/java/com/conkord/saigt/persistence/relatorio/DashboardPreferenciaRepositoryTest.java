package com.conkord.saigt.persistence.relatorio;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DashboardPreferenciaRepositoryTest extends PostgresTestcontainersBase {

    @Autowired
    private DashboardPreferenciaRepository repository;

    private static DashboardPreferencia preferencia(UUID utilizadorId, String codigo, boolean visivel, int ordem) {
        return new DashboardPreferencia(utilizadorId, codigo, visivel, (short) ordem, Instant.now());
    }

    @Test
    void apagarEReinserirAsMesmasPosicoesNaMesmaTransacaoNaoViolaAPkNemAUnica() {
        UUID utilizador = UUID.randomUUID();
        repository.saveAllAndFlush(List.of(preferencia(utilizador, "TAXA_REJEICAO", true, 0),
                preferencia(utilizador, "GRAFICO_MES", false, 1), preferencia(utilizador, "A_CADUCAR", true, 2)));

        assertThat(repository.apagarDoUtilizador(utilizador)).isEqualTo(3);
        repository.saveAllAndFlush(List.of(preferencia(utilizador, "GRAFICO_MES", true, 0),
                preferencia(utilizador, "TAXA_REJEICAO", false, 1), preferencia(utilizador, "ATIVIDADE_RECENTE", true, 2)));

        assertThat(repository.findByIdUtilizadorIdOrderByOrdemAsc(utilizador))
                .extracting(DashboardPreferencia::getKpiCodigo, DashboardPreferencia::isVisivel, DashboardPreferencia::getOrdem)
                .containsExactly(tuple("GRAFICO_MES", true, (short) 0), tuple("TAXA_REJEICAO", false, (short) 1),
                        tuple("ATIVIDADE_RECENTE", true, (short) 2));
    }

    @Test
    void apagarDoUtilizadorSoApagaAsDele() {
        UUID um = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        repository.saveAllAndFlush(List.of(preferencia(um, "TAXA_REJEICAO", true, 0),
                preferencia(outro, "TAXA_REJEICAO", false, 0), preferencia(outro, "GRAFICO_MES", true, 1)));

        assertThat(repository.apagarDoUtilizador(um)).isEqualTo(1);

        assertThat(repository.findByIdUtilizadorIdOrderByOrdemAsc(um)).isEmpty();
        assertThat(repository.findByIdUtilizadorIdOrderByOrdemAsc(outro)).extracting(DashboardPreferencia::getKpiCodigo)
                .containsExactly("TAXA_REJEICAO", "GRAFICO_MES");
    }

    @Test
    void duasPreferenciasDoMesmoUtilizadorNaMesmaPosicaoViolamAUnica() {
        UUID utilizador = UUID.randomUUID();

        assertThatThrownBy(() -> repository.saveAllAndFlush(List.of(preferencia(utilizador, "TAXA_REJEICAO", true, 3),
                preferencia(utilizador, "GRAFICO_MES", true, 3))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lePelaOrdemCrescente() {
        UUID utilizador = UUID.randomUUID();
        repository.saveAllAndFlush(List.of(preferencia(utilizador, "A_CADUCAR", true, 7),
                preferencia(utilizador, "A_INATIVAR", true, 2), preferencia(utilizador, "GRAFICO_ESTADO", false, 5)));

        assertThat(repository.findByIdUtilizadorIdOrderByOrdemAsc(utilizador)).extracting(DashboardPreferencia::getKpiCodigo)
                .containsExactly("A_INATIVAR", "GRAFICO_ESTADO", "A_CADUCAR");
    }
}
