package com.conkord.saigt.configuration;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.incm.ResultadoAutenticacaoIncm;
import com.conkord.saigt.integracao.incm.UtilizadorIncm;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Grant `incm-password` (passo 1 do login INCM) por HTTP. */
@ExtendWith(OutputCaptureExtension.class)
class IncmPasswordGrantIntegrationTest extends GrantsIncmIntegrationBase {

    @Autowired
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;

    private long ultimaAuditoria() {
        return auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc().stream()
                .mapToLong(Auditoria::getId).max().orElse(0L);
    }

    @Test
    void deveDevolverOTokenDePreAutenticacaoComAsEntidadesParaUmaAdesao() throws Exception {
        String email = emailUnico();
        incmAceita(email, "Helena Fictícia", adesao("ENR-201", "500051062", "Câmara Municipal Fictícia"));

        HttpResponse<String> resposta = primeiroPasso(email, PASSWORD_FICTICIA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode corpo = json(resposta);
        assertThat(corpo.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(corpo.get("expires_in").asLong()).isEqualTo(300L);
        assertThat(corpo.has("refresh_token")).isFalse();
        assertThat(corpo.get("entidades")).hasSize(1);
        JsonNode entidade = corpo.get("entidades").get(0);
        assertThat(entidade.get("incmEnrollmentId").asText()).isEqualTo("ENR-201");
        assertThat(entidade.get("nipc").asText()).isEqualTo("500051062");
        assertThat(entidade.get("nome").asText()).isEqualTo("Câmara Municipal Fictícia");

        SignedJWT jwt = SignedJWT.parse(corpo.get("access_token").asText());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(claims.getAudience()).containsExactly("saigt-api-pre-auth");
        assertThat(claims.getJWTID()).isNotBlank();

        Sessao linha = sessaoRepository.findById(claims.getJWTID()).orElseThrow();
        assertThat(linha.getTipo()).isEqualTo("PRE_AUTENTICACAO");
        assertThat(linha.getRevogadoEm()).isNull();
        assertThat(linha.getContextoSessao()).contains("ENR-201").doesNotContain("Responsável").doesNotContain("responsavel");
    }

    @Test
    void deveDevolverTodasAsAdesoesEOsVinculosSoDasQueTemEntidadeSaigt() throws Exception {
        String email = emailUnico();
        Entidade primeira = entidade("501000101", "Entidade Cem e Um", TipoEntidade.CM, null, "T-D-101");
        Entidade segunda = entidade("501000102", "Entidade Cem e Dois", TipoEntidade.CIM, null, "T-D-102");
        incmAceita(email, "Ricardo Fictício", adesao("ENR-101", "501000101", "Entidade Cem e Um"),
                adesao("ENR-102", "501000102", "Entidade Cem e Dois"),
                adesao("ENR-103", "501000103", "Entidade Sem Correspondência"));

        HttpResponse<String> resposta = primeiroPasso(email, PASSWORD_FICTICIA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode entidades = json(resposta).get("entidades");
        assertThat(entidades).extracting(e -> e.get("incmEnrollmentId").asText())
                .containsExactly("ENR-101", "ENR-102", "ENR-103");
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        assertThat(utilizadorEntidadeRepository.findByIdUtilizadorId(utilizador.getId()))
                .extracting(vinculo -> vinculo.getId().getEntidadeId())
                .containsExactlyInAnyOrder(primeira.getId(), segunda.getId());
        assertThat(utilizador.getOrigemAutenticacao()).isEqualTo("INCM");
        assertThat(utilizador.getNome()).isEqualTo("Ricardo Fictício");
    }

    @Test
    void deveAtualizarOContactoEOResponsavelDaEntidadeSemMudarNipcNemDico() throws Exception {
        String email = emailUnico();
        Entidade entidade = entidade("501000201", "Entidade Contactos", TipoEntidade.CM, "1234", "T-D-201");
        incmAceita(email, "Ana Fictícia", adesao("ENR-301", "501000201", "Entidade Contactos"));

        assertThat(primeiroPasso(email, PASSWORD_FICTICIA).statusCode()).isEqualTo(200);

        Entidade depois = entidadeRepository.findById(entidade.getId()).orElseThrow();
        assertThat(depois.getTelefone()).isEqualTo("210000000");
        assertThat(depois.getResponsavelNome()).isEqualTo("Responsável Fictício");
        assertThat(depois.getNipc()).isEqualTo("501000201");
        assertThat(depois.getDico()).isEqualTo("1234");
        assertThat(depois.getDescricaoIncm()).isEqualTo("Entidade Contactos");
    }

    @Test
    void oTokenDePreAutenticacaoNaoServeNosEndpointsDeNegocio() throws Exception {
        String email = emailUnico();
        incmAceita(email, "Helena Fictícia", adesao("ENR-401", "500051062", "Câmara Municipal Fictícia"));
        String token = preAuthToken(email);

        assertThat(http.get("/api/v1/processos", token).statusCode()).isEqualTo(401);
    }

    @Test
    void deveRecusarCredenciaisErradasAuditandoSoQuemTemContaIncm() throws Exception {
        String conhecido = emailUnico();
        Utilizador conta = contaIncm(conhecido, "Conta Conhecida");
        String desconhecido = emailUnico();
        String motivo = "User email address and/or password not valid";
        when(incm.autenticarComMotivo(any(), any())).thenReturn(ResultadoAutenticacaoIncm.recusada(motivo));
        long auditoriasAntes = ultimaAuditoria();

        HttpResponse<String> respostaConhecido = primeiroPasso(conhecido, "errada");

        assertThat(respostaConhecido.statusCode()).isEqualTo(400);
        assertThat(json(respostaConhecido).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(respostaConhecido).get("error_description").asText()).isEqualTo(motivo);
        List<Auditoria> registos = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", conta.getId().toString());
        assertThat(registos).hasSize(1);
        assertThat(registos.get(0).getAcao()).isEqualTo("LOGIN_RECUSADO");
        assertThat(registos.get(0).getUtilizadorId()).isEqualTo(conta.getId());
        assertThat(registos.get(0).getDadosAlteracao()).contains("CREDENCIAIS_RECUSADAS_PELA_INCM");

        HttpResponse<String> respostaDesconhecido = primeiroPasso(desconhecido, "errada");

        assertThat(respostaDesconhecido.statusCode()).isEqualTo(400);
        assertThat(json(respostaDesconhecido).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(respostaDesconhecido).get("error_description").asText()).isEqualTo(motivo);
        assertThat(auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc())
                .filteredOn(registo -> registo.getId() > auditoriasAntes).hasSize(1);
    }

    @Test
    void deveRecusarUmUtilizadorConhecidoSemAdesoes() throws Exception {
        String email = emailUnico();
        incmAceita(email, "Sem Adesões");
        long sessoesAntes = sessaoRepository.count();

        HttpResponse<String> resposta = primeiroPasso(email, PASSWORD_FICTICIA);

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(json(resposta).get("error").asText()).isEqualTo("invalid_grant");
        assertThat(json(resposta).get("error_description").asText()).isEqualTo("Utilizador sem entidade associada na INCM.");
        assertThat(sessaoRepository.count()).isEqualTo(sessoesAntes);
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", utilizador.getId().toString()))
                .extracting(Auditoria::getAcao).containsExactly("LOGIN_RECUSADO");
    }

    /** Uma conta por origem: a conta AD_DGT com o mesmo email não impede o login INCM nem é tocada. */
    @Test
    void deveEntrarPelaIncmComUmaContaAdComOMesmoEmailECriarAContaIncm() throws Exception {
        String email = emailUnico();
        Utilizador contaAd = new Utilizador("ad." + email.substring(0, email.indexOf('@')), email);
        contaAd.setPerfilInterno(PerfilInterno.TECNICO);
        contaAd = utilizadorRepository.saveAndFlush(contaAd);
        incmAceita(email, "Pessoa Com Duas Contas", adesao("ENR-501", "500051062", "Câmara Municipal Fictícia"));

        HttpResponse<String> resposta = primeiroPasso(email, PASSWORD_FICTICIA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        Utilizador contaIncm = utilizadorRepository.findByEmailAndOrigemAutenticacao(email, "INCM").orElseThrow();
        assertThat(contaIncm.getId()).isNotEqualTo(contaAd.getId());
        Utilizador adDepois = utilizadorRepository.findById(contaAd.getId()).orElseThrow();
        assertThat(adDepois.getOrigemAutenticacao()).isEqualTo("AD_DGT");
        assertThat(adDepois.getPerfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(adDepois.getNome()).isEqualTo(contaAd.getNome());
    }

    @Test
    void deveDevolver500ServerErrorQuandoAIncmEstaIndisponivel() throws Exception {
        when(incm.autenticarComMotivo(any(), any())).thenThrow(new IncmIndisponivelException("indisponível"));

        HttpResponse<String> resposta = primeiroPasso(emailUnico(), PASSWORD_FICTICIA);

        assertThat(resposta.statusCode()).isEqualTo(500);
        assertThat(json(resposta).get("error").asText()).isEqualTo("server_error");
    }

    @Test
    void deveDevolver400InvalidRequestQuandoFaltamCamposOuHaCredenciaisNaQuery() throws Exception {
        HttpResponse<String> semPassword = http.pedirToken("", null, null,
                form(java.util.Map.of("grant_type", GRANT_INCM, "client_id", "saigt-ui", "username", "a@b.invalid")));
        HttpResponse<String> passwordNaQuery = http.pedirToken("?password=segredo", null, null,
                form(java.util.Map.of("grant_type", GRANT_INCM, "client_id", "saigt-ui", "username", "a@b.invalid")));

        assertThat(semPassword.statusCode()).isEqualTo(400);
        assertThat(json(semPassword).get("error").asText()).isEqualTo("invalid_request");
        assertThat(passwordNaQuery.statusCode()).isEqualTo(400);
        assertThat(json(passwordNaQuery).get("error").asText()).isEqualTo("invalid_request");
    }

    @Test
    void naoDeveEscreverNosLogsEmailNomeNipcNemPassword(CapturedOutput saida) throws Exception {
        String email = emailUnico();
        String semConta = emailUnico();
        incmAceita(email, "Nome Muito Reconhecivel", adesao("ENR-601", "509876543", "Entidade Muito Reconhecivel"));
        when(incm.autenticarComMotivo(semConta, PASSWORD_FICTICIA)).thenReturn(ResultadoAutenticacaoIncm.recusada("recusada"));
        UtilizadorIncm semAdesoes = new UtilizadorIncm("Sem Adesoes Reconhecivel", "sem-" + email, null, List.of());
        when(incm.autenticarComMotivo("sem-" + email, PASSWORD_FICTICIA)).thenReturn(ResultadoAutenticacaoIncm.aceite(semAdesoes));

        primeiroPasso(email, PASSWORD_FICTICIA);
        primeiroPasso(semConta, PASSWORD_FICTICIA);
        primeiroPasso("sem-" + email, PASSWORD_FICTICIA);

        assertThat(saida.getAll())
                .doesNotContain(email).doesNotContain(semConta)
                .doesNotContain("Nome Muito Reconhecivel").doesNotContain("Sem Adesoes Reconhecivel")
                .doesNotContain("509876543").doesNotContain("Entidade Muito Reconhecivel")
                .doesNotContain(PASSWORD_FICTICIA);
    }
}
