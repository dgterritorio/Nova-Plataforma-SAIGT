package com.conkord.saigt.service.incm;

import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.IncmComunicacaoRepository;
import com.conkord.saigt.service.documento.LinkPublicoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Histórico permanente das chamadas à INCM.
 */
@Service
public class IncmComunicacaoService {

    private static final String MDC_TRACE_ID = "apiTraceId";
    private static final String SUBMIT_NEW_ACT = OperacaoIncm.SUBMIT_NEW_ACT.name();

    private final IncmComunicacaoRepository repository;
    private final ObjectMapper objectMapper;

    public IncmComunicacaoService(IncmComunicacaoRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Transação própria: a linha fica mesmo que o chamador faça rollback (ex.: envio com falha técnica). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IncmComunicacao registar(RegistoComunicacaoIncm registo) {
        return repository.save(IncmComunicacao.builder()
                .processoId(registo.processoId())
                .operacao(registo.operacao().name())
                .actIdentifier(registo.actIdentifier())
                .submissao((short) registo.submissao())
                .serieDr(registo.serieDr())
                .pedido(LinkPublicoService.mascararTokens(toJson(registo.pedido())))
                .resposta(toJson(registo.resposta()))
                .httpStatus(registo.httpStatus())
                .isSuccess(registo.isSuccess())
                .processingStatus(registo.processingStatus())
                .actStatus(registo.actStatus())
                .falhaTecnica(nome(registo.falhaTecnica()))
                .transicaoDerivada(nome(registo.transicaoDerivada()))
                .correlationId(correlationId(registo.correlationId()))
                .enviadoEm(registo.enviadoEm())
                .recebidoEm(registo.recebidoEm())
                .build());
    }

    @Transactional(readOnly = true)
    public int submissaoParaNovoEnvio(UUID processoId) {
        return (int) repository.countByProcessoIdAndOperacao(processoId, SUBMIT_NEW_ACT) + 1;
    }

    @Transactional(readOnly = true)
    public int submissaoCorrente(UUID processoId) {
        return repository.findFirstByProcessoIdAndOperacaoOrderByEnviadoEmDesc(processoId, SUBMIT_NEW_ACT)
                .map(comunicacao -> (int) comunicacao.getSubmissao())
                .orElse(1);
    }

    private static String correlationId(String doRegisto) {
        if (doRegisto != null && !doRegisto.isBlank()) {
            return doRegisto;
        }
        String doPedido = MDC.get(MDC_TRACE_ID);
        return doPedido != null ? doPedido : UUID.randomUUID().toString();
    }

    private static String nome(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private String toJson(Object corpo) {
        if (corpo == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar o corpo da comunicação com a INCM.", e);
        }
    }
}
