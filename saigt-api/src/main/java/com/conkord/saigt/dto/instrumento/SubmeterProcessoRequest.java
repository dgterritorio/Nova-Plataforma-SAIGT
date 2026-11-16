package com.conkord.saigt.dto.instrumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Sem `@NotNull`: a obrigatoriedade depende de `serie_dr` e de `pergunta_geoconform` e é verificada em
 * {@code ProcessoService}.
 * Os limites acompanham as colunas de `Processo`. `confirmacoes` nulo vale como vazio.
 */
public record SubmeterProcessoRequest(
        @NotNull Long versao,
        @Size(max = 2000) String sumario,
        LocalDate dataAssinatura,
        @Size(max = 500) String normaLegal,
        @Pattern(regexp = "TEXTO_INTEGRAL|EXTRATO", message = "Valores aceites: TEXTO_INTEGRAL, EXTRATO.") String tipoInformacao,
        Boolean publicarSuplemento,
        LocalDate dataSuplemento,
        Boolean comGeoconform,
        @Size(max = 20) List<@NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z_]{1,50}$", message = "Chave de confirmação inválida.") String> confirmacoes,
        /** Só no formulário da Ratificação; omitida, vale a gravada no rascunho. */
        @Pattern(regexp = "PUBLICACAO|ALTERACAO", message = "Valores aceites: PUBLICACAO, ALTERACAO.") String modalidadeRatificacao,
        /** Só no formulário `REN`; omitido usa o gravado. */
        Long motivoProcedimentoId,
        @Size(max = 500) String diplomaCorrigido,
        @Size(max = 20) String regiaoCodigo,
        @Size(max = 20) String municipioCodigo
) {
    /** Sem os campos do formulário `REN`, que ficam nulos (vale o gravado). */
    public SubmeterProcessoRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                   String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                   Boolean comGeoconform, List<String> confirmacoes, String modalidadeRatificacao) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                comGeoconform, confirmacoes, modalidadeRatificacao, null, null, null, null);
    }

    public SubmeterProcessoRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                   String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                   Boolean comGeoconform, List<String> confirmacoes) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                comGeoconform, confirmacoes, null, null, null, null, null);
    }

    /** Pedido de um Procedimento que não pergunta pelo GeoConform. */
    public SubmeterProcessoRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                   String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento, null, null);
    }

    public SubmeterProcessoRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                   String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                   Boolean comGeoconform) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                comGeoconform, null);
    }
}
