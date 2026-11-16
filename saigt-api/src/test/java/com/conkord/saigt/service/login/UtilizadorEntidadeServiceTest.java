package com.conkord.saigt.service.login;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UtilizadorEntidadeService.class)
class UtilizadorEntidadeServiceTest {

    @Autowired
    private UtilizadorEntidadeService service;
    @Autowired
    private UtilizadorEntidadeRepository repository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private EntityManager entityManager;

    private Utilizador utilizador(String email) {
        return utilizadorRepository.saveAndFlush(new Utilizador("Utilizador INCM", email, "INCM"));
    }

    private Entidade entidade(String nif, String nome) {
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, nome));
    }

    private List<Long> entidadesVinculadas(Utilizador utilizador) {
        entityManager.flush();
        entityManager.clear();
        return repository.findByIdUtilizadorId(utilizador.getId()).stream()
                .map(vinculo -> vinculo.getId().getEntidadeId()).toList();
    }

    @Test
    void deveCriarVinculoATodasAsEntidadesDaAdesao() {
        Utilizador ricardo = utilizador("ricardo.silva@incm.pt");
        Entidade cascais = entidade("501111222", "Câmara Municipal de Cascais");
        Entidade sintra = entidade("501222333", "Câmara Municipal de Sintra");

        service.sincronizar(ricardo, List.of(cascais, sintra));

        assertThat(entidadesVinculadas(ricardo)).containsExactlyInAnyOrder(cascais.getId(), sintra.getId());
        assertThat(repository.existsByIdUtilizadorIdAndIdEntidadeId(ricardo.getId(), sintra.getId())).isTrue();
    }

    @Test
    void deveRemoverVinculoQueDeixouDeVirEAtualizarOsRestantes() {
        Utilizador ricardo = utilizador("ricardo.remove@incm.pt");
        Entidade cascais = entidade("501111223", "Cascais");
        Entidade sintra = entidade("501222334", "Sintra");
        Instant antigo = Instant.now().minus(30, ChronoUnit.DAYS);
        repository.saveAndFlush(new UtilizadorEntidade(ricardo, cascais, antigo));
        repository.saveAndFlush(new UtilizadorEntidade(ricardo, sintra, antigo));

        service.sincronizar(ricardo, List.of(cascais));

        assertThat(entidadesVinculadas(ricardo)).containsExactly(cascais.getId());
        UtilizadorEntidade mantido = repository.findByIdUtilizadorId(ricardo.getId()).get(0);
        assertThat(mantido.getSincronizadoEm()).isAfter(antigo);
    }

    @Test
    void deveRemoverTodosOsVinculosQuandoAListaVemVazia() {
        Utilizador bruno = utilizador("bruno.semadesao@incm.pt");
        Entidade entidade = entidade("501222335", "Entidade Revogada");
        repository.saveAndFlush(new UtilizadorEntidade(bruno, entidade, Instant.now()));

        service.sincronizar(bruno, List.of());

        assertThat(entidadesVinculadas(bruno)).isEmpty();
    }

    /** Duas adesões reconciliadas por nome para a mesma linha `entidade` dão um só vínculo, sem violar a PK. */
    @Test
    void deveIgnorarEntidadesRepetidas() {
        Utilizador eva = utilizador("eva.repetida@incm.pt");
        Entidade entidade = entidade("501222336", "Município Y");

        service.sincronizar(eva, List.of(entidade, entidade));

        assertThat(entidadesVinculadas(eva)).containsExactly(entidade.getId());
    }

    @Test
    void naoDeveMexerNosVinculosDeOutrosUtilizadores() {
        Utilizador helena = utilizador("helena.sintra@incm.pt");
        Utilizador ricardo = utilizador("ricardo.outro@incm.pt");
        Entidade sintra = entidade("501222337", "Sintra");
        repository.saveAndFlush(new UtilizadorEntidade(helena, sintra, Instant.now()));

        service.sincronizar(ricardo, List.of());

        assertThat(entidadesVinculadas(helena)).containsExactly(sintra.getId());
        assertThat(repository.findUtilizadorIdsByEntidadeId(sintra.getId())).containsExactly(helena.getId());
    }
}
