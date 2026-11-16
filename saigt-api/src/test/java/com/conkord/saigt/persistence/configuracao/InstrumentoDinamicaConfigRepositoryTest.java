package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstrumentoDinamicaConfigRepositoryTest {

    @Autowired
    private InstrumentoDinamicaConfigRepository repository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveGuardarELerInstrumentoDinamicaConfig() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));

        InstrumentoDinamicaConfig novo = new InstrumentoDinamicaConfig(pdm, elaboracao);
        InstrumentoDinamicaConfig guardado = repository.saveAndFlush(novo);

        Optional<InstrumentoDinamicaConfig> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getTipoInstrumento().getId()).isEqualTo(pdm.getId());
        assertThat(lido.get().getTipoDinamica().getId()).isEqualTo(elaboracao.getId());
        assertThat(lido.get().isAtivo()).isTrue();
    }

    @Test
    void deveMapearAAssociacaoDeInstrumentosComUmPorOmissao() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig porOmissao = repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));
        InstrumentoDinamicaConfig opcional = new InstrumentoDinamicaConfig(pdm, revisao);
        opcional.setAssociacaoInstrumentos(AssociacaoInstrumentos.OPCIONAL);
        repository.saveAndFlush(opcional);

        assertThat(porOmissao.getAssociacaoInstrumentos()).isEqualTo(AssociacaoInstrumentos.UM);
        assertThat(jdbcTemplate.queryForObject("SELECT associacao_instrumentos FROM instrumento_dinamica_config "
                + "WHERE id = ?", String.class, porOmissao.getId())).isEqualTo("UM");
        assertThat(jdbcTemplate.queryForObject("SELECT associacao_instrumentos FROM instrumento_dinamica_config "
                + "WHERE id = ?", String.class, opcional.getId())).isEqualTo("OPCIONAL");
    }

    @Test
    void deveRejeitarAssociacaoDeInstrumentosForaDoEnum() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig config = repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE instrumento_dinamica_config "
                + "SET associacao_instrumentos = 'VARIOS' WHERE id = ?", config.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(AssociacaoInstrumentos.values()).extracting(Enum::name)
                .containsExactly("UM", "UM_OU_MAIS", "OPCIONAL");
    }

    @Test
    void deveRejeitarParTipoInstrumentoTipoDinamicaDuplicado() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));
        InstrumentoDinamicaConfig duplicado = new InstrumentoDinamicaConfig(pdm, elaboracao);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listarTodosOrdenadosDeveDevolverTodasAsLinhasPelaOrdemDoCatalogoIncluindoInativas() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        TipoDinamica suspensao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("SUSPENSAO", "Suspensão"));

        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, revisao));
        InstrumentoDinamicaConfig inativa = new InstrumentoDinamicaConfig(pdm, suspensao);
        inativa.setAtivo(false);
        repository.saveAndFlush(inativa);
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));

        List<InstrumentoDinamicaConfig> ordenados = repository.listarTodosOrdenados();

        assertThat(ordenados).extracting(c -> c.getTipoDinamica().getCodigo())
                .containsExactly("ELABORACAO", "REVISAO", "SUSPENSAO");
        assertThat(ordenados.get(2).isAtivo()).isFalse();
        assertThat(ordenados).allSatisfy(c -> assertThat(c.getTipoInstrumento().getCodigo()).isEqualTo("PDM"));
    }

    @Test
    void listarTodosOrdenadosDeveAgruparPorTipoInstrumentoAntesDoTipoDeDinamica() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoInstrumento pu = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PU", "Plano de Urbanização", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));

        repository.saveAndFlush(new InstrumentoDinamicaConfig(pu, elaboracao));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, revisao));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pu, revisao));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));

        List<InstrumentoDinamicaConfig> ordenados = repository.listarTodosOrdenados();

        assertThat(ordenados).extracting(c -> c.getTipoInstrumento().getCodigo() + "/" + c.getTipoDinamica().getCodigo())
                .containsExactly("PDM/ELABORACAO", "PDM/REVISAO", "PU/ELABORACAO", "PU/REVISAO");
    }

    @Test
    void pesquisarDeveFiltrarPorTipoEAtivoEOrdenarPeloCodigoEPeloCatalogo() {
        TipoInstrumento pu = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PU", "Plano de Urbanização", "PLANO"));
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pu, revisao));
        InstrumentoDinamicaConfig inativa = new InstrumentoDinamicaConfig(pdm, revisao);
        inativa.setAtivo(false);
        repository.saveAndFlush(inativa);
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));
        repository.saveAndFlush(new InstrumentoDinamicaConfig(pu, elaboracao));

        assertThat(repository.pesquisar(null, null, PageRequest.of(0, 20)).getContent())
                .extracting(c -> c.getTipoInstrumento().getCodigo() + "/" + c.getTipoDinamica().getCodigo())
                .containsExactly("PDM/ELABORACAO", "PDM/REVISAO", "PU/ELABORACAO", "PU/REVISAO");
        assertThat(repository.pesquisar(pdm.getId(), true, PageRequest.of(0, 20)).getContent())
                .extracting(c -> c.getTipoDinamica().getCodigo()).containsExactly("ELABORACAO");
        assertThat(repository.pesquisar(null, false, PageRequest.of(0, 20)).getContent())
                .extracting(c -> c.getTipoInstrumento().getCodigo()).containsExactly("PDM");
        Page<InstrumentoDinamicaConfig> segunda = repository.pesquisar(null, null, PageRequest.of(1, 3));
        assertThat(segunda.getTotalElements()).isEqualTo(4);
        assertThat(segunda.getContent()).extracting(c -> c.getTipoInstrumento().getCodigo() + "/" + c.getTipoDinamica().getCodigo())
                .containsExactly("PU/REVISAO");
    }

    @Test
    void existsDoParDeveIgnorarAPropriaLinhaNaVersaoComIdNot() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        InstrumentoDinamicaConfig config = repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, alteracao));

        assertThat(repository.existsByTipoInstrumento_IdAndTipoDinamica_Id(pdm.getId(), alteracao.getId())).isTrue();
        assertThat(repository.existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(pdm.getId(), alteracao.getId(),
                config.getId())).isFalse();
        assertThat(repository.existsByTipoInstrumento_IdAndTipoDinamica_IdAndIdNot(pdm.getId(), alteracao.getId(),
                config.getId() + 1)).isTrue();
    }

    @Test
    void deveGravarATrocaDosTiposDaChave() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoInstrumento prot = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PROT", "Programa Regional", "PROGRAMA"));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig config = repository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, alteracao));

        config.setTipoInstrumento(prot);
        config.setTipoDinamica(revisao);
        repository.saveAndFlush(config);

        assertThat(jdbcTemplate.queryForMap("SELECT tipo_instrumento_id, tipo_dinamica_id FROM instrumento_dinamica_config "
                + "WHERE id = ?", config.getId()))
                .containsEntry("TIPO_INSTRUMENTO_ID", prot.getId())
                .containsEntry("TIPO_DINAMICA_ID", revisao.getId());
    }
}
