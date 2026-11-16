package com.conkord.saigt.controller.notificacao;

import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumento;
import com.conkord.saigt.persistence.operacional.NotificacaoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.conkord.saigt.controller.documento.UploadMultipartReal.multipart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.auth-disabled=false", "app.notificacoes.anexos.tamanho-maximo=200B",
        "logging.level.com.conkord.saigt.service.notificacao=DEBUG"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NotificacaoAnexoIntegrationTest extends StorageTestcontainersBase {

    private static final String URL = "/api/v1/notificacoes/";
    private static final String PREFIXO_DESCARGA = "http://localhost:8080/api/v1/descargas/";
    private static final byte[] PDF = "%PDF-1.4 comprovativo de teste".getBytes(StandardCharsets.UTF_8);
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @SpyBean
    private ArmazenamentoDocumentos armazenamento;
    @Autowired
    private NotificacaoRepository notificacaoRepository;
    @Autowired
    private NotificacaoDocumentoRepository documentoRepository;
    @Autowired
    private ArmazenamentoIntencaoRepository intencaoRepository;
    @Autowired
    private DocumentoDescargaRepository descargaRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorRepository utilizadorRepository;

    private record Cenario(Entidade entidadeA, Entidade entidadeB, Utilizador utilizadorA, Utilizador utilizadorB,
                           Utilizador master) {
    }

    @Test
    void entidadeCarregaNaRespostaObrigatoriaEMasterDescarregaPorTicketComAuditoria(CapturedOutput saida)
            throws Exception {
        Cenario c = cenario();
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(),
                n -> n.setEntidade(c.entidadeA()));

        JsonNode criado = lerJson(anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "Ofício nº 3.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeFicheiro").value("Ofício nº 3.pdf"))
                .andExpect(jsonPath("$.tamanhoBytes").value(PDF.length))
                .andExpect(jsonPath("$.criadoEm").exists())
                .andExpect(jsonPath("$.caminhoFicheiro").doesNotExist()));
        UUID anexoId = UUID.fromString(criado.get("id").asText());

        NotificacaoDocumento anexo = documentoRepository.findById(anexoId).orElseThrow();
        String chave = "notificacoes/" + resposta.getId() + "/" + anexoId + "-Oficio_n_3.pdf";
        assertThat(anexo.getCaminhoFicheiro()).isEqualTo(chave);
        assertThat(armazenamento.versoes(chave)).isNotEmpty();
        List<ArmazenamentoIntencao> intencoes = intencaoRepository.findByChaveObjeto(chave);
        assertThat(intencoes).singleElement()
                .satisfies(i -> assertThat(i.getOperacao()).isEqualTo(OperacaoArmazenamento.GRAVACAO));

        mockMvc.perform(get(URL + resposta.getId()).with(externo(c.utilizadorA(), c.entidadeA())))
                .andExpect(jsonPath("$.documentos[0].id").value(anexoId.toString()))
                .andExpect(jsonPath("$.acoesDisponiveis").value(org.hamcrest.Matchers.hasItem("ANEXAR")));

        String ticket = pedirTicket(resposta, anexoId, interno(c.master()));
        var ficheiro = mockMvc.perform(get("/api/v1/descargas/" + ticket))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(ficheiro.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(ficheiro.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(ficheiro.getHeader("ETag")).isEqualTo("\"" + anexoId + "\"");

        List<DocumentoDescarga> linhas = descargaRepository.findAll().stream()
                .filter(linha -> anexoId.equals(linha.getNotificacaoDocumentoId())).toList();
        assertThat(linhas).singleElement().satisfies(linha -> {
            assertThat(linha.getResultado()).isEqualTo(ResultadoDescarga.CONCLUIDA);
            assertThat(linha.getUtilizadorId()).isEqualTo(c.master().getId());
            assertThat(linha.getProcessoId()).isNull();
            assertThat(linha.getProcessoDocumentoId()).isNull();
        });

        assertThat(saida.getOut()).contains("notificacaoDocumentoId=" + anexoId).doesNotContain(ticket)
                .doesNotContain("Ofício").doesNotContain("Oficio_n_3");
    }

    @Test
    void outraEntidadeRecebe404AoCarregarEAoPedirADescarga() throws Exception {
        Cenario c = cenario();
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(),
                n -> n.setEntidade(c.entidadeA()));
        UUID anexoId = UUID.fromString(lerJson(anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "a.pdf", PDF)
                .andExpect(status().isCreated())).get("id").asText());

        anexar(resposta, externo(c.utilizadorB(), c.entidadeB()), "b.pdf", PDF)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
        mockMvc.perform(post(URL + resposta.getId() + "/documentos/" + anexoId + "/descarga")
                        .with(externo(c.utilizadorB(), c.entidadeB())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
    }

    @Test
    void anexoDeOutraNotificacaoDa404() throws Exception {
        Cenario c = cenario();
        Notificacao primeira = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(), n -> n.setEntidade(c.entidadeA()));
        Notificacao segunda = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(), n -> n.setEntidade(c.entidadeA()));
        UUID anexoDaPrimeira = UUID.fromString(lerJson(anexar(primeira, interno(c.master()), "a.pdf", PDF)
                .andExpect(status().isCreated())).get("id").asText());

        mockMvc.perform(post(URL + segunda.getId() + "/documentos/" + anexoDaPrimeira + "/descarga")
                        .with(interno(c.master())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NOTIF_NAO_ENCONTRADA"));
    }

    @Test
    void ficheiroAcimaDoLimiteDa400SemLinhaNemObjeto() throws Exception {
        Cenario c = cenario();
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(), n -> n.setEntidade(c.entidadeA()));

        anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "grande.pdf", new byte[201])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_FICHEIRO_EXCEDE_LIMITE"))
                .andExpect(jsonPath("$.campos[0].path").value("ficheiro"));

        assertThat(documentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(resposta.getId())).isEmpty();
        List<ArmazenamentoIntencao> intencoes = intencoesDe(resposta);
        assertThat(intencoes).hasSize(1);
        assertThat(armazenamento.versoes(intencoes.get(0).getChaveObjeto())).isEmpty();
    }

    @Test
    void extensaoNaoAceiteEFicheiroVazioDao400() throws Exception {
        Cenario c = cenario();
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(), n -> n.setEntidade(c.entidadeA()));

        anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "programa.exe", PDF)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NOTIF_EXTENSAO_NAO_ACEITE"));
        assertThat(intencoesDe(resposta)).isEmpty();

        anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "vazio.pdf", new byte[0])
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        assertThat(documentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(resposta.getId())).isEmpty();
    }

    @Test
    void tiposEEstadosRecusados() throws Exception {
        Cenario c = cenario();
        Notificacao temporaria = gravar(TipoNotificacao.TEMPORARIA, null, n -> n.setUtilizador(c.utilizadorA()));
        anexar(temporaria, externo(c.utilizadorA(), c.entidadeA()), "a.pdf", PDF)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NOTIF_TIPO_SEM_CONVERSACAO"));

        Notificacao pedidoResolvido = gravar(TipoNotificacao.PEDIDO_APOIO, c.utilizadorA(), n -> {
            n.setPerfilDestino(PerfilInterno.MASTER);
            n.setEstado(EstadoNotificacao.RESOLVIDA);
        });
        anexar(pedidoResolvido, interno(c.master()), "a.pdf", PDF)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NOTIF_ESTADO_INVALIDO"));

        Notificacao pedidoAtivo = gravar(TipoNotificacao.PEDIDO_APOIO, c.utilizadorA(),
                n -> n.setPerfilDestino(PerfilInterno.MASTER));
        anexar(pedidoAtivo, interno(c.master()), "a.pdf", PDF).andExpect(status().isCreated());

        Notificacao noticia = gravar(TipoNotificacao.ESTATICA, c.master(), n -> { });
        anexar(noticia, interno(c.master()), "aviso.pdf", PDF).andExpect(status().isCreated());
        anexar(noticia, externo(c.utilizadorA(), c.entidadeA()), "aviso.pdf", PDF)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("NOTIF_PERFIL_NAO_AUTORIZADO"));
    }

    @Test
    void storageIndisponivelDaErroSemLinha() throws Exception {
        Cenario c = cenario();
        Notificacao resposta = gravar(TipoNotificacao.RESPOSTA_OBRIGATORIA, c.master(), n -> n.setEntidade(c.entidadeA()));
        doThrow(new StorageIndisponivelException("em baixo", null))
                .when(armazenamento).guardar(anyString(), any(InputStream.class), anyString());
        try {
            anexar(resposta, externo(c.utilizadorA(), c.entidadeA()), "a.pdf", PDF)
                    .andExpect(status().isInternalServerError());
        } finally {
            reset(armazenamento);
        }
        assertThat(documentoRepository.findByNotificacao_IdOrderByCriadoEmAsc(resposta.getId())).isEmpty();
    }

    private ResultActions anexar(Notificacao notificacao, RequestPostProcessor token, String nome, byte[] conteudo)
            throws Exception {
        return mockMvc.perform(multipart(URL + notificacao.getId() + "/documentos")
                .file(new MockMultipartFile("ficheiro", nome, "application/pdf", conteudo)).with(token));
    }

    private String pedirTicket(Notificacao notificacao, UUID anexoId, RequestPostProcessor token) throws Exception {
        String url = lerJson(mockMvc.perform(post(URL + notificacao.getId() + "/documentos/" + anexoId + "/descarga")
                .with(token)).andExpect(status().isOk())).get("url").asText();
        assertThat(url).startsWith(PREFIXO_DESCARGA);
        return url.substring(PREFIXO_DESCARGA.length());
    }

    private JsonNode lerJson(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private List<ArmazenamentoIntencao> intencoesDe(Notificacao notificacao) {
        return intencaoRepository.findAll().stream()
                .filter(i -> i.getChaveObjeto().startsWith("notificacoes/" + notificacao.getId() + "/")).toList();
    }

    private Cenario cenario() {
        int n = SEQUENCIA.incrementAndGet();
        Entidade a = entidadeRepository.saveAndFlush(EntidadeTeste.nova(String.format("53%06d1", n), "Câmara Anexos A" + n));
        Entidade b = entidadeRepository.saveAndFlush(EntidadeTeste.nova(String.format("53%06d2", n), "Câmara Anexos B" + n));
        Utilizador utilizadorA = utilizadorRepository.saveAndFlush(
                new Utilizador("Ana Anexos", "ana.anexos" + n + "@cm-a.pt", "INCM"));
        Utilizador utilizadorB = utilizadorRepository.saveAndFlush(
                new Utilizador("Bruno Anexos", "bruno.anexos" + n + "@cm-b.pt", "INCM"));
        Utilizador master = new Utilizador("Marta Anexos", "marta.anexos" + n + "@dgt.pt");
        master.setPerfilInterno(PerfilInterno.MASTER);
        return new Cenario(a, b, utilizadorA, utilizadorB, utilizadorRepository.saveAndFlush(master));
    }

    private Notificacao gravar(TipoNotificacao tipo, Utilizador criadoPor, Consumer<Notificacao> destino) {
        Notificacao notificacao = new Notificacao(tipo, "Título", "Texto", criadoPor);
        destino.accept(notificacao);
        return notificacaoRepository.saveAndFlush(notificacao);
    }

    private static RequestPostProcessor externo(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("id", entidade.getId(), "tipo_entidade", "CM", "nipc", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false)));
    }

    private static RequestPostProcessor interno(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", PerfilInterno.MASTER.name()));
    }
}
