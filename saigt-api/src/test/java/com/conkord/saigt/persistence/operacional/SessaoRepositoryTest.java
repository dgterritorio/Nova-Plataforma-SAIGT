package com.conkord.saigt.persistence.operacional;

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
class SessaoRepositoryTest {

    @Autowired
    private SessaoRepository sessaoRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private Utilizador criarUtilizador(String email) {
        Utilizador utilizador = new Utilizador("Utilizador Teste", email);
        utilizador.setPerfilInterno(PerfilInterno.TECNICO);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    @Test
    void devePurgarSessaoRevogadaAindaQueNaoExpirada() {
        Utilizador utilizador = criarUtilizador("purga.revogada@dgt.pt");
        Sessao revogada = new Sessao("jti-purga-revogada", utilizador, Instant.now().plusSeconds(3600));
        revogada.setRevogadoEm(Instant.now());
        sessaoRepository.saveAndFlush(revogada);

        long removidas = sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant.now());

        assertThat(removidas).isEqualTo(1);
        assertThat(sessaoRepository.findById("jti-purga-revogada")).isEmpty();
    }

    @Test
    void devePurgarSessaoExpiradaAindaQueNaoRevogada() {
        Utilizador utilizador = criarUtilizador("purga.expirada@dgt.pt");
        Sessao expirada = new Sessao("jti-purga-expirada", utilizador, Instant.now().minusSeconds(60));
        sessaoRepository.saveAndFlush(expirada);

        long removidas = sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant.now());

        assertThat(removidas).isEqualTo(1);
        assertThat(sessaoRepository.findById("jti-purga-expirada")).isEmpty();
    }

    @Test
    void devePurgarPreAutenticacaoExpiradaEAbandonada() {
        Utilizador utilizador = criarUtilizador("purga.preautenticacao@dgt.pt");
        Sessao preAuth = Sessao.paraPreAutenticacao("jti-purga-preauth", utilizador, Instant.now().minusSeconds(60), "{}");
        sessaoRepository.saveAndFlush(preAuth);

        long removidas = sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant.now());

        assertThat(removidas).isEqualTo(1);
        assertThat(sessaoRepository.findById("jti-purga-preauth")).isEmpty();
    }

    @Test
    void naoDevePurgarSessaoEmVigor() {
        Utilizador utilizador = criarUtilizador("purga.emvigor@dgt.pt");
        Sessao emVigor = new Sessao("jti-purga-emvigor", utilizador, Instant.now().plusSeconds(3600));
        sessaoRepository.saveAndFlush(emVigor);

        long removidas = sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant.now());

        assertThat(removidas).isZero();
        assertThat(sessaoRepository.findById("jti-purga-emvigor")).isPresent();
    }
}
