package com.conkord.saigt.persistence.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ListaValoresRepositoryTest {

    @Autowired
    private ListaValoresRepository repository;

    @Test
    void deveGuardarELerListaValores() {
        ListaValores novo = new ListaValores("SERIE_DR", "1", "I Série", 1);

        ListaValores guardado = repository.saveAndFlush(novo);

        Optional<ListaValores> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getTipo()).isEqualTo("SERIE_DR");
        assertThat(lido.get().getCodigo()).isEqualTo("1");
        assertThat(lido.get().getDescricao()).isEqualTo("I Série");
        assertThat(lido.get().isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarTipoCodigoDuplicado() {
        repository.saveAndFlush(new ListaValores("SERIE_DR", "1", "I Série", 1));
        ListaValores duplicado = new ListaValores("SERIE_DR", "1", "Duplicado", 2);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
