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
class TipoDinamicaRepositoryTest {

    @Autowired
    private TipoDinamicaRepository repository;

    @Test
    void deveGuardarELerTipoDinamica() {
        TipoDinamica novo = new TipoDinamica("ELABORACAO", "Elaboração");

        TipoDinamica guardado = repository.saveAndFlush(novo);

        Optional<TipoDinamica> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getCodigo()).isEqualTo("ELABORACAO");
        assertThat(lido.get().getNome()).isEqualTo("Elaboração");
        assertThat(lido.get().isAtivo()).isTrue();
    }
}
