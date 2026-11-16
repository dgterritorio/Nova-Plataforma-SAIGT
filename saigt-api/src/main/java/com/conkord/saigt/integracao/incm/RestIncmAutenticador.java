package com.conkord.saigt.integracao.incm;

import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.integracao.incm.ValidateUserResponse.ActTypePayload;
import com.conkord.saigt.integracao.incm.ValidateUserResponse.AdesaoPayload;
import com.conkord.saigt.integracao.incm.ValidateUserResponse.PartPayload;
import com.conkord.saigt.integracao.incm.ValidateUserResponse.ResponsiblePayload;
import com.conkord.saigt.integracao.incm.ValidateUserResponse.UserInformationPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Credenciais inválidas não são erro HTTP: 200 com {@code IsSuccess: false} e {@code UserInformation: null}.
 */
@Component
public class RestIncmAutenticador implements IncmAutenticador {

    private static final int MOTIVO_MAXIMO = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClienteIncm clienteIncm;

    public RestIncmAutenticador(ClienteIncm clienteIncm) {
        this.clienteIncm = clienteIncm;
    }

    @Override
    public Optional<UtilizadorIncm> autenticar(String username, String senha) {
        return autenticarComMotivo(username, senha).utilizador();
    }

    @Override
    public ResultadoAutenticacaoIncm autenticarComMotivo(String username, String senha) {
        ResultadoChamadaIncm<ValidateUserResponse> resultado = clienteIncm.validarUtilizador(username, senha);
        if (resultado.temFalhaTecnica()) {
            throw new IncmIndisponivelException("Serviço da INCM temporariamente indisponível.");
        }
        ValidateUserResponse resposta = resultado.corpo();
        if (resposta == null || !resposta.isSuccess() || resposta.userInformation() == null) {
            return ResultadoAutenticacaoIncm.recusada(motivo(resposta));
        }
        return ResultadoAutenticacaoIncm.aceite(paraUtilizadorIncm(resposta.userInformation()));
    }

    /** Texto da INCM, limitado: vai para o `error_description` do cliente. */
    private static String motivo(ValidateUserResponse resposta) {
        if (resposta == null || resposta.errorMessage() == null || resposta.errorMessage().isBlank()) {
            return null;
        }
        String texto = resposta.errorMessage().strip();
        return texto.length() > MOTIVO_MAXIMO ? texto.substring(0, MOTIVO_MAXIMO) : texto;
    }

    private UtilizadorIncm paraUtilizadorIncm(UserInformationPayload info) {
        List<AdesaoIncm> adesoes = info.enrollmentAgreementList() == null ? List.of() :
                info.enrollmentAgreementList().stream().map(this::paraAdesao).toList();
        return new UtilizadorIncm(info.userName(), info.userEmailAddress(), info.userJobTitle(), adesoes);
    }

    private AdesaoIncm paraAdesao(AdesaoPayload adesao) {
        List<Long> serie1 = adesao.serie1() == null ? List.of() :
                adesao.serie1().stream().map(ActTypePayload::actTypeId).toList();
        List<AdesaoIncm.AtoAutorizado> serie2 = adesao.serie2() == null ? List.of() :
                adesao.serie2().stream()
                        .map(ato -> new AdesaoIncm.AtoAutorizado(ato.actTypeId(), ato.actTypeName(), ato.canPublishExtract()))
                        .toList();
        ResponsiblePayload responsavel = adesao.responsible();
        PartPayload parte = adesao.part();

        return new AdesaoIncm(adesao.entityTaxId(), adesao.entityName(), paraMorada(adesao), adesao.entityPhoneNumber(),
                adesao.entityEmailAddress(),
                responsavel == null ? null : responsavel.name(),
                responsavel == null ? null : responsavel.email(),
                responsavel == null ? null : responsavel.jobTitle(),
                adesao.enrollmentIdentifier(),
                parte == null ? null : parte.partId(),
                parte == null ? null : parte.partLetter(),
                parte == null ? null : parte.partDescription(),
                serie1, serie2, corpo(adesao));
    }

    private static String corpo(AdesaoPayload adesao) {
        try {
            return JSON.writeValueAsString(adesao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a adesão da INCM.", e);
        }
    }

    /** `PublishingEntityAddress` é 0..1 — os restantes campos de morada só existem quando este existe. */
    private AdesaoIncm.Morada paraMorada(AdesaoPayload adesao) {
        if (adesao.entityAddress() == null) {
            return null;
        }
        return new AdesaoIncm.Morada(adesao.entityAddress(), adesao.entityAddressNumber(), adesao.entityFloor(),
                adesao.entityPostalCode(), adesao.entityPostalDesignation(), adesao.entityLocality());
    }
}
