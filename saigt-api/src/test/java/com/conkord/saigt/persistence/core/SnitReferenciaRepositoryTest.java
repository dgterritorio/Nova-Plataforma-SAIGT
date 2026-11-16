package com.conkord.saigt.persistence.core;

import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnitReferenciaRepositoryTest {

    private static final Instant AGORA = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    private SnitReferenciaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static SnitReferencia municipio(String dtcc, String nome) {
        return new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\"}", AGORA);
    }

    @Test
    void deveGuardarELerPelaListaEPeloCodigo() {
        repository.saveAndFlush(municipio("1111", "Sintra"));
        repository.saveAndFlush(municipio("1105", "Cascais"));
        repository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.NUT2, "NORTE", "Norte",
                "{\"nut2Code\":\"NORTE\"}", AGORA));

        assertThat(repository.findByLista(ListaReferenciaSnit.MUNICIPALITIES))
                .extracting(SnitReferencia::getCodigo).containsExactlyInAnyOrder("1111", "1105");
        SnitReferencia sintra = repository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, "1111").orElseThrow();
        assertThat(sintra.getDesignacao()).isEqualTo("Sintra");
        assertThat(sintra.getDadosOrigem()).contains("\"dtcc\"");
        assertThat(sintra.isAtivo()).isTrue();
        assertThat(sintra.getSincronizadoEm()).isEqualTo(AGORA);
        assertThat(repository.findByListaAndCodigo(ListaReferenciaSnit.NUT2, "1111")).isEmpty();
    }

    @Test
    void deveRejeitarOMesmoCodigoRepetidoNaMesmaLista() {
        repository.saveAndFlush(municipio("1111", "Sintra"));

        assertThatThrownBy(() -> repository.saveAndFlush(municipio("1111", "Outra designação")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveAceitarOMesmoCodigoEmListasDiferentes() {
        repository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.INSTRUMENT_TYPES, "1", "PDM", "{}", AGORA));
        repository.saveAndFlush(new SnitReferencia(ListaReferenciaSnit.CLASSES, "1", "Nacional", "{}", AGORA));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void deveRejeitarListaForaDoDominio() {
        Long id = repository.saveAndFlush(municipio("1111", "Sintra")).getId();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE snit_referencia SET lista = 'SRUP' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
