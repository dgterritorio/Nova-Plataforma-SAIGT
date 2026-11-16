package com.conkord.saigt.persistence.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TipoInstrumentoRepositoryTest {

    @Autowired
    private TipoInstrumentoRepository repository;

    @Test
    void deveGuardarELerTipoInstrumento() {
        TipoInstrumento novo = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");

        TipoInstrumento guardado = repository.saveAndFlush(novo);

        Optional<TipoInstrumento> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getCodigo()).isEqualTo("PDM");
        assertThat(lido.get().getNome()).isEqualTo("Plano Diretor Municipal");
        assertThat(lido.get().getCategoria()).isEqualTo("PLANO");
        assertThat(lido.get().isAtivo()).isTrue();
    }
}
