package com.conkord.saigt.persistence.regras;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegraCondicaoRepositoryTest {

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CondicaoRepository condicaoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    private InstrumentoDinamicaConfig criarDinamicaConfig() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        return dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, alteracao));
    }

    private InstrumentoProcedimentoConfig criarProcedimentoConfig() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        return procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
    }

    @Test
    void deveGuardarELerRegraComCondicoes() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, alteracao));

        Regra regra = Regra.paraDinamica(dinamicaConfig);
        regra.setOperador("AND");
        regra.setDescricao("Bloqueia nova Alteração enquanto o legado ainda gere o IGT");
        Regra regraGuardada = regraRepository.saveAndFlush(regra);

        Condicao condicao = new Condicao(regraGuardada, "DINAMICA_BLOQUEADA_LEGADO",
                "{\"origemDados\":\"ESPELHO_ABERTO\"}");
        condicao.setGrupo((short) 1);
        Condicao condicaoGuardada = condicaoRepository.saveAndFlush(condicao);

        Optional<Regra> regraLida = regraRepository.findById(regraGuardada.getId());
        assertThat(regraLida).isPresent();
        assertThat(regraLida.get().getInstrumentoDinamicaConfig().getId()).isEqualTo(dinamicaConfig.getId());
        assertThat(regraLida.get().getInstrumentoProcedimentoConfig()).isNull();
        assertThat(regraLida.get().getOperador()).isEqualTo("AND");
        assertThat(regraLida.get().isSubstituiSequenciamento()).isFalse();
        assertThat(regraLida.get().isAtivo()).isTrue();

        Optional<Condicao> condicaoLida = condicaoRepository.findById(condicaoGuardada.getId());
        assertThat(condicaoLida).isPresent();
        assertThat(condicaoLida.get().getRegra().getId()).isEqualTo(regraGuardada.getId());
        assertThat(condicaoLida.get().getAvaliador()).isEqualTo("DINAMICA_BLOQUEADA_LEGADO");
        assertThat(condicaoLida.get().getParametros()).contains("ESPELHO_ABERTO");
        assertThat(condicaoLida.get().getGrupo()).isEqualTo((short) 1);
        assertThat(condicaoLida.get().isAtivo()).isTrue();
    }

    @Test
    void deveRejeitarRegraComAsDuasConfiguracoesPreenchidas() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        InstrumentoDinamicaConfig dinamicaConfig = procedimentoConfig.getInstrumentoDinamicaConfig();

        Regra regra = Regra.paraDinamica(dinamicaConfig);
        regra.setOperador("AND");
        ReflectionTestUtils.setField(regra, "instrumentoProcedimentoConfig", procedimentoConfig);

        assertThatThrownBy(() -> regraRepository.saveAndFlush(regra))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarRegraSemNenhumaConfiguracao() {
        Regra regra = new Regra();
        regra.setOperador("AND");

        assertThatThrownBy(() -> regraRepository.saveAndFlush(regra))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarSegundaRegraParaOMesmoProcedimentoConfig() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        Regra primeira = Regra.paraProcedimento(procedimentoConfig);
        primeira.setOperador("AND");
        regraRepository.saveAndFlush(primeira);

        Regra segunda = Regra.paraProcedimento(procedimentoConfig);
        segunda.setOperador("OR");

        assertThatThrownBy(() -> regraRepository.saveAndFlush(segunda))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByRegraAndAtivoTrueDeveExcluirCondicoesInativas() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        Regra regra = regraRepository.saveAndFlush(Regra.paraDinamica(dinamicaConfig));
        Condicao ativa = condicaoRepository.saveAndFlush(new Condicao(regra, "DINAMICA_BLOQUEADA_LEGADO", "{}"));
        Condicao inativa = new Condicao(regra, "DINAMICA_BLOQUEADA_LEGADO", "{}");
        inativa.setAtivo(false);
        condicaoRepository.saveAndFlush(inativa);

        assertThat(condicaoRepository.findByRegraAndAtivoTrue(regra))
                .extracting(Condicao::getId)
                .containsExactly(ativa.getId());
    }

    @Test
    void findAvaliadoresDistintosDeveDevolverValoresUnicosEmBd() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        Regra regra = regraRepository.saveAndFlush(Regra.paraDinamica(dinamicaConfig));
        condicaoRepository.saveAndFlush(new Condicao(regra, "DINAMICA_BLOQUEADA_LEGADO", "{}"));
        condicaoRepository.saveAndFlush(new Condicao(regra, "DINAMICA_BLOQUEADA_LEGADO", "{}"));
        condicaoRepository.saveAndFlush(new Condicao(regra, "OCORRENCIA_EM_ESTADO", "{}"));

        assertThat(condicaoRepository.findAvaliadoresDistintos())
                .containsExactlyInAnyOrder("DINAMICA_BLOQUEADA_LEGADO", "OCORRENCIA_EM_ESTADO");
    }

    @Test
    void findByInstrumentoDinamicaConfigAndAtivoTrueDeveDevolverTodasAsRegrasAtivas() {
        InstrumentoDinamicaConfig dinamicaConfig = criarDinamicaConfig();
        Regra ativa1 = regraRepository.saveAndFlush(Regra.paraDinamica(dinamicaConfig));
        Regra ativa2 = regraRepository.saveAndFlush(Regra.paraDinamica(dinamicaConfig));
        Regra inativa = Regra.paraDinamica(dinamicaConfig);
        inativa.setAtivo(false);
        regraRepository.saveAndFlush(inativa);

        assertThat(regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(dinamicaConfig))
                .extracting(Regra::getId)
                .containsExactlyInAnyOrder(ativa1.getId(), ativa2.getId());
    }

    @Test
    void findByInstrumentoProcedimentoConfigAndAtivoTrueDeveDevolverVazioQuandoNaoExisteRegra() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();

        assertThat(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(procedimentoConfig)).isEmpty();
    }

    @Test
    void findByInstrumentoProcedimentoConfigAndAtivoTrueDeveDevolverARegraQuandoExiste() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        Regra regra = regraRepository.saveAndFlush(Regra.paraProcedimento(procedimentoConfig));

        assertThat(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(procedimentoConfig))
                .map(Regra::getId)
                .contains(regra.getId());
    }
}
