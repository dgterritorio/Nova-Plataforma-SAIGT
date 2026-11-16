package com.conkord.saigt.controller.notificacao;

import com.jayway.jsonpath.JsonPath;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagem;
import com.conkord.saigt.persistence.operacional.NotificacaoMensagemRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.auth-disabled=false",
        "logging.level.com.conkord.saigt.service.notificacao=DEBUG"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NotificacaoControllerIntegrationTest {

    private static final String URL = "/api/v1/notificacoes";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private NotificacaoMensagemRepository mensagemRepository;
    @Autowired
    private NotificacaoDocumentoRepository documentoRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
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
    private InstrumentoRepository instrumentoRepository;
    @Autowired
    private DinamicaRepository dinamicaRepository;
    @Autowired
    private ProcessoRepository processoRepository;

    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador utilizadorA;
    private Utilizador outroDeA;
    private Utilizador master;
    private Utilizador outroMaster;

    @BeforeEach
    void criarCenario() {
        mensagemRepository.deleteAllInBatch();
        documentoRepository.deleteAllInBatch();
        notificacaoRepository.deleteAllInBatch();

        int n = SEQUENCIA.incrementAndGet();
        entidadeA = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif(n, 1), "Câmara Municipal A" + n));
        entidadeB = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif(n, 2), "Câmara Municipal B" + n));
        utilizadorA = utilizador("Ana Teixeira", "ana" + n + "@cm-a.pt", "INCM");
        outroDeA = utilizador("Rui Matos", "rui" + n + "@cm-a.pt", "INCM");
        master = interno("Marta Master", "marta" + n + "@dgt.pt");
        outroMaster = interno("Mário Master", "mario" + n + "@dgt.pt");
    }

    @Test
    void externoVeASuaTemporariaADaSuaEntidadeEAsParaTodosENaoADeOutraEntidade() throws Exception {
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        Notificacao deA = gravar(TipoNotificacao.ESTATICA, master, x -> x.setEntidade(entidadeA));
        Notificacao deB = gravar(TipoNotificacao.ESTATICA, master, x -> x.setEntidade(entidadeB));
        Notificacao paraTodos = gravar(TipoNotificacao.ESTATICA, master, x -> { });

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        ids(temporaria, deA, paraTodos))));

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(
                        ids(temporaria, deB, paraTodos))));
    }

    @Test
    void alertaParaMasterSoChegaAoMasterETecnicoEVisitanteNaoAcedemEODetalheDa404() throws Exception {
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id(alerta))));
        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(URL).with(interno(outroDeA, PerfilInterno.TECNICO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get(URL).with(interno(outroDeA, PerfilInterno.VISITANTE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/" + alerta.getId()).with(interno(outroDeA, PerfilInterno.TECNICO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        mockMvc.perform(get(URL + "/" + alerta.getId()).with(interno(outroDeA, PerfilInterno.VISITANTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
    }

    @Test
    void pedidoDeApoioChegaAoAutorEAoMasterMasNaoAOutroUtilizadorDaEntidade() throws Exception {
        Notificacao pedido = gravar(TipoNotificacao.PEDIDO_APOIO, utilizadorA,
                x -> x.setPerfilDestino(PerfilInterno.MASTER));

        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(contains(id(pedido))));
        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id(pedido))));
        listarIds(externo(outroDeA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void pedidoDeApoioGeradoPelaPlataformaChegaATodosOsMasterENaoAosUtilizadoresExternos() throws Exception {
        Notificacao pedido = gravar(TipoNotificacao.PEDIDO_APOIO, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id(pedido))));
        listarIds(interno(outroMaster, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id(pedido))));
        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void respostaObrigatoriaCriadaPorUmMasterNaoChegaAOutroMaster() throws Exception {
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeA));

        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id(resposta))));
        listarIds(interno(outroMaster, PerfilInterno.MASTER)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void semEstadoSoVemAsAtivasEComEstadoSoAsPedidas() throws Exception {
        Notificacao ativa = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        Notificacao lida = gravar(TipoNotificacao.TEMPORARIA, null, x -> {
            x.setUtilizador(utilizadorA);
            x.setEstado(EstadoNotificacao.LIDA);
        });
        Notificacao resolvida = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> {
            x.setUtilizador(utilizadorA);
            x.setEstado(EstadoNotificacao.RESOLVIDA);
        });

        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(contains(id(ativa))));
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("estado", "LIDA", "RESOLVIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(ids(lida, resolvida))));
    }

    @Test
    void noticiaForaDeVigenciaNaoApareceNaListaENoDetalheDa404() throws Exception {
        Notificacao expirada = gravar(TipoNotificacao.ESTATICA, master, x -> {
            x.setEntidade(entidadeA);
            x.setPrazo(Instant.now().minus(Duration.ofDays(1)));
        });

        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(URL + "/" + expirada.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
    }

    @Test
    void filtraPorTipo() throws Exception {
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setPerfilDestino(null));
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));
        gravar(TipoNotificacao.ESTATICA, master, x -> { });

        mockMvc.perform(get(URL).with(interno(master, PerfilInterno.MASTER)).param("tipo", "TEMPORARIA", "ALERTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder(ids(temporaria, alerta))));
    }

    @Test
    void filtraPorProcessoComONumeroDoProcesso() throws Exception {
        Processo processo1 = criarProcesso("2026/000101");
        Processo processo2 = criarProcesso("2026/000102");
        Notificacao doProcesso1 = gravar(TipoNotificacao.TEMPORARIA, null, x -> {
            x.setUtilizador(utilizadorA);
            x.setProcesso(processo1);
        });
        gravar(TipoNotificacao.TEMPORARIA, null, x -> {
            x.setUtilizador(utilizadorA);
            x.setProcesso(processo2);
        });

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("processoId", processo1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(id(doProcesso1))))
                .andExpect(jsonPath("$.content[0].processoId").value(processo1.getId().toString()))
                .andExpect(jsonPath("$.content[0].numeroProcesso").value("2026/000101"));
    }

    @Test
    void filtraPorDiaDeCriacaoNoFusoDeLisboa() throws Exception {
        Notificacao dia10 = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        Notificacao dia12 = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        // 23:30 UTC de 9 de outubro já é dia 10 em Lisboa (UTC+1 no horário de verão).
        mudarCriadoEm(dia10, Instant.parse("2026-10-09T23:30:00Z"));
        mudarCriadoEm(dia12, Instant.parse("2026-10-12T10:00:00Z"));

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("desde", "2026-10-10").param("ate", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains(id(dia10))));
    }

    @Test
    void paginaComMaximoDe100EOrdenadaDaMaisRecenteParaAMaisAntiga() throws Exception {
        String[] esperados = new String[25];
        for (int i = 0; i < 25; i++) {
            Notificacao notificacao = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
            mudarCriadoEm(notificacao, Instant.parse("2026-10-01T10:00:00Z").plus(Duration.ofHours(i)));
            esperados[24 - i] = notificacao.getId().toString();
        }

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.content[*].id").value(contains(esperados)));
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void estadoOuTipoForaDoEnumDao400ComOCodigoProprio() throws Exception {
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("estado", "QUALQUER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_FILTRO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Filtro de notificações inválido."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.campos[0].path").value("estado"));
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("tipo", "OUTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_FILTRO_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].path").value("tipo"));
    }

    @Test
    void processoIdOuDataMalFormadosDao400Generico() throws Exception {
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("processoId", "nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("processoId"));
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("desde", "10/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void semTokenDa401NosDoisEndpoints() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").exists());
        mockMvc.perform(get(URL + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").exists());
    }

    @Test
    void criadoPorNomeNuloNasDaPlataformaENenhumaRespostaTemEmails() throws Exception {
        Notificacao daPlataforma = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        Notificacao doMaster = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setUtilizador(utilizadorA));

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + daPlataforma.getId() + "')].criadoPorNome").value(contains((Object) null)))
                .andExpect(jsonPath("$.content[?(@.id=='" + doMaster.getId() + "')].criadoPorNome").value(contains("Marta Master")))
                .andExpect(content().string(not(containsString("@"))));
        mockMvc.perform(get(URL + "/" + doMaster.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("@"))));
    }

    @Test
    void contaMensagensEAnexosDeCadaLinha() throws Exception {
        Notificacao comConversa = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setUtilizador(utilizadorA));
        Notificacao semConversa = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        mensagemRepository.saveAndFlush(new NotificacaoMensagem(comConversa, master, "Pedimos o regulamento."));
        mensagemRepository.saveAndFlush(new NotificacaoMensagem(comConversa, utilizadorA, "Segue em anexo."));
        documentoRepository.saveAndFlush(new NotificacaoDocumento(comConversa, "regulamento.pdf",
                "notificacoes/" + comConversa.getId() + "/regulamento.pdf", 2048L, utilizadorA));

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + comConversa.getId() + "')].numeroMensagens").value(contains(2)))
                .andExpect(jsonPath("$.content[?(@.id=='" + comConversa.getId() + "')].numeroDocumentos").value(contains(1)))
                .andExpect(jsonPath("$.content[?(@.id=='" + semConversa.getId() + "')].numeroMensagens").value(contains(0)))
                .andExpect(jsonPath("$.content[?(@.id=='" + semConversa.getId() + "')].numeroDocumentos").value(contains(0)));
    }

    @Test
    void detalheTrazAConversaPorOrdemOAnexoSemCaminhoEOsDestinatarios() throws Exception {
        Notificacao notificacao = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeA));
        mensagemRepository.saveAndFlush(new NotificacaoMensagem(notificacao, master, "Primeira"));
        mensagemRepository.saveAndFlush(new NotificacaoMensagem(notificacao, utilizadorA, "Segunda"));
        documentoRepository.saveAndFlush(new NotificacaoDocumento(notificacao, "parecer.pdf",
                "notificacoes/" + notificacao.getId() + "/parecer.pdf", 1024L, utilizadorA));

        mockMvc.perform(get(URL + "/" + notificacao.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificacao.getId().toString()))
                .andExpect(jsonPath("$.criadoPorNome").value("Marta Master"))
                .andExpect(jsonPath("$.destinatarioEntidadeNome").value(entidadeA.getDescricaoIncm()))
                .andExpect(jsonPath("$.destinatarioUtilizadorNome").doesNotExist())
                .andExpect(jsonPath("$.numeroMensagens").value(2))
                .andExpect(jsonPath("$.numeroDocumentos").value(1))
                .andExpect(jsonPath("$.mensagens[*].texto").value(contains("Primeira", "Segunda")))
                .andExpect(jsonPath("$.mensagens[0].autorId").value(master.getId().toString()))
                .andExpect(jsonPath("$.mensagens[0].autorNome").value("Marta Master"))
                .andExpect(jsonPath("$.mensagens[1].autorNome").value("Ana Teixeira"))
                .andExpect(jsonPath("$.documentos[0].nomeFicheiro").value("parecer.pdf"))
                .andExpect(jsonPath("$.documentos[0].tamanhoBytes").value(1024))
                .andExpect(jsonPath("$.documentos[0].caminhoFicheiro").doesNotExist())
                .andExpect(content().string(not(containsString("notificacoes/"))));
    }

    @Test
    void detalheDeLinhaNaoVisivelEIgualAoDeIdInexistente() throws Exception {
        Notificacao deB = gravar(TipoNotificacao.ESTATICA, master, x -> x.setEntidade(entidadeB));

        String naoVisivel = mockMvc.perform(get(URL + "/" + deB.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"))
                .andExpect(jsonPath("$.mensagem").value("A notificação não existe ou não está disponível."))
                .andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(semCamposVariaveis(naoVisivel)).isEqualTo(semCamposVariaveis(inexistente));
    }

    @Test
    void abrirODetalheNaoMarcaComoLida() throws Exception {
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));

        mockMvc.perform(get(URL + "/" + temporaria.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.lidoEm").doesNotExist());

        Notificacao relida = notificacaoRepository.findById(temporaria.getId()).orElseThrow();
        assertThat(relida.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(relida.getLidoEm()).isNull();
    }

    @Test
    void oLogDaListagemNaoTemTitulosTextosNomesNemEmails(CapturedOutput output) throws Exception {
        gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setUtilizador(utilizadorA));

        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA))).andExpect(status().isOk());

        assertThat(output.getOut()).contains("Listagem de notificações: utilizadorId=" + utilizadorA.getId())
                .doesNotContain("Título confidencial", "Texto confidencial", "Marta Master", "Ana Teixeira",
                        utilizadorA.getEmail(), master.getEmail());
    }

    @Test
    void masterCriaUmaNoticiaParaTodosQueTodosVeem() throws Exception {
        String corpo = criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"  Manutenção  ","texto":"Texto da notícia"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loteId").doesNotExist())
                .andExpect(jsonPath("$.notificacoes", hasSize(1)))
                .andExpect(jsonPath("$.notificacoes[0].tipo").value("ESTATICA"))
                .andExpect(jsonPath("$.notificacoes[0].titulo").value("Manutenção"))
                .andExpect(jsonPath("$.notificacoes[0].estado").value("ATIVA"))
                .andExpect(jsonPath("$.notificacoes[0].criadoPorNome").value("Marta Master"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.notificacoes[0].id");

        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(contains(id)));
        listarIds(interno(outroMaster, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id)));
    }

    @Test
    void noticiaParaDuasEntidadesEUmUtilizadorGravaTresLinhasComOMesmoLote() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"Aviso","texto":"Texto","destinatarios":[
                  {"entidadeId":%d},{"entidadeId":%d},{"utilizadorId":"%s"}]}"""
                .formatted(entidadeA.getId(), entidadeB.getId(), outroDeA.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes", hasSize(3)))
                .andExpect(jsonPath("$.loteId").isNotEmpty());

        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                "SELECT entidade_id, utilizador_id, lote_id FROM notificacao");
        assertThat(linhas).hasSize(3);
        assertThat(linhas).extracting(linha -> linha.get("lote_id")).doesNotContainNull()
                .containsOnly(linhas.get(0).get("lote_id"));
        assertThat(linhas).allSatisfy(linha ->
                assertThat(linha.get("entidade_id") == null).isNotEqualTo(linha.get("utilizador_id") == null));
    }

    @Test
    void noticiaComAMesmaEntidadeRepetidaGravaUmaSoLinha() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"Aviso","texto":"Texto","destinatarios":[
                  {"entidadeId":%d},{"entidadeId":%d}]}""".formatted(entidadeA.getId(), entidadeA.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes", hasSize(1)))
                .andExpect(jsonPath("$.loteId").doesNotExist());

        assertThat(notificacaoRepository.count()).isEqualTo(1);
    }

    @Test
    void respostaObrigatoriaParaAEntidadeComProcessoEVistaPelosUtilizadoresDaEntidade() throws Exception {
        Processo processo = criarProcesso("2026000123");

        String corpo = criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Regularização","texto":"Texto",
                 "processoId":"%s","prazo":"2026-12-31T23:59:59.999Z","destinatarios":[{"entidadeId":%d}]}"""
                .formatted(processo.getId(), entidadeA.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loteId").doesNotExist())
                .andExpect(jsonPath("$.notificacoes[0].processoId").value(processo.getId().toString()))
                .andExpect(jsonPath("$.notificacoes[0].numeroProcesso").value("2026000123"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.notificacoes[0].id"));

        Notificacao gravada = notificacaoRepository.findById(id).orElseThrow();
        assertThat(gravada.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
        assertThat(gravada.isCanalEmail()).isFalse();
        assertThat(gravada.getPrazo()).isEqualTo(Instant.parse("2026-12-31T23:59:59.999Z"));
        Map<String, Object> linha = jdbcTemplate.queryForMap(
                "SELECT entidade_id, processo_id, criado_por FROM notificacao WHERE id = ?", id);
        assertThat(((Number) linha.get("entidade_id")).longValue()).isEqualTo(entidadeA.getId());
        assertThat(linha.get("processo_id").toString()).isEqualTo(processo.getId().toString());
        assertThat(linha.get("criado_por").toString()).isEqualTo(master.getId().toString());

        listarIds(externo(outroDeA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(contains(id.toString())));
        listarIds(externo(utilizadorA, entidadeB))
                .andExpect(jsonPath("$.content[*].id").value(not(contains(id.toString()))));
    }

    @Test
    void respostaObrigatoriaSemProcessoNemIdIgtOuSoComIdIgtEAceite() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Pedido","texto":"Texto",
                 "destinatarios":[{"utilizadorId":"%s"}]}""".formatted(utilizadorA.getId()))
                .andExpect(status().isCreated());
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Pedido","texto":"Texto","idIgtSnit":"IGT-1",
                 "descricaoIgt":"PDM de teste","destinatarios":[{"entidadeId":%d}]}""".formatted(entidadeA.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes[0].idIgtSnit").value("IGT-1"));
    }

    @Test
    void respostaObrigatoriaComZeroOuDoisDestinatariosDa400() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Pedido","texto":"Texto"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIOS_INVALIDOS"));
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Pedido","texto":"Texto",
                 "destinatarios":[{"entidadeId":%d},{"entidadeId":%d}]}"""
                .formatted(entidadeA.getId(), entidadeB.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIOS_INVALIDOS"));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void destinatarioComEntidadeEUtilizadorOuSemNenhumDa400() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"Aviso","texto":"Texto",
                 "destinatarios":[{"entidadeId":%d,"utilizadorId":"%s"}]}"""
                .formatted(entidadeA.getId(), utilizadorA.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIOS_INVALIDOS"));
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"Aviso","texto":"Texto","destinatarios":[{}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIOS_INVALIDOS"));
    }

    @Test
    void entidadeUtilizadorOuProcessoInexistenteDa404ENadaEGravado() throws Exception {
        for (String destino : List.of("{\"entidadeId\":999999999}",
                "{\"utilizadorId\":\"" + UUID.randomUUID() + "\"}")) {
            criar(interno(master, PerfilInterno.MASTER), """
                    {"tipo":"ESTATICA","titulo":"Aviso","texto":"Texto","destinatarios":[{"entidadeId":%d},%s]}"""
                    .formatted(entidadeA.getId(), destino))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIO_INEXISTENTE"))
                    .andExpect(jsonPath("$.mensagem").value("Destinatário inexistente."));
        }
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"RESPOSTA_OBRIGATORIA","titulo":"Pedido","texto":"Texto","processoId":"%s",
                 "destinatarios":[{"entidadeId":%d}]}""".formatted(UUID.randomUUID(), entidadeA.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIO_INEXISTENTE"));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEMPORARIA", "ALERTA"})
    void tiposGeradosPelaPlataformaOuDeOutraVersaoDao400(String tipo) throws Exception {
        criar(externo(utilizadorA, entidadeA), """
                {"tipo":"%s","titulo":"Aviso","texto":"Texto"}""".formatted(tipo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_NAO_CRIAVEL"))
                .andExpect(jsonPath("$.mensagem").value(
                        "Este tipo de notificação é gerado pela plataforma e não pode ser criado manualmente."));
    }

    @Test
    void oUtilizadorDeUmaEntidadeCriaUmPedidoDeApoioParaOMaster() throws Exception {
        criar(externo(utilizadorA, entidadeA), """
                {"tipo":"PEDIDO_APOIO","titulo":"Dúvida","texto":"Não consigo carregar a planta."}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes.length()").value(1))
                .andExpect(jsonPath("$.notificacoes[0].tipo").value("PEDIDO_APOIO"));

        assertThat(notificacaoRepository.findAll()).singleElement().satisfies(linha -> {
            assertThat(linha.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
            assertThat(linha.getCriadoPor().getId()).isEqualTo(utilizadorA.getId());
        });
    }

    @Test
    void oMasterNaoCriaPedidosDeApoio() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"PEDIDO_APOIO","titulo":"Dúvida","texto":"Texto"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void tecnicoVisitanteEUtilizadorDeEntidadeRecebem403ENadaEGravado() throws Exception {
        for (RequestPostProcessor token : List.of(interno(outroMaster, PerfilInterno.TECNICO),
                interno(outroMaster, PerfilInterno.VISITANTE), externo(utilizadorA, entidadeA))) {
            for (String tipo : List.of("ESTATICA", "RESPOSTA_OBRIGATORIA")) {
                criar(token, """
                        {"tipo":"%s","titulo":"Aviso","texto":"Texto","destinatarios":[{"entidadeId":%d}]}"""
                        .formatted(tipo, entidadeA.getId()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"))
                        .andExpect(jsonPath("$.mensagem").value("Só um utilizador Master pode criar esta notificação."));
            }
        }
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void tituloEmBrancoOuCom201CaracteresETextoCom5001Dao400ComOCampo() throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"   ","texto":"Texto"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("titulo")));
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"%s","texto":"%s"}""".formatted("a".repeat(201), "b".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(containsInAnyOrder("titulo", "texto")));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void oLogDaCriacaoTemTipoLinhasELoteSemTitulosTextosNomesNemEmails(CapturedOutput output) throws Exception {
        criar(interno(master, PerfilInterno.MASTER), """
                {"tipo":"ESTATICA","titulo":"Título confidencial","texto":"Texto confidencial","destinatarios":[
                  {"entidadeId":%d},{"utilizadorId":"%s"}]}""".formatted(entidadeA.getId(), utilizadorA.getId()))
                .andExpect(status().isCreated());

        String lote = notificacaoRepository.findAll().get(0).getLoteId().toString();
        assertThat(output.getOut()).contains("Notificação criada: tipo=ESTATICA, linhas=2, loteId=" + lote
                        + ", utilizadorId=" + master.getId())
                .doesNotContain("Título confidencial", "Texto confidencial", "Marta Master", "Ana Teixeira",
                        utilizadorA.getEmail(), master.getEmail());
    }

    private ResultActions criar(RequestPostProcessor token, String corpo) throws Exception {
        return mockMvc.perform(post(URL).with(token).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    // --- POST /{id}/mensagens ---

    @Test
    void masterRespondeAoPedidoDeApoioAtivoEResolveo() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        responder(pedido, interno(master, PerfilInterno.MASTER), "  Pedido tratado.  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESOLVIDA"))
                .andExpect(jsonPath("$.mensagem.id").exists())
                .andExpect(jsonPath("$.mensagem.autorId").value(master.getId().toString()))
                .andExpect(jsonPath("$.mensagem.autorNome").value("Marta Master"))
                .andExpect(jsonPath("$.mensagem.texto").value("Pedido tratado."))
                .andExpect(jsonPath("$.mensagem.criadoEm").exists())
                .andExpect(jsonPath("$.mensagem.notificacao").doesNotExist());

        Notificacao relida = notificacaoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(relida.getEstado()).isEqualTo(EstadoNotificacao.RESOLVIDA);
        assertThat(relida.getResolvidoEm()).isNotNull();
        assertThat(mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(pedido.getId()))
                .extracting(NotificacaoMensagem::getTexto).containsExactly("Pedido tratado.");
    }

    @Test
    void oAutorRespondeAoSeuPedidoDeApoioEElePermaneceAtivo() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        responder(pedido, externo(utilizadorA, entidadeA), "Junto mais dados")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ATIVA"));
        assertThat(notificacaoRepository.findById(pedido.getId()).orElseThrow().getResolvidoEm()).isNull();
    }

    @Test
    void pedidoDeApoioResolvidoDa409ENadaEGravado() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.RESOLVIDA);

        responder(pedido, externo(utilizadorA, entidadeA), "Olá")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("A notificação já não aceita mensagens ou anexos."));
        responder(pedido, interno(master, PerfilInterno.MASTER), "Olá")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));
        assertThat(mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(pedido.getId())).isEmpty();
    }

    @Test
    void respostaObrigatoriaDaEntidadeResolveComARespostaDeOutroUtilizadorDaEntidade() throws Exception {
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeA));

        responder(resposta, externo(outroDeA, entidadeA), "Segue a resposta")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESOLVIDA"));
        assertThat(notificacaoRepository.findById(resposta.getId()).orElseThrow().getResolvidoEm()).isNotNull();
    }

    @Test
    void respostaObrigatoriaResolvidaContinuaAConversaSemMudarOEstado() throws Exception {
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> {
            x.setEntidade(entidadeA);
            x.setEstado(EstadoNotificacao.RESOLVIDA);
        });

        responder(resposta, interno(master, PerfilInterno.MASTER), "Obrigado")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESOLVIDA"));
        responder(resposta, externo(utilizadorA, entidadeA), "De nada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESOLVIDA"));
        assertThat(mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(resposta.getId())).hasSize(2);
    }

    @Test
    void oMasterAutorDaRespostaObrigatoriaAtivaNaoAResolve() throws Exception {
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeA));

        responder(resposta, interno(master, PerfilInterno.MASTER), "Lembrete")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ATIVA"));
    }

    @Test
    void masterPedeMaisInformacaoNaRegularizacaoEElaContinuaAtiva() throws Exception {
        Notificacao regularizacao = gravar(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorA,
                x -> x.setPerfilDestino(PerfilInterno.MASTER));

        responder(regularizacao, interno(master, PerfilInterno.MASTER), "Falta a deliberação")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ATIVA"));
    }

    @Test
    void tiposSemConversacaoDao409() throws Exception {
        Notificacao estatica = gravar(TipoNotificacao.ESTATICA, master, x -> x.setEntidade(entidadeA));
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        for (ResultActions resultado : new ResultActions[]{
                responder(estatica, externo(utilizadorA, entidadeA), "Olá"),
                responder(temporaria, externo(utilizadorA, entidadeA), "Olá"),
                responder(alerta, interno(master, PerfilInterno.MASTER), "Olá")}) {
            resultado.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_SEM_CONVERSACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Este tipo de notificação não tem conversação."));
        }
    }

    @Test
    void quemVeALinhaMasNaoParticipaRecebe403() throws Exception {
        Notificacao semDestino = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> { });

        responder(semDestino, externo(outroDeA, entidadeA), "Olá")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensagem").value("Não pode responder a esta notificação."));
    }

    @Test
    void tecnicoEVisitanteRecebem404AoResponderEAoAbrirOPedidoDeApoio() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        for (PerfilInterno perfil : new PerfilInterno[]{PerfilInterno.TECNICO, PerfilInterno.VISITANTE}) {
            responder(pedido, interno(outroMaster, perfil), "Olá")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
            mockMvc.perform(get(URL + "/" + pedido.getId()).with(interno(outroMaster, perfil)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        }
        assertThat(mensagemRepository.findByNotificacao_IdOrderByCriadoEmAsc(pedido.getId())).isEmpty();
    }

    @Test
    void responderAOutraEntidadeEIgualAResponderAUmIdInexistente() throws Exception {
        Notificacao deB = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeB));

        String naoVisivel = responder(deB, externo(utilizadorA, entidadeA), "Olá")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"))
                .andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(post(URL + "/" + UUID.randomUUID() + "/mensagens")
                        .with(externo(utilizadorA, entidadeA)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Olá")))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(semCamposVariaveis(naoVisivel)).isEqualTo(semCamposVariaveis(inexistente));
    }

    @Test
    void textoEmBrancoOuCom5001CaracteresDa400ComOCampo() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        responder(pedido, externo(utilizadorA, entidadeA), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("texto"));
        responder(pedido, externo(utilizadorA, entidadeA), "a".repeat(5001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("texto"));
        responder(pedido, externo(utilizadorA, entidadeA), "a".repeat(5000))
                .andExpect(status().isCreated());
    }

    @Test
    void responderSemTokenDa401ComOEnvelope() throws Exception {
        mockMvc.perform(post(URL + "/" + UUID.randomUUID() + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Olá")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").exists());
    }

    @Test
    void oLogDaMensagemTemOIdOTipoEOEstadoENaoOTextoNomesNemEmails(CapturedOutput output) throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        responder(pedido, interno(master, PerfilInterno.MASTER), "Texto secreto da resposta")
                .andExpect(status().isCreated());

        assertThat(output.getOut()).contains("Mensagem de notificação registada: notificacaoId=" + pedido.getId()
                        + ", tipo=PEDIDO_APOIO, estado=RESOLVIDA, utilizadorId=" + master.getId())
                .doesNotContain("Texto secreto da resposta", "Marta Master", "Ana Teixeira", master.getEmail(),
                        utilizadorA.getEmail());
    }

    @Test
    void oDetalheDizSeOChamadorPodeResponder() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);
        Notificacao resolvido = pedidoDeApoio(EstadoNotificacao.RESOLVIDA);
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));

        mockMvc.perform(get(URL + "/" + pedido.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(contains("RESPONDER", "ANEXAR")));
        mockMvc.perform(get(URL + "/" + pedido.getId()).with(interno(master, PerfilInterno.MASTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(contains("RESPONDER", "ANEXAR", "RESOLVER")));
        mockMvc.perform(get(URL + "/" + resolvido.getId()).with(interno(master, PerfilInterno.MASTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(empty()));
        mockMvc.perform(get(URL + "/" + temporaria.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(contains("LER")));
    }

    // --- ler, resolver e remover ---

    @Test
    void oDestinatarioLeASuaTemporariaQueSaiDaListagemSemFiltro() throws Exception {
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(utilizadorA));

        operacao(temporaria, "ler", externo(utilizadorA, entidadeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id(temporaria)))
                .andExpect(jsonPath("$.estado").value("LIDA"))
                .andExpect(jsonPath("$.lidoEm").value(notNullValue()));

        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(URL).with(externo(utilizadorA, entidadeA)).param("estado", "LIDA"))
                .andExpect(jsonPath("$.content[*].id").value(contains(id(temporaria))));
    }

    @Test
    void outroUtilizadorQueVeATemporariaRecebe403EQuemNaoAVeRecebe404() throws Exception {
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, outroDeA, x -> x.setUtilizador(utilizadorA));

        operacao(temporaria, "ler", externo(outroDeA, entidadeA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"));
        operacao(temporaria, "ler", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        assertThat(estado(temporaria)).isEqualTo(EstadoNotificacao.ATIVA);
    }

    @Test
    void umAlertaLidoPorUmMasterSaiDasAtivasDeTodosOsMaster() throws Exception {
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        operacao(alerta, "ler", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIDA"));

        listarIds(interno(outroMaster, PerfilInterno.MASTER)).andExpect(jsonPath("$.content", hasSize(0)));
        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void tiposSemLeituraEUmaTemporariaJaLidaDao409() throws Exception {
        Notificacao noticia = gravar(TipoNotificacao.ESTATICA, master, x -> x.setUtilizador(utilizadorA));
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);
        Notificacao lida = gravar(TipoNotificacao.TEMPORARIA, null, x -> {
            x.setUtilizador(utilizadorA);
            x.setEstado(EstadoNotificacao.LIDA);
        });

        for (Notificacao semLeitura : List.of(noticia, pedido)) {
            operacao(semLeitura, "ler", externo(utilizadorA, entidadeA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_SEM_LEITURA"))
                    .andExpect(jsonPath("$.mensagem").value("Este tipo de notificação não tem leitura."));
        }
        operacao(lida, "ler", externo(utilizadorA, entidadeA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));
    }

    @Test
    void masterResolveOPedidoDeApoioOAutorRecebe403ETecnicoEVisitante404() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);

        operacao(pedido, "resolver", externo(utilizadorA, entidadeA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensagem").value("Só um utilizador Master pode resolver esta notificação."));
        for (PerfilInterno perfil : List.of(PerfilInterno.TECNICO, PerfilInterno.VISITANTE)) {
            operacao(pedido, "resolver", interno(outroMaster, perfil))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        }
        operacao(pedido, "resolver", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESOLVIDA"))
                .andExpect(jsonPath("$.resolvidoEm").value(notNullValue()));
        operacao(pedido, "resolver", interno(outroMaster, PerfilInterno.MASTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));
    }

    @Test
    void regularizacaoERespostaObrigatoriaNaoSeResolvemManualmente() throws Exception {
        Notificacao regularizacao = gravar(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorA,
                x -> x.setPerfilDestino(PerfilInterno.MASTER));
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, master, x -> x.setEntidade(entidadeA));

        operacao(regularizacao, "resolver", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_SEM_RESOLUCAO_MANUAL"))
                .andExpect(jsonPath("$.mensagem").value("Esta notificação resolve-se com o registo do procedimento."));
        operacao(resposta, "resolver", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_SEM_RESOLUCAO_MANUAL"));
        mockMvc.perform(get(URL + "/" + resposta.getId()).with(interno(master, PerfilInterno.MASTER)))
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("RESOLVER"))));
    }

    @Test
    void removerUmaNoticiaDeLoteRemoveAsTresLinhasSemApagarNenhuma() throws Exception {
        UUID loteId = UUID.randomUUID();
        gravar(TipoNotificacao.ESTATICA, master, x -> {
            x.setEntidade(entidadeA);
            x.setLoteId(loteId);
        });
        Notificacao deB = gravar(TipoNotificacao.ESTATICA, master, x -> {
            x.setEntidade(entidadeB);
            x.setLoteId(loteId);
        });
        gravar(TipoNotificacao.ESTATICA, master, x -> {
            x.setUtilizador(outroDeA);
            x.setLoteId(loteId);
        });
        mockMvc.perform(get(URL + "/" + deB.getId()).with(interno(master, PerfilInterno.MASTER)))
                .andExpect(jsonPath("$.acoesDisponiveis").value(contains("ANEXAR", "REMOVER")));

        operacao(deB, "remover", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"linhasAtualizadas\":3}", true));

        assertThat(notificacaoRepository.findByLoteId(loteId)).hasSize(3)
                .allSatisfy(n -> assertThat(n.getEstado()).isEqualTo(EstadoNotificacao.REMOVIDA));
        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content", hasSize(0)));
        listarIds(externo(outroDeA, entidadeB)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void masterDescartaOAlertaEOutroMasterJaNaoOPodeDescartar() throws Exception {
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        operacao(alerta, "remover", interno(master, PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhasAtualizadas").value(1));
        operacao(alerta, "remover", interno(outroMaster, PerfilInterno.MASTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));
        assertThat(estado(alerta)).isEqualTo(EstadoNotificacao.REMOVIDA);
    }

    @Test
    void pedidoDeApoioETemporariaNaoSaoRemoviveis() throws Exception {
        Notificacao pedido = pedidoDeApoio(EstadoNotificacao.ATIVA);
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, x -> x.setUtilizador(master));

        for (Notificacao naoRemovivel : List.of(pedido, temporaria)) {
            operacao(naoRemovivel, "remover", interno(master, PerfilInterno.MASTER))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_NAO_REMOVIVEL"))
                    .andExpect(jsonPath("$.mensagem").value("Este tipo de notificação não pode ser removido."));
        }
    }

    @Test
    void utilizadorExternoNaoRemoveANoticiaParaTodosEOMasterQueNaoVeANoticiaRecebe404() throws Exception {
        Notificacao paraTodos = gravar(TipoNotificacao.ESTATICA, master, x -> { });
        Notificacao deA = gravar(TipoNotificacao.ESTATICA, master, x -> x.setEntidade(entidadeA));

        operacao(paraTodos, "remover", externo(utilizadorA, entidadeA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"));
        operacao(deA, "remover", interno(outroMaster, PerfilInterno.MASTER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        operacao(paraTodos, "remover", interno(outroMaster, PerfilInterno.MASTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhasAtualizadas").value(1));
    }

    @Test
    void asTresOperacoesSemTokenDao401() throws Exception {
        for (String operacao : List.of("ler", "resolver", "remover")) {
            mockMvc.perform(patch(URL + "/" + UUID.randomUUID() + "/" + operacao))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").exists());
        }
    }

    @Test
    void oLogDasOperacoesTemIdsTipoEOperacaoSemTitulosTextosNomesNemEmails(CapturedOutput output) throws Exception {
        Notificacao alerta = gravar(TipoNotificacao.ALERTA, null, x -> x.setPerfilDestino(PerfilInterno.MASTER));

        operacao(alerta, "remover", interno(master, PerfilInterno.MASTER)).andExpect(status().isOk());

        assertThat(output.getOut()).contains("Estado de notificação alterado: notificacaoId=" + alerta.getId()
                        + ", tipo=ALERTA, operacao=REMOVER, linhasAtualizadas=1, utilizadorId=" + master.getId())
                .doesNotContain("Título confidencial", "Texto confidencial", "Marta Master", master.getEmail());
    }

    private ResultActions operacao(Notificacao notificacao, String operacao, RequestPostProcessor token)
            throws Exception {
        return mockMvc.perform(patch(URL + "/" + notificacao.getId() + "/" + operacao).with(token));
    }

    private EstadoNotificacao estado(Notificacao notificacao) {
        return notificacaoRepository.findById(notificacao.getId()).orElseThrow().getEstado();
    }

    @Test
    void externoCriaUmPedidoDeApoioGeralQueOMasterEOAutorVeemMasNaoOutroDaEntidade() throws Exception {
        String corpo = criar(externo(utilizadorA, entidadeA), """
                {"tipo":"PEDIDO_APOIO","titulo":" Ajuda na submissão ","texto":" Não consigo submeter. "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loteId").doesNotExist())
                .andExpect(jsonPath("$.notificacoes", hasSize(1)))
                .andExpect(jsonPath("$.notificacoes[0].tipo").value("PEDIDO_APOIO"))
                .andExpect(jsonPath("$.notificacoes[0].titulo").value("Ajuda na submissão"))
                .andExpect(jsonPath("$.notificacoes[0].processoId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.notificacoes[0].id"));

        Map<String, Object> linha = jdbcTemplate.queryForMap("SELECT perfil_destino, criado_por, processo_id, "
                + "utilizador_id, entidade_id, canal_email, estado, lote_id FROM notificacao WHERE id = ?", id);
        assertThat(linha.get("perfil_destino")).isEqualTo("MASTER");
        assertThat(linha.get("criado_por").toString()).isEqualTo(utilizadorA.getId().toString());
        assertThat(linha.get("processo_id")).isNull();
        assertThat(linha.get("utilizador_id")).isNull();
        assertThat(linha.get("entidade_id")).isNull();
        assertThat(linha.get("lote_id")).isNull();
        assertThat(linha.get("canal_email")).isEqualTo(false);
        assertThat(linha.get("estado")).isEqualTo("ATIVA");

        listarIds(interno(master, PerfilInterno.MASTER)).andExpect(jsonPath("$.content[*].id").value(contains(id.toString())));
        listarIds(externo(utilizadorA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(contains(id.toString())));
        listarIds(externo(outroDeA, entidadeA)).andExpect(jsonPath("$.content[*].id").value(empty()));
        for (PerfilInterno perfil : List.of(PerfilInterno.TECNICO, PerfilInterno.VISITANTE)) {
            mockMvc.perform(get(URL).with(interno(outroMaster, perfil))).andExpect(status().isForbidden());
            mockMvc.perform(get(URL + "/" + id).with(interno(outroMaster, perfil))).andExpect(status().isNotFound());
        }
    }

    @Test
    void externoCriaUmPedidoDeApoioDeUmProcessoDaSuaEntidade() throws Exception {
        Processo processo = criarProcesso("2026000777");

        String corpo = criar(externo(utilizadorA, entidadeA), """
                {"tipo":"PEDIDO_APOIO","titulo":"Ajuda","texto":"Texto","processoId":"%s","destinatarios":[]}"""
                .formatted(processo.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes[0].processoId").value(processo.getId().toString()))
                .andExpect(jsonPath("$.notificacoes[0].numeroProcesso").value("2026000777"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.notificacoes[0].id"));

        assertThat(jdbcTemplate.queryForObject("SELECT processo_id FROM notificacao WHERE id = ?", String.class, id))
                .isEqualTo(processo.getId().toString());
    }

    @Test
    void processoDeOutraEntidadeOuInexistenteDaOMesmo404ENadaEGravado() throws Exception {
        Processo processoDeA = criarProcesso("2026000888");
        Utilizador deB = utilizador("Bruno Bento", "bruno" + SEQUENCIA.incrementAndGet() + "@cm-b.pt", "INCM");

        String deOutraEntidade = criar(externo(deB, entidadeB), """
                {"tipo":"PEDIDO_APOIO","titulo":"Ajuda","texto":"Texto","processoId":"%s"}"""
                .formatted(processoDeA.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIO_INEXISTENTE"))
                .andReturn().getResponse().getContentAsString();
        String inexistente = criar(externo(deB, entidadeB), """
                {"tipo":"PEDIDO_APOIO","titulo":"Ajuda","texto":"Texto","processoId":"%s"}"""
                .formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(semCamposVariaveis(deOutraEntidade)).isEqualTo(semCamposVariaveis(inexistente));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void externoCriaUmaRegularizacaoComADescricaoIgtESemElaRecebe400NoCampo() throws Exception {
        String corpo = criar(externo(utilizadorA, entidadeA), """
                {"tipo":"REGULARIZACAO_PROCEDIMENTO","titulo":"Regularização","texto":"Publicado no DR n.º 12",
                 "idIgtSnit":"IGT-77","descricaoIgt":"PDM de Teste"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificacoes[0].descricaoIgt").value("PDM de Teste"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.notificacoes[0].id"));
        Map<String, Object> linha = jdbcTemplate.queryForMap(
                "SELECT perfil_destino, canal_email, criado_por FROM notificacao WHERE id = ?", id);
        assertThat(linha.get("perfil_destino")).isEqualTo("MASTER");
        assertThat(linha.get("canal_email")).isEqualTo(false);
        assertThat(linha.get("criado_por").toString()).isEqualTo(utilizadorA.getId().toString());

        criar(externo(utilizadorA, entidadeA), """
                {"tipo":"REGULARIZACAO_PROCEDIMENTO","titulo":"Regularização","texto":"Texto","descricaoIgt":"  "}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[*].path").value(contains("descricaoIgt")));
        assertThat(notificacaoRepository.count()).isEqualTo(1);
    }

    @Test
    void masterTecnicoEVisitanteNaoCriamOsTiposDaEntidadeENadaEGravado() throws Exception {
        for (PerfilInterno perfil : PerfilInterno.values()) {
            for (String tipo : List.of("PEDIDO_APOIO", "REGULARIZACAO_PROCEDIMENTO")) {
                criar(interno(master, perfil), """
                        {"tipo":"%s","titulo":"Ajuda","texto":"Texto","descricaoIgt":"PDM"}""".formatted(tipo))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"))
                        .andExpect(jsonPath("$.mensagem").value("Este tipo de notificação é criado pela entidade."));
            }
        }
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void pedidoDeApoioComPrazoOuComDestinatariosDa400ENadaEGravado() throws Exception {
        criar(externo(utilizadorA, entidadeA), """
                {"tipo":"PEDIDO_APOIO","titulo":"Ajuda","texto":"Texto","prazo":"2026-12-31T23:59:59.999Z"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PRAZO_NAO_APLICAVEL"))
                .andExpect(jsonPath("$.mensagem").value("Prazo não aplicável a este tipo de notificação."));
        criar(externo(utilizadorA, entidadeA), """
                {"tipo":"PEDIDO_APOIO","titulo":"Ajuda","texto":"Texto","destinatarios":[{"entidadeId":%d}]}"""
                .formatted(entidadeB.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_DESTINATARIOS_INVALIDOS"));
        assertThat(notificacaoRepository.count()).isZero();
    }

    @Test
    void oAutorReabreOPedidoDeApoioResolvido(CapturedOutput output) throws Exception {
        Notificacao pedido = pedidoDeApoioResolvido();

        reabrir(pedido, externo(utilizadorA, entidadeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id(pedido)))
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.resolvidoEm").doesNotExist());

        Map<String, Object> linha = jdbcTemplate.queryForMap(
                "SELECT estado, resolvido_em FROM notificacao WHERE id = ?", pedido.getId());
        assertThat(linha.get("estado")).isEqualTo("ATIVA");
        assertThat(linha.get("resolvido_em")).isNull();
        assertThat(output).contains("Pedido de apoio reaberto").doesNotContain("Título confidencial")
                .doesNotContain("Ana Teixeira");
        responder(pedido, interno(master, PerfilInterno.MASTER), "Segunda resposta").andExpect(status().isCreated());
    }

    @Test
    void oMasterRecebe403EOutroDaEntidade404AoReabrir() throws Exception {
        Notificacao pedido = pedidoDeApoioResolvido();

        reabrir(pedido, interno(master, PerfilInterno.MASTER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensagem").value("Só quem criou o pedido o pode reabrir."));
        reabrir(pedido, externo(outroDeA, entidadeA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        assertThat(notificacaoRepository.findById(pedido.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoNotificacao.RESOLVIDA);
    }

    @Test
    void reabrirUmPedidoAtivoDa409EstadoInvalido() throws Exception {
        reabrir(pedidoDeApoio(EstadoNotificacao.ATIVA), externo(utilizadorA, entidadeA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));
    }

    @Test
    void reabrirUmaRespostaObrigatoriaOuUmaRegularizacaoResolvidasDa409TipoNaoReabrivel() throws Exception {
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, utilizadorA, x -> {
            x.setUtilizador(outroDeA);
            x.resolver(Instant.now());
        });
        Notificacao regularizacao = gravar(TipoNotificacao.REGULARIZACAO_PROCEDIMENTO, utilizadorA, x -> {
            x.setPerfilDestino(PerfilInterno.MASTER);
            x.resolver(Instant.now());
        });

        for (Notificacao notificacao : List.of(resposta, regularizacao)) {
            reabrir(notificacao, externo(utilizadorA, entidadeA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_NAO_REABRIVEL"))
                    .andExpect(jsonPath("$.mensagem").value("Só um pedido de apoio pode ser reaberto."));
        }
    }

    @Test
    void reabrirSemTokenDa401() throws Exception {
        mockMvc.perform(patch(URL + "/" + pedidoDeApoioResolvido().getId() + "/reabrir"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oDetalheDoPedidoResolvidoDaOReabrirSoAoAutor() throws Exception {
        Notificacao pedido = pedidoDeApoioResolvido();

        mockMvc.perform(get(URL + "/" + pedido.getId()).with(externo(utilizadorA, entidadeA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(contains("REABRIR")));
        mockMvc.perform(get(URL + "/" + pedido.getId()).with(interno(master, PerfilInterno.MASTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acoesDisponiveis").value(empty()));
    }

    private Notificacao pedidoDeApoioResolvido() {
        return gravar(TipoNotificacao.PEDIDO_APOIO, utilizadorA, x -> {
            x.setPerfilDestino(PerfilInterno.MASTER);
            x.resolver(Instant.now());
        });
    }

    private ResultActions reabrir(Notificacao notificacao, RequestPostProcessor token) throws Exception {
        return mockMvc.perform(patch(URL + "/" + notificacao.getId() + "/reabrir").with(token));
    }

    private Notificacao pedidoDeApoio(EstadoNotificacao estado) {
        return gravar(TipoNotificacao.PEDIDO_APOIO, utilizadorA, x -> {
            x.setPerfilDestino(PerfilInterno.MASTER);
            x.setEstado(estado);
        });
    }

    private ResultActions responder(Notificacao notificacao, RequestPostProcessor token, String texto)
            throws Exception {
        return mockMvc.perform(post(URL + "/" + notificacao.getId() + "/mensagens").with(token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo(texto)));
    }

    private static String corpo(String texto) {
        return "{\"texto\":\"" + texto + "\"}";
    }

    private Notificacao gravar(TipoNotificacao tipo, Utilizador criadoPor, Consumer<Notificacao> destino) {
        Notificacao notificacao = new Notificacao(tipo, "Título confidencial", "Texto confidencial", criadoPor);
        destino.accept(notificacao);
        return notificacaoRepository.saveAndFlush(notificacao);
    }

    private void mudarCriadoEm(Notificacao notificacao, Instant criadoEm) {
        jdbcTemplate.update("UPDATE notificacao SET criado_em = ? WHERE id = ?", Timestamp.from(criadoEm),
                notificacao.getId());
    }

    private Processo criarProcesso(String numeroProcesso) {
        String codigo = "NOT" + SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/" + codigo, utilizadorA, utilizadorA));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração", utilizadorA));
        Processo processo = processoRepository.saveAndFlush(
                new Processo(dinamica, procedimentoConfig, utilizadorA, entidadeA));
        jdbcTemplate.update("UPDATE processo SET numero_processo = ? WHERE id = ?", numeroProcesso, processo.getId());
        return processo;
    }

    private Utilizador utilizador(String nome, String email, String origem) {
        return utilizadorRepository.saveAndFlush(new Utilizador(nome, email, origem));
    }

    private Utilizador interno(String nome, String email) {
        Utilizador utilizador = new Utilizador(nome, email);
        utilizador.setPerfilInterno(PerfilInterno.MASTER);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private ResultActions listarIds(RequestPostProcessor token) throws Exception {
        return mockMvc.perform(get(URL).with(token)).andExpect(status().isOk());
    }

    private static String semCamposVariaveis(String corpo) {
        return corpo.replaceAll("\"(timestamp|traceId)\":\"?[^,}\"]*\"?", "");
    }

    private static String nif(int n, int sufixo) {
        return String.format("52%06d%d", n, sufixo);
    }

    private static String id(Notificacao notificacao) {
        return notificacao.getId().toString();
    }

    private static String[] ids(Notificacao... notificacoes) {
        String[] ids = new String[notificacoes.length];
        for (int i = 0; i < notificacoes.length; i++) {
            ids[i] = id(notificacoes[i]);
        }
        return ids;
    }

    private static RequestPostProcessor externo(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("id", entidade.getId(), "tipo_entidade", "CM", "nipc", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor interno(Utilizador utilizador, PerfilInterno perfil) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil.name()));
    }
}
