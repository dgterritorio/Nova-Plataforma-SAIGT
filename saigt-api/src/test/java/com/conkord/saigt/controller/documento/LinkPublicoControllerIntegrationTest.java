package com.conkord.saigt.controller.documento;

import com.conkord.saigt.persistence.operacional.ConsumidorDescarga;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoDescarga;
import com.conkord.saigt.persistence.operacional.DocumentoDescargaRepository;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import com.conkord.saigt.service.documento.LinkPublicoService;
import com.conkord.saigt.service.documento.LinkPublicoService.LinkPublico;
import com.conkord.saigt.service.incm.EnvioIncmTesteBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Descarga anónima pelo link público, com a cadeia de segurança própria e o RustFS real. */
@SpringBootTest(properties = "app.publico.base-url=https://saigt.teste")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LinkPublicoControllerIntegrationTest extends EnvioIncmTesteBase {

    // Um IP por teste: o limite de pedidos por IP é partilhado pelo contexto.
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkPublicoService linkPublicoService;

    @Autowired
    private DocumentoDescargaRepository descargaRepository;

    @Test
    void deveDescarregarSemAutenticacaoComOsCabecalhosDeSegurancaEFicarAuditado() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        mockMvc.perform(pedido(get(caminho(link)))).andExpect(status().isOk())
                .andExpect(content().bytes(CONTEUDO))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Regulamento.pdf")));

        assertThat(descargas(link)).singleElement().satisfies(descarga -> {
            assertThat(descarga.getConsumidor()).isEqualTo(ConsumidorDescarga.INCM);
            assertThat(descarga.getProcessoDocumentoId()).isEqualTo(cenario.publicacao().getId());
            assertThat(descarga.getResultado()).isEqualTo(ResultadoDescarga.CONCLUIDA);
            assertThat(descarga.getBytesEnviados()).isEqualTo(CONTEUDO.length);
            assertThat(descarga.getUtilizadorId()).isNull();
        });
    }

    @Test
    void deveAuditarComoPcgtODescarregadoPeloLinkDaPcgt() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.PCGT);

        mockMvc.perform(pedido(get(caminho(link)))).andExpect(status().isOk());

        assertThat(descargas(link)).singleElement().extracting(DocumentoDescarga::getConsumidor)
                .isEqualTo(ConsumidorDescarga.PCGT);
    }

    @Test
    void deveResponderAoHeadSoComOsCabecalhos() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        MvcResult resultado = mockMvc.perform(pedido(head(caminho(link)))).andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, CONTEUDO.length))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(descargas(link)).isEmpty();
    }

    @Test
    void deveDevolverSoOIntervaloPedidoCom206() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        mockMvc.perform(pedido(get(caminho(link))).header(HttpHeaders.RANGE, "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/" + CONTEUDO.length))
                .andExpect(content().bytes(new byte[] {'%', 'P', 'D', 'F'}));
    }

    @Test
    void deveDarO404IgualParaTokenDesconhecidoLinkRevogadoENomeErrado() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);
        String token = token(link);

        String desconhecido = corpo404("/api/v1/publico/" + UUID.randomUUID() + "/Regulamento.pdf");
        String naoUuid = corpo404("/api/v1/publico/nao-e-um-token/Regulamento.pdf");
        String nomeErrado = corpo404("/api/v1/publico/" + token + "/Outro.pdf");
        transactionTemplate.executeWithoutResult(estado ->
                linkPublicoService.revogar(cenario.publicacao().getId(), MotivoRevogacaoLink.SUBSTITUICAO));
        String revogado = corpo404(caminho(link));

        assertThat(List.of(naoUuid, nomeErrado, revogado)).allSatisfy(corpo ->
                assertThat(semTraceId(corpo)).isEqualTo(semTraceId(desconhecido)));
        assertThat(descargas(link)).extracting(DocumentoDescarga::getResultado)
                .containsOnly(ResultadoDescarga.RECUSADA).hasSize(2);
    }

    @Test
    void deveRecusarCom429O61oPedidoNoMesmoMinutoDoMesmoIp() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);
        String ip = novoIp();

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(head(caminho(link)).with(pedido -> {
                pedido.setRemoteAddr(ip);
                return pedido;
            })).andExpect(status().isOk());
        }
        mockMvc.perform(head(caminho(link)).with(pedido -> {
            pedido.setRemoteAddr(ip);
            return pedido;
        })).andExpect(status().isTooManyRequests());
    }

    @Test
    void naoDeveAceitarOutrosMetodosNemCaminhosNaRotaPublica() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        int estado = mockMvc.perform(pedido(post(caminho(link)))).andReturn().getResponse().getStatus();
        assertThat(estado).isIn(401, 403);
        assertThat(descargas(link)).isEmpty();
    }

    @Test
    void naoDeveEscreverOTokenNoLog(CapturedOutput output) throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico link = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        mockMvc.perform(pedido(get(caminho(link)))).andExpect(status().isOk());

        assertThat(output.getAll()).doesNotContain(token(link)).contains("/api/v1/publico/***/Regulamento.pdf");
    }

    private MockHttpServletRequestBuilder pedido(MockHttpServletRequestBuilder pedido) {
        String ip = novoIp();
        return pedido.with(requisicao -> {
            requisicao.setRemoteAddr(ip);
            return requisicao;
        });
    }

    private static String novoIp() {
        int n = IP.getAndIncrement();
        return "10.0." + (n / 250) + "." + (n % 250 + 1);
    }

    private String corpo404(String caminho) throws Exception {
        return mockMvc.perform(pedido(get(caminho))).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();
    }

    private static String semTraceId(String corpo) {
        return corpo.replaceAll("\"traceId\"\\s*:\\s*\"[^\"]*\"", "").replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]*\"", "");
    }

    private static String caminho(LinkPublico link) {
        return URI.create(link.url()).getRawPath();
    }

    private static String token(LinkPublico link) {
        return caminho(link).split("/")[4];
    }

    private List<DocumentoDescarga> descargas(LinkPublico link) {
        return descargaRepository.findAll().stream()
                .filter(descarga -> link.linkId().equals(descarga.getDocumentoLinkPublicoId()))
                .toList();
    }
}
