package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ObservacoesVaziasException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacaoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Observações de um item documental sem ficheiro (API-DOC-008, FR-137). */
@Service
public class ObservacaoDocumentoService {

    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_GRAVAR_OBSERVACOES = "GRAVAR_OBSERVACOES_DOCUMENTO";
    private static final String ACAO_GRAVAR_OBSERVACOES_OUTROS = "GRAVAR_OBSERVACOES_OUTROS";
    private static final String MSG_VAZIAS = "Indique as observações («NA» se não aplicável).";

    private final ProcessoAlteravel processoAlteravel;
    private final ProcessoDocumentoObservacaoRepository observacaoRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AuditoriaService auditoriaService;

    public ObservacaoDocumentoService(ProcessoAlteravel processoAlteravel,
                                      ProcessoDocumentoObservacaoRepository observacaoRepository,
                                      ContextoUtilizadorResolver contextoUtilizadorResolver,
                                      AuditoriaService auditoriaService) {
        this.processoAlteravel = processoAlteravel;
        this.observacaoRepository = observacaoRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.auditoriaService = auditoriaService;
    }

    /** Cria ou substitui. A auditoria guarda só o tamanho: o texto pode ter dados pessoais. */
    @Transactional
    public ProcessoDocumentoObservacao gravar(UUID processoId, Long documentoConfigId, String observacoes) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obter(processoId, contexto);
        String texto = observacoes == null ? "" : observacoes.trim();
        if (texto.isEmpty()) {
            throw new ObservacoesVaziasException("Documento inválido.",
                    List.of(new ValidacaoException.CampoErro("observacoes", MSG_VAZIAS)));
        }
        DocumentoConfig item = processoAlteravel.item(documentoConfigId, processo);

        ProcessoDocumentoObservacao observacao = observacaoRepository
                .findById(new ProcessoDocumentoObservacao.Id(processo.getId(), item.getId()))
                .orElse(null);
        if (observacao == null) {
            observacao = observacaoRepository.save(new ProcessoDocumentoObservacao(processo, item, texto));
        } else {
            observacao.setObservacoes(texto);
        }

        auditoriaService.registar(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_GRAVAR_OBSERVACOES,
                contexto.utilizadorId(), processo.getEntidade().getId(), null,
                Map.of("documentoConfigId", item.getId(), "caracteres", texto.length()));
        return observacao;
    }

    /** Observações da secção «Outros». Vazias apagam-nas: a secção não tem item que as exija. */
    @Transactional
    public String gravarOutros(UUID processoId, String observacoes) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obter(processoId, contexto);
        String texto = observacoes == null || observacoes.isBlank() ? null : observacoes.trim();
        processo.setObservacoesOutros(texto);
        auditoriaService.registar(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_GRAVAR_OBSERVACOES_OUTROS,
                contexto.utilizadorId(), processo.getEntidade().getId(), null,
                Map.of("caracteres", texto == null ? 0 : texto.length()));
        return texto;
    }
}
