package com.conkord.saigt.controller.utilizador;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class UtilizadorControllerIntegrationTest {

    private static final String URL = "/api/v1/utilizadores";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilizadorRepository utilizadorRepository;
    @Autowired
    private SessaoRepository sessaoRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private EntidadeRepository entidadeRepository;
    @Autowired
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Utilizador criarUtilizador(String email, PerfilInterno perfilInterno) {
        Utilizador utilizador = new Utilizador(null, email);
        utilizador.setPerfilInterno(perfilInterno);
        return utilizadorRepository.saveAndFlush(utilizador);
    }

    private Utilizador criarUtilizadorIncm(String email) {
        return utilizadorRepository.saveAndFlush(new Utilizador("Utilizador INCM", email, "INCM"));
    }

    private Entidade criarEntidade(String nif, String nome) {
        return entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, nome));
    }

    /** Responsável do termo de adesão (`entidade.utilizador_email`) e com vínculo, como o login deixa. */
    private Entidade criarEntidadeLigada(String nif, String nome, Utilizador utilizador) {
        Entidade entidade = EntidadeTeste.nova(nif, nome);
        entidade.setResponsavelEmail(utilizador.getEmail());
        entidade = entidadeRepository.saveAndFlush(entidade);
        criarVinculo(utilizador, entidade);
        return entidade;
    }

    /** Vínculo de `utilizador_entidade`, como o deixa o `ValidateUser`. */
    private void criarVinculo(Utilizador utilizador, Entidade entidade) {
        utilizadorEntidadeRepository.saveAndFlush(new UtilizadorEntidade(utilizador, entidade, Instant.now()));
    }

    private RequestPostProcessor comoAdmin() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private RequestPostProcessor comoVisitante() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "VISITANTE"));
    }

    private RequestPostProcessor comoTecnico() {
        return jwt().jwt(builder -> builder.claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "TECNICO"));
    }

    /** Criar, desativar, reativar e remover saíram: 404 `NAO_ENCONTRADO`, nada muda e nada vai para o log de erro. */
    @Test
    void osCaminhosRetiradosDevolvem404SemMudarNadaNemRegistarErro(CapturedOutput saida) throws Exception {
        Utilizador alvo = criarUtilizador("retirados.alvo@dgt.pt", PerfilInterno.TECNICO);
        long utilizadoresAntes = utilizadorRepository.count();
        String corpoComMotivo = "{\"motivo\":\"motivo\"}";

        mockMvc.perform(post(URL).with(comoAdmin()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"retirados.novo@dgt.pt\",\"perfilInterno\":\"TECNICO\",\"motivo\":\"motivo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Recurso não encontrado."));
        for (String acao : new String[] {"desativar", "reativar"}) {
            mockMvc.perform(patch(URL + "/" + alvo.getId() + "/" + acao).with(comoAdmin())
                            .contentType(APPLICATION_JSON).content(corpoComMotivo))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                    .andExpect(jsonPath("$.mensagem").value("Recurso não encontrado."));
        }
        mockMvc.perform(delete(URL + "/" + alvo.getId()).with(comoAdmin()).contentType(APPLICATION_JSON).content(corpoComMotivo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Recurso não encontrado."));

        assertThat(utilizadorRepository.count()).isEqualTo(utilizadoresAntes);
        assertThat(utilizadorRepository.findById(alvo.getId())).isPresent();
        assertThat(utilizadorRepository.findByEmailAndOrigemAutenticacao("retirados.novo@dgt.pt", "AD_DGT")).isEmpty();
        assertThat(auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", alvo.getId().toString())).isEmpty();
        assertThat(saida.getAll()).doesNotContain("Erro não tratado", "retirados.alvo@dgt.pt", "retirados.novo@dgt.pt");
    }

    @Test
    void umMetodoNaoSuportadoNumCaminhoExistenteDevolve404SemLogDeErro(CapturedOutput saida) throws Exception {
        Utilizador alvo = criarUtilizador("metodo.nao.suportado@dgt.pt", PerfilInterno.TECNICO);

        mockMvc.perform(put(URL + "/" + alvo.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON).content("{\"perfilInterno\":\"MASTER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));

        assertThat(utilizadorRepository.findById(alvo.getId()).orElseThrow().getPerfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(saida.getAll()).doesNotContain("Erro não tratado", "HttpRequestMethodNotSupportedException");
    }

    @Test
    void tecnicoNoCaminhoDeCriarRecebe404ENadaECriado() throws Exception {
        mockMvc.perform(post(URL).with(comoTecnico()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"novo2@dgt.pt\",\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isNotFound());
        assertThat(utilizadorRepository.findByEmailAndOrigemAutenticacao("novo2@dgt.pt", "AD_DGT")).isEmpty();
    }

    @Test
    void aListagemNaoTemAtivoEIgnoraOFiltroDesativado() throws Exception {
        criarUtilizador("listagem.sem.ativo@dgt.pt", PerfilInterno.VISITANTE);

        String comFiltro = mockMvc.perform(get(URL).with(comoAdmin()).param("desativado", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='listagem.sem.ativo@dgt.pt')]").exists())
                .andReturn().getResponse().getContentAsString();
        String semFiltro = mockMvc.perform(get(URL).with(comoAdmin()).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode itens = objectMapper.readTree(comFiltro).get("content");
        assertThat(itens).isNotEmpty();
        itens.forEach(item -> assertThat(item.has("ativo")).isFalse());
        assertThat(objectMapper.readTree(comFiltro).get("totalElements"))
                .isEqualTo(objectMapper.readTree(semFiltro).get("totalElements"));
    }

    @Test
    void tecnicoNaoDeveConseguirListarNemObterUtilizadores() throws Exception {
        Utilizador utilizador = criarUtilizador("leitura.tecnico@dgt.pt", PerfilInterno.VISITANTE);

        mockMvc.perform(get(URL).with(comoTecnico())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get(URL).with(comoVisitante())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(get(URL + "/" + utilizador.getId()).with(comoTecnico())).andExpect(status().isForbidden());
    }

    @Test
    void masterDeveConseguirListarComFiltroDePerfilInterno() throws Exception {
        criarUtilizador("listagem.admin@dgt.pt", PerfilInterno.MASTER);
        criarUtilizador("listagem.tecnico@dgt.pt", PerfilInterno.TECNICO);

        mockMvc.perform(get(URL).with(comoAdmin()).param("perfilInterno", "MASTER").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='listagem.admin@dgt.pt')]").exists())
                .andExpect(jsonPath("$.content[?(@.email=='listagem.tecnico@dgt.pt')]").doesNotExist());
    }

    @Test
    void deveDevolver404AoObterUtilizadorInexistente() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveAlterarPerfilInternoERevogarSessaoAtiva() throws Exception {
        Utilizador utilizador = criarUtilizador("alterar@dgt.pt", PerfilInterno.TECNICO);
        Sessao sessaoAtiva = new Sessao("jti-alterar-perfil", utilizador, Instant.now().plusSeconds(3600));
        sessaoRepository.saveAndFlush(sessaoAtiva);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"MASTER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilInterno").value("MASTER"))
                .andExpect(jsonPath("$.ativo").doesNotExist());

        assertThat(sessaoRepository.findById("jti-alterar-perfil").orElseThrow().getRevogadoEm()).isNotNull();
    }

    @Test
    void deveDespromoverMasterATecnicoRevogarSessaoEAuditarPerfilAntesEDepois() throws Exception {
        criarUtilizador("outro.master.despromocao@dgt.pt", PerfilInterno.MASTER);
        Utilizador master = criarUtilizador("despromover@dgt.pt", PerfilInterno.MASTER);
        sessaoRepository.saveAndFlush(new Sessao("jti-despromover", master, Instant.now().plusSeconds(3600)));

        mockMvc.perform(patch(URL + "/" + master.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilInterno").value("TECNICO"));

        assertThat(utilizadorRepository.findById(master.getId()).orElseThrow().getPerfilInterno())
                .isEqualTo(PerfilInterno.TECNICO);
        assertThat(sessaoRepository.findById("jti-despromover").orElseThrow().getRevogadoEm()).isNotNull();
        Auditoria registo = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "UTILIZADOR", master.getId().toString(), "ALTERAR_PERFIL_INTERNO").orElseThrow();
        JsonNode dados = objectMapper.readTree(registo.getDadosAlteracao());
        assertThat(dados.get("perfilInternoAntes").asText()).isEqualTo("MASTER");
        assertThat(dados.get("perfilInternoDepois").asText()).isEqualTo("TECNICO");
        assertThat(dados.has("motivo")).isFalse();
    }

    @Test
    void umMotivoEnviadoNoPerfilEIgnorado() throws Exception {
        Utilizador utilizador = criarUtilizador("motivo.ignorado@dgt.pt", PerfilInterno.VISITANTE);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\",\"motivo\":\"Mudança de funções\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilInterno").value("TECNICO"));

        Auditoria registo = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "UTILIZADOR", utilizador.getId().toString(), "ALTERAR_PERFIL_INTERNO").orElseThrow();
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).has("motivo")).isFalse();
    }

    @Test
    void deveRejeitarAlterarPerfilInternoParaAdminDgtComo400() throws Exception {
        Utilizador utilizador = criarUtilizador("valor.antigo@dgt.pt", PerfilInterno.TECNICO);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"ADMIN_DGT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("perfilInterno"));

        assertThat(utilizadorRepository.findById(utilizador.getId()).orElseThrow().getPerfilInterno())
                .isEqualTo(PerfilInterno.TECNICO);
    }

    @Test
    void tecnicoNaoDeveConseguirAlterarPerfilInterno() throws Exception {
        Utilizador utilizador = criarUtilizador("alvo.tecnico@dgt.pt", PerfilInterno.VISITANTE);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoTecnico())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoVisitante())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));

        assertThat(utilizadorRepository.findById(utilizador.getId()).orElseThrow().getPerfilInterno())
                .isEqualTo(PerfilInterno.VISITANTE);
    }

    @Test
    void deveDevolver400AoListarComFiltroDePerfilInternoForaDoEnum() throws Exception {
        mockMvc.perform(get(URL).with(comoAdmin()).param("perfilInterno", "ADMIN_DGT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarAlterarPerfilInternoDeUtilizadorIncmComo400() throws Exception {
        Utilizador utilizador = new Utilizador("Entidade Exemplo", "entidade.perfil@empresa.pt", "INCM");
        utilizadorRepository.saveAndFlush(utilizador);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarDespromoverOUnicoMasterComo409() throws Exception {
        despromoverOsMasters();
        Utilizador unicoMaster = criarUtilizador("unico.master.despromocao@dgt.pt", PerfilInterno.MASTER);

        mockMvc.perform(patch(URL + "/" + unicoMaster.getId() + "/perfil-interno").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"perfilInterno\":\"TECNICO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Operação deixaria a plataforma sem administrador."));

        assertThat(utilizadorRepository.findById(unicoMaster.getId()).orElseThrow().getPerfilInterno())
                .isEqualTo(PerfilInterno.MASTER);
    }

    private void despromoverOsMasters() {
        utilizadorRepository.findAll().stream()
                .filter(u -> u.getPerfilInterno() == PerfilInterno.MASTER)
                .forEach(u -> {
                    u.setPerfilInterno(PerfilInterno.TECNICO);
                    utilizadorRepository.saveAndFlush(u);
                });
    }

    @Test
    void deveRevogarAsSessoesSemCorpoEDevolverQuantasRevogou() throws Exception {
        Utilizador utilizador = criarUtilizador("revogar.sessoes@dgt.pt", PerfilInterno.TECNICO);
        sessaoRepository.saveAndFlush(new Sessao("jti-revogar-sessoes", utilizador, Instant.now().plusSeconds(3600)));

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/sessoes/revogar").with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessoesRevogadas").value(1))
                .andExpect(jsonPath("$.ativo").doesNotExist());

        assertThat(sessaoRepository.findById("jti-revogar-sessoes").orElseThrow().getRevogadoEm()).isNotNull();
        Auditoria registo = auditoriaRepository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmDescIdDesc(
                "UTILIZADOR", utilizador.getId().toString(), "REVOGAR_SESSOES").orElseThrow();
        assertThat(objectMapper.readTree(registo.getDadosAlteracao()).has("motivo")).isFalse();
    }

    @Test
    void deveRevogarComZeroSessoesEIgnorarUmMotivoEnviado() throws Exception {
        Utilizador utilizador = criarUtilizador("revogar.sem.sessoes@dgt.pt", PerfilInterno.VISITANTE);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/sessoes/revogar").with(comoAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Suspeita de credencial comprometida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessoesRevogadas").value(0));
    }

    @Test
    void deveDevolver404AoRevogarSessoesDeUtilizadorInexistente() throws Exception {
        mockMvc.perform(patch(URL + "/" + UUID.randomUUID() + "/sessoes/revogar").with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tecnicoNaoDeveConseguirRevogarSessoes() throws Exception {
        Utilizador utilizador = criarUtilizador("revogar.sessoes.tecnico@dgt.pt", PerfilInterno.TECNICO);

        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/sessoes/revogar").with(comoTecnico()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        mockMvc.perform(patch(URL + "/" + utilizador.getId() + "/sessoes/revogar").with(comoVisitante()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
    }

    @Test
    void deveDevolverDuasEntidadesLigadasNoDetalhe() throws Exception {
        Utilizador utilizador = criarUtilizadorIncm("roster.duasentidades@empresa.pt");
        criarEntidadeLigada("501000701", "Camara Roster A", utilizador);
        criarEntidadeLigada("501000702", "Camara Roster B", utilizador);

        mockMvc.perform(get(URL + "/" + utilizador.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidades", hasSize(2)))
                .andExpect(jsonPath("$.entidades[?(@.entidadeNome=='Camara Roster A')]").exists())
                .andExpect(jsonPath("$.entidades[?(@.entidadeNome=='Camara Roster B')]").exists());
    }

    @Test
    void deveDevolverListaVaziaDeEntidadesParaUtilizadorSemLigacao() throws Exception {
        Utilizador utilizador = criarUtilizador("roster.addgt@dgt.pt", PerfilInterno.TECNICO);

        mockMvc.perform(get(URL + "/" + utilizador.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidades", hasSize(0)));
    }

    /** `entidades` não deriva de `auditoria`: validar processos da entidade não a liga ao utilizador. */
    @Test
    void naoDeveDevolverEntidadeParaAdminQueApenasValidouProcessosSemSerATitularDaAdesao() throws Exception {
        Utilizador helena = criarUtilizadorIncm("helena.sintra@incm.pt");
        Entidade sintra = criarEntidadeLigada("501000708", "Camara Municipal de Sintra", helena);
        Utilizador ana = criarUtilizador("ana.silva.sintra@dgt.pt", PerfilInterno.MASTER);
        auditoriaService.registar("PROCESSO", "qualquer", "VALIDACAO", ana.getId(), sintra.getId(), null, null);

        mockMvc.perform(get(URL + "/" + ana.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidades", hasSize(0)));
    }

    @Test
    void masterDeveConseguirFiltrarUtilizadoresPorEntidadeId() throws Exception {
        Entidade entidade = criarEntidade("501000703", "Camara Filtro EntidadeId");
        Utilizador vinculado = criarUtilizadorIncm("roster.vinculado@empresa.pt");
        criarVinculo(vinculado, entidade);
        criarUtilizadorIncm("roster.naovinculado@empresa.pt");

        mockMvc.perform(get(URL).with(comoAdmin()).param("entidadeId", String.valueOf(entidade.getId())).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='roster.vinculado@empresa.pt')]").exists())
                .andExpect(jsonPath("$.content[?(@.email=='roster.naovinculado@empresa.pt')]").doesNotExist());
    }

    /** Filtro por vínculos da adesão: quem só atuou sob a entidade (a DGT) não pertence a ela. */
    @Test
    void deveFiltrarPorEntidadeOsVinculadosEExcluirAdminQueSoAtuouSobEla() throws Exception {
        Utilizador helena = criarUtilizadorIncm("helena.filtro@incm.pt");
        Entidade sintra = criarEntidadeLigada("501000709", "Camara Municipal de Sintra Filtro", helena);
        Utilizador ricardo = criarUtilizadorIncm("ricardo.filtro@incm.pt");
        criarVinculo(ricardo, sintra);
        Utilizador ana = criarUtilizador("ana.silva.filtro@dgt.pt", PerfilInterno.MASTER);
        auditoriaService.registar("PROCESSO", "qualquer", "VALIDACAO", ana.getId(), sintra.getId(), null, null);

        mockMvc.perform(get(URL).with(comoAdmin()).param("entidadeId", String.valueOf(sintra.getId())).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.email=='helena.filtro@incm.pt')]").exists())
                .andExpect(jsonPath("$.content[?(@.email=='ricardo.filtro@incm.pt')]").exists())
                .andExpect(jsonPath("$.content[?(@.email=='ana.silva.filtro@dgt.pt')]").doesNotExist());
    }

    /** Ricardo: responsável de Cascais e técnico de Sintra; Helena: responsável de Sintra. */
    @Test
    void deveAssinalarResponsavelEmCadaVinculo() throws Exception {
        Utilizador ricardo = criarUtilizadorIncm("ricardo.responsavel@incm.pt");
        Utilizador helena = criarUtilizadorIncm("helena.responsavel@incm.pt");
        criarEntidadeLigada("501000710", "Camara Municipal de Cascais Resp", ricardo);
        Entidade sintra = criarEntidadeLigada("501000711", "Camara Municipal de Sintra Resp", helena);
        criarVinculo(ricardo, sintra);

        mockMvc.perform(get(URL + "/" + ricardo.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidades", hasSize(2)))
                .andExpect(jsonPath("$.entidades[?(@.entidadeNome=='Camara Municipal de Cascais Resp')].responsavel").value(true))
                .andExpect(jsonPath("$.entidades[?(@.entidadeNome=='Camara Municipal de Sintra Resp')].responsavel").value(false));
        mockMvc.perform(get(URL + "/" + helena.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entidades", hasSize(1)))
                .andExpect(jsonPath("$.entidades[0].responsavel").value(true));
    }

    @Test
    void deveFiltrarUtilizadoresPorEntidadeIdEAtuouDesdeCombinados() throws Exception {
        Entidade entidade = criarEntidade("501000704", "Camara Filtro Combinado");
        Utilizador utilizador = criarUtilizadorIncm("roster.combinado@empresa.pt");
        criarVinculo(utilizador, entidade);
        auditoriaService.registar("PROCESSO", "qualquer", "CRIACAO", utilizador.getId(), entidade.getId(), null, null);

        mockMvc.perform(get(URL).with(comoAdmin())
                        .param("entidadeId", String.valueOf(entidade.getId()))
                        .param("atuouDesde", LocalDate.now().toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='roster.combinado@empresa.pt')]").exists());

        mockMvc.perform(get(URL).with(comoAdmin())
                        .param("entidadeId", String.valueOf(entidade.getId()))
                        .param("atuouDesde", LocalDate.now().plusDays(1).toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='roster.combinado@empresa.pt')]").doesNotExist());
    }

    @Test
    void deveRecusarAtuouDesdeSemEntidadeCom400IndiqueAEntidade() throws Exception {
        mockMvc.perform(get(URL).with(comoAdmin()).param("atuouDesde", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Indique a entidade."));
    }

    /** Linha de `auditoria` com a data pedida; o `AuditoriaService` grava sempre «agora». */
    private void registarAtuacao(Utilizador utilizador, Entidade entidade, String instante) {
        jdbcTemplate.update("INSERT INTO auditoria (tipo_ocorrencia, id_ocorrencia, acao, utilizador_id, entidade_id, criado_em) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "PROCESSO", "roster", "SUBMETER", utilizador.getId(), entidade.getId(), Timestamp.from(Instant.parse(instante)));
    }

    /** Atuou em A a 10/09 e em B a 20/09: no roster de A desde 01/09 com a data de A; desde 15/09 já não. */
    @Test
    void oRosterDaEntidadeUsaSoAAuditoriaDessaEntidadeEDevolveAUltimaAtuacaoNela() throws Exception {
        Entidade entidadeA = criarEntidade("501000712", "Camara Roster A");
        Entidade entidadeB = criarEntidade("501000713", "Camara Roster B");
        Utilizador rita = criarUtilizadorIncm("rita.roster@empresa.pt");
        registarAtuacao(rita, entidadeA, "2026-09-10T10:00:00Z");
        registarAtuacao(rita, entidadeB, "2026-09-20T10:00:00Z");

        String corpo = mockMvc.perform(get(URL).with(comoAdmin())
                        .param("entidadeId", String.valueOf(entidadeA.getId()))
                        .param("atuouDesde", "2026-09-01")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("rita.roster@empresa.pt"))
                .andReturn().getResponse().getContentAsString();
        JsonNode ultimaAtuacao = objectMapper.readTree(corpo).path("content").path(0).path("ultimaAtuacaoEm");
        assertThat(Instant.parse(ultimaAtuacao.asText())).isEqualTo(Instant.parse("2026-09-10T10:00:00Z"));

        mockMvc.perform(get(URL).with(comoAdmin())
                        .param("entidadeId", String.valueOf(entidadeA.getId()))
                        .param("atuouDesde", "2026-09-15")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** O roster não exige vínculo: conta quem atuou pela entidade, mesmo sem adesão a ela hoje. */
    @Test
    void oRosterIncluiQuemAtuouSemVinculoEExcluiQuemTemVinculoMasNaoAtuou() throws Exception {
        Entidade entidade = criarEntidade("501000714", "Camara Roster Vinculo");
        Utilizador atuouSemVinculo = criarUtilizadorIncm("roster.semvinculo@empresa.pt");
        Utilizador vinculadoSemAtuar = criarUtilizadorIncm("roster.parado@empresa.pt");
        criarVinculo(vinculadoSemAtuar, entidade);
        registarAtuacao(atuouSemVinculo, entidade, "2026-09-10T10:00:00Z");

        mockMvc.perform(get(URL).with(comoAdmin())
                        .param("entidadeId", String.valueOf(entidade.getId()))
                        .param("atuouDesde", "2026-09-01")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("roster.semvinculo@empresa.pt"));
    }

    /** Sem `entidadeId` o campo não vem; com `entidadeId` vem para quem já atuou pela entidade. */
    @Test
    void aUltimaAtuacaoSoVemNaListagemPorEntidade() throws Exception {
        Entidade entidade = criarEntidade("501000715", "Camara Ultima Atuacao");
        Utilizador vinculado = criarUtilizadorIncm("roster.ultima@empresa.pt");
        criarVinculo(vinculado, entidade);
        registarAtuacao(vinculado, entidade, "2026-09-12T08:30:00Z");

        String semEntidade = mockMvc.perform(get(URL).with(comoAdmin()).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(semEntidade).contains("roster.ultima@empresa.pt").doesNotContain("ultimaAtuacaoEm");

        mockMvc.perform(get(URL).with(comoAdmin()).param("entidadeId", String.valueOf(entidade.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ultimaAtuacaoEm").exists());
    }

    /** Conta `AD_DGT` sem email no AD: a listagem devolve-a com `email` nulo. */
    @Test
    void aContaAdDgtSemEmailApareceNaListagemComEmailNulo() throws Exception {
        Utilizador semEmail = new Utilizador("joao.semmail", null, "AD_DGT");
        semEmail.setPerfilInterno(PerfilInterno.VISITANTE);
        utilizadorRepository.saveAndFlush(semEmail);

        mockMvc.perform(get(URL).with(comoAdmin()).param("origemAutenticacao", "AD_DGT").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nome=='joao.semmail')]").exists())
                .andExpect(jsonPath("$.content[?(@.nome=='joao.semmail')].email").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void deveDevolver401SemJwt() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void deveDevolverEntidadesLigadasNaListagem() throws Exception {
        Utilizador utilizador = criarUtilizadorIncm("roster.listagem@empresa.pt");
        criarEntidadeLigada("501000706", "Camara Listagem", utilizador);

        mockMvc.perform(get(URL).with(comoAdmin()).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email=='roster.listagem@empresa.pt')].entidades[0].entidadeNome")
                        .value("Camara Listagem"));
    }

    /** `ultimoLoginEm` vem de qualquer evento LOGIN, não da última atuação por entidade. */
    @Test
    void deveDevolverUltimoLoginNoDetalhe() throws Exception {
        Utilizador utilizador = criarUtilizadorIncm("roster.ultimologin@empresa.pt");
        auditoriaService.registar("UTILIZADOR", utilizador.getId().toString(), "LOGIN", utilizador.getId(), (Object) null);

        mockMvc.perform(get(URL + "/" + utilizador.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoLoginEm").exists());
    }

    @Test
    void deveDevolverUltimoLoginNuloSemNenhumLoginRegistado() throws Exception {
        Utilizador utilizador = criarUtilizadorIncm("roster.semlogin@empresa.pt");

        mockMvc.perform(get(URL + "/" + utilizador.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoLoginEm").doesNotExist());
    }
}
