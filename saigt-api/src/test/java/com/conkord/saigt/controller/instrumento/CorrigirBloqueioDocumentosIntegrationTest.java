package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bloqueio dos ficheiros já validados ao iniciar a correção, de ponta a ponta pelo `PATCH .../corrigir`. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrigirBloqueioDocumentosIntegrationTest {

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
    private ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private record Cenario(Processo processo, Utilizador autor, Entidade entidade) {
    }

    private Cenario criarCenario(String codigo, EstadoProcesso estado) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("BLQC-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("BLQC-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("BLQC-" + codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig procedimento = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, procedimento));

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("50920" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autora " + codigo, "autora" + codigo + "@cm-blqc.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/BLQC/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimento, autor, entidade);
        processo.setEstado(estado);
        return new Cenario(processoRepository.saveAndFlush(processo), autor, entidade);
    }

    private ProcessoDocumento documento(Cenario cenario, String nome, EstadoValidacao estado, boolean bloqueado) {
        ProcessoDocumento documento = new ProcessoDocumento(cenario.processo(), null, CategoriaDocumento.DEPOSITO, "pdf",
                nome, "processos/blqc/" + UUID.randomUUID() + "/" + nome, "a".repeat(64), "v1", 10L, cenario.autor());
        documento.setEstadoValidacao(estado);
        documento.setBloqueadoEdicao(bloqueado);
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    private static RequestPostProcessor jwtDoAutor(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", cenario.entidade().getDescricaoIncm(),
                        "pcm", false)));
    }

    private long versao(Cenario cenario) {
        return processoRepository.findById(cenario.processo().getId()).orElseThrow().getVersao();
    }

    private ResultActions transitar(Cenario cenario, String acao, long versao) throws Exception {
        return mockMvc.perform(patch("/api/v1/processos/" + cenario.processo().getId() + "/" + acao)
                .with(jwtDoAutor(cenario))
                .contentType("application/json")
                .content("{\"versao\":" + versao + "}"));
    }

    private ResultActions corrigir(Cenario cenario) throws Exception {
        return transitar(cenario, "corrigir", versao(cenario));
    }

    private boolean bloqueado(ProcessoDocumento documento) {
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow().isBloqueadoEdicao();
    }

    private Auditoria ultimaAuditoriaCorrigir(Cenario cenario) {
        List<Auditoria> registos = auditoriaRepository.findByTipoOcorrenciaAndIdOcorrenciaAndAcaoOrderByCriadoEmAscIdAsc(
                "PROCESSO", cenario.processo().getId().toString(), "CORRIGIR");
        assertThat(registos).isNotEmpty();
        return registos.get(registos.size() - 1);
    }

    @Test
    void deveBloquearSoOValidadoAoCorrigirDeRejeitadoDgtEAuditarSoONumero() throws Exception {
        Cenario cenario = criarCenario("001", EstadoProcesso.REJEITADO_DGT);
        ProcessoDocumento validado = documento(cenario, "regulamento-validado.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitado = documento(cenario, "planta-recusada.pdf", EstadoValidacao.REJEITADO, false);
        ProcessoDocumento porValidar = documento(cenario, "anexo-por-validar.pdf", EstadoValidacao.POR_VALIDAR, false);

        corrigir(cenario).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        assertThat(bloqueado(validado)).isTrue();
        assertThat(bloqueado(rejeitado)).isFalse();
        assertThat(bloqueado(porValidar)).isFalse();

        String dados = ultimaAuditoriaCorrigir(cenario).getDadosAlteracao();
        JsonNode json = objectMapper.readTree(dados);
        assertThat(json.get("documentosBloqueados").asLong()).isEqualTo(1L);
        assertThat(dados).doesNotContain("regulamento-validado", "planta-recusada", "anexo-por-validar", ".pdf");
    }

    @Test
    void deveBloquearTodosOsFicheirosAoCorrigirDeAnuladoIncm() throws Exception {
        Cenario cenario = criarCenario("002", EstadoProcesso.ANULADO_INCM);
        List<ProcessoDocumento> documentos = List.of(
                documento(cenario, "a.pdf", EstadoValidacao.VALIDADO, false),
                documento(cenario, "b.pdf", EstadoValidacao.REJEITADO, false),
                documento(cenario, "c.pdf", EstadoValidacao.POR_VALIDAR, false));

        corrigir(cenario).andExpect(status().isOk());

        assertThat(documentos).allSatisfy(d -> assertThat(bloqueado(d)).isTrue());
        assertThat(objectMapper.readTree(ultimaAuditoriaCorrigir(cenario).getDadosAlteracao())
                .get("documentosBloqueados").asLong()).isEqualTo(3L);
    }

    @Test
    void deveDesbloquearNaNovaCorrecaoOFicheiroBloqueadoNaRondaAnteriorQueEstaAgoraRejeitado() throws Exception {
        Cenario cenario = criarCenario("003", EstadoProcesso.REJEITADO_DGT);
        ProcessoDocumento daRondaAnterior = documento(cenario, "a.pdf", EstadoValidacao.REJEITADO, true);

        corrigir(cenario).andExpect(status().isOk());

        assertThat(bloqueado(daRondaAnterior)).isFalse();
    }

    @Test
    void naoDeveMexerNoBloqueioAoResgatarDeEnviadoDgt() throws Exception {
        Cenario cenario = criarCenario("004", EstadoProcesso.ENVIADO_DGT);
        ProcessoDocumento validadoDesbloqueado = documento(cenario, "a.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitadoBloqueado = documento(cenario, "b.pdf", EstadoValidacao.REJEITADO, true);

        transitar(cenario, "resgatar", versao(cenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_SUBMISSAO"));

        assertThat(bloqueado(validadoDesbloqueado)).isFalse();
        assertThat(bloqueado(rejeitadoBloqueado)).isTrue();
    }

    @Test
    void naoDeveMexerNoBloqueioQuandoCorrigirFalhaPorVersaoDesatualizada() throws Exception {
        Cenario cenario = criarCenario("005", EstadoProcesso.REJEITADO_DGT);
        ProcessoDocumento validado = documento(cenario, "a.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitado = documento(cenario, "b.pdf", EstadoValidacao.REJEITADO, true);

        transitar(cenario, "corrigir", versao(cenario) + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        assertThat(bloqueado(validado)).isFalse();
        assertThat(bloqueado(rejeitado)).isTrue();
        assertThat(processoRepository.findById(cenario.processo().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProcesso.REJEITADO_DGT);
    }

    @Test
    void deveDevolverOBloqueioNaListagemDeDocumentosDepoisDeCorrigir() throws Exception {
        Cenario cenario = criarCenario("006", EstadoProcesso.REJEITADO_DGT);
        documento(cenario, "a.pdf", EstadoValidacao.VALIDADO, false);
        documento(cenario, "b.pdf", EstadoValidacao.REJEITADO, false);

        corrigir(cenario).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/processos/" + cenario.processo().getId() + "/documentos").with(jwtDoAutor(cenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nomeFicheiro == 'a.pdf')].bloqueadoEdicao").value(true))
                .andExpect(jsonPath("$.content[?(@.nomeFicheiro == 'b.pdf')].bloqueadoEdicao").value(false));
    }
}
