package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InstrumentoProcedimentoConfigRepositoryTest {

    @Autowired
    private InstrumentoProcedimentoConfigRepository repository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private InstrumentoDinamicaConfig criarDinamicaConfig() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        return dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, elaboracao));
    }

    @Test
    void deveGuardarELerInstrumentoProcedimentoConfig() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));

        InstrumentoProcedimentoConfig novo = new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H");
        novo.setPrazoDias(30);
        novo.setTipoPrazo("LEGAL");
        InstrumentoProcedimentoConfig guardado = repository.saveAndFlush(novo);

        Optional<InstrumentoProcedimentoConfig> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getInstrumentoDinamicaConfig().getId()).isEqualTo(dinamicaConfig.getId());
        assertThat(lido.get().getTipoProcedimento().getId()).isEqualTo(decisao.getId());
        assertThat(lido.get().getOrdem()).isEqualTo(1);
        assertThat(lido.get().isObrigatorio()).isTrue();
        assertThat(lido.get().getPrazoDias()).isEqualTo(30);
        assertThat(lido.get().getTipoPrazo()).isEqualTo("LEGAL");
        assertThat(lido.get().isAtivo()).isTrue();
        assertThat(lido.get().getSerieDr()).isEqualTo("SEGUNDA_H");
    }

    @Test
    void deveMapearAsColunasV15ComOsValoresPorOmissao() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        TipoProcedimento ato = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("REVOGACAO_ATO", "Revogação de ato"));
        InstrumentoProcedimentoConfig porOmissao =
                repository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig configurado = new InstrumentoProcedimentoConfig(dinamicaConfig, ato, 2, "SEGUNDA_H");
        configurado.setTamanhoMaximoProcesso(5_368_709_120L);
        configurado.setRepetivel(true);
        configurado.setAtoPontual(true);
        repository.saveAndFlush(configurado);

        assertThat(porOmissao.getTamanhoMaximoProcesso()).isNull();
        assertThat(porOmissao.isRepetivel()).isFalse();
        assertThat(porOmissao.isAtoPontual()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT tamanho_maximo_processo || '|' || repetivel || '|' || ato_pontual "
                + "FROM instrumento_procedimento_config WHERE id = ?", String.class, configurado.getId()))
                .isEqualTo("5368709120|TRUE|TRUE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(tamanho_maximo_processo) FROM instrumento_procedimento_config "
                + "WHERE id = ?", Integer.class, porOmissao.getId())).isZero();
    }

    @Test
    void deveRejeitarTamanhoMaximoDoProcessoNaoPositivo() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig zero = new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H");
        zero.setTamanhoMaximoProcesso(0L);

        assertThatThrownBy(() -> repository.saveAndFlush(zero)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarParDinamicaConfigTipoProcedimentoDuplicado() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig duplicado = new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 2, "SEGUNDA_H");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void devePermitirMesmaOrdemParaTiposDeProcedimentoDiferentes() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        TipoProcedimento discussao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública"));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));

        InstrumentoProcedimentoConfig guardado =
                repository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, discussao, 1, "SEGUNDA_H"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getOrdem()).isEqualTo(1);
    }

    @Test
    void deveRejeitarSerieDrForaDosValoresPermitidos() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig serieInvalida = new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "TERCEIRA");

        assertThatThrownBy(() -> repository.saveAndFlush(serieInvalida))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveAceitarSerieDrNula() {
        // A publicação das servidões (PUBLICACAO_SRUP) não vai ao Diário da República: o Procedimento fica sem série.
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));

        InstrumentoProcedimentoConfig semSerie = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, null));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM instrumento_procedimento_config WHERE id = ? AND serie_dr IS NULL",
                Long.class, semSerie.getId())).isEqualTo(1L);
    }

    @Test
    void findFirstObrigatorioAtivoDeMaiorOrdemDeveIgnorarOpcionaisEInativosPosteriores() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        InstrumentoProcedimentoConfig ordem1 = repository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P1", "Procedimento 1")), 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig ordem3 = repository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P3", "Procedimento 3")), 3, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig opcional = new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P4", "Procedimento 4")), 4, "SEGUNDA_H");
        opcional.setObrigatorio(false);
        repository.saveAndFlush(opcional);
        InstrumentoProcedimentoConfig inativo = new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P5", "Procedimento 5")), 5, "SEGUNDA_H");
        inativo.setAtivo(false);
        repository.saveAndFlush(inativo);

        assertThat(repository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(dinamicaConfig))
                .map(InstrumentoProcedimentoConfig::getId).contains(ordem3.getId());
        assertThat(ordem1.getId()).isNotEqualTo(ordem3.getId());
    }

    @Test
    void oProcedimentoQueTerminaADinamicaNuncaEUmAtoPontualMesmoObrigatorioEDeMaiorOrdem() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        InstrumentoProcedimentoConfig publicacao = repository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PUB-AP", "Publicação")), 3, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig pontual = new InstrumentoProcedimentoConfig(dinamicaConfig,
                tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("RET-AP", "Retificação")), 101, "SEGUNDA_H");
        pontual.setAtoPontual(true);
        repository.saveAndFlush(pontual);

        assertThat(repository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc(
                dinamicaConfig)).map(InstrumentoProcedimentoConfig::getId).contains(publicacao.getId());
        assertThat(repository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(dinamicaConfig))
                .map(InstrumentoProcedimentoConfig::getId).contains(pontual.getId());
    }

    @Test
    void findFirstObrigatorioAtivoDeMaiorOrdemDeveDevolverVazioSemProcedimentosObrigatorios() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        InstrumentoProcedimentoConfig opcional = new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("P1", "Procedimento 1")), 1, "SEGUNDA_H");
        opcional.setObrigatorio(false);
        repository.saveAndFlush(opcional);

        assertThat(repository.findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(dinamicaConfig))
                .isEmpty();
    }

    @Test
    void findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrueDeveExcluirOpcionaisEPosteriores() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        TipoProcedimento anexoOpcional = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("ANEXO_OPCIONAL", "Anexo Opcional"));
        TipoProcedimento discussao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública"));
        TipoProcedimento decisaoFinal = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_FINAL", "Decisão Final"));

        InstrumentoProcedimentoConfig ordem1 = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig ordem2Opcional = new InstrumentoProcedimentoConfig(dinamicaConfig, anexoOpcional, 2, "SEGUNDA_H");
        ordem2Opcional.setObrigatorio(false);
        repository.saveAndFlush(ordem2Opcional);
        InstrumentoProcedimentoConfig ordem3 = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, discussao, 3, "SEGUNDA_H"));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(dinamicaConfig, decisaoFinal, 4, "SEGUNDA_H"));

        assertThat(repository.findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(dinamicaConfig, 4))
                .extracting(InstrumentoProcedimentoConfig::getId)
                .containsExactlyInAnyOrder(ordem1.getId(), ordem3.getId());
    }

    @Test
    void listarAtivosPorConfigsDeveFiltrarPorAtivoEConfigIdsEOrdenarPorOrdem() {
        InstrumentoDinamicaConfig config1 = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        TipoProcedimento discussao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública"));
        TipoProcedimento decisaoFinal = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_FINAL", "Decisão Final"));
        InstrumentoProcedimentoConfig passo2 = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(config1, discussao, 2, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig passo1 = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(config1, decisao, 1, "SEGUNDA_H"));
        InstrumentoProcedimentoConfig inativo = new InstrumentoProcedimentoConfig(config1, decisaoFinal, 3, "SEGUNDA_H");
        inativo.setAtivo(false);
        repository.saveAndFlush(inativo);

        TipoInstrumento pdm = config1.getTipoInstrumento();
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig config2 = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, revisao));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(config2, decisao, 1, "SEGUNDA_H"));

        List<InstrumentoProcedimentoConfig> passos = repository.listarAtivosPorConfigs(List.of(config1.getId()));

        assertThat(passos).extracting(InstrumentoProcedimentoConfig::getId)
                .containsExactly(passo1.getId(), passo2.getId());
    }

    @Test
    void pesquisarDeveFiltrarPorDinamicaEAtivoEOrdenarPorOrdem() {
        InstrumentoDinamicaConfig config = criarDinamicaConfig();
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig outra = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(config.getTipoInstrumento(), revisao));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão"));
        TipoProcedimento discussao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão"));
        TipoProcedimento ratificacao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("RATIFICACAO", "Ratificação"));
        InstrumentoProcedimentoConfig terceiro = new InstrumentoProcedimentoConfig(config, ratificacao, 3, "PRIMEIRA");
        terceiro.setAtivo(false);
        repository.saveAndFlush(terceiro);
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(config, discussao, 2, "SEGUNDA_H"));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(config, decisao, 1, "SEGUNDA_H"));
        repository.saveAndFlush(new InstrumentoProcedimentoConfig(outra, decisao, 1, "SEGUNDA_H"));

        assertThat(repository.pesquisar(config.getId(), null, PageRequest.of(0, 20)).getContent())
                .extracting(InstrumentoProcedimentoConfig::getOrdem).containsExactly(1, 2, 3);
        assertThat(repository.pesquisar(config.getId(), true, PageRequest.of(0, 20)).getContent())
                .extracting(p -> p.getTipoProcedimento().getCodigo()).containsExactly("DECISAO_INICIO", "DISCUSSAO_PUBLICA");
        assertThat(repository.pesquisar(null, false, PageRequest.of(0, 20)).getContent())
                .extracting(p -> p.getTipoProcedimento().getCodigo()).containsExactly("RATIFICACAO");
        assertThat(repository.pesquisar(null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(4);
    }

    @Test
    void existsDoParDeveIgnorarAPropriaLinhaNaVersaoComIdNot() {
        InstrumentoDinamicaConfig config = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão"));
        InstrumentoProcedimentoConfig linha = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(config, decisao, 1, "SEGUNDA_H"));

        assertThat(repository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_Id(config.getId(), decisao.getId()))
                .isTrue();
        assertThat(repository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_IdAndIdNot(config.getId(),
                decisao.getId(), linha.getId())).isFalse();
        assertThat(repository.existsByInstrumentoDinamicaConfig_IdAndTipoProcedimento_IdAndIdNot(config.getId(),
                decisao.getId(), linha.getId() + 1)).isTrue();
    }

    @Test
    void deveGravarATrocaDaChaveEDaSerie() {
        InstrumentoDinamicaConfig config = criarDinamicaConfig();
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig outra = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(config.getTipoInstrumento(), revisao));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DECISAO_INICIO", "Decisão"));
        TipoProcedimento cc = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "CC"));
        InstrumentoProcedimentoConfig linha = repository.saveAndFlush(
                new InstrumentoProcedimentoConfig(config, decisao, 1, "SEGUNDA_H"));

        linha.setInstrumentoDinamicaConfig(outra);
        linha.setTipoProcedimento(cc);
        linha.setSerieDr("SEGUNDA_C");
        repository.saveAndFlush(linha);

        assertThat(jdbcTemplate.queryForMap("SELECT instrumento_dinamica_config_id, tipo_procedimento_id, serie_dr "
                + "FROM instrumento_procedimento_config WHERE id = ?", linha.getId()))
                .containsEntry("INSTRUMENTO_DINAMICA_CONFIG_ID", outra.getId())
                .containsEntry("TIPO_PROCEDIMENTO_ID", cc.getId())
                .containsEntry("SERIE_DR", "SEGUNDA_C");
    }
}
