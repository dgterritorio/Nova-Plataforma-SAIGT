package com.conkord.saigt.dto.sessao;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;

import java.util.List;

/**
 * `ator` e `perfilInterno` separados: não há valor comum aos dois tipos de utilizador. `tipoEntidade` é nulo para
 * internos e para sessões sem tipo, que têm lista vazia.
 */
public record PermissoesResponse(String origem, String ator, PerfilInterno perfilInterno, TipoEntidade tipoEntidade,
                                 List<Long> procedimentosPermitidos) {
}
