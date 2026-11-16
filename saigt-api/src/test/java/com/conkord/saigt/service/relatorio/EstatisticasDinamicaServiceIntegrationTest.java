package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.EstatisticasDinamicaResponse;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
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
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class EstatisticasDinamicaServiceIntegrationTest {

    private static final AtomicInteger CONTADOR = new AtomicInteger(800);

    @Autowired
    private TestEntityManager entityManager;

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

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Test
    void numeroDeConsultasNaoDependeDoNumeroDeProcessos() {
        Entidade entidade = entidadeRepository.saveAndFlush(
                EntidadeTeste.nova("509900" + CONTADOR.incrementAndGet(), "Câmara Municipal de Teste"));
        Dinamica comUmProcesso = dinamicaComProcessos(entidade, 1);
        Dinamica comVinteProcessos = dinamicaComProcessos(entidade, 20);
        EstatisticasDinamicaService service = serviceDaEntidade(entidade);

        long consultasComUm = consultasAo(() -> service.obter(comUmProcesso.getId()));
        EstatisticasDinamicaResponse[] resposta = new EstatisticasDinamicaResponse[1];
        long consultasComVinte = consultasAo(() -> resposta[0] = service.obter(comVinteProcessos.getId()));

        assertThat(resposta[0].processos()).hasSize(20);
        assertThat(resposta[0].numeroRejeicoesDgt()).isEqualTo(20);
        assertThat(resposta[0].numeroCiclosResubmissao()).isEqualTo(20);
        assertThat(consultasComVinte).isEqualTo(consultasComUm);
    }

    private long consultasAo(Runnable chamada) {
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        chamada.run();
        return statistics.getPrepareStatementCount();
    }

    private Dinamica dinamicaComProcessos(Entidade entidade, int quantos) {
        int n = CONTADOR.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB" + n, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + n, "autor" + n + "@cm-teste.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/PDM/" + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração", autor));
        for (int i = 1; i <= quantos; i++) {
            TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                    new TipoProcedimento("PROC" + n + "-" + i, "Procedimento " + i));
            InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                    new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, i, "SEGUNDA_H"));
            Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
            auditoriaRepository.save(Auditoria.builder().tipoOcorrencia("PROCESSO")
                    .idOcorrencia(processo.getId().toString()).acao("REJEITAR")
                    .estadoAntes("EM_VALIDACAO_DGT").estadoDepois("REJEITADO_DGT").build());
            idusProcessoRepository.saveAndFlush(IdusProcesso.builder().processo(processo)
                    .idus("01.0307.01." + n + "." + i).sistemaOrigem("SAIGT").idIgt("001234").idIgtDefinitivo(true)
                    .ordemDinamica("03").ordemDefinitiva(true).tentativa((short) 1).procedimento("002")
                    .submissao((short) 2).atualizadoEm(Instant.now()).build());
        }
        entityManager.flush();
        return dinamica;
    }

    private EstatisticasDinamicaService serviceDaEntidade(Entidade entidade) {
        ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(UUID.randomUUID(), "INCM", null, entidade.getNipc(), null, null, null, null, null,
                        TipoEntidade.CM, entidade.getId()));
        AutorizacaoService autorizacaoService = new AutorizacaoService(null, null, null, null, null);
        return new EstatisticasDinamicaService(dinamicaRepository, processoRepository, auditoriaRepository,
                idusProcessoRepository, autorizacaoService, contextoUtilizadorResolver);
    }
}
