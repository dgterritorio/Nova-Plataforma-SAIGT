package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rascunho do formulário: todos os campos são opcionais, a obrigatoriedade só se verifica em `submeter`.
 * Os limites acompanham as colunas de `Processo`. 
 */
public record AtualizarDadosFormularioRequest(
        @NotNull Long versao,
        @Size(max = 2000) String sumario,
        LocalDate dataAssinatura,
        @Size(max = 500) String normaLegal,
        @Pattern(regexp = "TEXTO_INTEGRAL|EXTRATO", message = "Valores aceites: TEXTO_INTEGRAL, EXTRATO.") String tipoInformacao,
        Boolean publicarSuplemento,
        LocalDate dataSuplemento,
        @Email(message = "Indique um email válido.") @Size(max = 254) String contactoTramitacao,
        @Size(max = 500) String entidadeEmitente,
        @Size(max = 500) String contactoPublicacao,
        List<String> elementosAfetados,
        @Size(max = 20) List<@Size(max = 20) String> fundamentacaoRjigt,
        @Valid InformacaoBaseRequest informacaoBase,
        Boolean comGeoconform,
        Boolean introduzPlantaNova,
        @Valid LocalizacaoRequest localizacao,
        Boolean conformaHierarquiaSuperior,
        @Size(max = 100) List<@Valid MotivoAlteracaoRequest> motivosAlteracao,
        @Size(max = 500) String entidadeInstrutora,
        /** Só nos processos que admitem associação (BL-PROC-062); omitido = nulo, sem associação. */
        UUID dinamicaAssociadaId,
        @Valid DadosFormularioRequest dadosFormulario,
        /** Omitido = não muda o gravado (ao contrário dos outros campos). */
        @Pattern(regexp = "PUBLICACAO_DEPOSITO|SO_DEPOSITO",
                message = "Valores aceites: PUBLICACAO_DEPOSITO, SO_DEPOSITO.") String destino,
        /** Só no formulário da Ratificação; nos outros não se grava. */
        @Pattern(regexp = "PUBLICACAO|ALTERACAO", message = "Valores aceites: PUBLICACAO, ALTERACAO.") String modalidadeRatificacao,
        /** O tipo de ato do cabeçalho; omitido = não muda o gravado (ao contrário dos outros campos). */
        Long tipoAtoId,
        /** Só nos Procedimentos sem série configurada; omitido = não muda a gravada. */
        @Pattern(regexp = "PRIMEIRA|SEGUNDA_H|SEGUNDA_C", message = "Valores aceites: PRIMEIRA, SEGUNDA_H, SEGUNDA_C.") String serieDr,
        /** Só no formulário `SGIFR`: códigos das redes de defesa; omitido não mexe nas gravadas. */
        @Size(max = 8) List<@Size(max = 10) String> redesDefesa,
        /** Só no formulário `SGIFR`, na Delimitação: `codigo` de uma Região ativa; omitido = nulo. */
        @Size(max = 20) String regiao,
        /** Só no PSA do formulário `SGIFR`: `codigo` de uma CIM / AM ativa (da Região, na Delimitação); omitido = nulo. */
        @Size(max = 20) String cimAm,
        /** Só no formulário `REN`: id de um valor ativo de `MOTIVO_PROCEDIMENTO_REN`; omitido não mexe no gravado. */
        Long motivoProcedimentoId,
        /** Só nas Dinâmicas com `pede_diploma_corrigido`; omitido não mexe no gravado. */
        @Size(max = 500) String diplomaCorrigido,
        /** Só no formulário `REN`: NUTS II (`NUT2`) e concelho (`MUNICIPALITIES`) da réplica do SNIT. */
        @Size(max = 20) String regiaoCodigo,
        @Size(max = 20) String municipioCodigo
) {
    /** Sem os campos do formulário `REN`, que ficam nulos (não mexem no gravado). */
    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino, String modalidadeRatificacao,
                                           Long tipoAtoId, String serieDr, List<String> redesDefesa, String regiao, String cimAm) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, modalidadeRatificacao,
                tipoAtoId, serieDr, redesDefesa, regiao, cimAm, null, null, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino, String modalidadeRatificacao,
                                           Long tipoAtoId, String serieDr, List<String> redesDefesa) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, modalidadeRatificacao,
                tipoAtoId, serieDr, redesDefesa, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino, String modalidadeRatificacao,
                                           Long tipoAtoId, String serieDr) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, modalidadeRatificacao,
                tipoAtoId, serieDr, null, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino, String modalidadeRatificacao,
                                           Long tipoAtoId) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, modalidadeRatificacao,
                tipoAtoId, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino, String modalidadeRatificacao) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, modalidadeRatificacao, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario, String destino) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, destino, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId,
                                           DadosFormularioRequest dadosFormulario) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, dadosFormulario, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora, UUID dinamicaAssociadaId) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, dinamicaAssociadaId, null);
    }

    /** Nulo quando omitido; o `@Pattern` já recusou outro valor. */
    public DestinoProcesso destinoPedido() {
        return destino == null ? null : DestinoProcesso.valueOf(destino);
    }

    /** Nula quando o pedido não traz `dadosFormulario` ou a chave. */
    public String simultaneoComPmot() {
        return dadosFormulario == null ? null : dadosFormulario.simultaneoComPmot();
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao,
                                           String entidadeInstrutora) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, entidadeInstrutora, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova, LocalizacaoRequest localizacao,
                                           Boolean conformaHierarquiaSuperior, List<MotivoAlteracaoRequest> motivosAlteracao) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, localizacao, conformaHierarquiaSuperior,
                motivosAlteracao, null, null);
    }

    /** Pedido sem resposta ao GeoConform: a resposta já gravada na Dinâmica fica como está. */
    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, null, null, null, null, null, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, null, null, null, null, null, null);
    }

    public AtualizarDadosFormularioRequest(Long versao, String sumario, LocalDate dataAssinatura, String normaLegal,
                                           String tipoInformacao, Boolean publicarSuplemento, LocalDate dataSuplemento,
                                           String contactoTramitacao, String entidadeEmitente, String contactoPublicacao,
                                           List<String> elementosAfetados, List<String> fundamentacaoRjigt,
                                           InformacaoBaseRequest informacaoBase, Boolean comGeoconform,
                                           Boolean introduzPlantaNova) {
        this(versao, sumario, dataAssinatura, normaLegal, tipoInformacao, publicarSuplemento, dataSuplemento,
                contactoTramitacao, entidadeEmitente, contactoPublicacao, elementosAfetados, fundamentacaoRjigt,
                informacaoBase, comGeoconform, introduzPlantaNova, null, null, null, null, null);
    }
}
