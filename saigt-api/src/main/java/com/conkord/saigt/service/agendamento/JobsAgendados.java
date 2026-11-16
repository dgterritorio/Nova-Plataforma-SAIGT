package com.conkord.saigt.service.agendamento;

import com.conkord.saigt.service.documento.PurgaIpDescargaJob;
import com.conkord.saigt.service.documento.LimpezaArmazenamentoService;
import com.conkord.saigt.service.documento.RetencaoRegistoExternoService;
import com.conkord.saigt.service.incm.ConsultaIncmJob;
import com.conkord.saigt.service.instrumento.CaducidadeDinamicaJob;
import com.conkord.saigt.service.instrumento.InatividadeProcessoJob;
import com.conkord.saigt.service.sessao.SessaoService;
import com.conkord.saigt.service.snit.SincronizacaoReferenciaSnitJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Todos os jobs periódicos, cada um com nome único no {@link ExecutorJobs}.
 */
@Component
public class JobsAgendados {

    static final String PURGA_SESSOES = "purga-sessoes";
    static final String LIMPEZA_ARMAZENAMENTO = "limpeza-armazenamento";
    static final String PURGA_IP_DESCARGAS = "purga-ip-descargas";
    static final String CONSULTA_INCM = "consulta-incm";
    static final String SINCRONIZACAO_REFERENCIA_SNIT = "sincronizacao-referencia-snit";
    static final String CADUCIDADE_DINAMICAS = "caducidade-dinamicas";
    static final String INATIVIDADE_PROCESSOS = "inatividade-processos";
    static final String RETENCAO_REGISTO_EXTERNO = "retencao-registo-externo";

    private final ExecutorJobs executorJobs;
    private final SessaoService sessaoService;
    private final LimpezaArmazenamentoService limpezaArmazenamentoService;
    private final PurgaIpDescargaJob purgaIpDescargaJob;
    private final ConsultaIncmJob consultaIncmJob;
    private final SincronizacaoReferenciaSnitJob sincronizacaoReferenciaSnitJob;
    private final CaducidadeDinamicaJob caducidadeDinamicaJob;
    private final InatividadeProcessoJob inatividadeProcessoJob;
    private final RetencaoRegistoExternoService retencaoRegistoExternoService;
    private final Duration lockMaximoPurgaSessoes;
    private final Duration lockMaximoLimpezaArmazenamento;
    private final Duration lockMaximoPurgaIpDescargas;
    private final Duration lockMaximoConsultaIncm;
    private final Duration lockMaximoReferenciaSnit;
    private final Duration lockMaximoCaducidadeDinamicas;
    private final Duration lockMaximoInatividadeProcessos;
    private final Duration lockMaximoRetencaoRegistoExterno;

    public JobsAgendados(ExecutorJobs executorJobs,
                         SessaoService sessaoService,
                         LimpezaArmazenamentoService limpezaArmazenamentoService,
                         PurgaIpDescargaJob purgaIpDescargaJob,
                         ConsultaIncmJob consultaIncmJob,
                         SincronizacaoReferenciaSnitJob sincronizacaoReferenciaSnitJob,
                         CaducidadeDinamicaJob caducidadeDinamicaJob,
                         InatividadeProcessoJob inatividadeProcessoJob,
                         RetencaoRegistoExternoService retencaoRegistoExternoService,
                         @Value("${app.sessao.purga.lock-maximo}") Duration lockMaximoPurgaSessoes,
                         @Value("${app.documentos.limpeza.lock-maximo}") Duration lockMaximoLimpezaArmazenamento,
                         @Value("${app.documentos.descarga.purga-ip.lock-maximo}") Duration lockMaximoPurgaIpDescargas,
                         @Value("${app.incm.consulta.lock-maximo}") Duration lockMaximoConsultaIncm,
                         @Value("${app.snit.referencia.lock-maximo}") Duration lockMaximoReferenciaSnit,
                         @Value("${app.dinamicas.caducidade.lock-maximo}") Duration lockMaximoCaducidadeDinamicas,
                         @Value("${app.processos.inatividade.lock-maximo}") Duration lockMaximoInatividadeProcessos,
                         @Value("${app.documentos.retencao.repeticao.lock-maximo}") Duration lockMaximoRetencaoRegistoExterno) {
        this.executorJobs = executorJobs;
        this.sessaoService = sessaoService;
        this.limpezaArmazenamentoService = limpezaArmazenamentoService;
        this.purgaIpDescargaJob = purgaIpDescargaJob;
        this.consultaIncmJob = consultaIncmJob;
        this.sincronizacaoReferenciaSnitJob = sincronizacaoReferenciaSnitJob;
        this.caducidadeDinamicaJob = caducidadeDinamicaJob;
        this.inatividadeProcessoJob = inatividadeProcessoJob;
        this.retencaoRegistoExternoService = retencaoRegistoExternoService;
        this.lockMaximoPurgaSessoes = lockMaximoPurgaSessoes;
        this.lockMaximoLimpezaArmazenamento = lockMaximoLimpezaArmazenamento;
        this.lockMaximoPurgaIpDescargas = lockMaximoPurgaIpDescargas;
        this.lockMaximoConsultaIncm = lockMaximoConsultaIncm;
        this.lockMaximoReferenciaSnit = lockMaximoReferenciaSnit;
        this.lockMaximoCaducidadeDinamicas = lockMaximoCaducidadeDinamicas;
        this.lockMaximoInatividadeProcessos = lockMaximoInatividadeProcessos;
        this.lockMaximoRetencaoRegistoExterno = lockMaximoRetencaoRegistoExterno;
    }

    @Scheduled(cron = "${app.sessao.purga.cron:0 0 3 * * *}")
    public void purgarSessoes() {
        executorJobs.executar(PURGA_SESSOES, lockMaximoPurgaSessoes, sessaoService::purgar);
    }

    @Scheduled(cron = "${app.documentos.limpeza.cron}")
    public void limparArmazenamento() {
        executorJobs.executar(LIMPEZA_ARMAZENAMENTO, lockMaximoLimpezaArmazenamento,
                () -> limpezaArmazenamentoService.limpar(Instant.now()));
    }

    @Scheduled(cron = "${app.documentos.descarga.purga-ip.cron}")
    public void purgarIpDescargas() {
        executorJobs.executar(PURGA_IP_DESCARGAS, lockMaximoPurgaIpDescargas, purgaIpDescargaJob::executar);
    }

    @Scheduled(cron = "${app.incm.consulta.cron}")
    public void consultarIncm() {
        executorJobs.executar(CONSULTA_INCM, lockMaximoConsultaIncm, consultaIncmJob::executar);
    }

    @Scheduled(cron = "${app.snit.referencia.cron}")
    public void sincronizarReferenciaSnit() {
        executorJobs.executar(SINCRONIZACAO_REFERENCIA_SNIT, lockMaximoReferenciaSnit,
                sincronizacaoReferenciaSnitJob::executar);
    }

    @Scheduled(cron = "${app.dinamicas.caducidade.cron}")
    public void verificarCaducidade() {
        executorJobs.executar(CADUCIDADE_DINAMICAS, lockMaximoCaducidadeDinamicas, caducidadeDinamicaJob::executar);
    }

    @Scheduled(cron = "${app.processos.inatividade.cron}")
    public void verificarInatividade() {
        executorJobs.executar(INATIVIDADE_PROCESSOS, lockMaximoInatividadeProcessos, inatividadeProcessoJob::executar);
    }

    @Scheduled(cron = "${app.documentos.retencao.repeticao.cron}")
    public void repetirRetencaoRegistoExterno() {
        executorJobs.executar(RETENCAO_REGISTO_EXTERNO, lockMaximoRetencaoRegistoExterno,
                retencaoRegistoExternoService::repetirPendentes);
    }
}
