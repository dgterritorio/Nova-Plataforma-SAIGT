package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
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
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class FormularioRenIntegrationTest {

    private static final String TIPO_MOTIVO = "MOTIVO_PROCEDIMENTO_REN";
    private static final String CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";
    private static final String REGIAO = "PTX7";
    private static final String OUTRA_REGIAO = "PTX1";
    private static final String CONCELHO = "9901";
    private static final String CONCELHO_DE_OUTRA_REGIAO = "9902";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
    private TipoAtoRepository tipoAtoRepository;
    @Autowired
    private ListaValoresRepository listaValoresRepository;
    @Autowired
    private SnitReferenciaRepository snitReferenciaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    /** Cada teste começa com a lista de motivos sem valores ativos: os que precisa, cria-os. */
    @BeforeEach
    void preparar() {
        listaValoresRepository.findByTipoOrderByOrdemAsc(TIPO_MOTIVO).forEach(valor -> {
            valor.setAtivo(false);
            listaValoresRepository.saveAndFlush(valor);
        });
        referenciaSeNaoExiste(ListaReferenciaSnit.NUT2, REGIAO, "{\"code\":\"" + REGIAO + "\"}");
        referenciaSeNaoExiste(ListaReferenciaSnit.NUT2, OUTRA_REGIAO, "{\"code\":\"" + OUTRA_REGIAO + "\"}");
        referenciaSeNaoExiste(ListaReferenciaSnit.MUNICIPALITIES, CONCELHO,
                "{\"dtcc\":\"" + CONCELHO + "\",\"nut2Code\":\"" + REGIAO + "\"}");
        referenciaSeNaoExiste(ListaReferenciaSnit.MUNICIPALITIES, CONCELHO_DE_OUTRA_REGIAO,
                "{\"dtcc\":\"" + CONCELHO_DE_OUTRA_REGIAO + "\",\"nut2Code\":\"" + OUTRA_REGIAO + "\"}");
    }

    private void referenciaSeNaoExiste(ListaReferenciaSnit lista, String codigo, String dados) {
        if (snitReferenciaRepository.findByListaAndCodigo(lista, codigo).isEmpty()) {
            snitReferenciaRepository.saveAndFlush(new SnitReferencia(lista, codigo, "Referência " + codigo, dados, Instant.now()));
        }
    }

    private ListaValores motivo(String codigo, int ordem, boolean ativo) {
        ListaValores valor = new ListaValores(TIPO_MOTIVO, codigo + "-" + UUID.randomUUID().toString().substring(0, 8),
                "Motivo " + codigo, ordem);
        valor.setAtivo(ativo);
        return listaValoresRepository.saveAndFlush(valor);
    }

    private TipoInstrumento tipoInstrumento(String codigo, String categoria) {
        return tipoInstrumentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigo, codigo, categoria)));
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Dados INCM completos e sem itens documentais: só o formulário pode travar a gravação ou a submissão. */
    private Cenario criarCenario(String sufixo, String tipo, String categoria, String codigoProcedimento,
                                 boolean pedeDiploma, String tipoInformacaoFixo) {
        TipoInstrumento tipoInstrumento = tipoInstrumento(tipo, categoria);
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("DIN-REN-" + sufixo, "Dinâmica " + sufixo));
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica);
        config.setPedeDiplomaCorrigido(pedeDiploma);
        config.setTipoInformacaoFixo(tipoInformacaoFixo);
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(config);
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento(codigoProcedimento), 1, "SEGUNDA_C"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5086" + sufixo, "Entidade " + sufixo));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autora " + sufixo, "autora" + sufixo + "@ren.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/REN/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Dinâmica " + sufixo, autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.now());
        processo.setNormaLegal("Norma Legal X");
        processo.setEntidadeEmitente("Entidade de Teste");
        // O tipo de ato escolhe-se no cabeçalho e é obrigatório para submeter (US-368).
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private Cenario alteracao(String sufixo) {
        return criarCenario(sufixo, "REN", "SRUP", "PUBLICACAO_REN", false, null);
    }

    private Cenario correcao(String sufixo) {
        return criarCenario(sufixo, "REN", "SRUP", "PUBLICACAO_REN", true, null);
    }

    private Cenario delimitacao(String sufixo) {
        return criarCenario(sufixo, "REN", "SRUP", "PUBLICACAO_REN", false, "TEXTO_INTEGRAL");
    }

    private Cenario pdm(String sufixo) {
        return criarCenario(sufixo, "PDM-REN", "PLANO", "DECISAO_INICIO", false, null);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "C", "descricao", "Parte C"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor(), cenario.entidade());
    }

    private Processo recarregar(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    private String corpo(Cenario cenario, String extras) {
        return "{\"versao\":" + recarregar(cenario).getVersao() + ",\"sumario\":\"Sumário alterado.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"entidadeEmitente\":\"Entidade de Teste\""
                + (extras.isEmpty() ? "" : "," + extras) + "}";
    }

    private ResultActions gravar(Cenario cenario, String extras) throws Exception {
        return gravar(cenario, extras, jwtDoAutor(cenario));
    }

    private ResultActions gravar(Cenario cenario, String extras, RequestPostProcessor jwt) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(corpo(cenario, extras)));
    }

    private ResultActions submeter(Cenario cenario, String extras) throws Exception {
        // A 2.ª série exige o tipo de informação na submissão; um teste que o envia não leva o de omissão.
        String tipoInformacao = extras.contains("tipoInformacao") ? "" : ",\"tipoInformacao\":\"TEXTO_INTEGRAL\"";
        String pedido = PedidosSubmissao.JSON_CONFIRMACOES + tipoInformacao + (extras.isEmpty() ? "" : "," + extras);
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario)).contentType("application/json").content(corpo(cenario, pedido)));
    }

    private ResultActions detalhe(Cenario cenario) throws Exception {
        return mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)));
    }

    private JsonNode dados(Cenario cenario) throws Exception {
        String dados = recarregar(cenario).getDadosFormulario();
        return dados == null ? objectMapper.createObjectNode() : objectMapper.readTree(dados);
    }

    @Test
    void deveGravarOMotivoAtivoJuntoDasChavesDaInformacaoGeral() throws Exception {
        Cenario cenario = alteracao("28601");
        ListaValores ativo = motivo("A", 1, true);

        gravar(cenario, "\"introduzPlantaNova\":true,\"motivoProcedimentoId\":" + ativo.getId()).andExpect(status().isOk());

        assertThat(dados(cenario).get("motivoProcedimentoId").asLong()).isEqualTo(ativo.getId());
        assertThat(dados(cenario).get("introduzPlantaNova").asBoolean()).isTrue();
    }

    @Test
    void deveManterOMotivoQuandoUmaGravacaoSeguinteOOmite() throws Exception {
        Cenario cenario = correcao("28602");
        ListaValores ativo = motivo("A", 1, true);
        gravar(cenario, "\"motivoProcedimentoId\":" + ativo.getId()).andExpect(status().isOk());

        gravar(cenario, "\"diplomaCorrigido\":\"Aviso n.º 1/2026\",\"regiaoCodigo\":\"" + REGIAO + "\"")
                .andExpect(status().isOk());

        JsonNode dados = dados(cenario);
        assertThat(dados.get("motivoProcedimentoId").asLong()).isEqualTo(ativo.getId());
        assertThat(dados.get("diplomaCorrigido").asText()).isEqualTo("Aviso n.º 1/2026");
        assertThat(dados.get("regiaoCodigo").asText()).isEqualTo(REGIAO);
    }

    @Test
    void deveRecusarUmMotivoInexistenteDeOutraListaOuDesativado() throws Exception {
        Cenario cenario = alteracao("28603");
        ListaValores desativado = motivo("D", 1, false);
        ListaValores deOutraLista = listaValoresRepository.saveAndFlush(
                new ListaValores("MOTIVO_ALTERACAO_LEI", "LEI-" + UUID.randomUUID().toString().substring(0, 8), "Lei", 1));

        for (Long id : new Long[]{999_999L, deOutraLista.getId(), desativado.getId()}) {
            gravar(cenario, "\"motivoProcedimentoId\":" + id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("motivoProcedimentoId"));
        }
        assertThat(dados(cenario).get("motivoProcedimentoId")).isNull();
    }

    @Test
    void deveRecusarASubmissaoSemMotivoComAListaPreenchida() throws Exception {
        Cenario cenario = alteracao("28604");
        motivo("A", 1, true);

        submeter(cenario, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value("Indique o motivo do procedimento."))
                .andExpect(jsonPath("$.campos[0].path").value("motivoProcedimentoId"));

        assertThat(recarregar(cenario).getEstado().name()).isEqualTo("EM_SUBMISSAO");
    }

    @Test
    void deveAceitarASubmissaoSemMotivoComAListaSemValoresAtivos() throws Exception {
        Cenario cenario = alteracao("28605");
        motivo("D", 1, false);

        submeter(cenario, "").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveAceitarASubmissaoComMotivoAtivo() throws Exception {
        Cenario cenario = alteracao("28606");
        ListaValores ativo = motivo("A", 1, true);
        gravar(cenario, "\"motivoProcedimentoId\":" + ativo.getId()).andExpect(status().isOk());

        submeter(cenario, "").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
    }

    @Test
    void deveAceitarOMotivoEnviadoNaPropriaSubmissao() throws Exception {
        Cenario cenario = alteracao("28607");
        ListaValores ativo = motivo("A", 1, true);

        submeter(cenario, "\"motivoProcedimentoId\":" + ativo.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));
        assertThat(dados(cenario).get("motivoProcedimentoId").asLong()).isEqualTo(ativo.getId());
    }

    @Test
    void deveRecusarOsCamposDaRenNumProcessoDePdm() throws Exception {
        Cenario cenario = pdm("28608");
        ListaValores ativo = motivo("A", 1, true);

        gravar(cenario, "\"motivoProcedimentoId\":" + ativo.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("motivoProcedimentoId"));
        gravar(cenario, "\"regiaoCodigo\":\"" + REGIAO + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("regiaoCodigo"));
        gravar(cenario, "\"municipioCodigo\":\"" + CONCELHO + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("municipioCodigo"));
    }

    @Test
    void deveGravarODiplomaSoNaCorrecaoMaterialERetificacao() throws Exception {
        Cenario correcao = correcao("28609");
        Cenario delimitacao = delimitacao("28610");
        Cenario alteracao = alteracao("28611");

        gravar(correcao, "\"diplomaCorrigido\":\"Aviso n.º 2/2026\"").andExpect(status().isOk());
        assertThat(dados(correcao).get("diplomaCorrigido").asText()).isEqualTo("Aviso n.º 2/2026");
        for (Cenario semDiploma : new Cenario[]{delimitacao, alteracao}) {
            gravar(semDiploma, "\"diplomaCorrigido\":\"Aviso n.º 2/2026\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].path").value("diplomaCorrigido"));
        }
    }

    @Test
    void deveImporOTextoIntegralNaDelimitacaoEAceitarOExtratoNaAlteracao() throws Exception {
        Cenario delimitacao = delimitacao("28612");
        Cenario alteracao = alteracao("28613");

        gravar(delimitacao, "\"tipoInformacao\":\"EXTRATO\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
        submeter(delimitacao, "\"tipoInformacao\":\"EXTRATO\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("tipoInformacao"));
        gravar(delimitacao, "").andExpect(status().isOk());
        assertThat(recarregar(delimitacao).getTipoInformacao()).isEqualTo("TEXTO_INTEGRAL");

        gravar(alteracao, "\"tipoInformacao\":\"EXTRATO\"").andExpect(status().isOk());
        assertThat(recarregar(alteracao).getTipoInformacao()).isEqualTo("EXTRATO");
    }

    @Test
    void deveGravarARegiaoEUmConcelhoDela() throws Exception {
        Cenario cenario = alteracao("28614");

        gravar(cenario, "\"regiaoCodigo\":\"" + REGIAO + "\",\"municipioCodigo\":\"" + CONCELHO + "\"").andExpect(status().isOk());

        assertThat(dados(cenario).get("regiaoCodigo").asText()).isEqualTo(REGIAO);
        assertThat(dados(cenario).get("municipioCodigo").asText()).isEqualTo(CONCELHO);
    }

    @Test
    void deveRecusarRegiaoInexistenteEConcelhoDeOutraRegiaoSemGravarNada() throws Exception {
        Cenario cenario = alteracao("28615");

        gravar(cenario, "\"regiaoCodigo\":\"PTZZ\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].path").value("regiaoCodigo"));
        gravar(cenario, "\"regiaoCodigo\":\"" + REGIAO + "\",\"municipioCodigo\":\"" + CONCELHO_DE_OUTRA_REGIAO + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("municipioCodigo"));

        assertThat(dados(cenario).get("regiaoCodigo")).isNull();
        assertThat(recarregar(cenario).getSumario()).isEqualTo("Resumo do ato.");
    }

    @Test
    void deveRecusarComForbiddenQuemNaoEOAutorSemMudarOMotivo() throws Exception {
        Cenario cenario = alteracao("28616");
        ListaValores ativo = motivo("A", 1, true);
        Utilizador outro = utilizadorRepository.saveAndFlush(new Utilizador("Outra pessoa", "outra28616@ren.pt", "INCM"));

        gravar(cenario, "\"motivoProcedimentoId\":" + ativo.getId(), jwtDe(outro, cenario.entidade()))
                .andExpect(status().isForbidden());

        assertThat(dados(cenario).get("motivoProcedimentoId")).isNull();
    }

    @Test
    void deveDevolverNoDetalheOFormularioRenComOsMotivosAtivosPorOrdem() throws Exception {
        Cenario cenario = correcao("28617");
        ListaValores segundo = motivo("B", 2, true);
        ListaValores primeiro = motivo("A", 1, true);
        motivo("D", 3, false);
        gravar(cenario, "\"motivoProcedimentoId\":" + segundo.getId() + ",\"diplomaCorrigido\":\"Aviso n.º 3/2026\","
                + "\"regiaoCodigo\":\"" + REGIAO + "\",\"municipioCodigo\":\"" + CONCELHO + "\"").andExpect(status().isOk());

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("REN"))
                .andExpect(jsonPath("$.motivoProcedimento.id").value(segundo.getId()))
                .andExpect(jsonPath("$.motivoProcedimento.descricao").value("Motivo B"))
                .andExpect(jsonPath("$.motivosProcedimentoDisponiveis[*].id")
                        .value(contains(primeiro.getId().intValue(), segundo.getId().intValue())))
                .andExpect(jsonPath("$.pedeDiplomaCorrigido").value(true))
                .andExpect(jsonPath("$.diplomaCorrigido").value("Aviso n.º 3/2026"))
                .andExpect(jsonPath("$.tipoInformacaoFixo").value(nullValue()))
                .andExpect(jsonPath("$.regiaoCodigo").value(REGIAO))
                .andExpect(jsonPath("$.municipioCodigo").value(CONCELHO));
    }

    @Test
    void deveDevolverNoDetalheAListaVaziaEOTipoFixoDaDelimitacao() throws Exception {
        Cenario cenario = delimitacao("28618");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoProcedimento").value(nullValue()))
                .andExpect(jsonPath("$.motivosProcedimentoDisponiveis", hasSize(0)))
                .andExpect(jsonPath("$.pedeDiplomaCorrigido").value(false))
                .andExpect(jsonPath("$.tipoInformacaoFixo").value("TEXTO_INTEGRAL"));
    }

    @Test
    void deveApontarNoDetalheAPendenciaDoMotivoComAListaPreenchida() throws Exception {
        Cenario cenario = alteracao("28619");
        motivo("A", 1, true);

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeSubmeter").value(false))
                .andExpect(jsonPath("$.pendenciasSubmissao[?(@.campo=='motivoProcedimentoId')]").isNotEmpty());
    }

    @Test
    void deveDevolverOsCamposDaRenNulosNumProcessoDePdm() throws Exception {
        Cenario cenario = pdm("28620");

        detalhe(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("INFORMACAO_GERAL"))
                .andExpect(jsonPath("$.motivoProcedimento").value(nullValue()))
                .andExpect(jsonPath("$.motivosProcedimentoDisponiveis").value(nullValue()))
                .andExpect(jsonPath("$.pedeDiplomaCorrigido").value(nullValue()))
                .andExpect(jsonPath("$.diplomaCorrigido").value(nullValue()))
                .andExpect(jsonPath("$.tipoInformacaoFixo").value(nullValue()))
                .andExpect(jsonPath("$.regiaoCodigo").value(nullValue()))
                .andExpect(jsonPath("$.municipioCodigo").value(nullValue()));
    }
}
