package com.conkord.saigt.service.nucleo;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.ClaimsUtilizador;

import java.util.UUID;

/**
 * Lido sempre do JWT validado, nunca de parâmetros do pedido nem da BD.
 * <p>
 * `ator` não é claim: `ENTIDADE`, `PCM` (`entidade.pcm`), `DGT` (`MASTER`/`TECNICO`) ou nulo (`VISITANTE`).
 * `tipoEntidade` vem da claim `entidade.tipo_entidade` e decide a Camada 3 (`permissao_acao`); é nulo nos
 * internos e quando a claim falta ou tem um valor desconhecido. `entidadeId` vem da claim `entidade.id` e é a
 * única identidade da entidade nas decisões de visibilidade e na criação (o NIF não identifica a entidade); é
 * nulo nos internos e quando a claim falta.
 * `perfilInterno` fica à parte porque nem a visibilidade transversal nem as ações só do `MASTER` coincidem
 * com os atores. Campos de
 * `entidade` são nulos para internos; `atosAutorizados` nulo distingue "sem claim" de "listas vazias".
 * `origem` é só informativo para o FE, nunca decide autorização.
 * <p>
 * `entidadeIncmEnrollmentId`/`entidadeDrParteId` servem só para congelar no {@code Processo} ao submeter,
 * porque quem invoca a INCM já não tem a sessão da entidade.
 */
public record ContextoUtilizador(UUID utilizadorId, String origem, PerfilInterno perfilInterno, String entidadeNif,
                                  String ator, ClaimsUtilizador.AtosAutorizados atosAutorizados,
                                  String entidadeDrParteLetra, String entidadeIncmEnrollmentId,
                                  Long entidadeDrParteId, TipoEntidade tipoEntidade, Long entidadeId) {

    private static final String ATOR_PCM = "PCM";

    /** Perfis internos e sessões de uma entidade de tipo `DGT` veem e agem em qualquer processo; a PCM não. */
    public boolean temVisibilidadeTransversal() {
        return perfilInterno != null || entidadeDgt();
    }

    public boolean entidadeDgt() {
        return perfilInterno == null && tipoEntidade == TipoEntidade.DGT;
    }

    public boolean pcm() {
        return ATOR_PCM.equals(ator);
    }
}
