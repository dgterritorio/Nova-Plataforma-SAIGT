package com.conkord.saigt.integracao.geoconform;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * `POST /api/geoconform/v1/processos`, com a ficha de dados estatísticos na mesma chamada. Os nulos não vão no JSON.
 * Sem `componentes`: a decomposição da proposta separa distrito e município e não tem o Sistema do IDUS da EF.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoCriacaoProcessoGeoconform(
        String idus,
        String numprocSaigt,
        String descricaoIgt,
        String descricaoDinamica,
        EntidadeGeoconform entidade,
        Boolean criarFichaDadosEstatisticos
) {
}
