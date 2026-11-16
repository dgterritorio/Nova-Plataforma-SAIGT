package com.conkord.saigt.service.sessao;

import com.conkord.saigt.dto.sessao.PermissoesResponse;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Só orienta a UI: a autorização real é sempre do {@code AutorizacaoService}. */
@Service
public class SessaoPermissoesService {

    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AutorizacaoService autorizacaoService;

    public SessaoPermissoesService(PermissaoAcaoRepository permissaoAcaoRepository,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver,
                                    AutorizacaoService autorizacaoService) {
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.autorizacaoService = autorizacaoService;
    }

    /**
     * Os utilizadores internos e as sessões sem tipo de entidade dão `tipoEntidade` nulo e lista vazia: a
     * `permissao_acao` é por tipo de entidade. A PCM mantém o tipo e recebe lista vazia: não cria processos.
     */
    public PermissoesResponse obterPermissoes() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Optional<TipoEntidade> tipoEntidade = autorizacaoService.tipoEntidadeDaSessao(contexto);
        List<Long> procedimentosPermitidos = autorizacaoService.tipoEntidadeQueCria(contexto)
                .map(tipo -> permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(tipo).stream()
                        .map(permissaoAcao -> permissaoAcao.getInstrumentoProcedimentoConfig().getId())
                        .toList())
                .orElse(List.of());
        return new PermissoesResponse(contexto.origem(), contexto.ator(), contexto.perfilInterno(),
                tipoEntidade.orElse(null), procedimentosPermitidos);
    }
}
