package com.conkord.saigt.dto.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.RegraNomenclatura;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Sem `regrasTiff`, uma peça gráfica fica com `RegrasTiff.inicialGeral()`; sem `regraNomenclatura`, `GERAL`. */
public record CriarDocumentoConfigRequest(
        @NotNull Long instrumentoProcedimentoConfigId,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9_]+$", message = "Só maiúsculas, algarismos e '_'.") String codigo,
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) String separador,
        @Size(max = 300) String descricao,
        @NotNull CategoriaDocumento categoria,
        @NotNull TipoElemento tipoElemento,
        @NotNull FormatoDocumento formato,
        @NotEmpty List<@NotNull @Pattern(regexp = "^\\.?[A-Za-z0-9]{1,10}$", message = "Extensão inválida.") String> extensoesAceites,
        @NotNull Boolean obrigatorio,
        @NotNull @Min(1) Integer ordem,
        ElementoInformacaoBase elementoInformacaoBase,
        @Min(1) Long tamanhoMaximoFicheiro,
        @Min(1) Long tamanhoMaximoTotal,
        @Min(1) Integer numeroMaximoFicheiros,
        @NotNull Boolean linkPublicoPermitido,
        @NotNull Boolean ativo,
        RegrasTiff regrasTiff,
        RegraNomenclatura regraNomenclatura) {

    public AtualizarDocumentoConfigRequest comoAtualizacao() {
        return new AtualizarDocumentoConfigRequest(nome, separador, descricao, categoria, tipoElemento, formato,
                extensoesAceites, obrigatorio, ordem, elementoInformacaoBase, tamanhoMaximoFicheiro,
                tamanhoMaximoTotal, numeroMaximoFicheiros, linkPublicoPermitido, ativo, regrasTiff,
                regraNomenclatura);
    }
}
