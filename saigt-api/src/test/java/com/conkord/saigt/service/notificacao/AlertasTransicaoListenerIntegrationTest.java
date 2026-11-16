package com.conkord.saigt.service.notificacao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.dto.instrumento.VersaoProcessoRequest;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.ProcessoEnviadoPcmEvento;
import com.conkord.saigt.service.instrumento.ProcessoService;
import com.conkord.saigt.service.instrumento.ProcessoTransitouEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transições reais, pelos endpoints e com os tokens de teste: o listener `AFTER_COMMIT` corre depois do commit de cada
 * pedido. U e V são da entidade A, W da entidade B.
 */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class AlertasTransicaoListenerIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String TIPO_PROCEDIMENTO_COM_FORMULARIO = "CONSTITUICAO_CC";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationEvents eventos;
    @Autowired private ApplicationEventPublisher publicador;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ProcessoService processoService;
    @Autowired private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired private PermissaoAcaoRepository permissaoAcaoRepository;
    @Autowired private DocumentoConfigRepository documentoConfigRepository;
    @Autowired private EntidadeRepository entidadeRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Autowired private InstrumentoRepository instrumentoRepository;
    @Autowired private DinamicaRepository dinamicaRepository;
    @Autowired private TipoAtoRepository tipoAtoRepository;
    @Autowired private ProcessoRepository processoRepository;
    @Autowired private ProcessoDocumentoRepository processoDocumentoRepository;
    @Autowired private ProcessoInformacaoBaseRepository processoInformacaoBaseRepository;
    @Autowired private ListaValoresRepository listaValoresRepository;

    @SpyBean
    private AlertaProcessoService alertaProcessoService;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AlertasTransicaoListener.class);

    private String nif;
    private Entidade entidadeA;
    private Entidade entidadeB;
    private Utilizador autorU;
    private Utilizador colegaV;
    private Utilizador deOutraW;
    private Utilizador tecnico;
    private Processo processo;

    private record Linha(UUID utilizadorId, String titulo, String texto, UUID loteId, String tipo, String estado,
                         Long entidadeId, String perfilDestino, UUID criadoPor) {
    }

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        int n = SEQUENCIA.incrementAndGet();
        nif = "5213%05d".formatted(n);
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("TRANS" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("TRANS-D" + n, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig config = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoComFormulario(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config));
        int ordem = 1;
        for (ElementoInformacaoBase elemento : ElementoInformacaoBase.values()) {
            DocumentoConfig item = new DocumentoConfig(config, elemento.name() + "_PUB", elemento.name(), elemento.name(),
                    CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]",
                    ordem++);
            item.setElementoInformacaoBase(elemento);
            documentoConfigRepository.saveAndFlush(item);
        }

        entidadeA = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara A " + n));
        entidadeB = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5214%05d".formatted(n), "Câmara B " + n));
        autorU = utilizadorRepository.saveAndFlush(new Utilizador("Ulisses Fictício", "u" + n + "@cm-a.pt", "INCM"));
        colegaV = utilizadorRepository.saveAndFlush(new Utilizador("Vera Fictícia", "v" + n + "@cm-a.pt", "INCM"));
        deOutraW = utilizadorRepository.saveAndFlush(new Utilizador("Wanda Fictícia", "w" + n + "@cm-b.pt", "INCM"));
        vincular(autorU, entidadeA);
        vincular(colegaV, entidadeA);
        vincular(deOutraW, entidadeB);
        Utilizador tecnicoDgt = new Utilizador("Teresa Fictícia", "t" + n + "@dgt.pt", "AD_DGT");
        tecnicoDgt.setPerfilInterno(PerfilInterno.TECNICO);
        tecnico = utilizadorRepository.saveAndFlush(tecnicoDgt);

        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/TRANS/" + n, autorU, autorU));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(
                new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autorU));
        Processo novo = new Processo(dinamica, config, autorU, entidadeA);
        novo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        novo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        novo.setEntidadeEmitente("Câmara Municipal de Teste");
        processo = processoRepository.saveAndFlush(novo);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        SecurityContextHolder.clearContext();
        // Os logs do listener só levam ids, destino e contagens.
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("@")
                || m.contains("Fictíci") || m.contains("Duplicado") || m.contains("Constituição")
                || m.contains("Processo "));
    }

    private TipoProcedimento tipoProcedimentoComFormulario() {
        return tipoProcedimentoRepository.findAll().stream()
                .filter(tipo -> tipo.getCodigo().equals(TIPO_PROCEDIMENTO_COM_FORMULARIO)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento(TIPO_PROCEDIMENTO_COM_FORMULARIO, "Constituição da CC")));
    }

    private void vincular(Utilizador utilizador, Entidade entidade) {
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, entidade, Instant.now()));
    }

    private void noEstado(EstadoProcesso estado) {
        Processo atual = processoRepository.findById(processo.getId()).orElseThrow();
        atual.setEstado(estado);
        processo = processoRepository.saveAndFlush(atual);
    }

    private Processo atual() {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    private long versao() {
        return atual().getVersao();
    }

    private String corpoVersao() throws Exception {
        return objectMapper.writeValueAsString(new VersaoProcessoRequest(versao()));
    }

    private String numero() {
        return atual().getNumeroProcesso();
    }

    private RequestPostProcessor jwtDoAutor() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, autorU.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidadeA.getId(), "nif", nif,
                        "nome", "Entidade Teste", "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor externo(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("id", entidade.getId(), "tipo_entidade", "CM", "nipc", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor interno(Utilizador utilizador, String perfil) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfil));
    }

    private List<Linha> linhas() {
        return jdbcTemplate.query("SELECT utilizador_id, titulo, texto, lote_id, tipo, estado, entidade_id, "
                        + "perfil_destino, criado_por FROM notificacao WHERE processo_id = ? ORDER BY criado_em, id",
                (rs, i) -> new Linha(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getObject(7, Long.class),
                        rs.getString(8), rs.getObject(9, UUID.class)),
                processo.getId());
    }

    private List<Linha> linhasComTitulo(String titulo) {
        return linhas().stream().filter(linha -> linha.titulo().equals(titulo)).toList();
    }

    private List<NotificacaoCriadaEvento> eventosDeNotificacao() {
        return eventos.stream(NotificacaoCriadaEvento.class).toList();
    }

    private List<ProcessoTransitouEvento> transicoes() {
        return eventos.stream(ProcessoTransitouEvento.class).toList();
    }

    private void transitar(String segmento, RequestPostProcessor ator, String corpo, String estadoEsperado)
            throws Exception {
        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/" + segmento).with(ator)
                        .contentType("application/json").content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estadoEsperado));
    }

    private void soOAutorComEstado(String nomeEstado) {
        assertThat(linhas()).singleElement().satisfies(linha -> {
            assertThat(linha.utilizadorId()).isEqualTo(autorU.getId());
            assertThat(linha.titulo()).isEqualTo("Alteração do estado do processo");
            assertThat(linha.texto()).isEqualTo("O processo " + numero() + " (Constituição da CC) passou ao estado "
                    + nomeEstado + ".");
            assertThat(linha.loteId()).isNull();
            assertThat(linha.tipo()).isEqualTo("TEMPORARIA");
            assertThat(linha.estado()).isEqualTo("ATIVA");
        });
        assertThat(eventosDeNotificacao()).singleElement()
                .satisfies(evento -> assertThat(evento.emailContactoTramitacao()).isFalse());
    }

    @Test
    void submissaoAvisaUeVNoMesmoLoteComEmailAoContactoDeTramitacaoEWNaoAVe() throws Exception {
        noEstado(EstadoProcesso.EM_SUBMISSAO);
        SubmeterProcessoRequest pedido = PedidosSubmissao.confirmado(versao(), "Resumo do ato.", LocalDate.now(),
                "Norma Legal X", "TEXTO_INTEGRAL", null, null);

        transitar("submeter", jwtDoAutor(), objectMapper.writeValueAsString(pedido), "ENVIADO_DGT");

        List<Linha> linhas = linhasComTitulo("Processo submetido");
        assertThat(linhas).extracting(Linha::utilizadorId).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(linhas).extracting(Linha::texto)
                .containsOnly("O processo " + numero() + " (Constituição da CC) foi submetido.");
        assertThat(linhas).extracting(Linha::loteId).doesNotContainNull().containsOnly(linhas.get(0).loteId());
        assertThat(linhas).allSatisfy(linha -> {
            assertThat(linha.tipo()).isEqualTo("TEMPORARIA");
            assertThat(linha.criadoPor()).isNull();
        });
        assertThat(transicoes()).containsExactly(ProcessoTransitouEvento.de(processo.getId(),
                EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT));
        assertThat(eventosDeNotificacao()).singleElement().satisfies(evento -> {
            assertThat(evento.emailContactoTramitacao()).isTrue();
            assertThat(evento.loteId()).isEqualTo(linhas.get(0).loteId());
        });

        String notificacoes = "/api/v1/notificacoes";
        mockMvc.perform(get(notificacoes).with(externo(autorU, entidadeA))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo").value(hasItem("Processo submetido")));
        mockMvc.perform(get(notificacoes).with(externo(colegaV, entidadeA))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo").value(hasItem("Processo submetido")));
        mockMvc.perform(get(notificacoes).with(externo(deOutraW, entidadeB))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].titulo").value(not(hasItem("Processo submetido"))));
    }

    @Test
    void capturarEValidarAvisamSoOAutorSemEmailAoContactoDeTramitacao() throws Exception {
        documentoConfigRepository.findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(
                processo.getInstrumentoProcedimentoConfig()).forEach(item -> {
                    item.setAtivo(false);
                    documentoConfigRepository.saveAndFlush(item);
                });
        noEstado(EstadoProcesso.ENVIADO_DGT);

        transitar("capturar", interno(tecnico, "TECNICO"), corpoVersao(), "EM_VALIDACAO_DGT");

        soOAutorComEstado("Em Validação DGT");

        transitar("validar", interno(tecnico, "TECNICO"), corpoVersao(), "VALIDADO_DGT");

        assertThat(linhasComTitulo("Alteração do estado do processo")).extracting(Linha::texto).contains(
                "O processo " + numero() + " (Constituição da CC) passou ao estado Validado DGT.");
        assertThat(linhas()).extracting(Linha::utilizadorId).containsOnly(autorU.getId());
        assertThat(eventosDeNotificacao()).hasSize(2).noneMatch(NotificacaoCriadaEvento::emailContactoTramitacao);
    }

    @Test
    void rejeicaoDgtLevaOsDoisItensEOMotivoAoAutorEAEntidade() throws Exception {
        noEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        int n = SEQUENCIA.incrementAndGet();
        ListaValores categoria = listaValoresRepository.saveAndFlush(
                new ListaValores("CATEGORIA_REJEICAO_DGT", "TC" + n, "Forma", 1));
        ListaValores motivo = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_REJEICAO_DGT", "TM" + n, "Ficheiro ilegível", 1));
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, null,
                CategoriaDocumento.DEPOSITO, "pdf", "Regulamento.pdf", "privado/" + processo.getId() + "/r.pdf",
                "a".repeat(64), "v1", 10L, autorU));
        ProcessoInformacaoBase deliberacao = processoInformacaoBaseRepository.saveAndFlush(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        ProcessoInformacaoBase textoAto = processoInformacaoBaseRepository.saveAndFlush(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.TEXTO_ATO));
        mockMvc.perform(put("/api/v1/processos/" + processo.getId() + "/validacao/itens")
                        .with(interno(tecnico, "TECNICO")).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("versao", versao(), "itens", List.of(
                                rejeitado("DOCUMENTO", documento.getId(), categoria, motivo),
                                rejeitado("INFORMACAO_BASE", deliberacao.getId(), categoria, motivo),
                                Map.of("tipo", "INFORMACAO_BASE", "itemId", textoAto.getId().toString(),
                                        "decisao", EstadoValidacao.VALIDADO.name()))))))
                .andExpect(status().isOk());
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("versao", versao());
        corpo.put("notasGerais", "Notas gerais.");

        transitar("rejeitar", interno(tecnico, "TECNICO"), objectMapper.writeValueAsString(corpo), "REJEITADO_DGT");

        List<Linha> linhas = linhasComTitulo("Processo rejeitado pela DGT");
        assertThat(linhas).extracting(Linha::utilizadorId).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(linhas).extracting(Linha::texto).containsOnly("O processo " + numero() + " (Constituição da CC) "
                + "foi rejeitado pela DGT nos itens Regulamento.pdf, Informação Base da Deliberação com o motivo "
                + "Ficheiro ilegível.");
        assertThat(transicoes()).singleElement().satisfies(evento -> {
            assertThat(evento.destino()).isEqualTo(EstadoProcesso.REJEITADO_DGT);
            assertThat(evento.rejeicaoId()).isNotNull();
        });
        assertThat(eventosDeNotificacao()).singleElement()
                .satisfies(evento -> assertThat(evento.emailContactoTramitacao()).isTrue());
    }

    private static Map<String, Object> rejeitado(String tipo, UUID itemId, ListaValores categoria,
                                                 ListaValores motivo) {
        return Map.of("tipo", tipo, "itemId", itemId.toString(), "decisao", EstadoValidacao.REJEITADO.name(),
                "categoriaId", categoria.getId(), "motivoId", motivo.getId(), "notas", "Nota do item.");
    }

    @Test
    void anulacaoDgtLevaOMotivoAoAutorEAEntidade() throws Exception {
        noEstado(EstadoProcesso.VALIDADO_DGT);
        Utilizador master = new Utilizador("Mário Fictício", "m" + SEQUENCIA.incrementAndGet() + "@dgt.pt", "AD_DGT");
        master.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.saveAndFlush(master);

        transitar("anular", interno(master, "MASTER"),
                objectMapper.writeValueAsString(new AnularProcessoRequest(versao(), "Duplicado")), "ANULADO");

        List<Linha> linhas = linhasComTitulo("Processo anulado pela DGT");
        assertThat(linhas).extracting(Linha::utilizadorId).containsExactlyInAnyOrder(autorU.getId(), colegaV.getId());
        assertThat(linhas).extracting(Linha::texto).containsOnly(
                "O processo " + numero() + " (Constituição da CC) foi anulado pela DGT com o motivo Duplicado.");
        assertThat(transicoes()).containsExactly(new ProcessoTransitouEvento(processo.getId(),
                EstadoProcesso.VALIDADO_DGT, EstadoProcesso.ANULADO, null, "Duplicado", List.of()));
    }

    @Test
    void resgatarAvisaOAutorComEmSubmissao() throws Exception {
        noEstado(EstadoProcesso.ENVIADO_DGT);

        transitar("resgatar", jwtDoAutor(), corpoVersao(), "EM_SUBMISSAO");

        soOAutorComEstado("Em Submissão");
    }

    @Test
    void corrigirAvisaOAutorComEmSubmissao() throws Exception {
        noEstado(EstadoProcesso.REJEITADO_DGT);

        transitar("corrigir", jwtDoAutor(), corpoVersao(), "EM_SUBMISSAO");

        soOAutorComEstado("Em Submissão");
    }

    @Test
    void enviarPcmAvisaOUtilizadorPcmNumaLinhaSemLote() throws Exception {
        InstrumentoProcedimentoConfig primeiraSerie = processo.getInstrumentoProcedimentoConfig();
        primeiraSerie.setSerieDr("PRIMEIRA");
        procedimentoConfigRepository.saveAndFlush(primeiraSerie);
        noEstado(EstadoProcesso.VALIDADO_DGT);
        int n = SEQUENCIA.incrementAndGet();
        Entidade pcm = entidadeRepository.saveAndFlush(EntidadeTeste.nova("6213%05d".formatted(n), "PCM " + n,
                TipoEntidade.PCM));
        Utilizador utilizadorPcm = utilizadorRepository.saveAndFlush(
                new Utilizador("Paula Fictícia", "p" + n + "@pcm.gov.pt", "INCM"));
        vincular(utilizadorPcm, pcm);

        mockMvc.perform(patch("/api/v1/processos/" + processo.getId() + "/enviar-pcm").with(interno(tecnico, "TECNICO"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EnviarPcmRequest(versao(), utilizadorPcm.getId()))))
                .andExpect(status().isOk());

        assertThat(linhas()).singleElement().satisfies(linha -> {
            assertThat(linha.utilizadorId()).isEqualTo(utilizadorPcm.getId());
            assertThat(linha.titulo()).isEqualTo("Processo para aprovação da PCM");
            assertThat(linha.texto()).isEqualTo("O processo " + numero() + " (Constituição da CC) aguarda aprovação da PCM.");
            assertThat(linha.loteId()).isNull();
            assertThat(linha.entidadeId()).isNull();
            assertThat(linha.perfilDestino()).isNull();
        });
        assertThat(eventos.stream(ProcessoEnviadoPcmEvento.class))
                .containsExactly(new ProcessoEnviadoPcmEvento(processo.getId(), utilizadorPcm.getId()));
        assertThat(eventosDeNotificacao()).singleElement()
                .satisfies(evento -> assertThat(evento.emailContactoTramitacao()).isFalse());
    }

    @Test
    void transicaoQueReverteNaoCriaNotificacao() {
        noEstado(EstadoProcesso.ENVIADO_DGT);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("token")
                .header("alg", "RS256").claim(JwtClaimNames.SUB, tecnico.getId().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "TECNICO").build()));

        transactionTemplate.executeWithoutResult(estado -> {
            processoService.capturar(processo.getId(), new VersaoProcessoRequest(versao()));
            estado.setRollbackOnly();
        });

        assertThat(transicoes()).hasSize(1);
        assertThat(atual().getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(linhas()).isEmpty();
        assertThat(eventosDeNotificacao()).isEmpty();
    }

    @Test
    void estadoMudadoEntreOCommitEOListenerNaoCriaNadaEDeixaInfo() {
        noEstado(EstadoProcesso.EM_SUBMISSAO);

        transactionTemplate.executeWithoutResult(estado -> publicador.publishEvent(ProcessoTransitouEvento.de(
                processo.getId(), EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT)));

        assertThat(linhas()).isEmpty();
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.INFO).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains("estado mudou entretanto", "processoId=" + processo.getId()));
    }

    @Test
    void excecaoNoListenerDeixaErrorENaoReverteATransicao() throws Exception {
        noEstado(EstadoProcesso.ENVIADO_DGT);
        doThrow(new IllegalStateException("falha simulada")).when(alertaProcessoService)
                .criarParaUtilizador(any(), any(), any(), any());

        transitar("capturar", interno(tecnico, "TECNICO"), corpoVersao(), "EM_VALIDACAO_DGT");

        assertThat(atual().getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_DGT);
        assertThat(linhas()).isEmpty();
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.ERROR).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains("processoId=" + processo.getId(), "destino=EM_VALIDACAO_DGT"));
        assertThat(Objects.requireNonNull(logs.list.get(logs.list.size() - 1).getThrowableProxy()).getMessage())
                .isEqualTo("falha simulada");
    }
}
