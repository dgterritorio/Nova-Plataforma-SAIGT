package com.conkord.saigt.service.sessao;

import com.conkord.saigt.exception.SessaoInvalidaException;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Só este serviço consulta `sessao`: os endpoints de negócio validam o access token offline. */
@Service
public class SessaoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);
    private static final String TIPO_ATIVA = "ATIVA";

    private final SessaoRepository sessaoRepository;
    private final ObjectMapper objectMapper;

    public SessaoService(SessaoRepository sessaoRepository, ObjectMapper objectMapper) {
        this.sessaoRepository = sessaoRepository;
        this.objectMapper = objectMapper;
    }

    public void revogarPreAutenticacao(String jti) {
        Sessao sessao = sessaoRepository.findById(jti)
                .orElseThrow(() -> new SessaoInvalidaException("Token de pré-autenticação inválido."));
        sessao.setRevogadoEm(Instant.now());
        sessaoRepository.save(sessao);
    }

    /** `contextoJson` nulo é sessão de utilizador interno. */
    ClaimsUtilizador claimsDaSessao(Utilizador utilizador, String contextoJson) {
        if (contextoJson == null) {
            // AD_DGT nunca tem cargo: explícito, sem confiar em getCargo() nulo.
            return new ClaimsUtilizador(utilizador.getId(), utilizador.getNome(), utilizador.getEmail(),
                    utilizador.getOrigemAutenticacao(), utilizador.getPerfilInterno(), null, null);
        }

        ContextoSessaoAtiva contexto = fromJson(contextoJson, ContextoSessaoAtiva.class);
        // Sessão gravada antes de o contexto levar a entidade SAIGT: obriga a novo login.
        if (contexto.entidade() == null || contexto.entidade().id() == null) {
            throw new SessaoInvalidaException("Refresh token inválido.");
        }
        return new ClaimsUtilizador(utilizador.getId(), utilizador.getNome(), utilizador.getEmail(),
                utilizador.getOrigemAutenticacao(), null, contexto.entidade(), utilizador.getCargo());
    }

    private <T> T fromJson(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contexto de sessão inválido em BD.", e);
        }
    }

    /** Também usado pelo `MASTER` (alteração de perfil e logout forçado). Devolve quantas sessões revogou. */
    public int revogarSessoesAtivas(UUID utilizadorId) {
        List<Sessao> ativas = sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizadorId, TIPO_ATIVA);
        Instant agora = Instant.now();
        ativas.forEach(sessao -> {
            sessao.setRevogadoEm(agora);
            sessaoRepository.save(sessao);
        });
        if (!ativas.isEmpty()) {
            // A revogação tem de chegar à BD antes do INSERT da sessão nova (índice de sessão única).
            sessaoRepository.flush();
        }
        return ativas.size();
    }

    /** Hard delete de sessões fechadas ou expiradas, incluindo PRE_AUTENTICACAO abandonadas: `sessao` não é auditoria. */
    @Transactional
    public void purgar() {
        long removidas = sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(Instant.now());
        log.info("Purga de sessões: {} linha(s) removida(s).", removidas);
    }
}
