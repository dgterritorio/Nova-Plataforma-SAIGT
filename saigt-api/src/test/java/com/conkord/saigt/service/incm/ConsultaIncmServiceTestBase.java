package com.conkord.saigt.service.incm;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.IncmComunicacaoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixture e leitura comuns aos testes do {@link ConsultaIncmService}. */
@ActiveProfiles("test")
abstract class ConsultaIncmServiceTestBase {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    protected ProcessoRepository processoRepository;

    @Autowired
    protected IncmComunicacaoRepository comunicacaoRepository;

    @Autowired
    protected AuditoriaRepository auditoriaRepository;

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

    protected Processo criarProcesso(EstadoProcesso estado, String actIdentifier) {
        int n = SEQUENCIA.incrementAndGet();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("CINCM" + n, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("CINCM-D" + n, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("CINCM-P" + n, "Procedimento " + n));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("5071%05d".formatted(n), "Câmara " + n));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + n, "autor" + n + "@cm-incm.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, String.valueOf(n), "2026/CINCM/" + n, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));

        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setEstado(estado);
        processo.setIncmActIdentifier(actIdentifier);
        return processoRepository.saveAndFlush(processo);
    }

    protected Processo recarregar(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    protected Dinamica dinamica(Processo processo) {
        return dinamicaRepository.findById(processo.getDinamica().getId()).orElseThrow();
    }

    protected List<Auditoria> auditoriasDaDinamica(Processo processo) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("DINAMICA", processo.getDinamica().getId().toString());
    }

    protected List<IncmComunicacao> comunicacoes(Processo processo) {
        return comunicacaoRepository.findByProcessoIdOrderByEnviadoEmAsc(processo.getId());
    }

    protected List<Auditoria> auditorias(Processo processo) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processo.getId().toString());
    }

    // O jsonb do Postgres devolve o JSON com espaços após ":" e ",".
    protected static String semEspacos(String json) {
        return json.replaceAll("\\s", "");
    }

    protected static UUID idInexistente() {
        return UUID.randomUUID();
    }
}
