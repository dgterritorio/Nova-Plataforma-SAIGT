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

/**
 * O `PUT` não recebe `codigo` nem `instrumentoProcedimentoConfigId`: ficam fixos. Sem `regrasTiff`, as regras gravadas
 * mantêm-se (e saem se o item deixar de ser uma peça gráfica); sem `regraNomenclatura`, mantém-se a gravada.
 */
public record AtualizarDocumentoConfigRequest(
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
        RegraNomenclatura regraNomenclatura) {}
