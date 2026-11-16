package com.conkord.saigt.controller.utilizador;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UtilizadorMeControllerIntegrationTest {

    private static final String URL = "/api/v1/utilizadores/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Utilizador criarDgt(String nome, String email) {
        Utilizador utilizador = new Utilizador(nome, email);
        utilizador.setPerfilInterno(PerfilInterno.TECNICO);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private Utilizador criarIncm(String nome, String email) {
        Utilizador utilizador = new Utilizador(nome, email, "INCM");
        utilizador.setCargo("Técnica Superior de Urbanismo");
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private RequestPostProcessor comoDgt(Utilizador utilizador) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    private RequestPostProcessor comoIncm(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, utilizador.getId().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("id", entidade.getId(), "codigo", entidade.getCodigo(), "tipo_entidade", "CM",
                        "nome", "Município de Teste", "nipc", "999999999", "pcm", false,
                        "morada", Map.of("arteria", "Av. Marginal", "codigo_postal", "2750-000"))));
    }

    private static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void oDgtRecebeNomeEmailTelefoneMoradaOrigemEPerfil() throws Exception {
        Utilizador utilizador = criarDgt("dgt." + unico(), "maria.me@dgt.pt");
        utilizador.setTelefone("912345678");
        utilizador.setMorada("Rua da Artilharia Um, 107");
        utilizadorRepository.saveAndFlush(utilizador);

        mockMvc.perform(get(URL).with(comoDgt(utilizador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(utilizador.getNome()))
                .andExpect(jsonPath("$.email").value("maria.me@dgt.pt"))
                .andExpect(jsonPath("$.telefone").value("912345678"))
                .andExpect(jsonPath("$.morada").value("Rua da Artilharia Um, 107"))
                .andExpect(jsonPath("$.origemAutenticacao").value("AD_DGT"))
                .andExpect(jsonPath("$.perfilInterno").value("TECNICO"))
                .andExpect(jsonPath("$.cargo").value(nullValue()))
                .andExpect(jsonPath("$.entidade").value(nullValue()))
                .andExpect(jsonPath("$.telemovel").doesNotExist());
    }

    @Test
    void oIncmRecebeNomeEmailCargoEAEntidadeComNipcTelefoneEmailEResponsavelSemMorada() throws Exception {
        Entidade entidade = EntidadeTeste.nova("501234567", "CM Teste");
        entidade.setTelefone("219000000");
        entidade.setEmail("geral@cm-teste.pt");
        entidade.setResponsavelNome("Carlos Pereira");
        entidade.setResponsavelEmail("carlos.pereira@cm-teste.pt");
        entidade.setResponsavelCargo("Diretor do Departamento de Urbanismo");
        entidade = entidadeRepository.saveAndFlush(entidade);
        Utilizador utilizador = criarIncm("Helena Sintra", "helena." + unico() + "@cm-teste.pt");

        mockMvc.perform(get(URL).with(comoIncm(utilizador, entidade)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Helena Sintra"))
                .andExpect(jsonPath("$.email").value(utilizador.getEmail()))
                .andExpect(jsonPath("$.cargo").value("Técnica Superior de Urbanismo"))
                .andExpect(jsonPath("$.origemAutenticacao").value("INCM"))
                .andExpect(jsonPath("$.perfilInterno").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value(nullValue()))
                .andExpect(jsonPath("$.morada").value(nullValue()))
                .andExpect(jsonPath("$.entidade.codigo").value(entidade.getCodigo()))
                .andExpect(jsonPath("$.entidade.nome").value("Município de Teste"))
                .andExpect(jsonPath("$.entidade.tipoEntidade").value("CM"))
                .andExpect(jsonPath("$.entidade.nipc").value("501234567"))
                .andExpect(jsonPath("$.entidade.telefone").value("219000000"))
                .andExpect(jsonPath("$.entidade.email").value("geral@cm-teste.pt"))
                .andExpect(jsonPath("$.entidade.responsavel.nome").value("Carlos Pereira"))
                .andExpect(jsonPath("$.entidade.responsavel.email").value("carlos.pereira@cm-teste.pt"))
                .andExpect(jsonPath("$.entidade.responsavel.cargo").value("Diretor do Departamento de Urbanismo"))
                .andExpect(jsonPath("$.entidade.morada").doesNotExist());
    }

    @Test
    void oDgtGravaEmailTelefoneEMoradaComAuditoriaDoAntesEDoDepois(CapturedOutput saida) throws Exception {
        Utilizador utilizador = criarDgt("dgt." + unico(), null);
        String email = "novo." + unico() + "@dgt.pt";

        mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"telefone\":\"+351 912 345 678\","
                                + "\"morada\":\"Rua Secreta da Morada, 99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.telefone").value("+351 912 345 678"))
                .andExpect(jsonPath("$.morada").value("Rua Secreta da Morada, 99"));

        Utilizador persistido = utilizadorRepository.findById(utilizador.getId()).orElseThrow();
        assertThat(persistido.getEmail()).isEqualTo(email);
        assertThat(persistido.getTelefone()).isEqualTo("+351 912 345 678");
        assertThat(persistido.getMorada()).isEqualTo("Rua Secreta da Morada, 99");
        assertThat(persistido.getNome()).isEqualTo(utilizador.getNome());

        Auditoria registo = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "UTILIZADOR", utilizador.getId().toString(), "EDITAR_PERFIL").orElseThrow();
        assertThat(registo.getUtilizadorId()).isEqualTo(utilizador.getId());
        JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
        assertThat(dados.get("email").get("antes").isNull()).isTrue();
        assertThat(dados.get("email").get("depois").asText()).isEqualTo(email);
        assertThat(dados.get("telefone").get("depois").asText()).isEqualTo("+351 912 345 678");
        assertThat(dados.get("morada").get("depois").asText()).isEqualTo("Rua Secreta da Morada, 99");

        assertThat(saida.getAll()).doesNotContain(email, "912 345 678", "Rua Secreta da Morada");
    }

    @Test
    void vazioLimpaOCampoEOOmitidoNaoMuda() throws Exception {
        Utilizador utilizador = criarDgt("dgt." + unico(), "limpar." + unico() + "@dgt.pt");
        utilizador.setTelefone("912345678");
        utilizador.setMorada("Morada a limpar");
        utilizadorRepository.saveAndFlush(utilizador);

        mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON).content("{\"morada\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morada").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value("912345678"))
                .andExpect(jsonPath("$.email").value(utilizador.getEmail()));
    }

    @Test
    void nomeOuCargoNoPedidoDa400ENadaMuda(CapturedOutput saida) throws Exception {
        Utilizador utilizador = criarDgt("dgt." + unico(), "intocado." + unico() + "@dgt.pt");

        for (String corpo : new String[] {"{\"nome\":\"Outro Nome\"}",
                "{\"cargo\":\"Diretor\",\"email\":\"mudaria@dgt.pt\"}"}) {
            mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value("Só pode alterar o email, o telefone e a morada."));
        }

        Utilizador persistido = utilizadorRepository.findById(utilizador.getId()).orElseThrow();
        assertThat(persistido.getNome()).isEqualTo(utilizador.getNome());
        assertThat(persistido.getEmail()).isEqualTo(utilizador.getEmail());
        assertThat(persistido.getCargo()).isNull();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", utilizador.getId().toString()))
                .isEmpty();
        assertThat(saida.getAll()).doesNotContain("mudaria@dgt.pt");
    }

    @Test
    void emailInvalidoDa400NoCampo() throws Exception {
        Utilizador utilizador = criarDgt("dgt." + unico(), null);

        mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"sem-arroba.pt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("email"))
                .andExpect(jsonPath("$.campos[0].erro").value("Formato de email inválido."));
        assertThat(utilizadorRepository.findById(utilizador.getId()).orElseThrow().getEmail()).isNull();
    }

    @Test
    void oIncmRecebe403GeridoPelaIncmENadaMuda() throws Exception {
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234568", "CM Outra"));
        Utilizador utilizador = criarIncm("Gestor Entidade", "gestor." + unico() + "@cm-teste.pt");

        mockMvc.perform(patch(URL).with(comoIncm(utilizador, entidade)).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"outro@cm-teste.pt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"))
                .andExpect(jsonPath("$.mensagem").value("Os seus dados são geridos pela INCM."));

        assertThat(utilizadorRepository.findById(utilizador.getId()).orElseThrow().getEmail())
                .isEqualTo(utilizador.getEmail());
    }

    @Test
    void oEmailDeOutraContaDgtDa409NoCampoEOdeUmaContaIncmNao() throws Exception {
        String emailOcupado = "ocupado." + unico() + "@dgt.pt";
        criarDgt("dgt." + unico(), emailOcupado);
        String emailIncm = "pessoa." + unico() + "@dgt.pt";
        criarIncm("Pessoa Dupla", emailIncm);
        Utilizador utilizador = criarDgt("dgt." + unico(), "meu." + unico() + "@dgt.pt");

        mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + emailOcupado.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_DUPLICADA"))
                .andExpect(jsonPath("$.campos[0].path").value("email"));
        assertThat(utilizadorRepository.findById(utilizador.getId()).orElseThrow().getEmail())
                .isEqualTo(utilizador.getEmail());

        mockMvc.perform(patch(URL).with(comoDgt(utilizador)).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + emailIncm + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailIncm));
    }

    @Test
    void deveDevolver401SemJwt() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(APPLICATION_JSON).content("{\"telefone\":\"912345678\"}"))
                .andExpect(status().isUnauthorized());
    }
}
