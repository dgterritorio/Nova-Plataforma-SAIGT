package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProcessoRepositoryTest {

    @Autowired
    private ProcessoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

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

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private SgifrRedeDefesaRepository redeDefesaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveGravarERelerAsRedesDeDefesaDoProcesso() {
        jdbcTemplate.update("INSERT INTO sgifr_rede_defesa (codigo, nome, aplica_pra, aplica_psa, publicavel) VALUES "
                + "('RPFGC', 'Rede primária de faixas de gestão de combustível', TRUE, FALSE, TRUE), "
                + "('RVF', 'Rede viária florestal', FALSE, TRUE, TRUE), "
                + "('RPA', 'Rede de pontos de água', FALSE, TRUE, TRUE)");
        SgifrRedeDefesa rvf = redeDefesaRepository.findByCodigo("RVF").orElseThrow();
        SgifrRedeDefesa rpa = redeDefesaRepository.findByCodigo("RPA").orElseThrow();
        Processo processo = criarProcesso();
        assertThat(processo.getRedesDefesa()).isEmpty();

        processo.getRedesDefesa().add(rvf);
        processo.getRedesDefesa().add(rpa);
        repository.saveAndFlush(processo);
        entityManager.clear();

        assertThat(jdbcTemplate.queryForList("SELECT r.codigo FROM processo_rede_defesa prd "
                + "JOIN sgifr_rede_defesa r ON r.id = prd.rede_defesa_id WHERE prd.processo_id = ? ORDER BY r.codigo",
                String.class, processo.getId())).containsExactly("RPA", "RVF");
        assertThat(repository.findById(processo.getId()).orElseThrow().getRedesDefesa())
                .extracting(SgifrRedeDefesa::getCodigo).containsExactlyInAnyOrder("RVF", "RPA");
    }

    private Processo criarProcesso() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));

        return repository.saveAndFlush(new Processo(dinamica, procedimentoConfig, criadoPor, cmLisboa));
    }

    @Test
    void deveGuardarELerProcessoComEstadoDefault() {
        Processo guardado = criarProcesso();

        Optional<Processo> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getEstado()).isEqualTo(EstadoProcesso.EM_SUBMISSAO);
        assertThat(lido.get().getOcorrencia()).isEqualTo(1);
        assertThat(lido.get().getVersao()).isZero();
        assertThat(lido.get().getOrigemDados()).isEqualTo("NATIVO");
        assertThat(lido.get().getCriadoPor()).isNotNull();
        assertThat(lido.get().getPcmAtribuidoA()).isNull();
        assertThat(lido.get().getTipoAto()).isNull();
    }

    @Test
    void deveAtribuirNumerosDeProcessoDeCincoDigitosDiferentesNaPrimeiraGravacao() {
        Processo primeiro = criarProcesso();
        Processo segundo = new Processo(primeiro.getDinamica(), primeiro.getInstrumentoProcedimentoConfig(), primeiro.getCriadoPor(), primeiro.getEntidade());
        segundo.setOcorrencia(2);
        segundo = repository.saveAndFlush(segundo);
        String numeroAntes = primeiro.getNumeroProcesso();
        primeiro.setEstado(EstadoProcesso.ANULADO);
        repository.saveAndFlush(primeiro);

        assertThat(numeroAntes).matches("\\d{5}");
        assertThat(segundo.getNumeroProcesso()).matches("\\d{5}").isNotEqualTo(numeroAntes);
        assertThat(repository.findById(primeiro.getId()).orElseThrow().getNumeroProcesso()).isEqualTo(numeroAntes);
    }

    @Test
    void devePersistirIncmEnrollmentIdEDrParteIdQuandoCongeladosNaSubmissao() {
        Processo guardado = criarProcesso();
        guardado.setIncmEnrollmentId("2015482");
        guardado.setDrParteId(1234567891234567L);
        repository.saveAndFlush(guardado);

        Optional<Processo> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getIncmEnrollmentId()).isEqualTo("2015482");
        assertThat(lido.get().getDrParteId()).isEqualTo(1234567891234567L);
    }

    @Test
    void deveAceitarProcessoSemIncmEnrollmentIdNemDrParteIdNoRascunho() {
        Processo guardado = criarProcesso();

        Optional<Processo> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getIncmEnrollmentId()).isNull();
        assertThat(lido.get().getDrParteId()).isNull();
    }

    @Test
    void deveGuardarELerElementosAfetados() {
        Processo guardado = criarProcesso();
        assertThat(guardado.getElementosAfetados()).isNull();

        guardado.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        repository.saveAndFlush(guardado);
        entityManager.clear();

        assertThat(repository.findById(guardado.getId()).orElseThrow().getElementosAfetados())
                .containsExactly("PECAS_ESCRITAS");
    }

    @Test
    void deveDetetarConflitoDeEscritaConcorrentePorVersao() {
        Processo guardado = criarProcesso();

        Processo copiaAntiga = repository.findById(guardado.getId()).orElseThrow();
        entityManager.detach(copiaAntiga);

        Processo copiaAtual = repository.findById(guardado.getId()).orElseThrow();
        copiaAtual.setEstado(EstadoProcesso.ENVIADO_DGT);
        repository.saveAndFlush(copiaAtual);
        entityManager.clear();

        copiaAntiga.setEstado(EstadoProcesso.ANULADO);
        assertThatThrownBy(() -> repository.saveAndFlush(copiaAntiga))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void existsByDinamicaEInstrumentoProcedimentoConfigEEstadoDeveContarQualquerTentativaPublicada() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));

        Processo tentativa1 = new Processo(dinamica, procedimentoConfig, criadoPor, cmLisboa);
        tentativa1.setEstado(EstadoProcesso.ANULADO);
        repository.saveAndFlush(tentativa1);
        Processo tentativa2 = new Processo(dinamica, procedimentoConfig, criadoPor, cmLisboa);
        tentativa2.setEstado(EstadoProcesso.PUBLICADO);
        tentativa2.setOcorrencia(2);
        repository.saveAndFlush(tentativa2);

        assertThat(repository.existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(dinamica, procedimentoConfig, EstadoProcesso.PUBLICADO))
                .isTrue();
        assertThat(repository.existsByDinamicaAndInstrumentoProcedimentoConfigAndEstado(dinamica, procedimentoConfig, EstadoProcesso.REJEITADO_DGT))
                .isFalse();
    }

    @Test
    void existsByDinamicaInstrumentoETipoProcedimentoEEstadoDeveContarQualquerTentativa() {
        Processo processo = criarProcesso();
        processo.setEstado(EstadoProcesso.PUBLICADO);
        processo.setOcorrencia(3);
        repository.saveAndFlush(processo);
        UUID instrumentoId = processo.getDinamica().getInstrumento().getId();
        String codigoProcedimento = processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo();

        assertThat(repository
                .existeProcessoAbrangendoComTipoEEstado(
                        instrumentoId, codigoProcedimento, EstadoProcesso.PUBLICADO))
                .isTrue();
        assertThat(repository
                .existeProcessoAbrangendoComTipoEEstado(
                        instrumentoId, codigoProcedimento, EstadoProcesso.EM_SUBMISSAO))
                .isFalse();
        assertThat(repository
                .existeProcessoAbrangendoComTipoEEstado(
                        UUID.randomUUID(), codigoProcedimento, EstadoProcesso.PUBLICADO))
                .isFalse();
    }

    @Test
    void existsByDinamicaInstrumentoETipoProcedimentoSemEstadoDeveCobrirQualquerEstado() {
        Processo processo = criarProcesso();
        UUID instrumentoId = processo.getDinamica().getInstrumento().getId();
        String codigoProcedimento = processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo();

        assertThat(repository
                .existeProcessoAbrangendoComTipo(
                        instrumentoId, codigoProcedimento))
                .isTrue();
        assertThat(repository
                .existeProcessoAbrangendoComTipo(
                        instrumentoId, "OUTRO_PROCEDIMENTO"))
                .isFalse();
    }

    @Test
    void existeProcessoAbrangendoDeveContarEmCadaInstrumentoQueADinamicaAbrangeNumaSoConsulta() {
        Processo processo = criarProcesso();
        processo.setEstado(EstadoProcesso.PUBLICADO);
        repository.saveAndFlush(processo);
        Instrumento pdmA = processo.getDinamica().getInstrumento();
        Utilizador criadoPor = processo.getCriadoPor();
        Instrumento pdmB = instrumentoRepository.saveAndFlush(
                new Instrumento(pdmA.getTipoInstrumento(), "1", "2026/PDM/002", criadoPor, criadoPor));
        Instrumento pdmC = instrumentoRepository.saveAndFlush(
                new Instrumento(pdmA.getTipoInstrumento(), "1", "2026/PDM/003", criadoPor, criadoPor));
        jdbcTemplate.update("INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) VALUES (?, ?)",
                processo.getDinamica().getId(), pdmB.getId());
        entityManager.clear();
        String codigo = processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo();

        for (UUID abrangido : List.of(pdmA.getId(), pdmB.getId())) {
            assertThat(repository.existeProcessoAbrangendoComTipoEEstado(abrangido, codigo, EstadoProcesso.PUBLICADO)).isTrue();
            assertThat(repository.existeProcessoAbrangendoComTipoEEstado(abrangido, codigo, EstadoProcesso.EM_SUBMISSAO)).isFalse();
            assertThat(repository.existeProcessoAbrangendoComTipo(abrangido, codigo)).isTrue();
            assertThat(repository.existeProcessoAbrangendoComTipo(abrangido, "OUTRO_PROCEDIMENTO")).isFalse();
        }
        assertThat(repository.existeProcessoAbrangendoComTipoEEstado(pdmC.getId(), codigo, EstadoProcesso.PUBLICADO)).isFalse();
        assertThat(repository.existeProcessoAbrangendoComTipo(pdmC.getId(), codigo)).isFalse();

        Statistics estatisticas = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        repository.existeProcessoAbrangendoComTipoEEstado(pdmB.getId(), codigo, EstadoProcesso.PUBLICADO);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
        estatisticas.clear();
        repository.existeProcessoAbrangendoComTipo(pdmB.getId(), codigo);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void contarProcessosAtivosPorConfigDinamicaDeveAgruparPorConfigEExcluirEstadosTerminais() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));

        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig configElaboracao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        Dinamica dinamicaElaboracao = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, configElaboracao, "Alteração do PDM", criadoPor));
        InstrumentoProcedimentoConfig procConfigElaboracao = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(configElaboracao, decisao, 1, "SEGUNDA_H"));

        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig configRevisao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, revisao));
        Dinamica dinamicaRevisao = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, configRevisao, "Alteração do PDM", criadoPor));
        InstrumentoProcedimentoConfig procConfigRevisao = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(configRevisao, decisao, 1, "SEGUNDA_H"));

        // Elaboração: uma ativa (EM_VALIDACAO_DGT) e uma terminal (PUBLICADO) — só a primeira conta.
        Processo ativaElaboracao = new Processo(dinamicaElaboracao, procConfigElaboracao, criadoPor, cmLisboa);
        ativaElaboracao.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        repository.saveAndFlush(ativaElaboracao);
        Processo publicadaElaboracao = new Processo(dinamicaElaboracao, procConfigElaboracao, criadoPor, cmLisboa);
        publicadaElaboracao.setEstado(EstadoProcesso.PUBLICADO);
        publicadaElaboracao.setOcorrencia(2);
        repository.saveAndFlush(publicadaElaboracao);

        // Revisão: ENVIADO_DGT e ANULADO_INCM — ambas ativas, a anulada pela INCM corrige-se na mesma linha.
        Processo ativaRevisao = new Processo(dinamicaRevisao, procConfigRevisao, criadoPor, cmLisboa);
        ativaRevisao.setEstado(EstadoProcesso.ENVIADO_DGT);
        repository.saveAndFlush(ativaRevisao);
        Processo anuladaIncmRevisao = new Processo(dinamicaRevisao, procConfigRevisao, criadoPor, cmLisboa);
        anuladaIncmRevisao.setEstado(EstadoProcesso.ANULADO_INCM);
        anuladaIncmRevisao.setOcorrencia(2);
        repository.saveAndFlush(anuladaIncmRevisao);

        List<ProcessoRepository.ContagemProcessosAtivosProjecao> contagens = repository
                .contarProcessosAtivosPorConfigDinamica(EstadoProcesso.TERMINAIS);

        assertThat(contagens).extracting(
                        ProcessoRepository.ContagemProcessosAtivosProjecao::getConfigId,
                        ProcessoRepository.ContagemProcessosAtivosProjecao::getTotal)
                .containsExactlyInAnyOrder(
                        tuple(configElaboracao.getId(), 1L),
                        tuple(configRevisao.getId(), 2L));
    }

    @Test
    void findEmCursoDaTentativaDeveExcluirTerminaisAtosPontuaisEOutrasDinamicas() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        InstrumentoDinamicaConfig configAlteracao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, alteracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, configAlteracao, "Alteração do PDM", criadoPor));
        TipoDinamica revisao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("REVISAO", "Revisão"));
        InstrumentoDinamicaConfig configRevisao = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(pdm, revisao));
        Dinamica outraDinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, configRevisao, "Revisão do PDM", criadoPor));

        InstrumentoProcedimentoConfig publicado = procedimentoConfig(configAlteracao, "DECISAO_INICIO", 1, false);
        InstrumentoProcedimentoConfig anulado = procedimentoConfig(configAlteracao, "ABERTURA_DISCUSSAO_PUBLICA", 2, false);
        InstrumentoProcedimentoConfig enviado = procedimentoConfig(configAlteracao, "PUBLICACAO_ALTERACAO", 3, false);
        InstrumentoProcedimentoConfig anuladoIncm = procedimentoConfig(configAlteracao, "CONSTITUICAO_CC", 4, false);
        InstrumentoProcedimentoConfig pontual = procedimentoConfig(configAlteracao, "REVOGACAO_ATO", 5, true);

        processo(dinamica, publicado, criadoPor, EstadoProcesso.PUBLICADO);
        processo(dinamica, anulado, criadoPor, EstadoProcesso.ANULADO);
        Processo emCurso = processo(dinamica, enviado, criadoPor, EstadoProcesso.ENVIADO_DGT);
        Processo emCursoAnuladoIncm = processo(dinamica, anuladoIncm, criadoPor, EstadoProcesso.ANULADO_INCM);
        processo(dinamica, pontual, criadoPor, EstadoProcesso.ENVIADO_DGT);
        processo(outraDinamica, enviado, criadoPor, EstadoProcesso.EM_SUBMISSAO);
        entityManager.clear();

        List<Processo> resultado = repository.findEmCursoDaTentativa(dinamica, 1, EstadoProcesso.TERMINAIS);

        assertThat(resultado).extracting(Processo::getId).containsExactlyInAnyOrder(emCurso.getId(), emCursoAnuladoIncm.getId());
        assertThat(resultado).allSatisfy(processo ->
                assertThat(org.hibernate.Hibernate.isInitialized(processo.getInstrumentoProcedimentoConfig())).isTrue());
    }

    @Test
    void asConsultasDoSequenciamentoContamSoATentativaDadaEOProcessoGuardaADaDinamicaNaCriacao() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(new Utilizador("Rui Matos", "rui.matos@cm-lisboa.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/002", criadoPor, criadoPor));
        TipoDinamica alteracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALTERACAO", "Alteração"));
        InstrumentoDinamicaConfig configAlteracao = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, alteracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, configAlteracao, "Alteração do PDM", criadoPor));
        InstrumentoProcedimentoConfig decisao = procedimentoConfig(configAlteracao, "DECISAO_INICIO", 1, false);
        InstrumentoProcedimentoConfig discussao = procedimentoConfig(configAlteracao, "ABERTURA_DISCUSSAO_PUBLICA", 2, false);

        Processo decisaoDaPrimeira = processo(dinamica, decisao, criadoPor, EstadoProcesso.PUBLICADO);
        Processo discussaoDaPrimeira = processo(dinamica, discussao, criadoPor, EstadoProcesso.ENVIADO_DGT);
        jdbcTemplate.update("UPDATE dinamica SET tentativa = 2 WHERE id = ?", dinamica.getId());
        entityManager.clear();
        Dinamica naSegunda = dinamicaRepository.findById(dinamica.getId()).orElseThrow();
        Processo decisaoDaSegunda = processo(naSegunda, decisao, criadoPor, EstadoProcesso.EM_SUBMISSAO);
        entityManager.clear();

        assertThat(repository.findById(decisaoDaPrimeira.getId()).orElseThrow().getTentativa()).isEqualTo(1);
        assertThat(repository.findById(discussaoDaPrimeira.getId()).orElseThrow().getTentativa()).isEqualTo(1);
        assertThat(repository.findById(decisaoDaSegunda.getId()).orElseThrow().getTentativa()).isEqualTo(2);
        assertThat(repository.findById(decisaoDaSegunda.getId()).orElseThrow().getAproveitaAtosAnteriores()).isNull();

        assertThat(repository.findEmCursoDaTentativa(naSegunda, 2, EstadoProcesso.TERMINAIS))
                .extracting(Processo::getId).containsExactly(decisaoDaSegunda.getId());
        assertThat(repository.findEmCursoDaTentativa(naSegunda, 1, EstadoProcesso.TERMINAIS))
                .extracting(Processo::getId).containsExactly(discussaoDaPrimeira.getId());
        assertThat(repository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(
                naSegunda, 1, decisao, EstadoProcesso.PUBLICADO)).isTrue();
        assertThat(repository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(
                naSegunda, 2, decisao, EstadoProcesso.PUBLICADO)).isFalse();
        assertThat(repository.findByDinamicaAndTentativaAndInstrumentoProcedimentoConfigIn(
                naSegunda, 2, List.of(decisao, discussao))).extracting(Processo::getId)
                .containsExactly(decisaoDaSegunda.getId());
        assertThat(repository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                naSegunda, 2, discussao)).isEmpty();
        assertThat(repository.findTopByDinamicaAndTentativaAndInstrumentoProcedimentoConfigOrderByOcorrenciaDesc(
                naSegunda, 2, decisao)).map(Processo::getId).contains(decisaoDaSegunda.getId());
    }

    @Test
    void asContagensPorEntidadeUsamAEntidadeDoProcessoEnaoADoInstrumento() {
        Processo daLisboa = criarProcesso();
        Entidade cmPorto = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234568", "Câmara Municipal do Porto"));
        Processo doPorto = new Processo(daLisboa.getDinamica(), daLisboa.getInstrumentoProcedimentoConfig(),
                daLisboa.getCriadoPor(), cmPorto);
        doPorto.setOcorrencia(2);
        doPorto.setEstado(EstadoProcesso.PUBLICADO);
        doPorto.setDataPublicacaoDr(java.time.LocalDate.of(2026, 5, 1));
        repository.saveAndFlush(doPorto);
        daLisboa.setEstado(EstadoProcesso.PUBLICADO);
        daLisboa.setDataPublicacaoDr(java.time.LocalDate.of(2026, 4, 1));
        repository.saveAndFlush(daLisboa);
        Processo caducadoDoPorto = new Processo(daLisboa.getDinamica(), daLisboa.getInstrumentoProcedimentoConfig(),
                daLisboa.getCriadoPor(), cmPorto);
        caducadoDoPorto.setOcorrencia(3);
        caducadoDoPorto.setEstado(EstadoProcesso.ANULADO);
        repository.saveAndFlush(caducadoDoPorto);
        entityManager.clear();

        var publicados = repository.contarPublicadosPorEntidadeETipo(List.of(EstadoProcesso.PUBLICADO));
        var caducados = repository.contarCaducadosPorEntidade(List.of(EstadoProcesso.ANULADO));
        var ultimas = repository.ultimaPublicacaoPorEntidade(List.of(EstadoProcesso.PUBLICADO));

        assertThat(publicados).extracting(p -> p.getEntidadeNome() + ":" + p.getTipoInstrumentoCodigo() + ":" + p.getTotal())
                .containsExactlyInAnyOrder("Câmara Municipal de Lisboa:PDM:1", "Câmara Municipal do Porto:PDM:1");
        assertThat(caducados).extracting(c -> c.getEntidadeNome() + ":" + c.getTotal())
                .containsExactly("Câmara Municipal do Porto:1");
        assertThat(ultimas).extracting(u -> u.getEntidadeNome() + ":" + u.getUltimaPublicacao())
                .containsExactlyInAnyOrder("Câmara Municipal de Lisboa:2026-04-01", "Câmara Municipal do Porto:2026-05-01");
    }

    private InstrumentoProcedimentoConfig procedimentoConfig(InstrumentoDinamicaConfig dinamicaConfig, String codigo, int ordem,
                                                              boolean atoPontual) {
        TipoProcedimento tipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo));
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig, tipo, ordem, "SEGUNDA_H");
        config.setAtoPontual(atoPontual);
        return procedimentoConfigRepository.saveAndFlush(config);
    }

    private Processo processo(Dinamica dinamica, InstrumentoProcedimentoConfig config, Utilizador criadoPor, EstadoProcesso estado) {
        Processo processo = new Processo(dinamica, config, criadoPor, entidadeDeTeste());
        processo.setEstado(estado);
        return repository.saveAndFlush(processo);
    }

    /** A entidade que os testes deste ficheiro já criaram pelo NIPC, ou uma nova. */
    private Entidade entidadeDeTeste() {
        return entidadeRepository.findAllByNipc("501234567").stream().findFirst().orElseGet(
                () -> entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa")));
    }
}
