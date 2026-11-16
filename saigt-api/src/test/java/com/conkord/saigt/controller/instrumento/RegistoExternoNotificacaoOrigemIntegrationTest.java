package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import com.conkord.saigt.service.documento.RetencaoRegistoExternoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.SetBucketVersioningArgs;
import io.minio.messages.Item;
import io.minio.messages.RetentionMode;
import io.minio.messages.VersioningConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registo externo com a notificação de origem, contra o RustFS real num bucket com versionamento e Object Lock:
 * cópia dos anexos para o depósito, retenção depois do commit e compensação quando a cópia falha.
 */
@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "saigt.teste.bucket=" + RegistoExternoNotificacaoOrigemIntegrationTest.BUCKET_OBJECT_LOCK})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistoExternoNotificacaoOrigemIntegrationTest extends StorageTestcontainersBase {

    static final String BUCKET_OBJECT_LOCK = "saigt-documentos-registo-externo-teste";
    private static final byte[] CONTEUDO_1 = "%PDF-1.4 comprovativo da publicação".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEUDO_2 = "%PDF-1.4 aviso publicado no DR".getBytes(StandardCharsets.UTF_8);

    static {
        MinioClient cliente = MinioClient.builder()
                .endpoint("http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORTA_S3))
                .credentials(ACCESS_KEY, SECRET_KEY).build();
        try {
            if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_OBJECT_LOCK).build())) {
                cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_OBJECT_LOCK).objectLock(true).build());
            }
            cliente.setBucketVersioning(SetBucketVersioningArgs.builder().bucket(BUCKET_OBJECT_LOCK)
                    .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private NotificacaoDocumentoRepository notificacaoDocumentoRepository;

    @Autowired
    private ArmazenamentoIntencaoRepository intencaoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private RetencaoRegistoExternoService retencaoRegistoExternoService;

    @SpyBean
    private ArmazenamentoDocumentos armazenamento;

    @SpyBean
    private IntencaoArmazenamentoService intencaoArmazenamento;

    private record Cenario(Dinamica dinamica, InstrumentoProcedimentoConfig config, Entidade entidade, UUID masterId,
                           Utilizador autorAnexos) {
    }

    @BeforeEach
    void confirmarQueAAplicacaoUsaOBucketComObjectLock() {
        assertThat(storageProperties.bucket()).isEqualTo(BUCKET_OBJECT_LOCK);
    }

    @AfterEach
    void reporOsSpies() {
        Mockito.reset(armazenamento, intencaoArmazenamento);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveResolverARegularizacaoECopiarOsDoisAnexosParaODepositoComRetencao(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("R01");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        NotificacaoDocumento anexo1 = criarAnexo(regularizacao, cenario.autorAnexos(), "Comprovativo DR.pdf", CONTEUDO_1);
        NotificacaoDocumento anexo2 = criarAnexo(regularizacao, cenario.autorAnexos(), "aviso.pdf", CONTEUDO_2);
        String versaoAnexo1 = versoes(anexo1.getCaminhoFicheiro()).get(0);

        JsonNode corpo = corpo(registar(cenario, regularizacao.getId().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.origemDados").value("REGISTO_EXTERNO"))
                .andExpect(jsonPath("$.documentosCopiados").value(2)));
        UUID processoId = UUID.fromString(corpo.get("id").asText());
        String numeroProcesso = corpo.get("numeroProcesso").asText();

        Notificacao resolvida = notificacaoRepository.findById(regularizacao.getId()).orElseThrow();
        assertThat(resolvida.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(resolvida.getResolvidoEm()).isNotNull();
        assertThat(notificacaoRepository.findComDestinosByIdIn(List.of(regularizacao.getId())).get(0).getProcesso().getId())
                .isEqualTo(processoId);

        List<ProcessoDocumento> documentos = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId);
        assertThat(documentos).hasSize(2);
        assertThat(documentos).extracting(ProcessoDocumento::getNomeFicheiro)
                .containsExactlyInAnyOrder("Comprovativo DR.pdf", "aviso.pdf");
        for (ProcessoDocumento documento : documentos) {
            byte[] conteudo = documento.getNomeFicheiro().equals("aviso.pdf") ? CONTEUDO_2 : CONTEUDO_1;
            assertThat(documento.getCategoria()).isEqualTo(CategoriaDocumento.DEPOSITO);
            assertThat(documento.getDocumentoConfig()).isNull();
            assertThat(documento.getTamanhoBytes()).isEqualTo(conteudo.length);
            assertThat(documento.getExtensao()).isEqualTo("pdf");
            assertThat(documento.getHashSha256()).isEqualTo(sha256(conteudo));
            assertThat(documento.getVersaoObjeto()).isNotBlank().isNotEqualTo("null");
            assertThat(documento.getCaminhoFicheiro())
                    .startsWith("processos/" + numeroProcesso + "/DEPOSITO/" + documento.getId() + "-");
            assertThat(lerObjeto(documento.getCaminhoFicheiro(), documento.getVersaoObjeto())).isEqualTo(conteudo);
            assertThat(retencao(documento.getCaminhoFicheiro(), documento.getVersaoObjeto()))
                    .isEqualTo(RetentionMode.GOVERNANCE);
            assertThat(operacoes(documento.getCaminhoFicheiro())).containsExactly(OperacaoArmazenamento.COPIA);
            InOrder ordem = Mockito.inOrder(intencaoArmazenamento, armazenamento);
            ordem.verify(intencaoArmazenamento).registar(documento.getCaminhoFicheiro(), OperacaoArmazenamento.COPIA);
            ordem.verify(armazenamento).guardar(eq(documento.getCaminhoFicheiro()), any(), anyString());
        }
        assertThat(documentos).extracting(d -> d.getCriadoPor().getId()).containsOnly(cenario.autorAnexos().getId());

        // Os objetos dos anexos ficam onde estavam, intactos e sem retenção.
        assertThat(versoes(anexo1.getCaminhoFicheiro())).containsExactly(versaoAnexo1);
        assertThat(lerObjeto(anexo1.getCaminhoFicheiro(), null)).isEqualTo(CONTEUDO_1);
        assertThat(armazenamento.temRetencao(anexo1.getCaminhoFicheiro(), versaoAnexo1)).isFalse();
        assertThat(lerObjeto(anexo2.getCaminhoFicheiro(), null)).isEqualTo(CONTEUDO_2);

        Auditoria registo = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "PROCESSO", processoId.toString(), "REGISTO_EXTERNO").orElseThrow();
        JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
        assertThat(dados.get("motivo").asText()).isEqualTo("Publicado fora da SAIGT.");
        assertThat(dados.get("notificacaoOrigemId").asText()).isEqualTo(regularizacao.getId().toString());
        assertThat(dados.get("documentosCopiados").asInt()).isEqualTo(2);
        assertThat(registo.getDadosAlteracao()).doesNotContain("Comprovativo", "aviso.pdf", cenario.autorAnexos().getEmail());
        assertThat(saida).contains("Registo externo | processoId=" + processoId + " notificacaoOrigemId="
                + regularizacao.getId() + " documentosCopiados=2");
        String linhaLog = saida.getAll().lines().filter(l -> l.contains("Registo externo |")).findFirst().orElseThrow();
        // O "@" do @timestamp do log em JSON não conta: um email só poderia vir na mensagem.
        assertThat(LogJson.ler(linhaLog).path("message").asText()).doesNotContain("Comprovativo", "aviso", "@");
    }

    @Test
    void deveCopiarOsAnexosSemMudarOEstadoNemAResolucaoDeUmaRespostaObrigatoriaJaResolvida() throws Exception {
        Cenario cenario = criarCenario("R02");
        Notificacao resposta = criarNotificacao(cenario, TipoNotificacao.RESPOSTA_OBRIGATORIA, EstadoNotificacao.RESOLVIDA);
        java.time.Instant resolvidaEm = java.time.Instant.parse("2026-11-01T09:00:00Z");
        resposta.setResolvidoEm(resolvidaEm);
        notificacaoRepository.saveAndFlush(resposta);
        criarAnexo(resposta, cenario.autorAnexos(), "resposta.pdf", CONTEUDO_1);

        JsonNode corpo = corpo(registar(cenario, resposta.getId().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentosCopiados").value(1)));

        Notificacao depois = notificacaoRepository.findById(resposta.getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(depois.getResolvidoEm()).isEqualTo(resolvidaEm);
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(UUID.fromString(corpo.get("id").asText())))
                .singleElement().extracting(ProcessoDocumento::getCategoria).isEqualTo(CategoriaDocumento.DEPOSITO);
    }

    @Test
    void deveDevolver409SemCriarProcessoQuandoARegularizacaoJaEstaResolvida() throws Exception {
        Cenario cenario = criarCenario("R03");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.RESOLVIDA);
        criarAnexo(regularizacao, cenario.autorAnexos(), "comprovativo.pdf", CONTEUDO_1);

        registar(cenario, regularizacao.getId().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("A notificação de origem já foi resolvida."));

        assertNenhumProcesso(cenario);
    }

    @Test
    void deveDevolver400NoCampoNotificacaoOrigemIdQuandoEUmPedidoDeApoio() throws Exception {
        Cenario cenario = criarCenario("R04");
        Notificacao pedidoApoio = criarNotificacao(cenario, TipoNotificacao.PEDIDO_APOIO, EstadoNotificacao.ATIVA);

        registar(cenario, pedidoApoio.getId().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("notificacaoOrigemId"))
                .andExpect(jsonPath("$.campos[0].erro")
                        .value("A notificação de origem não é uma regularização nem uma resposta obrigatória."));

        assertNenhumProcesso(cenario);
        assertThat(notificacaoRepository.findById(pedidoApoio.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    void deveDevolver404SemGravarNadaQuandoANotificacaoDeOrigemNaoExiste() throws Exception {
        Cenario cenario = criarCenario("R05");

        registar(cenario, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Notificação de origem não encontrada."));

        assertNenhumProcesso(cenario);
    }

    @Test
    void deveDevolver400QuandoONotificacaoOrigemIdNaoEUuid() throws Exception {
        Cenario cenario = criarCenario("R06");

        registar(cenario, "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("notificacaoOrigemId"));

        assertNenhumProcesso(cenario);
    }

    @Test
    void deveDevolver400ComOCampoQuandoFaltaADataDePublicacaoOuONumeroDrEstaEmBranco() throws Exception {
        Cenario cenario = criarCenario("R07");

        mockMvc.perform(post(url(cenario)).with(jwtMaster(cenario.masterId())).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", cenario.config().getId(),
                                "entidadeId", cenario.entidade().getId(), "numeroDr", "2026/DR/R07", "motivo", "m"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("dataPublicacaoDr"));
        mockMvc.perform(post(url(cenario)).with(jwtMaster(cenario.masterId())).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentoProcedimentoConfigId", cenario.config().getId(),
                                "entidadeId", cenario.entidade().getId(), "numeroDr", " ",
                                "dataPublicacaoDr", LocalDate.now().toString(), "motivo", "m"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("numeroDr"));

        assertNenhumProcesso(cenario);
    }

    @Test
    void deveDevolver403SemGravarNadaAoVisitanteAoTecnicoEAUmaEntidade() throws Exception {
        Cenario cenario = criarCenario("R08");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        String corpo = pedido(cenario, regularizacao.getId().toString());

        for (RequestPostProcessor quem : List.of(jwtInterno(UUID.randomUUID(), "VISITANTE"),
                jwtInterno(UUID.randomUUID(), "TECNICO"), jwtEntidade(cenario.entidade()))) {
            mockMvc.perform(post(url(cenario)).with(quem).contentType("application/json").content(corpo))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        }

        assertNenhumProcesso(cenario);
        assertThat(notificacaoRepository.findById(regularizacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveDevolver503SemProcessoNemDocumentosNemObjetosQuandoOStorageFalhaNaCopiaDoSegundoAnexo(CapturedOutput saida)
            throws Exception {
        Cenario cenario = criarCenario("R09");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        criarAnexo(regularizacao, cenario.autorAnexos(), "primeiro.pdf", CONTEUDO_1);
        criarAnexo(regularizacao, cenario.autorAnexos(), "segundo.pdf", CONTEUDO_2);
        Set<Long> intencoesAntes = idsIntencoes();
        doCallRealMethod()
                .doThrow(new StorageIndisponivelException("Falha simulada da gravação.", null))
                .when(armazenamento).guardar(startsWith("processos/"), any(), anyString());

        registar(cenario, regularizacao.getId().toString())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("STORAGE_INDISPONIVEL"));

        assertNenhumProcesso(cenario);
        Notificacao depois = notificacaoRepository.findById(regularizacao.getId()).orElseThrow();
        assertThat(depois.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(depois.getResolvidoEm()).isNull();
        List<ArmazenamentoIntencao> copias = intencaoRepository.findAll().stream()
                .filter(i -> !intencoesAntes.contains(i.getId())).toList();
        assertThat(copias).hasSize(2).allSatisfy(i -> assertThat(i.getOperacao()).isEqualTo(OperacaoArmazenamento.COPIA));
        for (ArmazenamentoIntencao copia : copias) {
            assertThat(versoes(copia.getChaveObjeto())).as(copia.getChaveObjeto()).isEmpty();
        }
        assertThat(saida).doesNotContain("Cópia para depósito não removida");
    }

    @Test
    void deveDevolver404SemGravarNadaQuandoOObjetoDoAnexoNaoExiste() throws Exception {
        Cenario cenario = criarCenario("R10");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        notificacaoDocumentoRepository.saveAndFlush(new NotificacaoDocumento(regularizacao, "perdido.pdf",
                "notificacoes/" + regularizacao.getId() + "/" + UUID.randomUUID() + "-perdido.pdf", 10L,
                cenario.autorAnexos()));

        registar(cenario, regularizacao.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));

        assertNenhumProcesso(cenario);
        assertThat(notificacaoRepository.findById(regularizacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deveResponder201EReterNaRepeticaoQuandoARetencaoFalhaDepoisDoCommit(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("R11");
        Notificacao regularizacao = criarNotificacao(cenario, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, EstadoNotificacao.ATIVA);
        criarAnexo(regularizacao, cenario.autorAnexos(), "comprovativo.pdf", CONTEUDO_1);
        doThrow(new StorageIndisponivelException("Falha simulada da retenção.", null))
                .when(armazenamento).reter(startsWith("processos/"), any(), any());

        JsonNode corpo = corpo(registar(cenario, regularizacao.getId().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.documentosCopiados").value(1)));
        UUID processoId = UUID.fromString(corpo.get("id").asText());

        assertThat(processoRepository.findById(processoId).orElseThrow().getEstado().name()).isEqualTo("PUBLICADO");
        ProcessoDocumento copiado = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId).get(0);
        assertThat(armazenamento.temRetencao(copiado.getCaminhoFicheiro(), copiado.getVersaoObjeto())).isFalse();
        assertThat(saida).contains("WARN").contains("Retenção do registo externo por fazer, fica para a repetição | processoId="
                + processoId);
        assertThat(notificacaoRepository.findById(regularizacao.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);

        Mockito.reset(armazenamento);
        retencaoRegistoExternoService.repetirPendentes();

        assertThat(retencao(copiado.getCaminhoFicheiro(), copiado.getVersaoObjeto())).isEqualTo(RetentionMode.GOVERNANCE);
        assertThat(processoRepository.idsSemAuditoria("REGISTO_EXTERNO",
                com.conkord.saigt.persistence.operacional.EstadoProcesso.PUBLICADO, "PROCESSO", "RETER_DOCUMENTOS"))
                .doesNotContain(processoId);
    }

    private Cenario criarCenario(String codigo) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("REGX-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-REGX-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-REGX-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig config = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5090000" + codigo.substring(1),
                "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo.toLowerCase() + "@cm-regx.pt", "INCM"));
        Utilizador master = new Utilizador("Master " + codigo, "master" + codigo.toLowerCase() + "@dgt.pt", "AD_DGT");
        master.setPerfilInterno(PerfilInterno.MASTER);
        UUID masterId = utilizadorRepository.saveAndFlush(master).getId();
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/REGX/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        return new Cenario(dinamica, config, entidade, masterId, autor);
    }

    private Notificacao criarNotificacao(Cenario cenario, TipoNotificacao tipo, EstadoNotificacao estado) {
        Notificacao notificacao = new Notificacao(tipo, "Publicado fora da SAIGT", "Segue o comprovativo.",
                cenario.autorAnexos());
        notificacao.setEntidade(cenario.entidade());
        notificacao.setEstado(estado);
        return notificacaoRepository.saveAndFlush(notificacao);
    }

    /** Como a #209 os grava: o objeto em `notificacoes/{notificacao_id}/{notificacao_documento_id}-{nome}`. */
    private NotificacaoDocumento criarAnexo(Notificacao notificacao, Utilizador autor, String nome, byte[] conteudo)
            throws Exception {
        UUID anexoId = UUID.randomUUID();
        String chave = "notificacoes/" + notificacao.getId() + "/" + anexoId + "-" + nome.replace(' ', '_');
        minioClient.putObject(PutObjectArgs.builder().bucket(BUCKET_OBJECT_LOCK).object(chave)
                .stream(new ByteArrayInputStream(conteudo), conteudo.length, -1).contentType("application/pdf").build());
        return notificacaoDocumentoRepository.saveAndFlush(
                new NotificacaoDocumento(notificacao, nome, chave, (long) conteudo.length, autor));
    }

    private ResultActions registar(Cenario cenario, String notificacaoOrigemId) throws Exception {
        return mockMvc.perform(post(url(cenario)).with(jwtMaster(cenario.masterId()))
                .contentType("application/json").content(pedido(cenario, notificacaoOrigemId)));
    }

    private String pedido(Cenario cenario, String notificacaoOrigemId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "instrumentoProcedimentoConfigId", cenario.config().getId(),
                "entidadeId", cenario.entidade().getId(),
                "numeroDr", "2026/DR/123",
                "dataPublicacaoDr", LocalDate.now().toString(),
                "motivo", "Publicado fora da SAIGT.",
                "notificacaoOrigemId", notificacaoOrigemId));
    }

    private JsonNode corpo(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void assertNenhumProcesso(Cenario cenario) {
        assertThat(processoRepository.findAll().stream()
                .filter(p -> p.getDinamica().getId().equals(cenario.dinamica().getId()))).isEmpty();
    }

    private static String url(Cenario cenario) {
        return "/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos/registo-externo";
    }

    private static RequestPostProcessor jwtMaster(UUID utilizadorId) {
        return jwtInterno(utilizadorId, "MASTER");
    }

    private static RequestPostProcessor jwtInterno(UUID utilizadorId, String perfil) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizadorId.toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", perfil));
    }

    private static RequestPostProcessor jwtEntidade(Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private Set<Long> idsIntencoes() {
        return intencaoRepository.findAll().stream().map(ArmazenamentoIntencao::getId).collect(Collectors.toSet());
    }

    private List<OperacaoArmazenamento> operacoes(String chave) {
        return intencaoRepository.findByChaveObjeto(chave).stream().map(ArmazenamentoIntencao::getOperacao).toList();
    }

    private byte[] lerObjeto(String chave, String versao) throws Exception {
        try (InputStream conteudo = armazenamento.abrir(chave, versao, 0, 0)) {
            return conteudo.readAllBytes();
        }
    }

    private RetentionMode retencao(String chave, String versao) throws Exception {
        return minioClient.getObjectRetention(GetObjectRetentionArgs.builder().bucket(BUCKET_OBJECT_LOCK)
                .object(chave).versionId(versao).build()).mode();
    }

    private List<String> versoes(String chave) throws Exception {
        List<String> resultado = new ArrayList<>();
        for (Result<Item> resposta : minioClient.listObjects(ListObjectsArgs.builder().bucket(BUCKET_OBJECT_LOCK)
                .prefix(chave).recursive(true).includeVersions(true).build())) {
            Item item = resposta.get();
            if (item.objectName().equals(chave)) {
                resultado.add(item.versionId());
            }
        }
        return resultado;
    }

    private static String sha256(byte[] conteudo) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
    }
}
