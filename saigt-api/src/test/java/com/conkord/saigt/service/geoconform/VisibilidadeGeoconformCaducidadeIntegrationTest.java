package com.conkord.saigt.service.geoconform;

import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoVisibilidadeGeoconform;
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
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.instrumento.CaducidadeDinamicaJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ciclo do job de caducidade com a BD: o pedido ao GeoConform sai depois do commit e não desfaz a caducidade. */
@SpringBootTest
@ActiveProfiles("test")
class VisibilidadeGeoconformCaducidadeIntegrationTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final ResultadoChamadaExterna<Void> OK = new ResultadoChamadaExterna<>(200, null, null, 5);

    @MockBean
    private ClienteGeoconform cliente;

    @Autowired
    private CaducidadeDinamicaJob job;

    @Autowired
    private VisibilidadeGeoconformService servico;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

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
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    private Instrumento instrumento;
    private InstrumentoDinamicaConfig dinamicaConfig;
    private InstrumentoProcedimentoConfig decisaoInicioConfig;
    private Utilizador autor;
    private Entidade entidade;
    private int n;

    @BeforeEach
    void setUp() {
        n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("VISGC" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("VISGC-D" + n, "Alteração"));
        dinamicaConfig = dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento decisaoInicio = tipoProcedimentoRepository.findAll().stream()
                .filter(t -> "DECISAO_INICIO".equals(t.getCodigo())).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(
                        new TipoProcedimento("DECISAO_INICIO", "Decisão de início")));
        decisaoInicioConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisaoInicio, 1, "SEGUNDA_H"));
        entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5073%05d".formatted(n), "Câmara " + n));
        autor = utilizadorRepository.saveAndFlush(new Utilizador("Autor " + n, "autor" + n + "@cm-visgc.pt", "INCM"));
        instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, String.valueOf(n), "2026/VISGC/" + n, autor, autor));
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("Europe/Lisbon"));
    }

    private Dinamica criarDinamica(Boolean comGeoconform, LocalDate dataCaducidade) {
        Dinamica dinamica = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM " + n, autor);
        dinamica.setEstado(EstadoDinamica.EM_CURSO);
        dinamica.setDataCaducidade(dataCaducidade);
        dinamica.setComGeoconform(comGeoconform);
        return dinamicaRepository.saveAndFlush(dinamica);
    }

    private Processo criarDecisaoInicio(Dinamica dinamica, int ocorrencia, EstadoProcesso estado, String idus) {
        Processo processo = new Processo(dinamica, decisaoInicioConfig, autor, entidade);
        processo.setOcorrencia(ocorrencia);
        processo.setEstado(estado);
        Processo gravado = processoRepository.saveAndFlush(processo);
        if (idus != null) {
            transacao.executeWithoutResult(e -> idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                    .processo(processoRepository.getReferenceById(gravado.getId())).idus(idus).sistemaOrigem("SAIGT")
                    .idIgt("001199").idIgtDefinitivo(true).ordemDinamica("02").ordemDefinitiva(true)
                    .tentativa((short) 1).procedimento("031").submissao((short) 1).atualizadoEm(Instant.now()).build()));
        }
        return gravado;
    }

    private String estado(Dinamica dinamica) {
        return jdbcTemplate.queryForObject("SELECT estado FROM dinamica WHERE id = ?", String.class, dinamica.getId());
    }

    private static String idus(int n, int ocorrencia) {
        return "01.1199.01.9%05d.02.90001.01.031.0%d".formatted(n, ocorrencia);
    }

    @Test
    void deveRetirarDepoisDoCommitPeloIdusDaDecisaoDeInicioPublicadaDeOcorrenciaMaisAlta() {
        LocalDate dataCaducidade = hoje().minusDays(1);
        Dinamica dinamica = criarDinamica(true, dataCaducidade);
        criarDecisaoInicio(dinamica, 1, EstadoProcesso.PUBLICADO, idus(n, 1));
        Processo segunda = criarDecisaoInicio(dinamica, 2, EstadoProcesso.PUBLICADO, idus(n, 2));
        criarDecisaoInicio(dinamica, 3, EstadoProcesso.ANULADO, idus(n, 3));
        List<String> estadoNaChamada = new ArrayList<>();
        when(cliente.alterarVisibilidade(any(), any(), any())).thenAnswer(inv -> {
            estadoNaChamada.add(estado(dinamica));
            return OK;
        });

        job.executar();

        verify(cliente).alterarVisibilidade(eq(idus(n, 2)),
                eq(new PedidoVisibilidadeGeoconform(false, "caducidade", dataCaducidade)),
                eq(new ReferenciaChamada(segunda.getId(), idus(n, 2), null)));
        assertThat(estadoNaChamada).containsExactly("CADUCADA");
        assertThat(estado(dinamica)).isEqualTo("CADUCADA");
    }

    @Test
    void deveManterADinamicaCaducadaQuandoOGeoconformFalhaOuAChamadaRebenta() {
        Dinamica falha = criarDinamica(true, hoje().minusDays(2));
        criarDecisaoInicio(falha, 1, EstadoProcesso.PUBLICADO, idus(n, 1));
        Dinamica rebenta = criarDinamica(true, hoje().minusDays(2));
        criarDecisaoInicio(rebenta, 1, EstadoProcesso.PUBLICADO, idus(n, 2));
        when(cliente.alterarVisibilidade(eq(idus(n, 1)), any(), any()))
                .thenReturn(new ResultadoChamadaExterna<>(503, null, FalhaTecnicaExterna.ERRO_HTTP, 5));
        when(cliente.alterarVisibilidade(eq(idus(n, 2)), any(), any())).thenThrow(new IllegalStateException("teste"));

        job.executar();

        assertThat(estado(falha)).isEqualTo("CADUCADA");
        assertThat(estado(rebenta)).isEqualTo("CADUCADA");
        assertThat(dinamicaRepository.findById(falha.getId()).orElseThrow().getAlertasEnviados()).contains("DATA");
    }

    @Test
    void naoDeveEnviarPedidoNumaDinamicaSemGeoconformNemNumAvisoDeQuinzeDias() {
        Dinamica semGeoconform = criarDinamica(false, hoje().minusDays(1));
        criarDecisaoInicio(semGeoconform, 1, EstadoProcesso.PUBLICADO, idus(n, 1));
        Dinamica aviso = criarDinamica(true, hoje().plusDays(10));
        criarDecisaoInicio(aviso, 1, EstadoProcesso.PUBLICADO, idus(n, 2));

        job.executar();

        assertThat(estado(semGeoconform)).isEqualTo("CADUCADA");
        assertThat(estado(aviso)).isEqualTo("EM_CURSO");
        assertThat(dinamicaRepository.findById(aviso.getId()).orElseThrow().getAlertasEnviados())
                .containsExactly("UM_MES", "QUINZE_DIAS");
        verify(cliente, never()).alterarVisibilidade(any(), any(), any());
    }

    @Test
    void reporDeveEnviarVisivelTrueSemAlterarAEstadoDaDinamica() {
        Dinamica dinamica = criarDinamica(true, hoje().minusDays(5));
        dinamica.setEstado(EstadoDinamica.CADUCADA);
        dinamicaRepository.saveAndFlush(dinamica);
        Processo decisao = criarDecisaoInicio(dinamica, 1, EstadoProcesso.PUBLICADO, idus(n, 1));
        when(cliente.alterarVisibilidade(any(), any(), any())).thenReturn(OK);

        servico.repor(dinamica.getId());

        verify(cliente).alterarVisibilidade(eq(idus(n, 1)), eq(new PedidoVisibilidadeGeoconform(true, null, null)),
                eq(new ReferenciaChamada(decisao.getId(), idus(n, 1), null)));
        assertThat(estado(dinamica)).isEqualTo("CADUCADA");
    }
}
