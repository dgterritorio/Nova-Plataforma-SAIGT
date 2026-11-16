package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
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
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormularioFixoIntegrationTest {

    private static final String SEM_FORMULARIO = "Este procedimento ainda não tem formulário na aplicação.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

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
    private PermissaoAcaoRepository permissaoAcaoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    /** O `codigo` é único na BD partilhada pelos testes: um tipo de Procedimento do seed é reutilizado. */
    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Sem itens documentais e com os dados INCM completos: só o formulário pode travar a gravação ou a submissão. */
    private Cenario criarCenario(String sufixo, String codigoProcedimento) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("FORM-" + sufixo, "Instrumento " + sufixo, "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-FORM-" + sufixo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento(codigoProcedimento), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5093" + sufixo, "Câmara " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cm-form.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/FORM/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS"));
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDeOutraEntidade() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "nif", "509399999", "nome", "Outra Câmara", "pcm", false)));
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    /** O `PUT` substitui tudo (omitido = nulo): os elementos afetados vão sempre, para a submissão não os exigir. */
    private String corpo(Cenario cenario, String sumario) {
        return "{\"versao\":" + versao(cenario) + ",\"sumario\":\"" + sumario + "\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"elementosAfetados\":[\"PECAS_ESCRITAS\"],\"entidadeEmitente\":\"Camara Municipal de Teste\"}";
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(corpo(cenario, "Sumário alterado.")));
    }

    private ResultActions submeter(Cenario cenario, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwt).contentType("application/json")
                .content(corpo(cenario, "Resumo do ato.").replaceFirst("}$", "," + PedidosSubmissao.JSON_CONFIRMACOES + "}")));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    @ParameterizedTest
    @CsvSource({"00001,DECISAO_INICIO", "00002,CONSTITUICAO_CC", "00003,ALTERACAO_SIMPLIFICADA"})
    void deveDevolverOFormularioInformacaoGeralNoDetalheENaDisponibilidade(String sufixo, String codigo) throws Exception {
        Cenario cenario = criarCenario(sufixo, codigo);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("submeter")));

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.processo().getDinamica().getInstrumento().getId() + "/disponivel")
                        .with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tipoProcedimento").value(codigo))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].formulario").value("INFORMACAO_GERAL"));
    }

    @Test
    void deveDevolverOFormularioDaPublicacaoDaAlteracaoNoDetalheENaDisponibilidade() throws Exception {
        Cenario cenario = criarCenario("00008", "ALTERACAO");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("PUBLICACAO_ALTERACAO"))
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("submeter")));

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.processo().getDinamica().getInstrumento().getId() + "/disponivel")
                        .with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tipoProcedimento").value("ALTERACAO"))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].formulario").value("PUBLICACAO_ALTERACAO"));
    }

    @Test
    void deveDevolverOFormularioDaDiscussaoPublicaNoDetalheENaDisponibilidade() throws Exception {
        Cenario cenario = criarCenario("00009", "ABERTURA_DISCUSSAO_PUBLICA");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("DISCUSSAO_PUBLICA"))
                .andExpect(jsonPath("$.dataCaducidadeDinamica").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.acoesDisponiveis").value(hasItem("submeter")));

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.processo().getDinamica().getInstrumento().getId() + "/disponivel")
                        .with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].formulario").value("DISCUSSAO_PUBLICA"));
    }

    @Test
    void deveGravarESubmeterUmProcessoComFormulario() throws Exception {
        Cenario cenario = criarCenario("00004", "ALTERACAO");

        gravar(cenario, jwtDoAutor(cenario)).andExpect(status().isOk());
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveRecusarGravarESubmeterUmProcedimentoSemFormulario() throws Exception {
        Cenario cenario = criarCenario("00005", "SEM-FORMULARIO-00005");

        gravar(cenario, jwtDoAutor(cenario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value(SEM_FORMULARIO));
        submeter(cenario, jwtDoAutor(cenario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value(SEM_FORMULARIO));

        Processo gravado = processoRepository.findById(cenario.processo().getId()).orElseThrow();
        assertThat(gravado.getSumario()).isEqualTo("Resumo do ato.");
        assertThat(gravado.getEstado().name()).isEqualTo("EM_SUBMISSAO");
    }

    @Test
    void deveDevolverOFormularioNuloSemEdicaoNemSubmissaoNumProcedimentoSemFormulario() throws Exception {
        Cenario cenario = criarCenario("00006", "SEM-FORMULARIO-00006");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").isEmpty())
                .andExpect(jsonPath("$.editavel").value(false))
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.acoesDisponiveis").value(not(hasItem("submeter"))));
    }

    @Test
    void deveDevolver404AOutraEntidadeAntesDaRegraDoFormulario() throws Exception {
        Cenario cenario = criarCenario("00007", "SEM-FORMULARIO-00007");

        gravar(cenario, jwtDeOutraEntidade()).andExpect(status().isNotFound());
        submeter(cenario, jwtDeOutraEntidade()).andExpect(status().isNotFound());
    }
}
