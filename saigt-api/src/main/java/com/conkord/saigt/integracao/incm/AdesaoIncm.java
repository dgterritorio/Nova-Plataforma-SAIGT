package com.conkord.saigt.integracao.incm;

import java.util.List;

/**
 * Termo de adesão (`UserEnrollmentAgreementList`), espelho integral do que a INCM devolve.
 * Todos os campos, exceto o NIPC e o nome, refrescam {@code Entidade}; o token/contexto de sessão leva os campos da
 * adesão (nunca o responsável). A INCM não devolve indicador PCM. {@code corpo} é o JSON da adesão tal como a INCM o
 * devolve.
 */
public record AdesaoIncm(
        String nif,
        String nomeEntidade,
        Morada morada,
        String telefone,
        String email,
        String responsavelNome,
        String responsavelEmail,
        String responsavelCargo,
        String incmEnrollmentId,
        Long drParteId,
        String drParteLetra,
        String drParteDescricao,
        List<Long> atosAutorizadosSerie1,
        List<AtoAutorizado> atosAutorizadosSerie2,
        String corpo
) {

    /** Ausente (não um `Morada` com campos nulos) quando a INCM não devolve morada para a adesão. */
    public record Morada(String arteria, String numero, String piso, String codigoPostal,
                          String designacaoPostal, String localidade) {
    }

    /** `extrato` = `ActTypeCanPublishExtract`, só na 2.ª série. `nome` é só apresentação. */
    public record AtoAutorizado(Long id, String nome, boolean extrato) {
    }
}
