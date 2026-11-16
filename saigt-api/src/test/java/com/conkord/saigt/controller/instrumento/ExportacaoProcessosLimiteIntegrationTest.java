package com.conkord.saigt.controller.instrumento;

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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contexto próprio só para baixar o limite; a base H2 é deste contexto. */
@SpringBootTest(properties = {"app.security.auth-disabled=false", "app.exportacao.limite-linhas=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportacaoProcessosLimiteIntegrationTest {

    private static final String URL = "/api/v1/processos/exportacao";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntidadeRepository entidadeRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired private InstrumentoRepository instrumentoRepository;
    @Autowired private DinamicaRepository dinamicaRepository;
    @Autowired private ProcessoRepository processoRepository;

    @Test
    void acimaDoLimiteRecusaSemFicheiroENoLimiteExporta() throws Exception {
        String tres = "Tres-" + UUID.randomUUID().toString().substring(0, 8);
        String dois = "Dois-" + UUID.randomUUID().toString().substring(0, 8);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("503399001", "Câmara 503399001"));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Utilizador Limite", UUID.randomUUID() + "@cm-teste.pt", "INCM"));
        for (int i = 0; i < 3; i++) {
            novoProcesso(tres, "L3-" + i, entidade, autor);
        }
        for (int i = 0; i < 2; i++) {
            novoProcesso(dois, "L2-" + i, entidade, autor);
        }

        for (String formato : new String[] {"EXCEL", "PDF"}) {
            mockMvc.perform(get(URL).param("formato", formato).param("designacaoInstrumento", tres).with(master()))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().doesNotExist("Content-Disposition"))
                    .andExpect(jsonPath("$.codigo").value("EXPORTACAO_LIMITE_EXCEDIDO"));
            mockMvc.perform(get(URL).param("formato", formato).param("designacaoInstrumento", dois).with(master()))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"));
        }
    }

    private void novoProcesso(String designacao, String codigo, Entidade entidade, Utilizador autor) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.findAll().stream()
                .filter(tipo -> "ELABORACAO".equals(tipo.getCodigo())).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração")));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, elaboracao));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento("P-" + codigo, "Procedimento " + codigo)), 1, "SEGUNDA_H"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", designacao, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração", autor));
        processoRepository.saveAndFlush(new Processo(dinamica, procedimento, autor, entidade));
    }

    private static RequestPostProcessor master() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "AD_DGT")
                .claim("perfil_interno", "MASTER"));
    }
}
