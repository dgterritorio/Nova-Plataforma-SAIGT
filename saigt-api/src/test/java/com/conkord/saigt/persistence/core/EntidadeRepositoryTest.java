package com.conkord.saigt.persistence.core;

import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntidadeRepositoryTest {

    @Autowired
    private EntidadeRepository repository;

    private static Entidade entidade(String codigo, TipoEntidade tipo, String nipc, String descricao) {
        return new Entidade(codigo, tipo, nipc, descricao, descricao);
    }

    @Test
    void deveGuardarELerEntidade() {
        Entidade nova = EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa");

        Entidade guardada = repository.saveAndFlush(nova);

        Optional<Entidade> lida = repository.findById(guardada.getId());
        assertThat(lida).isPresent();
        assertThat(lida.get().getNipc()).isEqualTo("501234567");
        assertThat(lida.get().getDescricaoIncm()).isEqualTo("Câmara Municipal de Lisboa");
        assertThat(lida.get().isAtivo()).isTrue();
    }

    @Test
    void deveAceitarOMesmoNipcEmCodigosDistintosEEntidadeSemNipc() {
        repository.saveAndFlush(entidade("T3001", TipoEntidade.SEC_ESTADO, "600025861", "Gabinete da Ministra"));
        repository.saveAndFlush(entidade("T3007", TipoEntidade.SEC_ESTADO, "600025861", "Gabinete do Ministro"));
        repository.saveAndFlush(entidade("T3008", TipoEntidade.SEC_ESTADO, null, "Gabinete sem NIPC"));

        assertThat(repository.findAllByNipc("600025861")).extracting(Entidade::getCodigo)
                .containsExactlyInAnyOrder("T3001", "T3007");
    }

    @Test
    void deveRejeitarEntidadeSemCodigoOuSemTipo() {
        Entidade semCodigo = entidade(null, TipoEntidade.CM, "501234567", "Câmara Municipal de Sintra");
        Entidade semTipo = entidade("T5245", null, "501234567", "Câmara Municipal de Sintra");

        assertThatThrownBy(() -> repository.saveAndFlush(semCodigo)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(semTipo)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveEncontrarEntidadesPorNipcEUmaSoPeloMetodoProvisorio() {
        repository.saveAndFlush(entidade("T5126", TipoEntidade.CM, "506804240", "Município de Lagoa"));
        repository.saveAndFlush(entidade("T3001", TipoEntidade.SEC_ESTADO, "600025861", "Gabinete da Ministra"));
        repository.saveAndFlush(entidade("T3007", TipoEntidade.SEC_ESTADO, "600025861", "Gabinete do Ministro"));

        assertThat(repository.findAllByNipc("506804240")).extracting(Entidade::getCodigo).containsExactly("T5126");
        assertThat(repository.findAllByNipc("600025861")).hasSize(2);
        assertThat(repository.findAllByNipc("999999999")).isEmpty();

        assertThat(repository.findByNipc("506804240")).get().extracting(Entidade::getCodigo).isEqualTo("T5126");
        assertThat(repository.findByNipc("600025861")).isEmpty();
        assertThat(repository.findByNipc("999999999")).isEmpty();
    }

    @Test
    void deveEncontrarEntidadesPorNome() {
        repository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));

        List<Entidade> encontradas = repository.findAllByDescricaoIncm("Câmara Municipal de Lisboa");

        assertThat(encontradas).hasSize(1);
        assertThat(repository.findAllByDescricaoIncm("Nome Inexistente")).isEmpty();
    }

    @Test
    void deveEncontrarEntidadePorCodigo() {
        repository.saveAndFlush(entidade("T1111", TipoEntidade.CM, "501234567", "Câmara Municipal de Lisboa"));

        assertThat(repository.findByCodigo("T1111")).isPresent();
        assertThat(repository.findByCodigo("T9999")).isEmpty();
        assertThat(repository.existsByCodigo("T1111")).isTrue();
        assertThat(repository.existsByCodigo("T9999")).isFalse();
    }

    @Test
    void deveRejeitarCodigoDuplicado() {
        repository.saveAndFlush(entidade("T2222", TipoEntidade.CM, "501234567", "Câmara Municipal de Lisboa"));
        Entidade duplicada = entidade("T2222", TipoEntidade.CM, "504567890", "Câmara Municipal de Oeiras");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveEncontrarEntidadesAtivasPelaDescricaoIncmSemDistinguirMaiusculas() {
        Entidade ativa = repository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Entidade inativa = EntidadeTeste.nova("502345678", "Câmara Municipal de Sintra");
        inativa.setAtivo(false);
        repository.saveAndFlush(inativa);

        assertThat(ativa.getId()).isNotNull();
        assertThat(repository.existsByDescricaoIncmIgnoreCaseAndAtivoTrue("CÂMARA MUNICIPAL DE LISBOA")).isTrue();
        assertThat(repository.existsByDescricaoIncmIgnoreCaseAndAtivoTrue("Câmara Municipal de Sintra")).isFalse();
    }

    @Test
    void deveGuardarOTipoDeEntidadeEAsDuasDescricoes() {
        Entidade entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Sintra");
        entidade.setDescricaoSaigt("CM Sintra");
        Entidade guardada = repository.saveAndFlush(entidade);

        Entidade lida = repository.findById(guardada.getId()).orElseThrow();
        assertThat(lida.getDescricaoIncm()).isEqualTo("Câmara Municipal de Sintra");
        assertThat(lida.getDescricaoSaigt()).isEqualTo("CM Sintra");
        assertThat(lida.getTipoEntidade()).isEqualTo(TipoEntidade.CM);
    }

    @Test
    void deveGuardarODicoETodosOsContactosDaEntidade() {
        Entidade camara = EntidadeTeste.nova("501234567", "Câmara Municipal de Sintra");
        camara.setDico("1111");
        camara.setTelefone("219238500");
        camara.setEmail("geral@cm-sintra.pt");
        camara.setResponsavelNome("Helena Marques");
        camara.setResponsavelEmail("helena.sintra@incm.pt");
        camara.setResponsavelCargo("RESPONSAVEL");

        Entidade lida = repository.findById(repository.saveAndFlush(camara).getId()).orElseThrow();

        assertThat(lida.getDico()).isEqualTo("1111");
        assertThat(lida.getTelefone()).isEqualTo("219238500");
        assertThat(lida.getEmail()).isEqualTo("geral@cm-sintra.pt");
        assertThat(lida.getResponsavelNome()).isEqualTo("Helena Marques");
        assertThat(lida.getResponsavelEmail()).isEqualTo("helena.sintra@incm.pt");
        assertThat(lida.getResponsavelCargo()).isEqualTo("RESPONSAVEL");
    }

    @Test
    void deveRecusarDicoNumaEntidadeQueNaoECm() {
        Entidade dgt = EntidadeTeste.nova("501234567", "Direção-Geral do Território", TipoEntidade.DGT);
        dgt.setDico("1111");

        assertThatThrownBy(() -> repository.saveAndFlush(dgt)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveTerNoEnumExatamenteOsValoresDoCheckDaEntidade() {
        String check = Arrays.stream(Entidade.class.getAnnotationsByType(Check.class))
                .filter(restricao -> restricao.name().equals("ck_entidade_tipo_entidade"))
                .findFirst().orElseThrow().constraints();
        List<String> noCheck = Pattern.compile("'([A-Z_]+)'").matcher(check).results()
                .map(resultado -> resultado.group(1)).toList();

        assertThat(noCheck).containsExactlyInAnyOrder(Arrays.stream(TipoEntidade.values())
                .map(Enum::name).toArray(String[]::new));
        assertThat(TipoEntidade.values()).hasSize(11);
    }
}
