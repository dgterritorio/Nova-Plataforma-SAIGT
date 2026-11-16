package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoEntidade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UtilizadorEntidadeRepositoryTest {

    @Autowired
    private UtilizadorEntidadeRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveDevolverSoOsUtilizadoresComVinculoAEntidadePedida() {
        Entidade entidadeA = entityManager.persist(EntidadeTeste.nova("512000001", "Câmara A"));
        Entidade entidadeB = entityManager.persist(EntidadeTeste.nova("512000002", "Câmara B"));
        Utilizador primeiroDeA = entityManager.persist(new Utilizador("Primeiro de A", "a1@exemplo.pt", "INCM"));
        Utilizador segundoDeA = entityManager.persist(new Utilizador("Segundo de A", "a2@exemplo.pt", "INCM"));
        Utilizador deB = entityManager.persist(new Utilizador("De B", "b1@exemplo.pt", "INCM"));
        entityManager.persist(new UtilizadorEntidade(primeiroDeA, entidadeA, Instant.now()));
        entityManager.persist(new UtilizadorEntidade(segundoDeA, entidadeA, Instant.now()));
        entityManager.persist(new UtilizadorEntidade(deB, entidadeB, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findUtilizadoresByEntidadeId(entidadeA.getId()))
                .extracting(Utilizador::getId)
                .containsExactlyInAnyOrder(primeiroDeA.getId(), segundoDeA.getId());
    }

    @Test
    void deveDizerSeOUtilizadorTemVinculoAUmaEntidadeAtivaDoTipo() {
        Entidade pcm = entityManager.persist(EntidadeTeste.nova("600000101", "PCM ativa", TipoEntidade.PCM));
        Entidade pcmInativa = EntidadeTeste.nova("600000102", "PCM inativa", TipoEntidade.PCM);
        pcmInativa.setAtivo(false);
        entityManager.persist(pcmInativa);
        Entidade camara = entityManager.persist(EntidadeTeste.nova("512000103", "Câmara C"));
        Utilizador daPcm = entityManager.persist(new Utilizador("Da PCM", "pcm1@exemplo.pt", "INCM"));
        Utilizador daPcmInativa = entityManager.persist(new Utilizador("Da PCM inativa", "pcm2@exemplo.pt", "INCM"));
        Utilizador daCamara = entityManager.persist(new Utilizador("Da Câmara", "cm1@exemplo.pt", "INCM"));
        Utilizador semVinculo = entityManager.persist(new Utilizador("Sem vínculo", "sv1@exemplo.pt", "INCM"));
        entityManager.persist(new UtilizadorEntidade(daPcm, pcm, Instant.now()));
        entityManager.persist(new UtilizadorEntidade(daPcmInativa, pcmInativa, Instant.now()));
        entityManager.persist(new UtilizadorEntidade(daCamara, camara, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(daPcm.getId(), TipoEntidade.PCM)).isTrue();
        assertThat(repository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(daPcmInativa.getId(), TipoEntidade.PCM)).isFalse();
        assertThat(repository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(daCamara.getId(), TipoEntidade.PCM)).isFalse();
        assertThat(repository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(semVinculo.getId(), TipoEntidade.PCM)).isFalse();
        assertThat(repository.existsByIdUtilizadorIdAndEntidadeTipoEntidadeAndEntidadeAtivoTrue(java.util.UUID.randomUUID(), TipoEntidade.PCM)).isFalse();
    }
}
