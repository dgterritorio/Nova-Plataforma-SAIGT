package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Região e CIM / AM do formulário SGIFR: quando se pedem, as opções no detalhe e as regras do `PUT`. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormularioSgifrTerritorioIntegrationTest {

    private static final String CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(20000);

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
    @Autowired
    private ListaValoresRepository listaValoresRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    private TipoInstrumento tipoInstrumento(String codigo, String categoria) {
        return tipoInstrumentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigo, codigo, categoria)));
    }

    private TipoDinamica tipoDinamica(String codigo) {
        return tipoDinamicaRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigo, codigo)));
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    private InstrumentoDinamicaConfig dinamicaConfig(TipoInstrumento tipoInstrumento, TipoDinamica tipoDinamica) {
        return dinamicaConfigRepository.findAll().stream()
                .filter(config -> config.getTipoInstrumento().getId().equals(tipoInstrumento.getId())
                        && config.getTipoDinamica().getId().equals(tipoDinamica.getId()))
                .findFirst()
                .orElseGet(() -> dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica)));
    }

    private InstrumentoProcedimentoConfig procedimentoConfig(InstrumentoDinamicaConfig dinamicaConfig, String codigoProcedimento) {
        TipoProcedimento tipoProcedimento = tipoProcedimento(codigoProcedimento);
        return procedimentoConfigRepository.findAll().stream()
                .filter(config -> config.getInstrumentoDinamicaConfig().getId().equals(dinamicaConfig.getId())
                        && config.getTipoProcedimento().getId().equals(tipoProcedimento.getId()))
                .findFirst()
                .orElseGet(() -> {
                    InstrumentoProcedimentoConfig novo = procedimentoConfigRepository.saveAndFlush(
                            new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
                    permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, novo));
                    return novo;
                });
    }

    private Cenario cenario(String programa, String categoria, String codigoDinamica, String codigoProcedimento) {
        String sufixo = String.valueOf(SEQUENCIA.incrementAndGet());
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfig(tipoInstrumento(programa, categoria), tipoDinamica(codigoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfig(dinamicaConfig, codigoProcedimento);
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5096" + sufixo, "Entidade " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@cim-sgifr.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(dinamicaConfig.getTipoInstrumento(), "1", "2026/SGIFR-T/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, codigoDinamica, autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setEntidadeEmitente("Entidade de Teste");
        processo.setEntidadeInstrutora("Entidade instrutora de teste");
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private Cenario psaDelimitacao() {
        return cenario("PSA", "PROGRAMA", "DELIMITACAO", "PUBLICACAO_SGIFR");
    }

    private Cenario psaAlteracao() {
        return cenario("PSA", "PROGRAMA", "ALTERACAO", "PUBLICACAO_SGIFR");
    }

    private Cenario praDelimitacao() {
        return cenario("PRA", "PROGRAMA", "DELIMITACAO", "PUBLICACAO_SGIFR");
    }

    private Cenario praAlteracao() {
        return cenario("PRA", "PROGRAMA", "ALTERACAO", "PUBLICACAO_SGIFR");
    }

    private static String codigo(String prefixo) {
        return prefixo + UUID.randomUUID().toString().substring(0, 8);
    }

    private ListaValores regiao(String descricao) {
        return listaValoresRepository.saveAndFlush(new ListaValores("REGIAO", codigo("R"), descricao, 1));
    }

    private ListaValores cimAm(String descricao, ListaValores regiao) {
        return listaValoresRepository.saveAndFlush(new ListaValores("CIM_AM", codigo("C"), descricao, 1, regiao));
    }

    private void desativar(ListaValores item) {
        item.setAtivo(false);
        listaValoresRepository.saveAndFlush(item);
    }

    private void desativarTodas() {
        listaValoresRepository.findByTipoInAndAtivoTrueOrderByOrdemAsc(java.util.List.of("REGIAO", "CIM_AM"))
                .forEach(this::desativar);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor comoMaster() {
        return jwt().jwt(builder -> builder.claim(SUB, UUID.randomUUID().toString())
                .claim("origem", "AD_DGT").claim("perfil_interno", "MASTER"));
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private String dadosFormulario(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getDadosFormulario();
    }

    private ResultActions gravar(Cenario cenario, String extras) throws Exception {
        return gravar(cenario, extras, jwtDe(cenario.autor(), cenario.entidade()));
    }

    private ResultActions gravar(Cenario cenario, String extras, RequestPostProcessor jwt) throws Exception {
        String corpo = "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Sumário.\",\"normaLegal\":\"Norma Legal X\","
                + "\"entidadeEmitente\":\"Entidade de Teste\",\"entidadeInstrutora\":\"Entidade instrutora de teste\""
                + (extras.isEmpty() ? "" : "," + extras) + "}";
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(corpo));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId())
                .with(jwtDe(cenario.autor(), cenario.entidade())));
    }

    private static String campos(String regiao, String cimAm) {
        StringBuilder corpo = new StringBuilder();
        if (regiao != null) {
            corpo.append("\"regiao\":\"").append(regiao).append('"');
        }
        if (cimAm != null) {
            corpo.append(corpo.isEmpty() ? "" : ",").append("\"cimAm\":\"").append(cimAm).append('"');
        }
        return corpo.toString();
    }

    @Test
    void praNaDelimitacaoPedeARegiaoENaoACimAm() throws Exception {
        ListaValores norte = regiao("Norte");
        cimAm("CIM do Alto Minho", norte);

        detalhe(praDelimitacao())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("SGIFR"))
                .andExpect(jsonPath("$.pedeRegiao").value(true))
                .andExpect(jsonPath("$.opcoesRegiao[*].codigo", hasItem(norte.getCodigo())))
                .andExpect(jsonPath("$.pedeCimAm").value(false))
                .andExpect(jsonPath("$.opcoesCimAm", hasSize(0)));
    }

    @Test
    void praNaAlteracaoNaoPedeNemARegiaoNemACimAm() throws Exception {
        cimAm("CIM do Cávado", regiao("Norte"));

        detalhe(praAlteracao())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeRegiao").value(false))
                .andExpect(jsonPath("$.opcoesRegiao", hasSize(0)))
                .andExpect(jsonPath("$.pedeCimAm").value(false))
                .andExpect(jsonPath("$.opcoesCimAm", hasSize(0)));
    }

    @Test
    void psaNaDelimitacaoPedeAsDuasEACimAmTrazARegiaoDeQueDepende() throws Exception {
        ListaValores centro = regiao("Centro");
        ListaValores coimbra = cimAm("CIM da Região de Coimbra", centro);
        ListaValores inativa = regiao("Região inativa");
        ListaValores semRegiaoAtiva = cimAm("CIM de uma Região inativa", inativa);
        desativar(inativa);

        detalhe(psaDelimitacao())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeRegiao").value(true))
                .andExpect(jsonPath("$.opcoesRegiao[*].codigo", not(hasItem(inativa.getCodigo()))))
                .andExpect(jsonPath("$.pedeCimAm").value(true))
                .andExpect(jsonPath("$.opcoesCimAm[?(@.codigo=='" + coimbra.getCodigo() + "')].regiao", contains(centro.getCodigo())))
                .andExpect(jsonPath("$.opcoesCimAm[*].codigo", not(hasItem(semRegiaoAtiva.getCodigo()))));
    }

    @Test
    void psaNaAlteracaoSoPedeACimAmComAListaCompleta() throws Exception {
        ListaValores inativa = regiao("Região inativa");
        ListaValores comRegiaoInativa = cimAm("AM de Lisboa", inativa);
        desativar(inativa);
        ListaValores outra = cimAm("CIM do Oeste", regiao("Centro"));

        detalhe(psaAlteracao())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeRegiao").value(false))
                .andExpect(jsonPath("$.opcoesRegiao", hasSize(0)))
                .andExpect(jsonPath("$.pedeCimAm").value(true))
                .andExpect(jsonPath("$.opcoesCimAm[*].codigo", hasItems(comRegiaoInativa.getCodigo(), outra.getCodigo())));
    }

    @Test
    void comAsListasVaziasNenhumCampoSePedeNemEObrigatorio() throws Exception {
        desativarTodas();
        Cenario cenario = psaDelimitacao();

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedeRegiao").value(false))
                .andExpect(jsonPath("$.opcoesRegiao", hasSize(0)))
                .andExpect(jsonPath("$.pedeCimAm").value(false))
                .andExpect(jsonPath("$.opcoesCimAm", hasSize(0)));
        gravar(cenario, "").andExpect(status().isOk());
    }

    @Test
    void gravaARegiaoEACimAmEODetalheDevolveOsValoresComADescricao() throws Exception {
        ListaValores norte = regiao("Norte");
        ListaValores altoMinho = cimAm("CIM do Alto Minho", norte);
        Cenario cenario = psaDelimitacao();

        gravar(cenario, campos(norte.getCodigo(), altoMinho.getCodigo())).andExpect(status().isOk());

        assertThat(dadosFormulario(cenario))
                .contains("\"regiao\":\"" + norte.getCodigo() + "\"")
                .contains("\"cimAm\":\"" + altoMinho.getCodigo() + "\"");
        detalhe(cenario)
                .andExpect(jsonPath("$.regiao").value(norte.getCodigo()))
                .andExpect(jsonPath("$.regiaoDescricao").value("Norte"))
                .andExpect(jsonPath("$.cimAm").value(altoMinho.getCodigo()))
                .andExpect(jsonPath("$.cimAmDescricao").value("CIM do Alto Minho"));
    }

    @Test
    void naAlteracaoDoPsaGravaACimAmSemRegiao() throws Exception {
        ListaValores oeste = cimAm("CIM do Oeste", regiao("Centro"));
        Cenario cenario = psaAlteracao();

        gravar(cenario, campos(null, oeste.getCodigo())).andExpect(status().isOk());

        detalhe(cenario).andExpect(jsonPath("$.cimAm").value(oeste.getCodigo()))
                .andExpect(jsonPath("$.regiao").value(nullValue()));
    }

    @Test
    void recusaUmValorQueNaoEstaNaListaOuEstaInativoSemMudarNada() throws Exception {
        ListaValores norte = regiao("Norte");
        ListaValores inativa = regiao("Inativa");
        desativar(inativa);
        Cenario cenario = psaDelimitacao();
        gravar(cenario, campos(norte.getCodigo(), null)).andExpect(status().isOk());
        String gravado = dadosFormulario(cenario);

        gravar(cenario, campos("NAOEXISTE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regiao"));
        gravar(cenario, campos(inativa.getCodigo(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("regiao"));
        gravar(cenario, campos(norte.getCodigo(), "NAOEXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("cimAm"));

        assertThat(dadosFormulario(cenario)).isEqualTo(gravado);
    }

    @Test
    void naDelimitacaoRecusaUmaCimAmDeOutraRegiao() throws Exception {
        ListaValores regiaoA = regiao("Norte");
        ListaValores regiaoB = regiao("Centro");
        ListaValores daRegiaoB = cimAm("CIM de Viseu Dão Lafões", regiaoB);
        Cenario cenario = psaDelimitacao();

        gravar(cenario, campos(regiaoA.getCodigo(), daRegiaoB.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("cimAm"))
                .andExpect(jsonPath("$.campos[0].erro").value("A CIM / AM não pertence à Região escolhida."));
        gravar(cenario, campos(null, daRegiaoB.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("cimAm"));

        assertThat(dadosFormulario(cenario)).isNull();
    }

    @Test
    void recusaACampoQueNaoSeAplica() throws Exception {
        ListaValores norte = regiao("Norte");
        ListaValores altoMinho = cimAm("CIM do Alto Minho", norte);

        gravar(praAlteracao(), campos(norte.getCodigo(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("regiao"));
        gravar(psaAlteracao(), campos(norte.getCodigo(), altoMinho.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].path").value("regiao"));
        gravar(praDelimitacao(), campos(norte.getCodigo(), altoMinho.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("cimAm"));
        Cenario outroFormulario = cenario("PDM-SGIFR-T", "PLANO", "ALTERACAO", "ALTERACAO");
        gravar(outroFormulario, campos(norte.getCodigo(), altoMinho.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[*].path", contains("regiao", "cimAm")));
        detalhe(outroFormulario)
                .andExpect(jsonPath("$.pedeRegiao").value(false))
                .andExpect(jsonPath("$.opcoesRegiao").value(nullValue()))
                .andExpect(jsonPath("$.opcoesCimAm").value(nullValue()));
    }

    @Test
    void quemNaoEOAutorRecebe403EOsValoresNaoMudam() throws Exception {
        ListaValores norte = regiao("Norte");
        Cenario cenario = praDelimitacao();
        Utilizador outro = utilizadorRepository.saveAndFlush(
                new Utilizador("Outra pessoa", "outra" + SEQUENCIA.incrementAndGet() + "@cim-sgifr.pt", "INCM"));

        gravar(cenario, campos(norte.getCodigo(), null), jwtDe(outro, cenario.entidade())).andExpect(status().isForbidden());

        assertThat(dadosFormulario(cenario)).isNull();
    }

    @Test
    void osValoresQueOMasterAcrescentaFicamDisponiveisNoFormularioSemNovoDeploy() throws Exception {
        Cenario cenario = psaDelimitacao();
        String codigoRegiao = codigo("R");
        String resposta = mockMvc.perform(post("/api/v1/lista-valores").with(comoMaster()).contentType("application/json")
                        .content("{\"tipo\":\"REGIAO\",\"codigo\":\"" + codigoRegiao + "\",\"descricao\":\"Alentejo\",\"ordem\":1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long regiaoId = com.jayway.jsonpath.JsonPath.parse(resposta).read("$.id", Long.class);
        String codigoCim = codigo("C");
        mockMvc.perform(post("/api/v1/lista-valores").with(comoMaster()).contentType("application/json")
                        .content("{\"tipo\":\"CIM_AM\",\"codigo\":\"" + codigoCim + "\",\"descricao\":\"CIM do Alentejo Central\","
                                + "\"ordem\":1,\"paiId\":" + regiaoId + "}"))
                .andExpect(status().isCreated());

        detalhe(cenario)
                .andExpect(jsonPath("$.opcoesRegiao[*].codigo", hasItem(codigoRegiao)))
                .andExpect(jsonPath("$.opcoesCimAm[?(@.codigo=='" + codigoCim + "')].regiao", contains(codigoRegiao)));
        gravar(cenario, campos(codigoRegiao, codigoCim)).andExpect(status().isOk());
    }
}
