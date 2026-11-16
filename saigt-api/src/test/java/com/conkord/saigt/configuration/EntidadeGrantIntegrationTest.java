package com.conkord.saigt.configuration;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Grant `entidade` (passo 2 do login INCM) por HTTP. */
class EntidadeGrantIntegrationTest extends GrantsIncmIntegrationBase {

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entidadeDoToken(JWTClaimsSet claims) {
        return (Map<String, Object>) claims.getClaim("entidade");
    }

    @Test
    void deveEmitirAccessTokenERefreshTokenParaACamaraMunicipalEscolhida() throws Exception {
        String email = emailUnico();
        Entidade sintra = entidade("500051062", "Câmara Municipal de Sintra", TipoEntidade.CM, "1111", "5245");
        incmAceita(email, "Helena Fictícia", adesao("ENR-100", "500051062", "Câmara Municipal de Sintra"));
        String preAuth = preAuthToken(email);

        HttpResponse<String> resposta = segundoPasso(preAuth, "ENR-100");

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode corpo = json(resposta);
        assertThat(corpo.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expires_in").asLong()).isEqualTo(900L);
        assertThat(corpo.get("refresh_token").asText()).isNotBlank();
        JWTClaimsSet claims = SignedJWT.parse(corpo.get("access_token").asText()).getJWTClaimsSet();
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(claims.getAudience()).containsExactly("saigt-api");
        assertThat(claims.getStringClaim("origem")).isEqualTo("INCM");
        Map<String, Object> entidade = entidadeDoToken(claims);
        assertThat(entidade)
                .containsEntry("id", sintra.getId())
                .containsEntry("codigo", "5245")
                .containsEntry("tipo_entidade", "CM")
                .containsEntry("dico", "1111")
                .containsEntry("nipc", "500051062")
                .containsEntry("incm_enrollment_id", "ENR-100")
                .containsEntry("pcm", false)
                .doesNotContainKeys("responsavel", "nif");

        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        List<Auditoria> login = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "UTILIZADOR", utilizador.getId().toString(), "LOGIN");
        assertThat(login).hasSize(1);
        assertThat(login.get(0).getUtilizadorId()).isEqualTo(utilizador.getId());
        assertThat(login.get(0).getEntidadeId()).isEqualTo(sintra.getId());
    }

    @Test
    void oIndicadorPcmSegueOTipoDaEntidadeEscolhidaEMudaDeLoginParaLogin() throws Exception {
        String email = emailUnico();
        entidade("502000131", "PCM Fictícia", TipoEntidade.PCM, null, "T-PCM-131");
        entidade("502000132", "Câmara Fictícia", TipoEntidade.CM, "1112", "T-PCM-132");
        incmAceita(email, "Rita Fictícia", adesao("ENR-131", "502000131", "PCM Fictícia"),
                adesao("ENR-132", "502000132", "Câmara Fictícia"));

        JWTClaimsSet comoPcm = SignedJWT.parse(json(segundoPasso(preAuthToken(email), "ENR-131")).get("access_token").asText())
                .getJWTClaimsSet();
        JWTClaimsSet comoCamara = SignedJWT.parse(json(segundoPasso(preAuthToken(email), "ENR-132")).get("access_token").asText())
                .getJWTClaimsSet();

        assertThat(entidadeDoToken(comoPcm)).containsEntry("pcm", true).containsEntry("tipo_entidade", "PCM");
        assertThat(entidadeDoToken(comoCamara)).containsEntry("pcm", false).containsEntry("tipo_entidade", "CM");
    }

    @Test
    void naoDeveLevarDicoQuandoAEntidadeNaoECm() throws Exception {
        String email = emailUnico();
        entidade("502000111", "CIM Fictícia", TipoEntidade.CIM, null, "T-E-111");
        incmAceita(email, "Rui Fictício", adesao("ENR-111", "502000111", "CIM Fictícia"));

        JWTClaimsSet claims = SignedJWT.parse(json(segundoPasso(preAuthToken(email), "ENR-111")).get("access_token").asText())
                .getJWTClaimsSet();

        assertThat(entidadeDoToken(claims)).containsEntry("tipo_entidade", "CIM").doesNotContainKey("dico");
    }

    @Test
    void aSessaoAtivaAnteriorSoFicaRevogadaQuandoAEscolhaConclui() throws Exception {
        String email = emailUnico();
        entidade("502000121", "Entidade Sessão", TipoEntidade.CM, null, "T-E-121");
        incmAceita(email, "Ana Fictícia", adesao("ENR-121", "502000121", "Entidade Sessão"));
        Utilizador utilizador = contaIncm(email, "Ana Fictícia");
        sessaoRepository.saveAndFlush(new Sessao("jti-anterior-" + utilizador.getId(), utilizador, Instant.now().plusSeconds(3600)));
        String jtiAnterior = "jti-anterior-" + utilizador.getId();

        String preAuth = preAuthToken(email);

        assertThat(sessaoRepository.findById(jtiAnterior).orElseThrow().getRevogadoEm()).isNull();

        assertThat(segundoPasso(preAuth, "ENR-121").statusCode()).isEqualTo(200);

        assertThat(sessaoRepository.findById(jtiAnterior).orElseThrow().getRevogadoEm()).isNotNull();
        String jtiPreAuth = SignedJWT.parse(preAuth).getJWTClaimsSet().getJWTID();
        assertThat(sessaoRepository.findById(jtiPreAuth).orElseThrow().getRevogadoEm()).isNotNull();
    }

    @Test
    void deveRecusarUmaAdesaoForaDoContextoSemConsumirAPreAutenticacao() throws Exception {
        String email = emailUnico();
        entidade("502000131", "Entidade Contexto", TipoEntidade.CM, null, "T-E-131");
        incmAceita(email, "Ana Fictícia", adesao("ENR-131", "502000131", "Entidade Contexto"));
        String preAuth = preAuthToken(email);

        HttpResponse<String> recusada = segundoPasso(preAuth, "ENR-FORA-DO-CONTEXTO");

        assertThat(recusada.statusCode()).isEqualTo(400);
        assertThat(json(recusada).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(recusada).get("error_description").asText()).isEqualTo("Entidade não disponível para este utilizador.");
        assertThat(segundoPasso(preAuth, "ENR-131").statusCode()).isEqualTo(200);
    }

    @Test
    void deveRecusarAAdesaoSemCorrespondenciaAuditandoEDeixandoEscolherOutra() throws Exception {
        String email = emailUnico();
        entidade("502000141", "Entidade Com Correspondência", TipoEntidade.CM, null, "T-E-141");
        incmAceita(email, "Rui Fictício", adesao("ENR-141", "502000141", "Entidade Com Correspondência"),
                adesao("ENR-142", "502000142", "Entidade Sem Correspondência"));
        String preAuth = preAuthToken(email);

        HttpResponse<String> recusada = segundoPasso(preAuth, "ENR-142");

        assertThat(recusada.statusCode()).isEqualTo(400);
        assertThat(json(recusada).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(recusada).get("error_description").asText())
                .isEqualTo("A entidade não está criada na SAIGT. Foi enviado um pedido de apoio à DGT.");
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "UTILIZADOR", utilizador.getId().toString(), "LOGIN_RECUSADO"))
                .hasSize(1).allSatisfy(registo -> assertThat(registo.getUtilizadorId()).isEqualTo(utilizador.getId()));
        assertThat(entidadeRepository.findAllByNipc("502000142")).isEmpty();

        assertThat(segundoPasso(preAuth, "ENR-141").statusCode()).isEqualTo(200);
    }

    @Test
    void aRecusaSemCorrespondenciaDeixaUmPedidoDeApoioAosMasterSemDuplicarEQueOUtilizadorNaoVe() throws Exception {
        String email = emailUnico();
        entidade("502000181", "Entidade Pedido Apoio", TipoEntidade.CM, null, "T-E-181");
        incmAceita(email, "Rita Fictícia", adesao("ENR-181", "502000181", "Entidade Pedido Apoio"),
                adesao("ENR-182", "502000182", "Entidade Sem Correspondência 182"));
        String preAuth = preAuthToken(email);

        HttpResponse<String> recusada = segundoPasso(preAuth, "ENR-182");
        HttpResponse<String> repetida = segundoPasso(preAuth, "ENR-182");

        for (HttpResponse<String> resposta : List.of(recusada, repetida)) {
            assertThat(resposta.statusCode()).isEqualTo(400);
            assertThat(json(resposta).get("error").asText()).isEqualTo("invalid_grant");
            assertThat(json(resposta).get("error_description").asText())
                    .isEqualTo("A entidade não está criada na SAIGT. Foi enviado um pedido de apoio à DGT.");
        }
        List<Notificacao> pedidos = notificacaoRepository.findAll().stream()
                .filter(notificacao -> notificacao.getTexto().contains(email)).toList();
        assertThat(pedidos).singleElement().satisfies(pedido -> {
            assertThat(pedido.getTipo()).isEqualTo(TipoNotificacao.PEDIDO_APOIO);
            assertThat(pedido.getEstado()).isEqualTo(EstadoNotificacao.ATIVA);
            assertThat(pedido.getPerfilDestino()).isEqualTo(PerfilInterno.MASTER);
            assertThat(pedido.getCriadoPor()).isNull();
            assertThat(pedido.getTitulo()).isEqualTo("Login recusado: entidade sem correspondência na SAIGT");
            assertThat(pedido.getTexto()).contains("Rita Fictícia", "«Entidade Sem Correspondência 182»",
                    "(NIPC 502000182)");
        });

        String acesso = json(segundoPasso(preAuth, "ENR-181")).get("access_token").asText();
        HttpResponse<String> lista = http.get("/api/v1/notificacoes", acesso);
        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).doesNotContain(pedidos.get(0).getId().toString());
    }

    @Test
    void deveGuardarNaSessaoAtivaSoOJtiDoRefreshTokenEUmContextoSemResponsavel() throws Exception {
        String email = emailUnico();
        Entidade sintra = entidade("502000151", "Câmara Municipal Fictícia", TipoEntidade.CM, "4321", "T-E-151");
        incmAceita(email, "Helena Fictícia", adesao("ENR-151", "502000151", "Câmara Municipal Fictícia"));

        JsonNode corpo = json(segundoPasso(preAuthToken(email), "ENR-151"));

        String refresh = corpo.get("refresh_token").asText();
        JWTClaimsSet claimsRefresh = SignedJWT.parse(refresh).getJWTClaimsSet();
        assertThat(claimsRefresh.getAudience()).containsExactly("saigt-api-refresh");
        assertThat(claimsRefresh.getJWTID()).isNotBlank();
        Sessao ativa = sessaoRepository.findById(claimsRefresh.getJWTID()).orElseThrow();
        assertThat(ativa.getTipo()).isEqualTo("ATIVA");
        assertThat(ativa.getJti()).isNotEqualTo(refresh);
        assertThat(sessaoRepository.findAll()).noneMatch(sessao -> refresh.equals(sessao.getJti()));
        assertThat(ativa.getExpiraEm()).isBetween(Instant.now().plusSeconds(8 * 3600 - 60), Instant.now().plusSeconds(8 * 3600 + 60));
        assertThat(ativa.getContextoSessao())
                .contains("\"id\":" + sintra.getId()).contains("\"codigo\":\"T-E-151\"")
                .contains("\"tipoEntidade\":\"CM\"").contains("\"dico\":\"4321\"")
                .doesNotContain("responsavel").doesNotContain("Responsável");
        assertThat(http.get("/api/v1/processos", refresh).statusCode()).isEqualTo(401);
    }

    @Test
    void aPreAutenticacaoSoServeUmaVezEOsTokensErradosSaoRecusados() throws Exception {
        String email = emailUnico();
        entidade("502000161", "Entidade Uma Vez", TipoEntidade.CM, null, "T-E-161");
        incmAceita(email, "Ana Fictícia", adesao("ENR-161", "502000161", "Entidade Uma Vez"));
        String preAuth = preAuthToken(email);
        JsonNode concluido = json(segundoPasso(preAuth, "ENR-161"));

        HttpResponse<String> reutilizada = segundoPasso(preAuth, "ENR-161");
        HttpResponse<String> comTokenDeAcesso = segundoPasso(concluido.get("access_token").asText(), "ENR-161");
        HttpResponse<String> comRefresh = segundoPasso(concluido.get("refresh_token").asText(), "ENR-161");
        HttpResponse<String> lixo = segundoPasso("isto-nao-e-um-token", "ENR-161");

        for (HttpResponse<String> resposta : List.of(reutilizada, comTokenDeAcesso, comRefresh, lixo)) {
            assertThat(resposta.statusCode()).isEqualTo(400);
            assertThat(json(resposta).get("error").asText()).isEqualTo("invalid_grant");
        }
    }

    @Test
    void oTokenDeAcessoDoEndpointDeTokensEAceiteNosEndpointsDeNegocio() throws Exception {
        String email = emailUnico();
        entidade("502000171", "Entidade Token Aceite", TipoEntidade.CM, null, "T-E-171");
        incmAceita(email, "Ana Fictícia", adesao("ENR-171", "502000171", "Entidade Token Aceite"));

        String acesso = json(segundoPasso(preAuthToken(email), "ENR-171")).get("access_token").asText();

        assertThat(http.get("/api/v1/processos", acesso).statusCode()).isEqualTo(200);
        assertThat(http.get("/api/v1/processos", null).statusCode()).isEqualTo(401);
    }

    @Test
    void deveDevolver400InvalidRequestQuandoFaltamCampos() throws Exception {
        HttpResponse<String> resposta = http.pedirToken("", null, null,
                form(Map.of("grant_type", GRANT_ENTIDADE, "client_id", "saigt-ui", "pre_auth_token", "x")));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(json(resposta).get("error").asText()).isEqualTo("invalid_request");
    }
}
