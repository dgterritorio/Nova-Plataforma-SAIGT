package com.conkord.saigt.service.snit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Diff da réplica contra a BD, com respostas do {@link ClienteSnit} na forma das do SNIT real. */
@SpringBootTest
@ActiveProfiles("test")
class SincronizacaoReferenciaSnitServiceTest {

    private static final ListaReferenciaSnit MUNICIPIOS = ListaReferenciaSnit.MUNICIPALITIES;
    private static final String LVT = "LISBOA E VALE DO TEJO";
    private static final String SINTRA = municipio("1111", "SINTRA");
    private static final String CASCAIS = municipio("1105", "CASCAIS");
    private static final String OEIRAS = municipio("1110", "OEIRAS");
    private static final String MAFRA = municipio("1109", "MAFRA");

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private SincronizacaoReferenciaSnitService service;

    @Autowired
    private SnitReferenciaRepository repository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SincronizacaoReferenciaSnitService.class);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void deveInserirOsTresMunicipiosAtivosNumaReplicaVazia() throws Exception {
        responder(MUNICIPIOS, SINTRA, CASCAIS, OEIRAS);
        Instant antes = Instant.now();

        service.sincronizar(MUNICIPIOS);

        Map<String, SnitReferencia> replica = replica(MUNICIPIOS);
        assertThat(replica).containsOnlyKeys("1111", "1105", "1110");
        SnitReferencia sintra = replica.get("1111");
        assertThat(sintra.isAtivo()).isTrue();
        assertThat(sintra.getDesignacao()).isEqualTo("SINTRA");
        assertThat(objectMapper.readTree(sintra.getDadosOrigem())).isEqualTo(objectMapper.readTree(SINTRA));
        assertThat(sintra.getSincronizadoEm()).isAfterOrEqualTo(antes.minusMillis(1));
        assertThat(replica.values()).allMatch(SnitReferencia::isAtivo);
        assertThat(resumo()).isEqualTo("Réplica do SNIT sincronizada | lista=MUNICIPALITIES recebidos=3 inseridos=3 "
                + "atualizados=0 desativados=0 ignorados=0");
    }

    @Test
    void deveAtualizarOAlteradoInserirONovoEDesativarOAusenteSemApagarNenhumaLinha() throws Exception {
        responder(MUNICIPIOS, SINTRA, CASCAIS, OEIRAS);
        service.sincronizar(MUNICIPIOS);
        Instant primeira = replica(MUNICIPIOS).get("1111").getSincronizadoEm();
        String cascaisAlterado = municipio("1105", "CASCAIS (alterado)");
        responder(MUNICIPIOS, SINTRA, cascaisAlterado, MAFRA);

        service.sincronizar(MUNICIPIOS);

        Map<String, SnitReferencia> replica = replica(MUNICIPIOS);
        assertThat(replica).containsOnlyKeys("1111", "1105", "1110", "1109");
        assertThat(replica.get("1105").getDesignacao()).isEqualTo("CASCAIS (alterado)");
        assertThat(objectMapper.readTree(replica.get("1105").getDadosOrigem()))
                .isEqualTo(objectMapper.readTree(cascaisAlterado));
        assertThat(replica.get("1109").isAtivo()).isTrue();
        assertThat(replica.get("1110").isAtivo()).isFalse();
        assertThat(replica.get("1110").getDesignacao()).isEqualTo("OEIRAS");
        assertThat(replica.get("1111").isAtivo()).isTrue();
        assertThat(replica.get("1111").getSincronizadoEm()).isAfter(primeira);
        assertThat(resumo()).endsWith("recebidos=3 inseridos=1 atualizados=1 desativados=1 ignorados=0");
    }

    @Test
    void deveReativarUmCodigoInativoQueVolta() {
        responder(MUNICIPIOS, SINTRA, CASCAIS);
        service.sincronizar(MUNICIPIOS);
        responder(MUNICIPIOS, SINTRA);
        service.sincronizar(MUNICIPIOS);
        assertThat(replica(MUNICIPIOS).get("1105").isAtivo()).isFalse();
        responder(MUNICIPIOS, SINTRA, CASCAIS);

        service.sincronizar(MUNICIPIOS);

        assertThat(replica(MUNICIPIOS).get("1105").isAtivo()).isTrue();
        assertThat(resumo()).endsWith("recebidos=2 inseridos=0 atualizados=1 desativados=0 ignorados=0");
    }

    @Test
    void deveIgnorarORegistoSemCodigoComUmAvisoSemOConteudoETratarOsRestantes() {
        responder(MUNICIPIOS, SINTRA, "{\"name\": \"Concelho sem DTCC\", \"nut2Code\": \"NORTE\"}",
                "{\"dtcc\": \"  \", \"name\": \"Concelho com DTCC vazio\"}", CASCAIS);

        service.sincronizar(MUNICIPIOS);

        assertThat(replica(MUNICIPIOS)).containsOnlyKeys("1111", "1105");
        List<String> avisos = mensagens(Level.WARN);
        assertThat(avisos).containsExactly(
                "Registo do SNIT sem código válido, ignorado | lista=MUNICIPALITIES posicao=1",
                "Registo do SNIT sem código válido, ignorado | lista=MUNICIPALITIES posicao=2");
        assertThat(String.join("\n", avisos)).doesNotContain("Concelho");
        assertThat(resumo()).endsWith("recebidos=4 inseridos=2 atualizados=0 desativados=0 ignorados=2");
    }

    @Test
    void deveManterSemDesativarUmCodigoQueVemSemDesignacao() {
        responder(MUNICIPIOS, SINTRA, CASCAIS);
        service.sincronizar(MUNICIPIOS);
        responder(MUNICIPIOS, SINTRA, "{\"dtcc\": \"1105\", \"nut2Code\": \"" + LVT + "\"}");

        service.sincronizar(MUNICIPIOS);

        SnitReferencia cascais = replica(MUNICIPIOS).get("1105");
        assertThat(cascais.isAtivo()).isTrue();
        assertThat(cascais.getDesignacao()).isEqualTo("CASCAIS");
        assertThat(mensagens(Level.WARN)).containsExactly(
                "Registo do SNIT sem designação válida, ignorado | lista=MUNICIPALITIES codigo=1105");
        assertThat(resumo()).endsWith("inseridos=0 atualizados=0 desativados=0 ignorados=1");
    }

    @Test
    void deveFicarComOPrimeiroQuandoOCodigoVemRepetido() {
        responder(MUNICIPIOS, SINTRA, municipio("1111", "SINTRA repetida"));

        service.sincronizar(MUNICIPIOS);

        assertThat(replica(MUNICIPIOS).get("1111").getDesignacao()).isEqualTo("SINTRA");
        assertThat(mensagens(Level.WARN)).containsExactly(
                "Código repetido na resposta do SNIT, fica o primeiro | lista=MUNICIPALITIES codigo=1111");
    }

    @Test
    void naoDeveContarComoAlteradoORegistoQueSoMudaAOrdemDasChaves() {
        responder(MUNICIPIOS, SINTRA);
        service.sincronizar(MUNICIPIOS);
        responder(MUNICIPIOS, "{\"nut2Code\": \"" + LVT + "\", \"name\": \"SINTRA\", \"dtcc\": \"1111\"}");

        service.sincronizar(MUNICIPIOS);

        assertThat(resumo()).endsWith("inseridos=0 atualizados=0 desativados=0 ignorados=0");
    }

    @Test
    void deveGuardarOConcelhoPeloDtccComONameEORegistoInteiro() throws Exception {
        String calhetaMadeira = "{\"dtcc\": \"3101\", \"name\": \"CALHETA\", \"nut2Code\": \"MADEIRA\"}";
        String calhetaAcores = "{\"dtcc\": \"4501\", \"name\": \"CALHETA\", \"nut2Code\": \"CENTRAL\"}";
        responder(MUNICIPIOS, calhetaMadeira, calhetaAcores);

        service.sincronizar(MUNICIPIOS);

        Map<String, SnitReferencia> replica = replica(MUNICIPIOS);
        assertThat(replica).containsOnlyKeys("3101", "4501");
        assertThat(replica.get("3101").getDesignacao()).isEqualTo("CALHETA");
        assertThat(objectMapper.readTree(replica.get("3101").getDadosOrigem()))
                .isEqualTo(objectMapper.readTree(calhetaMadeira));
        assertThat(objectMapper.readTree(replica.get("4501").getDadosOrigem()).get("nut2Code").asText())
                .isEqualTo("CENTRAL");
    }

    @Test
    void deveGuardarOsTiposDeInstrumentoPeloIdNumericoComoTextoEPeloLabel() throws Exception {
        responder(ListaReferenciaSnit.INSTRUMENT_TYPES,
                "{\"id\": 1, \"code\": \"PDM\", \"label\": \"Plano Diretor Municipal\", \"classId\": 3}",
                "{\"id\": 37, \"code\": \"REN\", \"label\": \"Reserva Ecológica Nacional\", \"classId\": 4}",
                "{\"id\": 4, \"code\": \"PROT\", \"label\": \"Programa Regional de Ordenamento\", \"classId\": 2}",
                "{\"id\": 51, \"code\": \"PROT\", \"label\": \"Plano Regional de Ordenamento\", \"classId\": 2}");

        service.sincronizar(ListaReferenciaSnit.INSTRUMENT_TYPES);

        Map<String, SnitReferencia> replica = replica(ListaReferenciaSnit.INSTRUMENT_TYPES);
        assertThat(replica).containsOnlyKeys("1", "37", "4", "51");
        assertThat(replica.get("37").getDesignacao()).isEqualTo("Reserva Ecológica Nacional");
        JsonNode ren = objectMapper.readTree(replica.get("37").getDadosOrigem());
        assertThat(ren.get("code").asText()).isEqualTo("REN");
        assertThat(ren.get("classId").asInt()).isEqualTo(4);
    }

    @Test
    void deveGuardarProcedimentosTipologiasEClassesPeloIdEPeloLabel() throws Exception {
        String revisao = "{\"id\": 7, \"code\": \"03\", \"label\": \"Revisão\"}";
        responder(ListaReferenciaSnit.PROCEDURES, "{\"id\": 1, \"code\": \"01\", \"label\": \"Elaboração\"}", revisao);
        responder(ListaReferenciaSnit.PROCEDURE_TYPES, revisao);
        responder(ListaReferenciaSnit.CLASSES, "{\"id\": 3, \"code\": \"3\", \"label\": \"Âmbito municipal\"}");

        service.sincronizar(ListaReferenciaSnit.PROCEDURES);
        service.sincronizar(ListaReferenciaSnit.PROCEDURE_TYPES);
        service.sincronizar(ListaReferenciaSnit.CLASSES);

        assertThat(replica(ListaReferenciaSnit.PROCEDURES)).containsOnlyKeys("1", "7");
        SnitReferencia revisaoNaReplica = replica(ListaReferenciaSnit.PROCEDURES).get("7");
        assertThat(revisaoNaReplica.getDesignacao()).isEqualTo("Revisão");
        assertThat(objectMapper.readTree(revisaoNaReplica.getDadosOrigem()).get("code").asText()).isEqualTo("03");
        assertThat(replica(ListaReferenciaSnit.PROCEDURE_TYPES).get("7").getDesignacao()).isEqualTo("Revisão");
        assertThat(replica(ListaReferenciaSnit.CLASSES).get("3").getDesignacao()).isEqualTo("Âmbito municipal");
    }

    @Test
    void deveGuardarANutsIIPeloCodeEPeloLabel() {
        responder(ListaReferenciaSnit.NUT2, "{\"code\": \"LISBOA E VALE DO TEJO\", \"label\": \"Lisboa e Vale do Tejo\"}");

        service.sincronizar(ListaReferenciaSnit.NUT2);

        assertThat(replica(ListaReferenciaSnit.NUT2).get(LVT).getDesignacao()).isEqualTo("Lisboa e Vale do Tejo");
    }

    @Test
    void deveFicarComUmaLinhaEUmAvisoQuandoOInstrumentTypesTrazO90015Repetido() {
        String ren = "{\"id\": 90015, \"code\": \"REN\", \"label\": \"Reserva Ecológica Nacional\", \"classId\": 4}";
        responder(ListaReferenciaSnit.INSTRUMENT_TYPES, ren, ren);

        service.sincronizar(ListaReferenciaSnit.INSTRUMENT_TYPES);

        assertThat(repository.findByLista(ListaReferenciaSnit.INSTRUMENT_TYPES)).singleElement()
                .extracting(SnitReferencia::getCodigo).isEqualTo("90015");
        assertThat(mensagens(Level.WARN)).containsExactly(
                "Código repetido na resposta do SNIT, fica o primeiro | lista=INSTRUMENT_TYPES codigo=90015");
    }

    @Test
    void deveAvisarPorCadaEntidadeComDicoInexistenteOuInativoNaReplicaSemDadosPessoais() {
        responder(MUNICIPIOS, SINTRA, CASCAIS);
        service.sincronizar(MUNICIPIOS);
        responder(MUNICIPIOS, SINTRA);
        service.sincronizar(MUNICIPIOS);
        Entidade comConcelho = camara("505000001", "1111");
        Entidade concelhoInativo = camara("505000002", "1105");
        Entidade concelhoInexistente = camara("505000003", "9999");
        logs.list.clear();

        service.avisarDicoSemConcelho();

        List<String> avisos = mensagens(Level.WARN);
        assertThat(avisos).contains(
                "Entidade com DICO sem concelho ativo na réplica do SNIT | entidade=" + concelhoInativo.getCodigo()
                        + " dico=1105",
                "Entidade com DICO sem concelho ativo na réplica do SNIT | entidade=" + concelhoInexistente.getCodigo()
                        + " dico=9999");
        assertThat(avisos).noneMatch(aviso -> aviso.contains("entidade=" + comConcelho.getCodigo() + " "));
        assertThat(String.join("\n", avisos)).doesNotContain("505000", "Câmara");
    }

    @Test
    void naoDeveTocarNasOutrasListas() {
        responder(ListaReferenciaSnit.NUT2, "{\"code\": \"NORTE\", \"label\": \"Norte\"}");
        service.sincronizar(ListaReferenciaSnit.NUT2);
        responder(MUNICIPIOS, SINTRA);

        service.sincronizar(MUNICIPIOS);

        assertThat(replica(ListaReferenciaSnit.NUT2).get("NORTE").isAtivo()).isTrue();
    }

    private static String municipio(String dtcc, String nome) {
        return "{\"dtcc\": \"" + dtcc + "\", \"name\": \"" + nome + "\", \"nut2Code\": \"" + LVT + "\"}";
    }

    private Entidade camara(String nipc, String dico) {
        Entidade entidade = EntidadeTeste.nova(nipc, "Câmara " + nipc);
        entidade.setDico(dico);
        return entidadeRepository.saveAndFlush(entidade);
    }

    private void responder(ListaReferenciaSnit lista, String... registos) {
        List<JsonNode> nos = new ArrayList<>();
        for (String registo : registos) {
            try {
                nos.add(objectMapper.readTree(registo));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        when(clienteSnit.obterTabelaReferencia(lista)).thenReturn(nos);
    }

    private Map<String, SnitReferencia> replica(ListaReferenciaSnit lista) {
        return repository.findByLista(lista).stream()
                .collect(Collectors.toMap(SnitReferencia::getCodigo, linha -> linha));
    }

    private List<String> mensagens(Level nivel) {
        return logs.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** A linha INFO da última sincronização. */
    private String resumo() {
        List<String> linhas = mensagens(Level.INFO);
        return linhas.get(linhas.size() - 1);
    }
}
