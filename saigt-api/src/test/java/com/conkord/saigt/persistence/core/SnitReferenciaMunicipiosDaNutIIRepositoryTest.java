package com.conkord.saigt.persistence.core;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnitReferenciaMunicipiosDaNutIIRepositoryTest extends PostgresTestcontainersBase {

    private static final Instant AGORA = Instant.parse("2026-11-10T10:00:00Z");

    @Autowired
    private SnitReferenciaRepository repository;

    private void guardar(ListaReferenciaSnit lista, String codigo, String nut2, boolean ativo) {
        SnitReferencia referencia = new SnitReferencia(lista, codigo, "Designação " + codigo,
                "{\"dtcc\":\"" + codigo + "\",\"nut2Code\":\"" + nut2 + "\"}", AGORA);
        referencia.setAtivo(ativo);
        repository.saveAndFlush(referencia);
    }

    @Test
    void devolveSoOsCodigosAtivosDosMunicipiosDaNutIIPedida() {
        guardar(ListaReferenciaSnit.MUNICIPALITIES, "8106", "LISBOA", true);
        guardar(ListaReferenciaSnit.MUNICIPALITIES, "8110", "LISBOA", true);
        guardar(ListaReferenciaSnit.MUNICIPALITIES, "8199", "LISBOA", false);
        guardar(ListaReferenciaSnit.MUNICIPALITIES, "8312", "NORTE", true);
        guardar(ListaReferenciaSnit.NUT2, "LISBOA", "LISBOA", true);

        assertThat(repository.findCodigosMunicipiosDaNut2("LISBOA")).containsExactlyInAnyOrder("8106", "8110");
        assertThat(repository.findCodigosMunicipiosDaNut2("NORTE")).containsExactly("8312");
        assertThat(repository.findCodigosMunicipiosDaNut2("ALGARVE")).isEmpty();
    }
}
