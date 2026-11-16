package com.conkord.saigt.persistence.operacional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdusContadorRepositoryTest {

    @Autowired
    private IdusContadorRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveGravarELerOContadorPeloNome() {
        repository.saveAndFlush(new IdusContador("NUMERO_PROCESSO", 41));
        entityManager.clear();

        IdusContador lido = repository.findById("NUMERO_PROCESSO").orElseThrow();

        assertThat(lido.getNome()).isEqualTo("NUMERO_PROCESSO");
        assertThat(lido.getValorAtual()).isEqualTo(41);
        assertThat(repository.findById("OUTRO")).isEmpty();
    }

    @Test
    void deveAtualizarOValorAtual() {
        IdusContador contador = repository.saveAndFlush(new IdusContador("NUMERO_PROCESSO", 41));

        contador.setValorAtual(42);
        repository.saveAndFlush(contador);
        entityManager.clear();

        assertThat(repository.findById("NUMERO_PROCESSO").orElseThrow().getValorAtual()).isEqualTo(42);
        assertThat(repository.count()).isEqualTo(1);
    }
}
