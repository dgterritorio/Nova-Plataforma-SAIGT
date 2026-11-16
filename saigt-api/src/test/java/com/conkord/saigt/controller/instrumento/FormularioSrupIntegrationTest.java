package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.PedidosSubmissao;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoAtoTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Formulário das restantes servidões com a configuração criada no teste: nenhuma do seed usa `PUBLICACAO_SRUP`. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormularioSrupIntegrationTest {

    private static final String PUBLICACAO_SRUP = "PUBLICACAO_SRUP";
    private static final String CAMPO_NAO_APLICAVEL = "Campo não aplicável a este procedimento.";

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

    private record Cenario(Processo processo, Utilizador autor, Utilizador colega, Entidade entidade) {
    }

    /** O `codigo` é único na BD partilhada pelos testes. */
    private TipoProcedimento publicacaoSrup() {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(PUBLICACAO_SRUP))
                .findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento(PUBLICACAO_SRUP, "Publicação da servidão")));
    }

    /** Sem itens documentais nem elementos afetados gravados: só o formulário pode travar a gravação ou a submissão. */
    private Cenario criarCenario(String sufixo, String categoria) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("SRUP-" + sufixo, "Servidão " + sufixo, categoria));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("DEL-SRUP-" + sufixo, "Delimitação"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, publicacaoSrup(), 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.APA, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(
                EntidadeTeste.nova("5094" + sufixo, "Agência " + sufixo, TipoEntidade.APA));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + sufixo, "autora" + sufixo + "@srup.pt", "INCM"));
        Utilizador colega = utilizadorRepository.saveAndFlush(
                new Utilizador("Colega " + sufixo, "colega" + sufixo + "@srup.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/SRUP/" + sufixo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Delimitação", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setTipoAto(TipoAtoTeste.paraSubmeter(tipoAtoRepository));
        return new Cenario(processoRepository.saveAndFlush(processo), autor, colega, entidade);
    }

    private static RequestPostProcessor jwtDe(Utilizador utilizador, Entidade entidade) {
        return jwt().jwt(builder -> builder.claim(SUB, utilizador.getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "APA", "id", entidade.getId(), "nif", entidade.getNipc(),
                        "nome", entidade.getDescricaoIncm(), "pcm", false, "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"))));
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwtDe(cenario.autor(), cenario.entidade());
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private Processo lido(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow();
    }

    /** O cabeçalho comum completo, sem `elementosAfetados`. */
    private String cabecalho(Cenario cenario, String extra) {
        return "{\"versao\":" + versao(cenario) + ",\"sumario\":\"Delimitação da servidão.\",\"dataAssinatura\":\""
                + LocalDate.now() + "\",\"normaLegal\":\"Norma Legal X\",\"tipoInformacao\":\"TEXTO_INTEGRAL\","
                + "\"entidadeEmitente\":\"Agência de Teste\",\"contactoTramitacao\":\"tramitacao@srup.pt\","
                + "\"contactoPublicacao\":\"Divisão de Servidões\"" + extra + "}";
    }

    private ResultActions gravar(Cenario cenario, RequestPostProcessor jwt, String extra) throws Exception {
        return mockMvc.perform(put("/api/v1/processos/" + cenario.processo().getId() + "/dados-formulario")
                .with(jwt).contentType("application/json").content(cabecalho(cenario, extra)));
    }

    private ResultActions submeter(Cenario cenario) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/submeter")
                .with(jwtDoAutor(cenario)).contentType("application/json")
                .content(cabecalho(cenario, "," + PedidosSubmissao.JSON_CONFIRMACOES)));
    }

    @Test
    void deveDevolverOFormularioSrupNoDetalheENaDisponibilidadeSemPedirElementosAfetados() throws Exception {
        Cenario cenario = criarCenario("00001", "SRUP");

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("SRUP"))
                .andExpect(jsonPath("$.editavel").value(true))
                .andExpect(jsonPath("$.exigeElementosAfetados").value(false))
                .andExpect(jsonPath("$.destinoEditavel").value(true));

        mockMvc.perform(get("/api/v1/instrumentos/" + cenario.processo().getDinamica().getInstrumento().getId() + "/disponivel")
                        .with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].tipoProcedimento").value(PUBLICACAO_SRUP))
                .andExpect(jsonPath("$.dinamicasEmCurso[0].procedimentosDisponiveis[0].formulario").value("SRUP"));
    }

    /** O formulário, e não a categoria do tipo de Instrumento, é que dispensa os elementos afetados. */
    @ParameterizedTest
    @CsvSource({"00002,SRUP", "00003,PLANO"})
    void deveGravarESubmeterOCabecalhoComumSemElementosAfetadosEFicarEnviadoDgt(String sufixo, String categoria)
            throws Exception {
        Cenario cenario = criarCenario(sufixo, categoria);

        gravar(cenario, jwtDoAutor(cenario), "").andExpect(status().isOk());
        submeter(cenario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO_DGT"));

        Processo gravado = lido(cenario);
        assertThat(gravado.getEstado()).isEqualTo(EstadoProcesso.ENVIADO_DGT);
        assertThat(gravado.getElementosAfetados()).isNullOrEmpty();
        assertThat(gravado.getContactoTramitacao()).isEqualTo("tramitacao@srup.pt");
        assertThat(gravado.getEntidadeEmitente()).isEqualTo("Agência de Teste");
    }

    @Test
    void deveAceitarSoDepositoNoFormularioSrupEDevolveloNoDetalhe() throws Exception {
        Cenario cenario = criarCenario("00004", "SRUP");

        gravar(cenario, jwtDoAutor(cenario), ",\"destino\":\"SO_DEPOSITO\"").andExpect(status().isOk());

        assertThat(lido(cenario).getDestino()).isEqualTo(DestinoProcesso.SO_DEPOSITO);
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("SO_DEPOSITO"));
    }

    @Test
    void quemNaoEAutorRecebe403SemGravarNada() throws Exception {
        Cenario cenario = criarCenario("00005", "SRUP");

        gravar(cenario, jwtDe(cenario.colega(), cenario.entidade()), ",\"destino\":\"SO_DEPOSITO\"")
                .andExpect(status().isForbidden());

        Processo gravado = lido(cenario);
        assertThat(gravado.getSumario()).isNull();
        assertThat(gravado.getDestino()).isEqualTo(DestinoProcesso.PUBLICACAO_DEPOSITO);
    }

    @Test
    void deveRecusarAsRedesDeDefesaNoFormularioSrupSemGravarNada() throws Exception {
        Cenario cenario = criarCenario("00006", "SRUP");

        gravar(cenario, jwtDoAutor(cenario), ",\"redesDefesa\":[\"APPS\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.mensagem").value(CAMPO_NAO_APLICAVEL))
                .andExpect(jsonPath("$.campos[0].path").value("redesDefesa"))
                .andExpect(jsonPath("$.campos[0].erro").value(CAMPO_NAO_APLICAVEL));

        assertThat(lido(cenario).getSumario()).isNull();
    }

    /** Só uma lista com redes é que é recusada; vazia ou omitida, o SRUP grava e o detalhe não tem redes. */
    @Test
    void deveGravarOFormularioSrupComAsRedesDeDefesaVaziasEDevolveLasNulasNoDetalhe() throws Exception {
        Cenario cenario = criarCenario("00007", "SRUP");

        gravar(cenario, jwtDoAutor(cenario), ",\"redesDefesa\":[]").andExpect(status().isOk());

        assertThat(lido(cenario).getSumario()).isEqualTo("Delimitação da servidão.");
        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId()).with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario").value("SRUP"))
                .andExpect(jsonPath("$.redesDefesa").value(nullValue()))
                .andExpect(jsonPath("$.redesDefesaDisponiveis").value(nullValue()));
    }
}
