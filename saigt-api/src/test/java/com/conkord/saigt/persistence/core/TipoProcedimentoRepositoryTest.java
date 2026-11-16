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
class TipoProcedimentoRepositoryTest {

    @Autowired
    private TipoProcedimentoRepository repository;

    @Test
    void deveGuardarELerTipoProcedimento() {
        TipoProcedimento novo = new TipoProcedimento("DECISAO_INICIO", "Decisão de Início");

        TipoProcedimento guardado = repository.saveAndFlush(novo);

        Optional<TipoProcedimento> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getCodigo()).isEqualTo("DECISAO_INICIO");
        assertThat(lido.get().getNome()).isEqualTo("Decisão de Início");
        assertThat(lido.get().isAtivo()).isTrue();
    }
}
