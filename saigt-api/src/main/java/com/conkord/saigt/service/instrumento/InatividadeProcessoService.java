package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.conkord.saigt.service.documento.IntencaoArmazenamentoService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processos `EM_SUBMISSAO` parados: avisos aos dois primeiros prazos e anulação ao terceiro. Os avisos não gravam o
 * processo, porque qualquer gravação reinicia `atualizado_em` e com ela a contagem. A anulação não mexe na Dinâmica
 * nem apaga ficheiros: regista as intenções de apagamento, que a limpeza noturna executa.
 */
@Service
@EnableConfigurationProperties(InatividadeProcessoProperties.class)
public class InatividadeProcessoService {

    private static final ZoneId ZONA_LISBOA = ZoneId.of("Europe/Lisbon");
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_ANULACAO = "ANULACAO_INATIVIDADE";

    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final IntencaoArmazenamentoService intencaoArmazenamentoService;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher publicador;
    private final InatividadeProcessoProperties prazos;

    public InatividadeProcessoService(ProcessoRepository processoRepository,
                                      ProcessoDocumentoRepository processoDocumentoRepository,
                                      IntencaoArmazenamentoService intencaoArmazenamentoService,
                                      AuditoriaService auditoriaService, ApplicationEventPublisher publicador,
                                      InatividadeProcessoProperties prazos) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.intencaoArmazenamentoService = intencaoArmazenamentoService;
        this.auditoriaService = auditoriaService;
        this.publicador = publicador;
        this.prazos = prazos;
    }

    public Instant limiteDaConsulta(Instant agora) {
        ZonedDateTime limite = agora.atZone(ZONA_LISBOA).minus(prazos.primeiroAviso());
        while (!agora.isBefore(limite.plusDays(1).plus(prazos.primeiroAviso()).toInstant())) {
            limite = limite.plusDays(1);
        }
        return limite.toInstant();
    }

    /**
     * Relê o processo: se entretanto mudou de estado ou foi gravado, avalia-se o que está na BD. Publica no máximo um
     * evento, o do prazo mais avançado: um processo que passou o prazo de anulação é anulado sem os avisos em falta.
     * Os eventos são publicados dentro da transação, para os listeners `AFTER_COMMIT` os receberem depois do commit.
     * Uma gravação concorrente faz falhar a anulação com {@code ObjectOptimisticLockingFailureException}, e a
     * reversão leva também as intenções de apagamento.
     */
    @Transactional
    public DesfechoInatividade avaliar(UUID processoId, Instant agora) {
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null || processo.getEstado() != EstadoProcesso.EM_SUBMISSAO) {
            return DesfechoInatividade.SEM_ACAO;
        }
        Instant atualizadoEm = processo.getAtualizadoEm();
        Instant momentoAnulacao = marco(atualizadoEm, prazos.anulacao());
        if (!agora.isBefore(momentoAnulacao)) {
            anular(processo, atualizadoEm);
            return DesfechoInatividade.ANULADO;
        }
        Instant momentoSegundo = marco(atualizadoEm, prazos.segundoAviso());
        if (!agora.isBefore(momentoSegundo)) {
            avisar(processoId, LimiarInatividade.SEGUNDO_AVISO, momentoSegundo, atualizadoEm, momentoAnulacao);
            return DesfechoInatividade.SEGUNDO_AVISO;
        }
        Instant momentoPrimeiro = marco(atualizadoEm, prazos.primeiroAviso());
        if (!agora.isBefore(momentoPrimeiro)) {
            avisar(processoId, LimiarInatividade.PRIMEIRO_AVISO, momentoPrimeiro, atualizadoEm, momentoAnulacao);
            return DesfechoInatividade.PRIMEIRO_AVISO;
        }
        return DesfechoInatividade.SEM_ACAO;
    }

    private void avisar(UUID processoId, LimiarInatividade limiar, Instant momentoLimiar, Instant atualizadoEm,
                        Instant momentoAnulacao) {
        publicador.publishEvent(new AvisoInatividadeProcessoEvento(processoId, limiar, momentoLimiar, atualizadoEm,
                momentoAnulacao.atZone(ZONA_LISBOA).toLocalDate()));
    }

    private void anular(Processo processo, Instant atualizadoEm) {
        EstadoProcesso estadoAntes = processo.getEstado();
        if (!estadoAntes.podeTransitarPara(EstadoProcesso.ANULADO)) {
            throw new IllegalStateException("Transição não permitida a partir de " + estadoAntes + ".");
        }
        processo.setEstado(EstadoProcesso.ANULADO);
        processoRepository.saveAndFlush(processo);

        List<ProcessoDocumento> documentos =
                processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId());
        documentos.forEach(documento -> intencaoArmazenamentoService.registar(documento.getCaminhoFicheiro(),
                OperacaoArmazenamento.APAGAMENTO));
        auditoriaService.registarSistema(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_ANULACAO,
                TransicaoEstado.de(estadoAntes, EstadoProcesso.ANULADO),
                Map.of("atualizadoEm", atualizadoEm, "ficheirosAApagar", documentos.size()));
        publicador.publishEvent(new ProcessoAnuladoPorInatividadeEvento(processo.getId()));
    }

    private static Instant marco(Instant atualizadoEm, Period prazo) {
        return atualizadoEm.atZone(ZONA_LISBOA).plus(prazo).toInstant();
    }
}
