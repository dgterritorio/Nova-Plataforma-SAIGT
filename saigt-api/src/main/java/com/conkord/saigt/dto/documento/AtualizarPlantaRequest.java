package com.conkord.saigt.dto.documento;

/**
 * Só os campos presentes mudam; uma string vazia grava nulo. O `linkJpeg` não está aqui: é gerado pelo sistema e
 * um valor enviado no corpo é ignorado.
 */
public record AtualizarPlantaRequest(String nomeRegulamento, String numeroFolha, String nomeTiff, String nomeZip,
                                     Boolean emVigor) {

    public boolean vazio() {
        return nomeRegulamento == null && numeroFolha == null && nomeTiff == null && nomeZip == null && emVigor == null;
    }
}
