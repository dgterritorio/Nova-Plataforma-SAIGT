package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntidadeContactoRepositoryTest {

    @Autowired
    private EntidadeContactoRepository repository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private Entidade criarEntidade() {
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
    }

    private Utilizador criarUtilizador() {
        Utilizador utilizador = new Utilizador("Ana Ferreira", "ana.ferreira@dgt.pt");
        utilizador.setPerfilInterno(PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    @Test
    void deveGuardarELerContacto() {
        Entidade entidade = criarEntidade();
        Utilizador autor = criarUtilizador();

        EntidadeContacto novo = new EntidadeContacto(entidade, "geral@cm-lisboa.pt", autor);
        EntidadeContacto guardado = repository.saveAndFlush(novo);

        Optional<EntidadeContacto> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getEmail()).isEqualTo("geral@cm-lisboa.pt");
        assertThat(lido.get().getEntidade().getId()).isEqualTo(entidade.getId());
        assertThat(lido.get().getCriadoPor().getId()).isEqualTo(autor.getId());
        assertThat(lido.get().isPrincipal()).isFalse();
        assertThat(lido.get().isAtivo()).isTrue();
        assertThat(lido.get().getCriadoEm()).isNotNull();
    }

    @Test
    void deveRejeitarEmailDuplicadoParaAMesmaEntidade() {
        Entidade entidade = criarEntidade();
        Utilizador autor = criarUtilizador();
        repository.saveAndFlush(new EntidadeContacto(entidade, "geral@cm-lisboa.pt", autor));
        EntidadeContacto duplicado = new EntidadeContacto(entidade, "geral@cm-lisboa.pt", autor);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveEncontrarApenasContactosAtivosDaEntidade() {
        Entidade entidade = criarEntidade();
        Utilizador autor = criarUtilizador();
        EntidadeContacto ativo = repository.saveAndFlush(new EntidadeContacto(entidade, "ativo@cm-lisboa.pt", autor));
        EntidadeContacto inativo = repository.saveAndFlush(new EntidadeContacto(entidade, "inativo@cm-lisboa.pt", autor));
        inativo.setAtivo(false);
        repository.saveAndFlush(inativo);

        List<EntidadeContacto> encontrados = repository.findAllByEntidadeIdAndAtivoTrue(entidade.getId());

        assertThat(encontrados).extracting(EntidadeContacto::getId).containsExactly(ativo.getId());
    }

    @Test
    void deveListarAtivosEInativosComOPrincipalPrimeiroEOsInativosNoFim() {
        Entidade entidade = criarEntidade();
        Entidade outra = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234568", "Câmara Municipal de Sintra"));
        Utilizador autor = criarUtilizador();
        EntidadeContacto inativo = new EntidadeContacto(entidade, "a-inativo@cm-lisboa.pt", autor);
        inativo.setAtivo(false);
        inativo = repository.saveAndFlush(inativo);
        EntidadeContacto ativo = repository.saveAndFlush(new EntidadeContacto(entidade, "b-ativo@cm-lisboa.pt", autor));
        EntidadeContacto principal = new EntidadeContacto(entidade, "z-principal@cm-lisboa.pt", autor);
        principal.setPrincipal(true);
        principal = repository.saveAndFlush(principal);
        repository.saveAndFlush(new EntidadeContacto(outra, "geral@cm-sintra.pt", autor));

        List<EntidadeContacto> encontrados = repository.findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(
                entidade.getId());

        assertThat(encontrados).extracting(EntidadeContacto::getId)
                .containsExactly(principal.getId(), ativo.getId(), inativo.getId());
    }
}
