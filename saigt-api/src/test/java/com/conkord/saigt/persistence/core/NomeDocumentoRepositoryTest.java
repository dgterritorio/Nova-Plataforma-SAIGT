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
class NomeDocumentoRepositoryTest {

    @Autowired
    private NomeDocumentoRepository repository;

    @Test
    void deveGuardarELerNomeDocumento() {
        NomeDocumento novo = new NomeDocumento("Texto DR", 1);

        NomeDocumento guardado = repository.saveAndFlush(novo);

        Optional<NomeDocumento> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getNome()).isEqualTo("Texto DR");
        assertThat(lido.get().getOrdem()).isEqualTo(1);
        assertThat(lido.get().isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarNomeDuplicado() {
        repository.saveAndFlush(new NomeDocumento("Deliberação", 1));
        NomeDocumento duplicado = new NomeDocumento("Deliberação", 2);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarNomeDuplicadoMesmoQuandoLinhaExistenteEstaInativa() {
        NomeDocumento existente = repository.saveAndFlush(new NomeDocumento("Regulamento", 1));
        existente.setAtivo(false);
        repository.saveAndFlush(existente);
        NomeDocumento duplicado = new NomeDocumento("Regulamento", 2);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveOrdenarPorOrdemEFiltrarInativos() {
        repository.saveAndFlush(new NomeDocumento("Memória Descritiva", 5));
        repository.saveAndFlush(new NomeDocumento("Texto DR", 1));
        NomeDocumento inativo = repository.saveAndFlush(new NomeDocumento("Planta Condicionantes", 2));
        inativo.setAtivo(false);
        repository.saveAndFlush(inativo);

        assertThat(repository.findByAtivoTrueOrderByOrdemAsc())
                .extracting(NomeDocumento::getNome)
                .containsExactly("Texto DR", "Memória Descritiva");
    }
}
