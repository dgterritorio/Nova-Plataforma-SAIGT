package com.conkord.saigt.service.core;

import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Auditoria de ações de negócio, nunca de debug (isso é o RequestLoggingFilter).
 * Sem `utilizadorId` (ex.: login com credencial desconhecida) não grava nada; as ações do sistema passam por
 * {@link #registarSistema}.
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository repository;
    private final ObjectMapper objectMapper;

    public AuditoriaService(AuditoriaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Auditoria registar(String tipoOcorrencia, String idOcorrencia, String acao, UUID utilizadorId, Object dados) {
        return registar(tipoOcorrencia, idOcorrencia, acao, utilizadorId, null, dados);
    }

    /** Usada pelos fluxos de autenticação. */
    public Auditoria registar(String tipoOcorrencia, String idOcorrencia, String acao, UUID utilizadorId,
                               String ipOrigem, Object dados) {
        return registar(tipoOcorrencia, idOcorrencia, acao, utilizadorId, null, ipOrigem, dados);
    }

    /** {@code entidadeId}: entidade sob a qual a ação foi praticada; vazio para a DGT. */
    public Auditoria registar(String tipoOcorrencia, String idOcorrencia, String acao, UUID utilizadorId,
                               Long entidadeId, String ipOrigem, Object dados) {
        return gravar(Auditoria.builder()
                .tipoOcorrencia(tipoOcorrencia)
                .idOcorrencia(idOcorrencia)
                .acao(acao)
                .entidadeId(entidadeId)
                .ipOrigem(ipOrigem), utilizadorId, dados);
    }

    /** Ação do sistema, sem utilizador (ex.: jobs agendados): o único caminho que grava `utilizadorId` vazio. */
    public Auditoria registarSistema(String tipoOcorrencia, String idOcorrencia, String acao, Object dados) {
        return registarSistema(tipoOcorrencia, idOcorrencia, acao, new TransicaoEstado(null, null), dados);
    }

    /** Ação do sistema que muda o estado da ocorrência: grava `estado_antes` e `estado_depois`. */
    public Auditoria registarSistema(String tipoOcorrencia, String idOcorrencia, String acao, TransicaoEstado transicao,
                                     Object dados) {
        return repository.save(Auditoria.builder()
                .tipoOcorrencia(tipoOcorrencia)
                .idOcorrencia(idOcorrencia)
                .acao(acao)
                .estadoAntes(transicao.antes())
                .estadoDepois(transicao.depois())
                .dadosAlteracao(toJson(dados))
                .build());
    }

    public Auditoria registarTransicao(String tipoOcorrencia, String idOcorrencia, String acao, TransicaoEstado transicao,
                                       UUID utilizadorId, Long entidadeId, Object dados) {
        return gravar(Auditoria.builder()
                .tipoOcorrencia(tipoOcorrencia)
                .idOcorrencia(idOcorrencia)
                .acao(acao)
                .estadoAntes(transicao.antes())
                .estadoDepois(transicao.depois())
                .entidadeId(entidadeId), utilizadorId, dados);
    }

    private Auditoria gravar(Auditoria.AuditoriaBuilder auditoria, UUID utilizadorId, Object dados) {
        if (utilizadorId == null) {
            return null;
        }
        return repository.save(auditoria
                .utilizadorId(utilizadorId)
                .dadosAlteracao(toJson(dados))
                .build());
    }

    private String toJson(Object dados) {
        if (dados == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar 'dados' para auditoria.", e);
        }
    }

    /** Qualquer um pode ser nulo. */
    public record TransicaoEstado(String antes, String depois) {

        /** Grava o nome do estado; `null` mantém-se nulo (ex.: registo externo, sem estado anterior). */
        public static TransicaoEstado de(Enum<?> antes, Enum<?> depois) {
            return new TransicaoEstado(antes == null ? null : antes.name(), depois == null ? null : depois.name());
        }
    }
}
