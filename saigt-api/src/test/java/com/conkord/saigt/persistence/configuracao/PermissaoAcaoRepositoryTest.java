package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PermissaoAcaoRepositoryTest {

    @Autowired
    private PermissaoAcaoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    private InstrumentoProcedimentoConfig criarProcedimentoConfig() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        return procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
    }

    @Test
    void deveGuardarELerPermissaoAcao() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();

        PermissaoAcao nova = new PermissaoAcao(TipoEntidade.DGT, procedimentoConfig);
        PermissaoAcao guardada = repository.saveAndFlush(nova);

        Optional<PermissaoAcao> lida = repository.findById(guardada.getId());
        assertThat(lida).isPresent();
        assertThat(lida.get().getTipoEntidade()).isEqualTo(TipoEntidade.DGT);
        assertThat(lida.get().getInstrumentoProcedimentoConfig().getId()).isEqualTo(procedimentoConfig.getId());
        assertThat(lida.get().isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarPermissaoDuplicada() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, procedimentoConfig));
        PermissaoAcao duplicada = new PermissaoAcao(TipoEntidade.DGT, procedimentoConfig);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveAceitarTodosOsTiposDeEntidadeDaLista() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();

        for (TipoEntidade tipo : TipoEntidade.values()) {
            assertThat(repository.saveAndFlush(new PermissaoAcao(tipo, procedimentoConfig)).getId()).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MASTER", "ENTIDADE", "ENTIDADE_PCM", ""})
    void deveRejeitarUmValorForaDaListaDeTiposDeEntidade(String valor) {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO permissao_acao (tipo_entidade, instrumento_procedimento_config_id, ativo) VALUES (?, ?, true)",
                valor, procedimentoConfig.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveDevolverTrueQuandoOTipoTemLinhaNoProcedimentoInicial() {
        InstrumentoProcedimentoConfig inicial = criarProcedimentoConfig();
        TipoInstrumento tipoInstrumento = inicial.getInstrumentoDinamicaConfig().getTipoInstrumento();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, inicial));

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).isTrue();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveDevolverFalseQuandoALinhaEDeOutroTipoDeEntidade() {
        InstrumentoProcedimentoConfig inicial = criarProcedimentoConfig();
        TipoInstrumento tipoInstrumento = inicial.getInstrumentoDinamicaConfig().getTipoInstrumento();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, inicial));

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).isFalse();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveDevolverFalseQuandoALinhaEstaDesativada() {
        InstrumentoProcedimentoConfig inicial = criarProcedimentoConfig();
        TipoInstrumento tipoInstrumento = inicial.getInstrumentoDinamicaConfig().getTipoInstrumento();
        PermissaoAcao desativada = new PermissaoAcao(TipoEntidade.CM, inicial);
        desativada.setAtivo(false);
        repository.saveAndFlush(desativada);

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).isFalse();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveDevolverFalseQuandoOTipoSoTemLinhaNumProcedimentoPosterior() {
        InstrumentoProcedimentoConfig inicial = criarProcedimentoConfig();
        InstrumentoProcedimentoConfig constituicaoCc = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(inicial.getInstrumentoDinamicaConfig(),
                        tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")),
                        2, "SEGUNDA_C"));
        TipoInstrumento tipoInstrumento = inicial.getInstrumentoDinamicaConfig().getTipoInstrumento();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, inicial));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CCDR_DR, constituicaoCc));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, constituicaoCc));

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).isTrue();
        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CCDR_DR, tipoInstrumento)).isFalse();
        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.DGT, tipoInstrumento)).isFalse();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveContarOProcedimentoAtivoDeMenorOrdemQuandoOPrimeiroEstaInativo() {
        InstrumentoProcedimentoConfig primeiro = criarProcedimentoConfig();
        InstrumentoProcedimentoConfig segundo = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(primeiro.getInstrumentoDinamicaConfig(),
                        tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("CONSTITUICAO_CC", "Constituição da CC")),
                        2, "SEGUNDA_C"));
        TipoInstrumento tipoInstrumento = primeiro.getInstrumentoDinamicaConfig().getTipoInstrumento();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, primeiro));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CCDR_DR, segundo));
        primeiro.setAtivo(false);
        procedimentoConfigRepository.saveAndFlush(primeiro);

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CCDR_DR, tipoInstrumento)).isTrue();
        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, tipoInstrumento)).isFalse();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoNaoDeveContarAsDinamicasQueSeIniciamSemIgt() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica suspensaoCcdr = new TipoDinamica("SUSPENSAO_CCDR", "Suspensão (iniciativa da CCDR)");
        ReflectionTestUtils.setField(suspensaoCcdr, "inicioSemIgt", true);
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, tipoDinamicaRepository.saveAndFlush(suspensaoCcdr)));
        InstrumentoProcedimentoConfig unico = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("SUSPENSAO_CCDR", "Suspensão (CCDR)")),
                1, "SEGUNDA_C"));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CCDR_DR, unico));

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CCDR_DR, pdm)).isFalse();
    }

    @Test
    void existeNoProcedimentoInicialDoTipoDeveDevolverFalseQuandoADinamicaEstaInativaOuENoutroTipoDeInstrumento() {
        InstrumentoProcedimentoConfig inicial = criarProcedimentoConfig();
        TipoInstrumento pdm = inicial.getInstrumentoDinamicaConfig().getTipoInstrumento();
        TipoInstrumento pu = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("PU", "Plano de Urbanização", "PLANO"));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, inicial));

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, pu)).isFalse();

        inicial.getInstrumentoDinamicaConfig().setAtivo(false);
        dinamicaConfigRepository.saveAndFlush(inicial.getInstrumentoDinamicaConfig());

        assertThat(repository.existeNoProcedimentoInicialDoTipo(TipoEntidade.CM, pdm)).isFalse();
    }

    @Test
    void findByInstrumentoProcedimentoConfigIdInAndAtivoTrueDeveFiltrarPorIdsEAtivo() {
        InstrumentoProcedimentoConfig passo1 = criarProcedimentoConfig();
        InstrumentoProcedimentoConfig passo2 = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(passo1.getInstrumentoDinamicaConfig(),
                        tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública")),
                        2, "SEGUNDA_H"));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, passo1));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, passo1));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, passo2));
        PermissaoAcao inativa = new PermissaoAcao(TipoEntidade.DGT, passo2);
        inativa.setAtivo(false);
        repository.saveAndFlush(inativa);

        List<PermissaoAcao> permissoes = repository.findByInstrumentoProcedimentoConfig_IdInAndAtivoTrue(
                List.of(passo1.getId(), passo2.getId()));

        assertThat(permissoes).extracting(PermissaoAcao::getTipoEntidade)
                .containsExactlyInAnyOrder(TipoEntidade.CM, TipoEntidade.DGT, TipoEntidade.CM);
    }

    @Test
    void existsByTipoEntidadeEProcedimentoSoDeveDevolverTrueParaALinhaAtivaDoTipo() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimentoConfig));
        PermissaoAcao desativada = new PermissaoAcao(TipoEntidade.DGT, procedimentoConfig);
        desativada.setAtivo(false);
        repository.saveAndFlush(desativada);

        assertThat(repository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CM, procedimentoConfig)).isTrue();
        assertThat(repository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.DGT, procedimentoConfig)).isFalse();
        assertThat(repository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(TipoEntidade.CCDR_DR, procedimentoConfig)).isFalse();
    }

    @Test
    void findAllByTipoEntidadeDeveDevolverSoAsLinhasAtivasDoTipo() {
        InstrumentoProcedimentoConfig passo1 = criarProcedimentoConfig();
        InstrumentoProcedimentoConfig passo2 = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(passo1.getInstrumentoDinamicaConfig(),
                        tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("DISCUSSAO_PUBLICA", "Discussão Pública")),
                        2, "SEGUNDA_H"));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, passo1));
        repository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, passo1));
        PermissaoAcao inativa = new PermissaoAcao(TipoEntidade.CM, passo2);
        inativa.setAtivo(false);
        repository.saveAndFlush(inativa);

        assertThat(repository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .extracting(permissao -> permissao.getInstrumentoProcedimentoConfig().getId())
                .containsExactly(passo1.getId());
    }
}
