package com.conkord.saigt.service.entidade;

import com.conkord.saigt.dto.entidade.EntidadeEmitenteResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * A lista das entidades emitentes de um tipo, para os formulários que a pedem (Suspensão da CCDR: `CCDR_DR`).
 * Camadas 1–2: qualquer sessão com entidade; os perfis internos, sem entidade, não a leem. Só `id` e nome.
 */
@Service
public class EntidadeEmitenteService {

    private static final Logger log = LoggerFactory.getLogger(EntidadeEmitenteService.class);
    static final String CAMPO_TIPO_ENTIDADE = "tipoEntidade";
    static final String MSG_TIPO_INVALIDO = "Tipo de entidade inválido.";

    private final EntidadeRepository entidadeRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public EntidadeEmitenteService(EntidadeRepository entidadeRepository,
                                   ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.entidadeRepository = entidadeRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    /** 403 antes do 400, como na administração das entidades. */
    @Transactional(readOnly = true)
    public List<EntidadeEmitenteResponse> listar(String tipoEntidade) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.entidadeId() == null) {
            throw new AcaoNaoAutorizadaException("Só uma sessão com entidade lê a lista das entidades emitentes.");
        }
        TipoEntidade tipo = converter(tipoEntidade);
        List<EntidadeEmitenteResponse> entidades = entidadeRepository
                .findByTipoEntidadeAndAtivoTrueOrderByDescricaoSaigtAscIdAsc(tipo).stream()
                .map(EntidadeEmitenteResponse::de)
                .toList();
        log.debug("Entidades emitentes listadas | tipoEntidade={} total={}", tipo, entidades.size());
        return entidades;
    }

    private static TipoEntidade converter(String tipoEntidade) {
        return Arrays.stream(TipoEntidade.values())
                .filter(tipo -> tipo.name().equals(tipoEntidade))
                .findFirst()
                .orElseThrow(() -> new ValidacaoException(MSG_TIPO_INVALIDO,
                        List.of(new CampoErro(CAMPO_TIPO_ENTIDADE, MSG_TIPO_INVALIDO))));
    }
}
