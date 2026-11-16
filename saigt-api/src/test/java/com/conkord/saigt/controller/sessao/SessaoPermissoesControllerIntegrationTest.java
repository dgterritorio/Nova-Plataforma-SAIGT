package com.conkord.saigt.controller.sessao;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Claims lidos só do JWT mock, sem decoder real. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessaoPermissoesControllerIntegrationTest {

    private static final String URL = "/api/v1/sessao/me/permissoes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    private InstrumentoProcedimentoConfig criarProcedimentoConfig(int ordem) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM-" + ordem, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-" + ordem, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("PROC-" + ordem, "Procedimento " + ordem));
        return procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, ordem, "SEGUNDA_H"));
    }

    @Test
    void deveDevolver200ComOrigemAtorEOsProcedimentosPermitidos() throws Exception {
        InstrumentoProcedimentoConfig config1 = criarProcedimentoConfig(1);
        InstrumentoProcedimentoConfig config2 = criarProcedimentoConfig(2);
        InstrumentoProcedimentoConfig config3 = criarProcedimentoConfig(3);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config1));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config2));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config3));
        InstrumentoProcedimentoConfig semPermissaoEntidade = criarProcedimentoConfig(6);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, semPermissaoEntidade));

        mockMvc.perform(get(URL).with(jwtComEntidade("512523698", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("INCM"))
                .andExpect(jsonPath("$.ator").value("ENTIDADE"))
                .andExpect(jsonPath("$.perfilInterno").value(nullValue()))
                .andExpect(jsonPath("$.tipoEntidade").value("CM"))
                // A BD é a do contexto, partilhada com outras classes da mesma configuração: não se conta o total.
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItems(
                        config1.getId().intValue(), config2.getId().intValue(), config3.getId().intValue())))
                .andExpect(jsonPath("$.procedimentosPermitidos", not(hasItem(semPermissaoEntidade.getId().intValue()))));
    }

    @Test
    void deveDevolverAtorDgtEListaVaziaParaTecnicoMesmoComPermissoesDgt() throws Exception {
        PermissaoAcao permissaoDgt = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, criarProcedimentoConfig(9)));
        try {
            mockMvc.perform(get(URL).with(jwtComPerfilInterno("TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origem").value("AD_DGT"))
                    .andExpect(jsonPath("$.ator").value("DGT"))
                    .andExpect(jsonPath("$.perfilInterno").value("TECNICO"))
                    .andExpect(jsonPath("$.tipoEntidade").value(nullValue()))
                    .andExpect(jsonPath("$.procedimentosPermitidos", empty()));
        } finally {
            permissaoAcaoRepository.delete(permissaoDgt);
        }
    }

    @Test
    void deveDevolverTipoNuloEListaVaziaParaMasterMesmoComPermissoesDgt() throws Exception {
        PermissaoAcao permissaoDgt = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, criarProcedimentoConfig(10)));
        try {
            mockMvc.perform(get(URL).with(jwtComPerfilInterno("MASTER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ator").value("DGT"))
                    .andExpect(jsonPath("$.perfilInterno").value("MASTER"))
                    .andExpect(jsonPath("$.tipoEntidade").value(nullValue()))
                    .andExpect(jsonPath("$.procedimentosPermitidos", empty()));
        } finally {
            permissaoAcaoRepository.delete(permissaoDgt);
        }
    }

    @Test
    void deveDevolverTipoNuloEListaVaziaParaSessaoSemTipoDeEntidade() throws Exception {
        PermissaoAcao permissaoCm = permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, criarProcedimentoConfig(11)));
        try {
            mockMvc.perform(get(URL).with(jwtComEntidadeSemTipo("512523698")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ator").value("ENTIDADE"))
                    .andExpect(jsonPath("$.tipoEntidade").value(nullValue()))
                    .andExpect(jsonPath("$.procedimentosPermitidos", empty()));
        } finally {
            permissaoAcaoRepository.delete(permissaoCm);
        }
    }

    @Test
    void deveDevolverSoOsProcedimentosDoTipoDaEntidadeDaSessao() throws Exception {
        InstrumentoProcedimentoConfig configCm = criarProcedimentoConfig(12);
        InstrumentoProcedimentoConfig configCcdr = criarProcedimentoConfig(13);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, configCm));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CCDR_DR, configCcdr));

        mockMvc.perform(get(URL).with(jwtComEntidade("512523698", false, "CCDR_DR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEntidade").value("CCDR_DR"))
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItem(configCcdr.getId().intValue())))
                .andExpect(jsonPath("$.procedimentosPermitidos", not(hasItem(configCm.getId().intValue()))));
    }

    @Test
    void deveDevolverAtorNuloEListaVaziaParaVisitante() throws Exception {
        mockMvc.perform(get(URL).with(jwtComPerfilInterno("VISITANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ator").value(nullValue()))
                .andExpect(jsonPath("$.perfilInterno").value("VISITANTE"))
                .andExpect(jsonPath("$.procedimentosPermitidos", empty()));
    }

    @Test
    void deveIgnorarParametroOuHeaderQueTentaForcarOutroAtor() throws Exception {
        mockMvc.perform(get(URL)
                        .param("ator", "DGT")
                        .header("X-Ator", "DGT")
                        .with(jwtComEntidade("512523698", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ator").value("ENTIDADE"));
    }

    @Test
    void deveDevolver401ComEnvelopeQuandoSemJwt() throws Exception {
        MvcResult resultado = mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(resultado.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.codigo()).isEqualTo("NAO_AUTENTICADO");
    }

    @Test
    void naoDeveIncluirProcedimentoDePermissaoDesativada() throws Exception {
        InstrumentoProcedimentoConfig configAtivo = criarProcedimentoConfig(4);
        InstrumentoProcedimentoConfig configInativo = criarProcedimentoConfig(5);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.DGT, configAtivo));
        PermissaoAcao permissaoDesativada = new PermissaoAcao(TipoEntidade.DGT, configInativo);
        permissaoDesativada.setAtivo(false);
        permissaoAcaoRepository.saveAndFlush(permissaoDesativada);

        mockMvc.perform(get(URL).with(jwtComEntidade("512523698", false, "DGT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEntidade").value("DGT"))
                .andExpect(jsonPath("$.procedimentosPermitidos", hasItem(configAtivo.getId().intValue())))
                .andExpect(jsonPath("$.procedimentosPermitidos", not(hasItem(configInativo.getId().intValue()))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComPerfilInterno(String perfilInterno) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", perfilInterno));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComEntidade(String nif, boolean pcm) {
        return jwtComEntidade(nif, pcm, "CM");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComEntidade(String nif, boolean pcm, String tipoEntidade) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", tipoEntidade, "nif", nif, "nome", "Entidade Teste", "pcm", pcm)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtComEntidadeSemTipo(String nif) {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("nif", nif, "nome", "Entidade Teste", "pcm", false)));
    }
}
