package com.conkord.saigt.persistence.operacional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UtilizadorRepositoryTest {

    @Autowired
    private UtilizadorRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deveGuardarELerUtilizadorComIdUuidECriadoEm() {
        Utilizador novo = new Utilizador("Ana Ferreira", "ana.ferreira@dgt.pt");
        novo.setPerfilInterno(PerfilInterno.MASTER);

        Utilizador guardado = repository.saveAndFlush(novo);

        assertThat(guardado.getId()).isInstanceOf(UUID.class);
        assertThat(guardado.getCriadoEm()).isNotNull();

        Optional<Utilizador> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getEmail()).isEqualTo("ana.ferreira@dgt.pt");
    }

    @Test
    void deveGravarPerfilInternoPorNomeELerComoEnum() {
        Utilizador rita = new Utilizador("Rita Lopes", "rita.lopes@dgt.pt");
        rita.setPerfilInterno(PerfilInterno.VISITANTE);
        repository.saveAndFlush(rita);
        entityManager.clear();

        Object gravado = entityManager.createNativeQuery(
                "SELECT perfil_interno FROM utilizador WHERE email = 'rita.lopes@dgt.pt'").getSingleResult();

        assertThat(String.valueOf(gravado)).isEqualTo("VISITANTE");
        assertThat(repository.findByEmailAndOrigemAutenticacao("rita.lopes@dgt.pt", "AD_DGT").orElseThrow().getPerfilInterno())
                .isEqualTo(PerfilInterno.VISITANTE);
    }

    @Test
    void deveRejeitarPerfilInternoAntigoAoNivelDaBd() {
        Query insercao = entityManager.createNativeQuery(
                "INSERT INTO utilizador (id, email, origem_autenticacao, perfil_interno, criado_em, atualizado_em) "
                        + "VALUES (random_uuid(), 'perfil.antigo@dgt.pt', 'AD_DGT', 'ADMIN_DGT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        assertThatThrownBy(insercao::executeUpdate).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deveContarOsMastersPeloPerfil() {
        long antes = repository.countByPerfilInterno(PerfilInterno.MASTER);
        Utilizador master = new Utilizador("Master Contado", "master.contado@dgt.pt");
        master.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador tecnico = new Utilizador("Técnico", "tecnico.contado@dgt.pt");
        tecnico.setPerfilInterno(PerfilInterno.TECNICO);
        repository.saveAllAndFlush(List.of(master, tecnico));

        assertThat(repository.countByPerfilInterno(PerfilInterno.MASTER)).isEqualTo(antes + 1);
    }

    @Test
    void deveAplicarOrigemAutenticacaoAdDgtPorDefeito() {
        Utilizador registado = new Utilizador("Bruno Costa", "bruno.costa@cm-porto.pt");
        registado.setPerfilInterno(PerfilInterno.TECNICO);

        Utilizador guardado = repository.saveAndFlush(registado);

        Optional<Utilizador> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getOrigemAutenticacao()).isEqualTo("AD_DGT");
    }

    @Test
    void deveEncontrarPorEmailDentroDaOrigem() {
        Utilizador carla = new Utilizador("Carla Dias", "carla.dias@dgt.pt");
        carla.setPerfilInterno(PerfilInterno.MASTER);
        repository.saveAndFlush(carla);

        Optional<Utilizador> encontrado = repository.findByEmailAndOrigemAutenticacao("carla.dias@dgt.pt", "AD_DGT");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNome()).isEqualTo("Carla Dias");
        assertThat(repository.findByEmailAndOrigemAutenticacao("carla.dias@dgt.pt", "INCM")).isEmpty();
    }

    @Test
    void deveEncontrarAContaAdPeloNome() {
        Utilizador ad = new Utilizador("nome.autenticacao", null);
        ad.setPerfilInterno(PerfilInterno.VISITANTE);
        repository.saveAndFlush(ad);
        repository.saveAndFlush(new Utilizador("nome.autenticacao", "externo.mesmo.nome@municipio.pt", "INCM"));

        assertThat(repository.findByNomeAndOrigemAutenticacao("nome.autenticacao", "AD_DGT"))
                .get().extracting(Utilizador::getId).isEqualTo(ad.getId());
    }

    @Test
    void deveDevolverVazioQuandoEmailNaoExiste() {
        assertThat(repository.findByEmailAndOrigemAutenticacao("ninguem@dgt.pt", "AD_DGT")).isEmpty();
    }

    /** SQL nativo para contornar o default do Java e provar a restrição na BD. */
    @Test
    void deveRejeitarOrigemAutenticacaoNulaAoNivelDaBd() {
        Query insercao = entityManager.createNativeQuery(
                "INSERT INTO utilizador (id, email, origem_autenticacao, criado_em, atualizado_em) "
                        + "VALUES (random_uuid(), 'sem.origem@dgt.pt', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        assertThatThrownBy(insercao::executeUpdate).isInstanceOf(RuntimeException.class);
    }

    /** Uma conta por origem: o mesmo email numa conta INCM e noutra AD_DGT. */
    @Test
    void deveAceitarOMesmoEmailEmOrigensDiferentes() {
        Utilizador ad = new Utilizador("pessoa.duas.origens", "pessoa.duas.origens@dgt.pt");
        ad.setPerfilInterno(PerfilInterno.TECNICO);
        repository.saveAndFlush(ad);
        repository.saveAndFlush(new Utilizador("Pessoa Duas Origens", "pessoa.duas.origens@dgt.pt", "INCM"));

        assertThat(repository.findByEmailAndOrigemAutenticacao("pessoa.duas.origens@dgt.pt", "AD_DGT")).isPresent();
        assertThat(repository.findByEmailAndOrigemAutenticacao("pessoa.duas.origens@dgt.pt", "INCM")).isPresent();
    }

    @Test
    void deveRejeitarEmailDuplicadoNaMesmaOrigem() {
        Utilizador ana = new Utilizador("Ana Ferreira", "ana.ferreira@dgt.pt");
        ana.setPerfilInterno(PerfilInterno.MASTER);
        repository.saveAndFlush(ana);

        Utilizador duplicado = new Utilizador("Outra Pessoa", "ana.ferreira@dgt.pt");
        duplicado.setPerfilInterno(PerfilInterno.MASTER);
        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarAdDgtSemPerfilInternoAoNivelDaBd() {
        Utilizador semPerfil = new Utilizador("Sem Perfil", "sem.perfil@dgt.pt");

        assertThatThrownBy(() -> repository.saveAndFlush(semPerfil))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarIncmComPerfilInternoPreenchidoAoNivelDaBd() {
        Utilizador externo = new Utilizador("Externo Incm", "externo@municipio.pt", "INCM");
        externo.setPerfilInterno(PerfilInterno.MASTER);

        assertThatThrownBy(() -> repository.saveAndFlush(externo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
