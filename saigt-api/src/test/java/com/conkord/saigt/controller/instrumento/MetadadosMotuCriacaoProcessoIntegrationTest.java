package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
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
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.incm.EnvioIncmService;
import com.conkord.saigt.service.instrumento.ProcessoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetadadosMotuCriacaoProcessoIntegrationTest {

    private static final AtomicLong PROXIMO_TIPO_ATO_INCM = new AtomicLong(9_420_000L);

    @MockBean
    private EnvioIncmService envioIncmService;

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    private DocumentoConfigRepository documentoConfigRepository;

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
    private MetadadosMotuRepository metadadosMotuRepository;

    private record Cenario(Entidade entidade, Utilizador autor, Dinamica dinamica, InstrumentoProcedimentoConfig decisaoInicio,
                           InstrumentoProcedimentoConfig procedimentoFinal, TipoAto tipoAto) {
    }

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveCriarASerieSemConjuntosComOProcessoDoProcedimentoFinal() throws Exception {
        Cenario cenario = criarCenario("MOTU1", "501942001", true);

        UUID processoId = criar(cenario, cenario.procedimentoFinal());

        assertThat(metadadosMotuRepository.findByProcesso_Id(processoId)).singleElement().satisfies(serie -> {
            assertThat(serie.getTipo()).isEqualTo(TipoMetadadosMotu.SERIE);
            assertThat(serie.isCompleto()).isFalse();
            assertThat(serie.getProcessoDocumento()).isNull();
        });
    }

    @Test
    void naoDeveCriarFichaMotuComOProcessoDaDecisaoDeInicio() throws Exception {
        Cenario cenario = criarCenario("MOTU2", "501942002", true);

        UUID processoId = criar(cenario, cenario.decisaoInicio());

        assertThat(metadadosMotuRepository.findByProcesso_Id(processoId)).isEmpty();
    }

    @Test
    void naoDeveCriarFichaMotuNumProcedimentoFinalSemPecaGraficaDePublicacaoAtiva() throws Exception {
        Cenario cenario = criarCenario("MOTU3", "501942003", false);

        UUID processoId = criar(cenario, cenario.procedimentoFinal());

        assertThat(metadadosMotuRepository.findByProcesso_Id(processoId)).isEmpty();
    }

    @Test
    void naoDeveFicarASerieQuandoACriacaoDoProcessoReverte() {
        Cenario cenario = criarCenario("MOTU4", "501942004", true);
        autenticar(cenario);
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        UUID processoId = transacao.execute(estado -> {
            Processo processo = processoService.criar(cenario.dinamica().getId(),
                    new CriarProcessoRequest(cenario.procedimentoFinal().getId(), cenario.tipoAto().getId()));
            assertThat(metadadosMotuRepository.findByProcesso_IdAndTipo(processo.getId(), TipoMetadadosMotu.SERIE))
                    .isPresent();
            estado.setRollbackOnly();
            return processo.getId();
        });

        assertThat(processoRepository.findById(processoId)).isEmpty();
        assertThat(metadadosMotuRepository.findByProcesso_Id(processoId)).isEmpty();
    }

    private UUID criar(Cenario cenario, InstrumentoProcedimentoConfig config) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/dinamicas/" + cenario.dinamica().getId() + "/processos")
                        .with(jwtDe(cenario))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CriarProcessoRequest(config.getId(), cenario.tipoAto().getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resposta).get("id").asText());
    }

    private static Map<String, Object> entidadeDoToken(Cenario cenario) {
        return Map.of("tipo_entidade", "CM", "id", cenario.entidade().getId(), "nif", cenario.entidade().getNipc(), "nome", "Entidade Teste", "pcm", false,
                "incm_enrollment_id", "2015482",
                "dr_parte", Map.of("id", 1234567891234567L, "letra", "H", "descricao", "Parte H"));
    }

    private RequestPostProcessor jwtDe(Cenario cenario) {
        return jwt().jwt(builder -> builder.claim(SUB, cenario.autor().getId().toString()).claim("origem", "INCM")
                .claim("entidade", entidadeDoToken(cenario)));
    }

    private void autenticar(Cenario cenario) {
        Jwt token = Jwt.withTokenValue("teste").header("alg", "none")
                .subject(cenario.autor().getId().toString())
                .claim("origem", "INCM")
                .claim("entidade", entidadeDoToken(cenario))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
    }

    private Cenario criarCenario(String codigo, String nif, boolean pecaGraficaDePublicacaoAtiva) {
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ALT-" + codigo, "Alteração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));

        InstrumentoProcedimentoConfig decisaoInicio = procedimento(dinamicaConfig, "DEC-" + codigo, 1, false);
        InstrumentoProcedimentoConfig procedimentoFinal = procedimento(dinamicaConfig, "PUB-" + codigo, 2, true);
        documentoConfigRepository.saveAndFlush(item(decisaoInicio, "PLANTA_AREA_INTERVENCAO", CategoriaDocumento.PUBLICACAO));
        documentoConfigRepository.saveAndFlush(item(procedimentoFinal, "PLANTA_ORDENAMENTO_DEP", CategoriaDocumento.DEPOSITO));
        DocumentoConfig plantaDePublicacao = item(procedimentoFinal, "PLANTA_ORDENAMENTO_PUB", CategoriaDocumento.PUBLICACAO);
        plantaDePublicacao.setAtivo(pecaGraficaDePublicacaoAtiva);
        documentoConfigRepository.saveAndFlush(plantaDePublicacao);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova(nif, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor-" + codigo + "@cm-" + codigo + ".pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        TipoAto tipoAto = new TipoAto("ATO-" + codigo, "Ato " + codigo, 1);
        tipoAto.setIncmActTypeIdSerie2(PROXIMO_TIPO_ATO_INCM.incrementAndGet());
        tipoAto = tipoAtoRepository.saveAndFlush(tipoAto);

        return new Cenario(entidade, autor, dinamica, decisaoInicio, procedimentoFinal, tipoAto);
    }

    private InstrumentoProcedimentoConfig procedimento(InstrumentoDinamicaConfig dinamicaConfig, String codigo, int ordem,
                                                       boolean obrigatorio) {
        TipoProcedimento tipo = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, "Procedimento " + codigo));
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig, tipo, ordem, "SEGUNDA_H");
        config.setObrigatorio(obrigatorio);
        config = procedimentoConfigRepository.saveAndFlush(config);
        permissaoAcaoRepository.saveAndFlush(new PermissaoAcao(TipoEntidade.CM, config));
        return config;
    }

    private static DocumentoConfig item(InstrumentoProcedimentoConfig procedimento, String codigo,
                                        CategoriaDocumento categoria) {
        return new DocumentoConfig(procedimento, codigo, "Planta", "Planta", categoria, TipoElemento.PECA_GRAFICA,
                FormatoDocumento.NAO_EDITAVEL, "[\"zip\"]", 1);
    }
}
