package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DinamicaRepositoryTest {

    @Autowired
    private DinamicaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Test
    void deveGuardarELerDinamica() {
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

        Dinamica nova = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor);
        Dinamica guardada = repository.saveAndFlush(nova);

        Optional<Dinamica> lida = repository.findById(guardada.getId());
        assertThat(lida).isPresent();
        assertThat(lida.get().getInstrumento().getId()).isEqualTo(instrumento.getId());
        assertThat(lida.get().getInstrumentoDinamicaConfig().getId()).isEqualTo(dinamicaConfig.getId());
        assertThat(lida.get().getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(lida.get().getOrigemDados()).isEqualTo("NATIVO");
        assertThat(lida.get().getDataInicio()).isNotNull();
        assertThat(lida.get().getDataConclusao()).isNull();
        assertThat(lida.get().getCriadoPor().getId()).isEqualTo(criadoPor.getId());
        assertThat(lida.get().getDescritivo()).isEqualTo("Alteração do PDM");
        assertThat(lida.get().getTentativa()).isEqualTo(1);
    }

    @Test
    void findByIdParaCriarProcessoDeveDevolverADinamicaPedidaEVazioQuandoNaoExiste() {
        Dinamica dinamica = criarDinamica(criarInstrumento(), "ELABORACAO", EstadoDinamica.EM_CURSO, "NATIVO");

        assertThat(repository.findByIdParaCriarProcesso(dinamica.getId())).get().extracting(Dinamica::getId)
                .isEqualTo(dinamica.getId());
        assertThat(repository.findByIdParaCriarProcesso(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deveAtribuirNumerosDeDinamicaDeCincoDigitosDiferentesNaPrimeiraGravacao() {
        Instrumento instrumento = criarInstrumento();

        Dinamica primeira = criarDinamica(instrumento, "ELABORACAO", EstadoDinamica.EM_CURSO, "NATIVO");
        Dinamica segunda = criarDinamica(instrumento, "REVISAO", EstadoDinamica.EM_CURSO, "NATIVO");

        assertThat(primeira.getNumeroDinamica()).matches("\\d{5}");
        assertThat(segunda.getNumeroDinamica()).matches("\\d{5}");
        assertThat(Integer.parseInt(segunda.getNumeroDinamica())).isGreaterThan(Integer.parseInt(primeira.getNumeroDinamica()));
    }

    @Test
    void deveListarAsDinamicasQueAbrangemOInstrumentoPelaLigacao() {
        Instrumento instrumento = criarInstrumento();
        Dinamica primeira = criarDinamica(instrumento, "ELABORACAO", EstadoDinamica.EM_CURSO, "NATIVO");
        Dinamica segunda = criarDinamica(instrumento, "REVISAO", EstadoDinamica.TERMINADA, "NATIVO");

        assertThat(repository.listarQueAbrangem(instrumento.getId())).containsExactlyInAnyOrder(primeira, segunda);
        assertThat(repository.listarQueAbrangem(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deveGuardarELerDataCaducidadeEAlertasEnviados() {
        Instrumento instrumento = criarInstrumento();
        Dinamica semCaducidade = criarDinamica(instrumento, "ELABORACAO", EstadoDinamica.EM_CURSO, "NATIVO");
        assertThat(semCaducidade.getDataCaducidade()).isNull();
        assertThat(semCaducidade.getAlertasEnviados()).isNull();

        semCaducidade.setDataCaducidade(LocalDate.of(2027, 1, 31));
        semCaducidade.setAlertasEnviados(List.of("UM_MES"));
        repository.saveAndFlush(semCaducidade);

        Dinamica lida = repository.findById(semCaducidade.getId()).orElseThrow();
        assertThat(lida.getDataCaducidade()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(lida.getAlertasEnviados()).contains("UM_MES");
    }

    @Test
    void deveContarApenasDinamicasEmCursoComCaducidadeAnteriorAData() {
        Instrumento instrumento = criarInstrumento();
        LocalDate hoje = LocalDate.of(2026, 10, 23);
        criarDinamicaComCaducidade(instrumento, "ELABORACAO", EstadoDinamica.EM_CURSO, hoje.minusDays(1));
        criarDinamicaComCaducidade(instrumento, "REVISAO", EstadoDinamica.EM_CURSO, hoje);
        criarDinamicaComCaducidade(instrumento, "ALTERACAO", EstadoDinamica.EM_CURSO, null);
        criarDinamicaComCaducidade(instrumento, "REVOGACAO", EstadoDinamica.TERMINADA, hoje.minusDays(1));

        assertThat(repository.countByEstadoAndDataCaducidadeBefore(EstadoDinamica.EM_CURSO, hoje)).isEqualTo(1);
    }

    private Dinamica criarDinamicaComCaducidade(Instrumento instrumento, String codigoTipoDinamica, EstadoDinamica estado,
                                                LocalDate dataCaducidade) {
        Dinamica dinamica = criarDinamica(instrumento, codigoTipoDinamica, estado, "NATIVO");
        dinamica.setDataCaducidade(dataCaducidade);
        return repository.saveAndFlush(dinamica);
    }

    private Instrumento criarInstrumento() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        return instrumentoRepository.saveAndFlush(new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
    }

    private Dinamica criarDinamica(Instrumento instrumento, String codigoTipoDinamica, EstadoDinamica estado, String origemDados) {
        Utilizador criadoPor = utilizadorRepository.findAll().get(0);
        TipoDinamica tipo = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoTipoDinamica, codigoTipoDinamica));
        InstrumentoDinamicaConfig config = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(instrumento.getTipoInstrumento(), tipo));
        Dinamica dinamica = new Dinamica(instrumento, config, "Alteração do PDM", criadoPor);
        dinamica.setEstado(estado);
        dinamica.setOrigemDados(origemDados);
        return repository.saveAndFlush(dinamica);
    }

    @Test
    void existsByInstrumentoETipoDinamicaEEstadoDeveDistinguirOInstrumentoEOEstado() {
        Instrumento instrumento = criarInstrumento();
        criarDinamica(instrumento, "ELABORACAO", EstadoDinamica.TERMINADA, "NATIVO");

        assertThat(repository.existeAbrangendoComTipoEEstado(
                instrumento.getId(), "ELABORACAO", EstadoDinamica.TERMINADA)).isTrue();
        assertThat(repository.existeAbrangendoComTipoEEstado(
                instrumento.getId(), "ELABORACAO", EstadoDinamica.EM_CURSO)).isFalse();
        assertThat(repository.existeAbrangendoComTipoEEstado(
                UUID.randomUUID(), "ELABORACAO", EstadoDinamica.TERMINADA)).isFalse();
    }

    @Test
    void existsByInstrumentoETipoDinamicaSemEstadoDeveCobrirQualquerEstado() {
        Instrumento instrumento = criarInstrumento();
        criarDinamica(instrumento, "REVISAO", EstadoDinamica.EM_CURSO, "NATIVO");

        assertThat(repository.existeAbrangendoComTipo(
                instrumento.getId(), "REVISAO")).isTrue();
        assertThat(repository.existeAbrangendoComTipo(
                instrumento.getId(), "REVOGACAO")).isFalse();
    }

    @Test
    void existsByInstrumentoEOrigemDadosETipoDinamicaDeveDetetarEspelhoAbertoHomonimo() {
        Instrumento instrumento = criarInstrumento();
        criarDinamica(instrumento, "ALTERACAO", EstadoDinamica.EM_CURSO, "ESPELHO_ABERTO");

        assertThat(repository.existeAbrangendoComOrigemETipo(
                instrumento.getId(), "ESPELHO_ABERTO", "ALTERACAO")).isTrue();
        assertThat(repository.existeAbrangendoComOrigemETipo(
                instrumento.getId(), "ESPELHO_ABERTO", "REVISAO")).isFalse();
        assertThat(repository.existeAbrangendoComOrigemETipo(
                instrumento.getId(), "NATIVO", "ALTERACAO")).isFalse();
    }

    private Dinamica recarregar(Dinamica dinamica) {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(dinamica.getId()).orElseThrow();
    }

    @Test
    void findByInstrumentoEEstadoInDeveDevolverSoAsDinamicasDosEstadosPedidosDoInstrumento() {
        Instrumento instrumento = criarInstrumento();
        Dinamica emCurso = criarDinamica(instrumento, "EMCURSO", EstadoDinamica.EM_CURSO, "NATIVO");
        Dinamica caducada = criarDinamica(instrumento, "CADUCADA", EstadoDinamica.CADUCADA, "NATIVO");
        criarDinamica(instrumento, "TERMINADA", EstadoDinamica.TERMINADA, "NATIVO");

        assertThat(repository.findByInstrumento_IdAndEstadoIn(instrumento.getId(),
                List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA)))
                .containsExactlyInAnyOrder(emCurso, caducada);
        assertThat(repository.findByInstrumento_IdAndEstadoIn(instrumento.getId(), List.of(EstadoDinamica.TERMINADA)))
                .hasSize(1);
        assertThat(repository.findByInstrumento_IdAndEstadoIn(UUID.randomUUID(),
                List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).isEmpty();
    }

    @Test
    void findIdsParaVerificacaoCaducidadeDeveDevolverSoAsEmCursoComDataAteAoLimite() {
        LocalDate hoje = LocalDate.of(2026, 11, 2);
        Instrumento instrumento = criarInstrumento();
        Dinamica aVinteDias = criarDinamicaComCaducidade(instrumento, "D20", EstadoDinamica.EM_CURSO, hoje.plusDays(20));
        criarDinamicaComCaducidade(instrumento, "D40", EstadoDinamica.EM_CURSO, hoje.plusDays(40));
        criarDinamicaComCaducidade(instrumento, "TERMINADA", EstadoDinamica.TERMINADA, hoje.minusDays(5));
        criarDinamicaComCaducidade(instrumento, "CADUCADA", EstadoDinamica.CADUCADA, hoje.minusDays(5));
        criarDinamicaComCaducidade(instrumento, "SEMDATA", EstadoDinamica.EM_CURSO, null);

        assertThat(repository.findIdsParaVerificacaoCaducidade(hoje.plusMonths(1))).containsExactly(aVinteDias.getId());
    }

    @Test
    void findIdsParaVerificacaoCaducidadeDeveOrdenarPorDataDeCaducidade() {
        LocalDate hoje = LocalDate.of(2026, 11, 2);
        Instrumento instrumento = criarInstrumento();
        Dinamica maisLonge = criarDinamicaComCaducidade(instrumento, "LONGE", EstadoDinamica.EM_CURSO, hoje.plusDays(20));
        Dinamica passada = criarDinamicaComCaducidade(instrumento, "PASSADA", EstadoDinamica.EM_CURSO, hoje.minusDays(3));
        Dinamica maisPerto = criarDinamicaComCaducidade(instrumento, "PERTO", EstadoDinamica.EM_CURSO, hoje.plusDays(2));

        assertThat(repository.findIdsParaVerificacaoCaducidade(hoje.plusMonths(1)))
                .containsExactly(passada.getId(), maisPerto.getId(), maisLonge.getId());
    }

    @Test
    void registarAlertasDeveGuardarOsLimiaresDeUmaDinamicaEmCurso() {
        Dinamica dinamica = criarDinamicaComCaducidade(criarInstrumento(), "ALERTAS", EstadoDinamica.EM_CURSO,
                LocalDate.of(2026, 12, 1));

        int atualizadas = repository.registarAlertas(dinamica.getId(), List.of("UM_MES", "QUINZE_DIAS"));

        assertThat(atualizadas).isEqualTo(1);
        assertThat(recarregar(dinamica).getAlertasEnviados()).containsExactly("UM_MES", "QUINZE_DIAS");
    }

    @Test
    void registarAlertasNaoDeveAlterarUmaDinamicaCaducada() {
        Dinamica dinamica = criarDinamicaComCaducidade(criarInstrumento(), "CADUCADA", EstadoDinamica.CADUCADA,
                LocalDate.of(2026, 10, 1));

        int atualizadas = repository.registarAlertas(dinamica.getId(), List.of("DATA"));

        assertThat(atualizadas).isZero();
        assertThat(recarregar(dinamica).getAlertasEnviados()).isNull();
    }

    @Test
    void marcarCaducadaDeveCaducarSoUmaDinamicaEmCurso() {
        Instrumento instrumento = criarInstrumento();
        Dinamica emCurso = criarDinamicaComCaducidade(instrumento, "EMCURSO", EstadoDinamica.EM_CURSO, LocalDate.of(2026, 11, 2));
        Dinamica terminada = criarDinamicaComCaducidade(instrumento, "TERMINADA", EstadoDinamica.TERMINADA, LocalDate.of(2026, 11, 2));

        assertThat(repository.marcarCaducada(emCurso.getId())).isEqualTo(1);
        assertThat(repository.marcarCaducada(terminada.getId())).isZero();

        assertThat(recarregar(emCurso).getEstado()).isEqualTo(EstadoDinamica.CADUCADA);
        assertThat(recarregar(terminada).getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
    }

    @Test
    void consultasAbrangendoDevemContarADinamicaEmCadaInstrumentoQueAbrangeENaoNoutros() {
        Instrumento pdmA = criarInstrumento();
        Instrumento pdmB = outroInstrumento(pdmA, "2026/PDM/002");
        Instrumento pdmC = outroInstrumento(pdmA, "2026/PDM/003");
        Dinamica suspensao = criarDinamica(pdmA, "SUSPENSAO", EstadoDinamica.EM_CURSO, "ESPELHO_ABERTO");
        abranger(suspensao, pdmB);

        for (Instrumento abrangido : List.of(pdmA, pdmB)) {
            UUID id = abrangido.getId();
            assertThat(repository.existeAbrangendoComTipoEEstado(id, "SUSPENSAO", EstadoDinamica.EM_CURSO)).isTrue();
            assertThat(repository.existeAbrangendoComTipoEEstado(id, "SUSPENSAO", EstadoDinamica.TERMINADA)).isFalse();
            assertThat(repository.existeAbrangendoComTipo(id, "SUSPENSAO")).isTrue();
            assertThat(repository.existeAbrangendoComTipo(id, "REVISAO")).isFalse();
            assertThat(repository.existeAbrangendoComEstado(id, EstadoDinamica.EM_CURSO)).isTrue();
            assertThat(repository.existeAbrangendoComEstado(id, EstadoDinamica.CADUCADA)).isFalse();
            assertThat(repository.existeAbrangendo(id)).isTrue();
            assertThat(repository.existeAbrangendoComOrigemETipo(id, "ESPELHO_ABERTO", "SUSPENSAO")).isTrue();
            assertThat(repository.existeAbrangendoComOrigemETipo(id, "NATIVO", "SUSPENSAO")).isFalse();
        }
        UUID foraDaDinamica = pdmC.getId();
        assertThat(repository.existeAbrangendoComTipoEEstado(foraDaDinamica, "SUSPENSAO", EstadoDinamica.EM_CURSO)).isFalse();
        assertThat(repository.existeAbrangendoComTipo(foraDaDinamica, "SUSPENSAO")).isFalse();
        assertThat(repository.existeAbrangendoComEstado(foraDaDinamica, EstadoDinamica.EM_CURSO)).isFalse();
        assertThat(repository.existeAbrangendo(foraDaDinamica)).isFalse();
        assertThat(repository.existeAbrangendoComOrigemETipo(foraDaDinamica, "ESPELHO_ABERTO", "SUSPENSAO")).isFalse();
    }

    @Test
    void ultimaTerminadaDeveVirDasDinamicasQueAbrangemEEmCursoSoDasIniciadasNoInstrumento() {
        Instrumento pdmA = criarInstrumento();
        Instrumento pdmB = outroInstrumento(pdmA, "2026/PDM/002");
        Dinamica antigaDeB = criarDinamica(pdmB, "ELABORACAO", EstadoDinamica.TERMINADA, "NATIVO");
        antigaDeB.setDataConclusao(Instant.parse("2026-01-10T10:00:00Z"));
        Dinamica recenteIniciadaEmA = criarDinamica(pdmA, "ALTERACAO", EstadoDinamica.TERMINADA, "NATIVO");
        recenteIniciadaEmA.setDataConclusao(Instant.parse("2026-06-10T10:00:00Z"));
        Dinamica emCursoIniciadaEmA = criarDinamica(pdmA, "SUSPENSAO", EstadoDinamica.EM_CURSO, "NATIVO");
        repository.saveAllAndFlush(List.of(antigaDeB, recenteIniciadaEmA));
        abranger(recenteIniciadaEmA, pdmB);
        abranger(emCursoIniciadaEmA, pdmB);

        assertThat(repository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(pdmB.getId(), EstadoDinamica.TERMINADA,
                Limit.of(1))).extracting(Dinamica::getId).containsExactly(recenteIniciadaEmA.getId());
        assertThat(repository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(pdmB.getId(), EstadoDinamica.TERMINADA,
                Limit.of(2))).extracting(Dinamica::getId).containsExactly(recenteIniciadaEmA.getId(), antigaDeB.getId());
        assertThat(repository.findByInstrumento_IdAndEstadoIn(pdmB.getId(),
                List.of(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA))).isEmpty();
    }

    @Test
    void cadaConsultaAbrangendoDeveFazerUmaSoIdaABaseDeDados() {
        Instrumento pdmA = criarInstrumento();
        Instrumento pdmB = outroInstrumento(pdmA, "2026/PDM/002");
        Dinamica dinamica = criarDinamica(pdmA, "SUSPENSAO", EstadoDinamica.TERMINADA, "NATIVO");
        abranger(dinamica, pdmB);
        UUID id = pdmB.getId();

        assertThat(statements(() -> repository.existeAbrangendoComTipoEEstado(id, "SUSPENSAO", EstadoDinamica.TERMINADA)))
                .isEqualTo(1);
        assertThat(statements(() -> repository.existeAbrangendoComTipo(id, "SUSPENSAO"))).isEqualTo(1);
        assertThat(statements(() -> repository.existeAbrangendoComEstado(id, EstadoDinamica.TERMINADA))).isEqualTo(1);
        assertThat(statements(() -> repository.existeAbrangendo(id))).isEqualTo(1);
        assertThat(statements(() -> repository.existeAbrangendoComOrigemETipo(id, "NATIVO", "SUSPENSAO"))).isEqualTo(1);
        assertThat(statements(() -> repository.listarAbrangendoComEstadoPelaConclusaoMaisRecente(
                id, EstadoDinamica.TERMINADA, Limit.of(1)).get(0).getInstrumentoDinamicaConfig().getTipoDinamica().getNome()))
                .isEqualTo(1);
    }

    @Test
    void oInstrumentoDeInicioDeveEstarSempreNosInstrumentosQueADinamicaAbrange() {
        Instrumento instrumento = criarInstrumento();
        Dinamica dinamica = criarDinamica(instrumento, "ELABORACAO", EstadoDinamica.EM_CURSO, "NATIVO");

        Dinamica lida = recarregar(dinamica);

        assertThat(lida.getInstrumentos()).extracting(Instrumento::getId).containsExactly(instrumento.getId());
        assertThat(entityManager.createNativeQuery("SELECT count(*) FROM dinamica d WHERE NOT EXISTS (SELECT 1 FROM "
                + "dinamica_instrumento di WHERE di.dinamica_id = d.id AND di.instrumento_id = d.instrumento_id)")
                .getSingleResult()).isEqualTo(0L);
    }

    private Instrumento outroInstrumento(Instrumento existente, String designacao) {
        Utilizador criadoPor = utilizadorRepository.findAll().get(0);
        return instrumentoRepository.saveAndFlush(
                new Instrumento(existente.getTipoInstrumento(), "1", designacao, criadoPor, criadoPor));
    }

    private void abranger(Dinamica dinamica, Instrumento instrumento) {
        entityManager.flush();
        entityManager.createNativeQuery("INSERT INTO dinamica_instrumento (dinamica_id, instrumento_id) VALUES (?1, ?2)")
                .setParameter(1, dinamica.getId()).setParameter(2, instrumento.getId()).executeUpdate();
        entityManager.clear();
    }

    private long statements(Supplier<Object> consulta) {
        entityManager.flush();
        entityManager.clear();
        Statistics estatisticas = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        consulta.get();
        return estatisticas.getPrepareStatementCount();
    }
}
