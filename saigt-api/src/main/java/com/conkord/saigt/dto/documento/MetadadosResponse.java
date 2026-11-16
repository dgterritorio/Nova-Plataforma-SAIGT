package com.conkord.saigt.dto.documento;

/**
 * Ficha de metadados do processo: o responsável vem da entidade e o título da designação do instrumento (EF v1.5
 * §3.6), só de leitura; `dados` são os campos que a entidade preenche.
 */
public record MetadadosResponse(
        boolean exigeMetadados,
        Responsavel responsavel,
        String titulo,
        MetadadosProcesso dados
) {

    public record Responsavel(String nomeOrganizacao, String funcao, String correioEletronico, String enderecoUrl,
                              String rua, String localidade, String codigoPostal, String pais, String telefone,
                              String fax) {
    }
}
