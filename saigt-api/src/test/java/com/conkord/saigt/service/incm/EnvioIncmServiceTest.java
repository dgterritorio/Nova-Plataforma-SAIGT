package com.conkord.saigt.service.incm;

import com.conkord.saigt.configuration.LogJson;
import com.conkord.saigt.exception.ConkordException;
import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.VersaoDesatualizadaException;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste.PedidoRecebido;
import com.conkord.saigt.integracao.incm.ServidorIncmTeste;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublico;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "app.incm.service-key=" + EnvioIncmServiceTest.SERVICE_KEY,
        "app.incm.read-timeout=1s",
        "app.publico.base-url=https://saigt.teste"})
@ActiveProfiles("test")
@ExtendWith({OutputCaptureExtension.class, LogJson.Extensao.class})
class EnvioIncmServiceTest extends EnvioIncmTesteBase {

    static final String SERVICE_KEY = "chave-secreta-envio-incm";

    private static final String SUBMIT_NEW_ACT = "/SubmitNewAct";
    private static final String SUCESSO = """
            {"IsSuccess": true, "ErrorMessageList": [], "ActIdentifier": 900000000000001}""";
    private static final String ERRO = """
            {"IsSuccess": false, "ErrorMessageList": ["ImageURLList cannot be empty."], "ActIdentifier": null}""";
    private static final String ERRO_DUAS_MENSAGENS = """
            {"IsSuccess": false, "ErrorMessageList": ["ImageURLList cannot be empty.", "ActTypeId is invalid."],
             "ActIdentifier": null}""";
    private static final ServidorIncmTeste INCM = ServidorIncmTeste.iniciar();

    @DynamicPropertySource
    static void urlIncm(DynamicPropertyRegistry registry) {
        registry.add("app.incm.base-url", INCM::url);
    }

    @AfterAll
    static void pararIncm() {
        INCM.close();
    }

    @SpyBean
    private ClienteIncm clienteIncm;

    @SpyBean
    private RetencaoDocumentosService retencaoDocumentosService;

    @Autowired
    private ArmazenamentoDocumentos armazenamentoDocumentos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void limparPedidos() {
        INCM.pedidos().clear();
        INCM.responder(SUBMIT_NEW_ACT, 200, SUCESSO);
    }

    @Test
    void deveReterOsDocumentosAindaSemRetencaoNaSaidaDeValidadoDgt() {
        Cenario cenario = criarCenario("Aviso de início");
        assertThat(retido(cenario.publicacao())).isFalse();

        enviar(cenario);

        assertThat(retido(cenario.publicacao())).isTrue();
        assertThat(retido(cenario.deposito())).isTrue();
    }

    @Test
    void deveFicarEmValidadoDgtSemChamarAINCMQuandoARetencaoFalha() {
        Cenario cenario = criarCenario("Aviso de início");
        doThrow(new StorageIndisponivelException("Armazenamento indisponível.", new IllegalStateException()))
                .when(retencaoDocumentosService).reterDocumentosSemRetencao(any(), eq(MomentoRetencao.SAIDA_VALIDADO_DGT));

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(StorageIndisponivelException.class);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(INCM.pedidos()).isEmpty();
        assertThat(comunicacoes(cenario.processo())).isEmpty();
    }

    @Test
    void deveFicarEmValidacaoIncmComOActIdentifierQuandoAINCMAceita() {
        Cenario cenario = criarCenario("Aviso de início");

        Processo devolvido = enviar(cenario);

        Processo processo = recarregar(cenario.processo());
        assertThat(devolvido.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(processo.getIncmActIdentifier()).isEqualTo("900000000000001");
        assertThat(linksAtivos(cenario.publicacao())).hasSize(1);
    }

    @Test
    void deveGravarALinhaSubmitNewActCompletaQuandoAINCMAceita() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        List<IncmComunicacao> linhas = comunicacoes(cenario.processo());
        assertThat(linhas).hasSize(1);
        IncmComunicacao linha = linhas.get(0);
        assertThat(linha.getOperacao()).isEqualTo("SUBMIT_NEW_ACT");
        assertThat(linha.getActIdentifier()).isEqualTo(900000000000001L);
        assertThat(linha.getIsSuccess()).isTrue();
        assertThat(linha.getHttpStatus()).isEqualTo(200);
        assertThat(linha.getSerieDr()).isEqualTo("SEGUNDA_H");
        assertThat(linha.getSubmissao()).isEqualTo((short) 1);
        assertThat(linha.getTransicaoDerivada()).isEqualTo("EM_VALIDACAO_INCM");
        assertThat(linha.getFalhaTecnica()).isNull();
        assertThat(linha.getCorrelationId()).isNotBlank();
        assertThat(linha.getEnviadoEm()).isNotNull();
        assertThat(linha.getRecebidoEm()).isNotNull();
        assertThat(objectMapper.readTree(linha.getResposta()).get("ActIdentifier").asLong()).isEqualTo(900000000000001L);
        JsonNode ato = objectMapper.readTree(linha.getPedido()).get("DGTAct");
        assertThat(ato.get("DiarioRepublicaSeries2Act").get("ActSummary").asText()).isEqualTo("Aviso de início");
    }

    @Test
    void comASegundaCEscolhidaNoProcessoEnviaAVarianteDaSegundaSerieEGravaASerieNaComunicacao() throws Exception {
        Cenario cenario = criarCenario(null, "Delimitação da servidão");
        Processo processo = recarregar(cenario.processo());
        processo.setSerieDr("SEGUNDA_C");
        processoRepository.saveAndFlush(processo);

        enviar(cenario);

        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getSerieDr()).isEqualTo("SEGUNDA_C");
        JsonNode ato = objectMapper.readTree(linha.getPedido()).get("DGTAct");
        assertThat(ato.path("DiarioRepublicaSeries1Act").isObject()).isFalse();
        assertThat(ato.get("DiarioRepublicaSeries2Act").get("PartId").asLong()).isEqualTo(3L);
        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
    }

    @Test
    void deveAuditarOEnvioEAAceitacaoComOUtilizadorRecebido() {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(auditoria(cenario.processo()))
                .extracting(Auditoria::getAcao, Auditoria::getEstadoAntes, Auditoria::getEstadoDepois,
                        Auditoria::getUtilizadorId, Auditoria::getEntidadeId)
                .containsExactlyInAnyOrder(
                        tuple("RETER_DOCUMENTOS", null, null, null, null),
                        tuple("ENVIAR_INCM", "VALIDADO_DGT", "ENVIADO_INCM",
                                cenario.utilizadorId(), null),
                        tuple("SUBMISSAO_INCM_ACEITE", "ENVIADO_INCM", "EM_VALIDACAO_INCM",
                                cenario.utilizadorId(), null));
    }

    @Test
    void deveFicarAnuladoIncmComAsMensagensQuandoAINCMRecusa() throws Exception {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        Processo processo = recarregar(cenario.processo());
        assertThat(processo.getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(processo.getIncmActIdentifier()).isNull();
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getActIdentifier()).isNull();
        assertThat(linha.getIsSuccess()).isFalse();
        assertThat(linha.getTransicaoDerivada()).isEqualTo("ANULADO_INCM");
        assertThat(objectMapper.readTree(linha.getResposta()).get("ErrorMessageList").get(0).asText())
                .isEqualTo("ImageURLList cannot be empty.");
        Auditoria rejeicao = auditoria(processo).stream()
                .filter(a -> a.getAcao().equals("SUBMISSAO_INCM_REJEITADA")).findFirst().orElseThrow();
        assertThat(rejeicao.getEstadoAntes()).isEqualTo("ENVIADO_INCM");
        assertThat(rejeicao.getEstadoDepois()).isEqualTo("ANULADO_INCM");
        assertThat(objectMapper.readTree(rejeicao.getDadosAlteracao()).get("mensagens").get(0).asText())
                .isEqualTo("ImageURLList cannot be empty.");
    }

    @Test
    void deveRegistarARecusaDaIncmComAsMensagensSemAutor() {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO_DUAS_MENSAGENS);
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(recusas(cenario.processo())).singleElement().satisfies(linha -> {
            assertThat(linha.get("sequencia")).isEqualTo(1);
            assertThat(linha.get("origem")).isEqualTo("INCM");
            assertThat(linha.get("motivo_texto")).isEqualTo("ImageURLList cannot be empty.\nActTypeId is invalid.");
            assertThat(linha.get("criado_por")).isNull();
            assertThat(linha.get("criado_em")).isNotNull();
        });
    }

    @Test
    void deveNumerarAsRecusasDaIncmDoMesmoProcessoNoReenvio() {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");
        enviar(cenario);
        // A correção e a nova validação da DGT não interessam aqui: só o reenvio e a segunda recusa.
        Processo anulado = recarregar(cenario.processo());
        anulado.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoRepository.saveAndFlush(anulado);
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO_DUAS_MENSAGENS);

        enviar(cenario);

        assertThat(recusas(cenario.processo()))
                .extracting(linha -> linha.get("sequencia"), linha -> linha.get("motivo_texto"))
                .containsExactly(
                        tuple(1, "ImageURLList cannot be empty."),
                        tuple(2, "ImageURLList cannot be empty.\nActTypeId is invalid."));
    }

    @Test
    void naoDeveFicarRecusaQuandoATransacaoDoEnvioReverte() {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");

        transactionTemplate.executeWithoutResult(estado -> {
            service.enviar(cenario.processo().getId(), cenario.utilizadorId());
            assertThat(recusas(cenario.processo())).hasSize(1);
            estado.setRollbackOnly();
        });

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(recusas(cenario.processo())).isEmpty();
    }

    @Test
    void deveEscreverAsMensagensDaIncmNaRespostaDoLog(LogJson log) {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO_DUAS_MENSAGENS);
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(recusas(cenario.processo())).hasSize(1);
        assertThat(log.ultimaChamada("SUBMIT_NEW_ACT").path("resposta").asText())
                .contains("ImageURLList cannot be empty.", "ActTypeId is invalid.");
    }

    @Test
    void naoDeveRegistarRecusaQuandoAINCMAceita() {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(recusas(cenario.processo())).isEmpty();
    }

    @Test
    void deveManterValidadoDgtQuandoAINCMDevolveHttp500() {
        INCM.responder(SUBMIT_NEW_ACT, 500, null);
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class)
                .hasMessageContaining("não respondeu")
                .extracting(e -> ((ConkordException) e).getCodigo()).isEqualTo("INTEGRACAO_INDISPONIVEL");

        verificarSemTransicaoComLinkCriado(cenario);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("ERRO_HTTP");
        assertThat(linha.getHttpStatus()).isEqualTo(500);
        assertThat(linha.getTransicaoDerivada()).isNull();
        assertThat(linha.getRecebidoEm()).isNotNull();
    }

    @Test
    void deveEnviarDeNovoDepoisDeUmaFalhaTecnicaMantendoAsDuasLinhas() {
        INCM.responder(SUBMIT_NEW_ACT, 500, null);
        Cenario cenario = criarCenario("Aviso de início");
        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);
        verificarSemTransicaoComLinkCriado(cenario);
        INCM.responder(SUBMIT_NEW_ACT, 200, SUCESSO);

        Processo devolvido = enviar(cenario);

        assertThat(devolvido.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(recarregar(cenario.processo()).getIncmActIdentifier()).isEqualTo("900000000000001");
        assertThat(comunicacoes(cenario.processo()))
                .extracting(IncmComunicacao::getSubmissao, IncmComunicacao::getFalhaTecnica,
                        IncmComunicacao::getTransicaoDerivada)
                .containsExactly(
                        tuple((short) 1, "ERRO_HTTP", null),
                        tuple((short) 2, null, "EM_VALIDACAO_INCM"));
    }

    @Test
    void deveManterValidadoDgtSemRecebidoEmQuandoAINCMNaoRespondeATempo() {
        INCM.reterResposta(SUBMIT_NEW_ACT);
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);

        verificarSemTransicaoComLinkCriado(cenario);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo("TIMEOUT");
        assertThat(linha.getHttpStatus()).isNull();
        assertThat(linha.getRecebidoEm()).isNull();
    }

    @Test
    void naoDeveChamarAINCMComOCircuitoAberto() {
        circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).transitionToForcedOpenState();
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class)
                .hasMessageContaining("suspenso");

        assertThat(INCM.pedidos()).isEmpty();
        verificarSemTransicaoComLinkCriado(cenario);
        assertThat(comunicacoes(cenario.processo()).get(0).getFalhaTecnica()).isEqualTo("CIRCUITO_ABERTO");
    }

    @Test
    void deveConfigurarOsDoisCircuitosComAConfiguracaoDoApplicationProperties() {
        assertThat(List.of(ClienteIncm.CIRCUITO_AUTENTICACAO, ClienteIncm.CIRCUITO_SUBMISSAO)).allSatisfy(nome -> {
            CircuitBreakerConfig config = circuitBreakerRegistry.circuitBreaker(nome).getCircuitBreakerConfig();
            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(30_000L);
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        });
    }

    @Test
    void deveTratarComoFalhaUmaAceitacaoSemActIdentifier() {
        INCM.responder(SUBMIT_NEW_ACT, 200, """
                {"IsSuccess": true, "ErrorMessageList": [], "ActIdentifier": null}""");
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(IncmIndisponivelException.class);

        verificarSemTransicaoComLinkCriado(cenario);
        IncmComunicacao linha = comunicacoes(cenario.processo()).get(0);
        assertThat(linha.getIsSuccess()).isTrue();
        assertThat(linha.getFalhaTecnica()).isNull();
        assertThat(linha.getTransicaoDerivada()).isNull();
    }

    @Test
    void deveRecusarPrimeiraSerieSemOsDadosDaPrimeiraSerieSemChamarAINCMNemExpor() {
        Cenario cenario = criarCenario("PRIMEIRA", "Decreto");

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(DadosIncmEmFaltaException.class)
                .extracting(e -> ((DadosIncmEmFaltaException) e).getCampos().stream()
                        .map(campo -> campo.path()).toList())
                .isEqualTo(List.of("tipoAto", "publicarSuplemento"));

        assertThat(INCM.pedidos()).isEmpty();
        assertThat(comunicacoes(cenario.processo())).isEmpty();
        verificarSemTransicaoNemLink(cenario);
    }

    @Test
    void deveRecusarProcessoSemNormaLegalSemChamarAINCMNemExpor() {
        Cenario cenario = criarCenario("Aviso de início");
        Processo processo = recarregar(cenario.processo());
        processo.setNormaLegal(null);
        processoRepository.saveAndFlush(processo);

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(DadosIncmEmFaltaException.class)
                .extracting(e -> ((ConkordException) e).getCodigo()).isEqualTo("DADOS_INCM_EM_FALTA");

        assertThat(INCM.pedidos()).isEmpty();
        assertThat(comunicacoes(cenario.processo())).isEmpty();
        verificarSemTransicaoNemLink(cenario);
    }

    @Test
    void deveRecusarProcessoForaDeValidadoDgtSemGravarNada() {
        Cenario cenario = criarCenario("Aviso de início");
        Processo processo = recarregar(cenario.processo());
        processo.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        processoRepository.saveAndFlush(processo);

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(EstadoInvalidoException.class)
                .extracting(e -> ((ConkordException) e).getCodigo()).isEqualTo("ESTADO_INVALIDO");

        assertThat(INCM.pedidos()).isEmpty();
        assertThat(comunicacoes(processo)).isEmpty();
        assertThat(auditoria(processo)).isEmpty();
        assertThat(linksAtivos(cenario.publicacao())).isEmpty();
    }

    @Test
    void deveDarVersaoDesatualizadaEManterALinhaQuandoOProcessoMudaDuranteAChamada() {
        Cenario cenario = criarCenario("Aviso de início");
        TransactionTemplate outraTransacao = new TransactionTemplate(transactionTemplate.getTransactionManager());
        outraTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(invocacao -> {
            Object resultado = invocacao.callRealMethod();
            outraTransacao.executeWithoutResult(estado -> jdbcTemplate.update(
                    "UPDATE processo SET versao = versao + 1 WHERE id = ?", cenario.processo().getId()));
            return resultado;
        }).when(clienteIncm).submeterAto(any(), any());

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(VersaoDesatualizadaException.class)
                .extracting(e -> ((ConkordException) e).getCodigo()).isEqualTo("VERSAO_DESATUALIZADA");

        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(comunicacoes(cenario.processo())).singleElement()
                .extracting(IncmComunicacao::getActIdentifier).isEqualTo(900000000000001L);
    }

    @Test
    void deveEnviarSoOUrlDoLinkIncmDoDocumentoDePublicacao() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        DocumentoLinkPublico link = linksAtivos(cenario.publicacao()).get(0);
        assertThat(link.getDestino()).isEqualTo(DestinoLinkPublico.INCM);
        JsonNode enviadas = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct").get("FileURLList");
        assertThat(enviadas).hasSize(1);
        assertThat(enviadas.get(0).asText()).isEqualTo("https://saigt.teste/api/v1/publico/" + link.getToken()
                + "/Regulamento.pdf");
        assertThat(linksAtivos(cenario.deposito())).isEmpty();
    }

    @Test
    void deveGravarOPedidoComOTokenDosLinksMascarado() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        String token = linksAtivos(cenario.publicacao()).get(0).getToken().toString();
        String gravado = comunicacoes(cenario.processo()).get(0).getPedido();
        assertThat(gravado).doesNotContain(token);
        assertThat(objectMapper.readTree(gravado).get("DGTAct").get("FileURLList").get(0).asText())
                .isEqualTo("https://saigt.teste/api/v1/publico/***/Regulamento.pdf");
    }

    @Test
    void deveReutilizarOMesmoLinkNumReenvio() {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");
        enviar(cenario);
        UUID tokenDoPrimeiroEnvio = linksAtivos(cenario.publicacao()).get(0).getToken();
        Processo anulado = recarregar(cenario.processo());
        anulado.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoRepository.saveAndFlush(anulado);
        INCM.responder(SUBMIT_NEW_ACT, 200, SUCESSO);

        enviar(cenario);

        assertThat(linksAtivos(cenario.publicacao())).singleElement()
                .extracting(DocumentoLinkPublico::getToken).isEqualTo(tokenDoPrimeiroEnvio);
        assertThat(INCM.pedidos()).extracting(PedidoRecebido::corpo)
                .allSatisfy(corpo -> assertThat(corpo).contains(tokenDoPrimeiroEnvio.toString()));
    }

    @Test
    void deveCriarUmLinkPorDocumentoDePublicacaoELigarAPlantaAoJpegDeMesmoNomeBase() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento segundo = adicionarDocumentoDePublicacao(cenario, "Deliberacao.pdf", false);
        ProcessoDocumento zip = adicionarPlantaComNomeTiff(cenario, "Planta_Ordenamento.zip", "0307PCgeral0854.tif");
        ProcessoDocumento jpeg = adicionarImagemDePublicacao(cenario, "0307pcgeral0854.JPG");

        enviar(cenario);

        assertThat(linksAtivos(cenario.publicacao())).hasSize(1);
        assertThat(linksAtivos(segundo)).hasSize(1);
        assertThat(linksAtivos(zip)).hasSize(1);
        assertThat(linksAtivos(jpeg)).hasSize(1);
        String urlJpeg = "https://saigt.teste/api/v1/publico/" + linksAtivos(jpeg).get(0).getToken()
                + "/0307pcgeral0854.JPG";
        assertThat(linkJpeg(zip)).isEqualTo(urlJpeg);
        JsonNode dgtAct = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
        assertThat(dgtAct.get("ImageURLList")).hasSize(1);
        assertThat(dgtAct.get("ImageURLList").get(0).asText()).isEqualTo(urlJpeg);
        assertThat(dgtAct.get("FileURLList")).hasSize(3);
        assertThat(dgtAct.get("FileURLList").toString()).doesNotContain(urlJpeg).contains("Planta_Ordenamento.zip");
    }

    /** AC8: quando o `SubmitNewAct` sai, o link INCM já tem commit (uma transação independente já o vê). */
    @Test
    void deveTerOLinkIncmConfirmadoNaBdAntesDeChamarAIncm() {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento jpeg = adicionarImagemDePublicacao(cenario, "0307pcgeral0854.jpg");
        TransactionTemplate outraTransacao = new TransactionTemplate(transactionTemplate.getTransactionManager());
        outraTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Integer> linksVistosDuranteAChamada = new java.util.ArrayList<>();
        doAnswer(invocacao -> {
            // Ainda dentro da chamada à INCM (e da transação do envio): outra transação tem de ver o link.
            linksVistosDuranteAChamada.add(outraTransacao.execute(estado -> jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM documento_link_publico WHERE destino = 'INCM' AND revogado_em IS NULL "
                            + "AND processo_documento_id IN (?, ?)", Integer.class,
                    cenario.publicacao().getId(), jpeg.getId())));
            return invocacao.callRealMethod();
        }).when(clienteIncm).submeterAto(any(), any());

        enviar(cenario);

        assertThat(INCM.pedidos()).hasSize(1);
        assertThat(linksVistosDuranteAChamada).containsExactly(2);
        assertThat(linksAtivos(jpeg)).hasSize(1);
    }

    /** AC9 (OI-443, TR-218): a SAIGT não converte TIFF em JPEG; um TIFF de publicação não gera imagem. */
    @Test
    void naoDeveConverterTiffEmJpegUmTiffDePublicacaoNaoGeraImagemNaImageUrlList() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento tiff = adicionarPlantaTiffDePublicacao(cenario, "0307PCgeral0854");

        enviar(cenario);

        JsonNode dgtAct = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
        assertThat(dgtAct.get("ImageURLList")).isEmpty();
        assertThat(dgtAct.get("FileURLList").toString()).contains("0307PCgeral0854.tif");
        assertThat(dgtAct.toString()).doesNotContain(".jpg").doesNotContain(".jpeg");
        assertThat(linkJpeg(tiff)).isNull();
        // Nada foi gravado no armazenamento além do que o utilizador carregou: não existe nenhum documento JPEG gerado.
        assertThat(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(cenario.processo().getId()).stream()
                .map(ProcessoDocumento::getExtensao)).doesNotContain("jpg", "jpeg");
    }

    @Test
    void deveEnviarAImageUrlListVaziaNumAtoSemPecasGraficas() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        JsonNode dgtAct = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
        assertThat(dgtAct.get("ImageURLList")).isEmpty();
        assertThat(dgtAct.get("FileURLList")).hasSize(1);
    }

    @Test
    void deveDeixarOLinkJpegNuloNumaPlantaSemJpegCorrespondente() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento zip = adicionarPlantaComNomeTiff(cenario, "Planta_Ordenamento.zip", "0307PCgeral0854.tif");
        adicionarImagemDePublicacao(cenario, "outra_planta.jpg");

        enviar(cenario);

        assertThat(linkJpeg(zip)).isNull();
        JsonNode dgtAct = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
        assertThat(dgtAct.get("ImageURLList")).hasSize(1);
    }

    @Test
    void deveReutilizarOsLinksENumReenvioEnviarAsMesmasListas() throws Exception {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");
        adicionarPlantaComNomeTiff(cenario, "Planta_Ordenamento.zip", "0307PCgeral0854");
        adicionarImagemDePublicacao(cenario, "0307PCgeral0854.jpg");
        enviar(cenario);
        Processo anulado = recarregar(cenario.processo());
        anulado.setEstado(EstadoProcesso.VALIDADO_DGT);
        processoRepository.saveAndFlush(anulado);
        INCM.responder(SUBMIT_NEW_ACT, 200, SUCESSO);

        enviar(cenario);

        JsonNode primeiro = objectMapper.readTree(INCM.pedidos().get(0).corpo()).get("DGTAct");
        JsonNode segundo = objectMapper.readTree(INCM.pedidos().get(1).corpo()).get("DGTAct");
        assertThat(primeiro.get("ImageURLList")).hasSize(1).isEqualTo(segundo.get("ImageURLList"));
        assertThat(primeiro.get("FileURLList")).hasSize(2).isEqualTo(segundo.get("FileURLList"));
    }

    @Test
    void deveRecusarSemChamarAINCMNemCriarLinksQuandoUmTipoNaoPermiteLinkPublico() {
        Cenario cenario = criarCenario("Aviso de início");
        retirarLinkPublicoPermitido(cenario.publicacao());

        assertThatThrownBy(() -> enviar(cenario)).isInstanceOf(DadosIncmEmFaltaException.class)
                .hasMessageContaining("link público");

        assertThat(INCM.pedidos()).isEmpty();
        verificarSemTransicaoNemLink(cenario);
    }

    @Test
    void deveSubstituirOActIdentifierNumSegundoEnvio() {
        INCM.responder(SUBMIT_NEW_ACT, 200, ERRO);
        Cenario cenario = criarCenario("Aviso de início");
        enviar(cenario);
        // Correção e nova validação da DGT fora do âmbito: só interessa o reenvio.
        Processo anulado = recarregar(cenario.processo());
        anulado.setEstado(EstadoProcesso.VALIDADO_DGT);
        anulado.setIncmActIdentifier("900000000000009");
        processoRepository.saveAndFlush(anulado);
        INCM.responder(SUBMIT_NEW_ACT, 200, SUCESSO);

        enviar(cenario);

        assertThat(recarregar(cenario.processo()).getIncmActIdentifier()).isEqualTo("900000000000001");
        assertThat(comunicacoes(cenario.processo()))
                .extracting(IncmComunicacao::getSubmissao, IncmComunicacao::getTransicaoDerivada)
                .containsExactly(
                        tuple((short) 1, "ANULADO_INCM"),
                        tuple((short) 2, "EM_VALIDACAO_INCM"));
    }

    @Test
    void deveFalharForaDeUmaTransacao() {
        Cenario cenario = criarCenario("Aviso de início");

        assertThatThrownBy(() -> service.enviar(cenario.processo().getId(), cenario.utilizadorId()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(INCM.pedidos()).isEmpty();
    }

    @Test
    void naoDeveEscreverOServiceKeyNosLogsNemNoPedidoGravado(CapturedOutput output) {
        Cenario cenario = criarCenario("Aviso de início");

        enviar(cenario);

        assertThat(INCM.pedidos()).singleElement()
                .satisfies(pedido -> assertThat(pedido.cabecalhos().getFirst("ServiceKey")).isEqualTo(SERVICE_KEY));
        assertThat(output.getAll()).doesNotContain(SERVICE_KEY);
        assertThat(comunicacoes(cenario.processo()).get(0).getPedido()).doesNotContain(SERVICE_KEY);
    }

    private List<Map<String, Object>> recusas(Processo processo) {
        return jdbcTemplate.queryForList("SELECT sequencia, origem, motivo_texto, criado_por, criado_em "
                + "FROM processo_rejeicao WHERE processo_id = ? ORDER BY sequencia", processo.getId());
    }

    private void verificarSemTransicaoNemLink(Cenario cenario) {
        verificarSemTransicao(cenario);
        assertThat(linksAtivos(cenario.publicacao())).isEmpty();
    }

    /** O link tem commit antes da chamada: fica, e o reenvio reutiliza-o. */
    private void verificarSemTransicaoComLinkCriado(Cenario cenario) {
        verificarSemTransicao(cenario);
        assertThat(linksAtivos(cenario.publicacao())).hasSize(1);
    }

    private void verificarSemTransicao(Cenario cenario) {
        assertThat(recarregar(cenario.processo()).getEstado()).isEqualTo(EstadoProcesso.VALIDADO_DGT);
        assertThat(recarregar(cenario.processo()).getIncmActIdentifier()).isNull();
        assertThat(auditoria(cenario.processo())).isEmpty();
    }

    private boolean retido(ProcessoDocumento documento) {
        ProcessoDocumento atual = recarregar(documento);
        return armazenamentoDocumentos.temRetencao(atual.getCaminhoFicheiro(), atual.getVersaoObjeto());
    }
}
