package com.conkord.saigt.controller.snit;

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
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusDinamica;
import com.conkord.saigt.persistence.operacional.IdusDinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.service.pcgt.ComunicacaoPcgtService;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** `infoEntradaVigor` com a segurança ligada: só o cliente SNIT (scope `snit`) chega ao endpoint. */
@SpringBootTest(properties = "app.security.auth-disabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntradaVigorSnitControllerIntegrationTest {

    private static final String CAMINHO = "/api/v1/integracoes/snit/dinamicas/vigencia";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ProcessoRepository processoRepository;
    @Autowired private IdusProcessoRepository idusProcessoRepository;
    @Autowired private IdusProcessoHistoricoRepository historicoRepository;
    @Autowired private IdusDinamicaRepository idusDinamicaRepository;
    @Autowired private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired private EntidadeRepository entidadeRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private InstrumentoRepository instrumentoRepository;
    @Autowired private DinamicaRepository dinamicaRepository;
    @Autowired private PlatformTransactionManager transacoes;
    @MockBean private ClienteSnit clienteSnit;
    @MockBean private ComunicacaoPcgtService comunicacaoPcgt;

    private Processo decisaoInicio;
    private Processo publicacaoFinal;
    private String numeroDeposito;
    private String pubIncmFinal;
    private String pubIncmDecisao;
    private Entidade entidade;

    /** Elaboração de um PP com a Decisão de início e a Elaboração publicadas, ainda com ID_IGT e ordem provisórios. */
    @BeforeEach
    void dinamicaPublicadaComIdusProvisorio() {
        when(clienteSnit.atualizarIdusDeposito(anyLong(), anyString(), any()))
                .thenReturn(new ResultadoChamadaExterna<>(200, null, null, 1));
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento pp = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento("EV" + n, "Plano de Pormenor", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("EV-D" + n, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pp, elaboracao));
        InstrumentoProcedimentoConfig decisao = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("EV-P1-" + n, "Decisão")), 1,
                "SEGUNDA_H"));
        InstrumentoProcedimentoConfig final_ = procedimentoConfigRepository.saveAndFlush(new InstrumentoProcedimentoConfig(
                dinamicaConfig, tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("EV-P2-" + n, "Elaboração")), 2,
                "SEGUNDA_H"));
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5072%05d".formatted(n), "Câmara EV " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor EV " + n, "ev" + n + "@cm-ev.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pp, String.valueOf(n), "2026/EV/" + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Elaboração do PP", autor));

        decisaoInicio = processo(dinamica, decisao, autor, null);
        numeroDeposito = "02.09.11/PP/01/2026/" + (100 + n);
        publicacaoFinal = processo(dinamica, final_, autor, numeroDeposito);
        pubIncmFinal = "2026%04d".formatted(1000 + n);
        pubIncmDecisao = "2026%04d".formatted(5000 + n);
        idus(decisaoInicio, "029", pubIncmDecisao);
        idus(publicacaoFinal, "001", pubIncmFinal);
    }

    private Processo processo(Dinamica dinamica, InstrumentoProcedimentoConfig config, Utilizador autor, String deposito) {
        Processo processo = new Processo(dinamica, config, autor, entidade);
        processo.setEstado(EstadoProcesso.PUBLICADO);
        processo.setNumeroDeposito(deposito);
        return processoRepository.saveAndFlush(processo);
    }

    private void idus(Processo processo, String procedimento, String pubIncm) {
        String idIgt = "9" + "%05d".formatted(Integer.parseInt(decisaoInicio.getNumeroProcesso()) % 100000);
        new TransactionTemplate(transacoes).executeWithoutResult(estado -> idusProcessoRepository.save(IdusProcesso.builder()
                .processo(processoRepository.findById(processo.getId()).orElseThrow())
                .idus(String.join(".", "01", "0911", "06", idIgt, "01", "90112", "01", procedimento, "01", pubIncm))
                .sistemaOrigem("01").idIgt(idIgt).idIgtDefinitivo(false).ordemDinamica("90112").ordemDefinitiva(false)
                .tentativa((short) 1).procedimento(procedimento).submissao((short) 1).pubIncm(pubIncm)
                .atualizadoEm(Instant.now()).build()));
    }

    private ResultActions enviar(String corpo) throws Exception {
        return mockMvc.perform(post(CAMINHO).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .with(tokenDoSistema("snit")));
    }

    /** Token client_credentials do Authorization Server: o claim `sistema` é o id do cliente OAuth. */
    private static JwtRequestPostProcessor tokenDoSistema(String sistema) {
        return jwt().jwt(token -> token.claim("sistema", sistema));
    }

    private String pedido(String deposito, Object saigtId, long idIgt, int ordem) {
        return """
                {"numeroDeposito": "%s", "saigtId": %s, "idIGT": %d, "ordemDinamica": %d, "dataEntradaVigor": "2026-11-10"}
                """.formatted(deposito, saigtId, idIgt, ordem);
    }

    private String idusAtual(Processo processo) {
        return idusProcessoRepository.findById(processo.getId()).orElseThrow().getIdus();
    }

    @Test
    void deveTornarDefinitivosOIdIgtEAOrdemEmTodosOsProcessosEGerarOIdusDaDinamica() throws Exception {
        enviar(pedido(numeroDeposito, Long.parseLong(publicacaoFinal.getNumeroProcesso()), 1234, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDeposito").value(numeroDeposito))
                .andExpect(jsonPath("$.estado").value("RECEBIDO"));

        assertThat(idusAtual(decisaoInicio)).isEqualTo("01.0911.06.001234.01.00001.01.029.01." + pubIncmDecisao);
        assertThat(idusAtual(publicacaoFinal)).isEqualTo("01.0911.06.001234.01.00001.01.001.01." + pubIncmFinal);
        IdusProcesso doFinal = idusProcessoRepository.findById(publicacaoFinal.getId()).orElseThrow();
        assertThat(doFinal.isIdIgtDefinitivo()).isTrue();
        assertThat(doFinal.isOrdemDefinitiva()).isTrue();
        IdusDinamica daDinamica = idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId()).orElseThrow();
        assertThat(daDinamica.getIdus()).isEqualTo("01.0911.06.001234.01.00001." + pubIncmFinal);
        assertThat(daDinamica.getIdIgtSnit()).isEqualTo("1234");
        assertThat(daDinamica.getOrdemSnit()).isEqualTo("1");
        List<IdusProcessoHistorico> historico = historicoRepository.findAll().stream()
                .filter(h -> h.getProcessoId().equals(publicacaoFinal.getId())).toList();
        assertThat(historico).extracting(IdusProcessoHistorico::getSegmento)
                .containsExactly(SegmentoIdus.ID_IGT, SegmentoIdus.ORDEM_DINAMICA);
        assertThat(historico).allMatch(h -> h.getEvento() == EventoIdus.ENTRADA_EM_VIGOR);
        assertThat(historico.get(1).getIdusAnterior()).isEqualTo(historico.get(0).getIdusNovo());
        verify(clienteSnit).atualizarIdusDeposito(eq(Long.parseLong(publicacaoFinal.getNumeroProcesso())),
                eq("01.0911.06.001234.01.00001." + pubIncmFinal), any());
        verify(comunicacaoPcgt).comunicarPublicacao(decisaoInicio.getId());
        verify(comunicacaoPcgt).comunicarPublicacao(publicacaoFinal.getId());
    }

    @Test
    void deveResponder201EManterOsIdusDefinitivosQuandoAPropagacaoAoSnitFalha() throws Exception {
        when(clienteSnit.atualizarIdusDeposito(anyLong(), anyString(), any()))
                .thenReturn(new ResultadoChamadaExterna<>(503, null, FalhaTecnicaExterna.ERRO_HTTP, 1));

        enviar(pedido(numeroDeposito, "null", 1234, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECEBIDO"));

        assertThat(idusAtual(publicacaoFinal)).isEqualTo("01.0911.06.001234.01.00001.01.001.01." + pubIncmFinal);
        assertThat(idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId())).isPresent();
        verify(comunicacaoPcgt).comunicarPublicacao(publicacaoFinal.getId());
    }

    @Test
    void devePropagarSoDepoisDoCommitEResponder201QuandoAPcgtFalhaEmTodosOsProcessos() throws Exception {
        List<Boolean> transacaoAtiva = new ArrayList<>();
        List<String> idusVistoNaChamada = new ArrayList<>();
        when(clienteSnit.atualizarIdusDeposito(anyLong(), anyString(), any())).thenAnswer(chamada -> {
            transacaoAtiva.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new ResultadoChamadaExterna<>(200, null, null, 1);
        });
        doAnswer(chamada -> {
            transacaoAtiva.add(TransactionSynchronizationManager.isActualTransactionActive());
            idusVistoNaChamada.add(new TransactionTemplate(transacoes).execute(estado -> idusAtual(publicacaoFinal)));
            throw new IllegalStateException("falha simulada da PCGT");
        }).when(comunicacaoPcgt).comunicarPublicacao(any());

        enviar(pedido(numeroDeposito, Long.parseLong(publicacaoFinal.getNumeroProcesso()), 1234, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECEBIDO"));

        String definitivo = "01.0911.06.001234.01.00001.01.001.01." + pubIncmFinal;
        assertThat(transacaoAtiva).hasSize(3).containsOnly(false);
        assertThat(idusVistoNaChamada).containsOnly(definitivo);
        assertThat(idusAtual(publicacaoFinal)).isEqualTo(definitivo);
        assertThat(idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId())).isPresent();
        verify(clienteSnit).atualizarIdusDeposito(anyLong(), anyString(), any());
        verify(comunicacaoPcgt).comunicarPublicacao(decisaoInicio.getId());
        verify(comunicacaoPcgt).comunicarPublicacao(publicacaoFinal.getId());
    }

    @Test
    void devePorAOrdemEm4AlgarismosComOZeroAntes() throws Exception {
        enviar(pedido(numeroDeposito, "null", 77, 5)).andExpect(status().isCreated());

        assertThat(idusAtual(publicacaoFinal)).isEqualTo("01.0911.06.000077.01.00005.01.001.01." + pubIncmFinal);
    }

    @Test
    void deveResponderOMesmoSemReprocessarNaRepeticaoComOsMesmosDados() throws Exception {
        enviar(pedido(numeroDeposito, "null", 1234, 1)).andExpect(status().isCreated());
        long linhas = historicoRepository.count();

        enviar(pedido(numeroDeposito, "null", 1234, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECEBIDO"));

        assertThat(historicoRepository.count()).isEqualTo(linhas);
        verify(clienteSnit, times(1)).atualizarIdusDeposito(anyLong(), anyString(), any());
        verify(comunicacaoPcgt, times(1)).comunicarPublicacao(publicacaoFinal.getId());
    }

    @Test
    void deveResponder409NaRepeticaoComOutroIdIgtOuOutraOrdemSemMudarNada() throws Exception {
        enviar(pedido(numeroDeposito, "null", 1234, 1)).andExpect(status().isCreated());
        String antes = idusAtual(publicacaoFinal);

        enviar(pedido(numeroDeposito, "null", 1234, 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_EM_VIGOR_EM_CONFLITO"));
        enviar(pedido(numeroDeposito, "null", 999, 1)).andExpect(status().isConflict());
        verify(clienteSnit, times(1)).atualizarIdusDeposito(anyLong(), anyString(), any());

        assertThat(idusAtual(publicacaoFinal)).isEqualTo(antes);
    }

    @Test
    void deveResponder409SemMudarNadaQuandoAOrdemJaEDeOutraDinamicaDoMesmoIgt() throws Exception {
        String depositoDaPrimeira = numeroDeposito;
        Processo finalDaPrimeira = publicacaoFinal;
        String pubIncm = pubIncmFinal;
        dinamicaPublicadaComIdusProvisorio();
        new TransactionTemplate(transacoes).executeWithoutResult(estado -> {
            IdusProcesso idus = idusProcessoRepository.findById(publicacaoFinal.getId()).orElseThrow();
            idus.setPubIncm(pubIncm);
            idus.setIdus(idus.getIdus().substring(0, idus.getIdus().lastIndexOf('.') + 1) + pubIncm);
        });
        enviar(pedido(depositoDaPrimeira, "null", 4321, 3)).andExpect(status().isCreated());
        String antes = idusAtual(publicacaoFinal);

        enviar(pedido(numeroDeposito, "null", 4321, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTRADA_EM_VIGOR_EM_CONFLITO"));

        assertThat(idusAtual(publicacaoFinal)).isEqualTo(antes);
        assertThat(idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId())).isEmpty();
        assertThat(idusDinamicaRepository.findById(finalDaPrimeira.getDinamica().getId())).isPresent();
        verify(clienteSnit, times(1)).atualizarIdusDeposito(anyLong(), anyString(), any());
    }

    @Test
    void deveResponder404ComUmNumeroDeDepositoDesconhecido() throws Exception {
        enviar(pedido("99.99.99/PP/01/2026/1", "null", 1234, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PROCESSO_DESCONHECIDO"));
        verifyNoInteractions(clienteSnit, comunicacaoPcgt);
    }

    @Test
    void deveResponder400QuandoOSaigtIdNaoCorrespondeAoNumeroDeDeposito() throws Exception {
        enviar(pedido(numeroDeposito, Long.parseLong(decisaoInicio.getNumeroProcesso()), 1234, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
                .andExpect(jsonPath("$.campos[0].path").value("saigtId"));

        assertThat(idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId())).isEmpty();
    }

    @Test
    void deveResponder400ComOsCamposObrigatoriosEmFaltaOuForaDosAlgarismos() throws Exception {
        enviar("{\"saigtId\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
                .andExpect(jsonPath("$.campos.length()").value(3));
        enviar(pedido(numeroDeposito, "null", 100000, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("idIGT"));
        enviar(pedido(numeroDeposito, "null", 1234, 10000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("ordemDinamica"));
        enviar("{\"numeroDeposito\": \"x\", \"idIGT\": 1, \"ordemDinamica\": 1, \"dataEntradaVigor\": \"10/11/2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].path").value("dataEntradaVigor"));
        verifyNoInteractions(clienteSnit, comunicacaoPcgt);
    }

    @Test
    void deveRecusarSemTokenOuComUmTokenDeUtilizadorDaSaigt() throws Exception {
        mockMvc.perform(post(CAMINHO).contentType(MediaType.APPLICATION_JSON).content(pedido(numeroDeposito, "null", 1, 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(CAMINHO).contentType(MediaType.APPLICATION_JSON).content(pedido(numeroDeposito, "null", 1, 1))
                        .with(jwt().jwt(token -> token.claim("perfil_interno", "MASTER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CAMINHO).contentType(MediaType.APPLICATION_JSON).content(pedido(numeroDeposito, "null", 1, 1))
                        .with(tokenDoSistema("geoconform")))
                .andExpect(status().isForbidden());

        assertThat(idusDinamicaRepository.findById(publicacaoFinal.getDinamica().getId())).isEmpty();
        verifyNoInteractions(clienteSnit, comunicacaoPcgt);
    }
}
