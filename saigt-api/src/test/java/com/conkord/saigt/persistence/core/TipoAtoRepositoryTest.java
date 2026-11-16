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
class TipoAtoRepositoryTest {

    @Autowired
    private TipoAtoRepository repository;

    @Test
    void deveGuardarELerTipoAto() {
        TipoAto novo = new TipoAto("6", "Deliberação", 1);
        novo.setIncmActTypeIdSerie1(1234567891234568L);
        novo.setIncmActTypeIdSerie2(1234567891234570L);

        TipoAto guardado = repository.saveAndFlush(novo);

        Optional<TipoAto> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getCodigo()).isEqualTo("6");
        assertThat(lido.get().getNome()).isEqualTo("Deliberação");
        assertThat(lido.get().getIncmActTypeIdSerie1()).isEqualTo(1234567891234568L);
        assertThat(lido.get().getIncmActTypeIdSerie2()).isEqualTo(1234567891234570L);
        assertThat(lido.get().isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarCodigoDuplicado() {
        TipoAto original = new TipoAto("6", "Deliberação", 1);
        original.setIncmActTypeIdSerie1(1234567891234568L);
        repository.saveAndFlush(original);

        TipoAto duplicado = new TipoAto("6", "Duplicado", 2);
        duplicado.setIncmActTypeIdSerie2(1234567891234570L);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarSemNenhumIdentificadorIncm() {
        TipoAto semIncm = new TipoAto("7", "Sem INCM", 3);

        assertThatThrownBy(() -> repository.saveAndFlush(semIncm))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
