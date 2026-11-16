package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InformacaoBaseRequestValidationTest {

    private static final String MENSAGEM_PRAZO = "O prazo tem de ser um número inteiro não negativo.";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        fabrica.close();
    }

    private Set<ConstraintViolation<InformacaoBaseRequest>> validar(InformacaoBaseRequest pedido) {
        return validador.validate(pedido);
    }

    @Test
    void deveAceitarUmaInformacaoBaseCompleta() {
        InformacaoBaseRequest pedido = new InformacaoBaseRequest(
                new InformacaoBaseDeliberacaoRequest(0, 6, 15, true, LocalDate.of(2026, 9, 1), "Texto."),
                new InformacaoBaseTextoAtoRequest(1, 0, 0, false));

        assertThat(validar(pedido)).isEmpty();
    }

    @Test
    void deveAceitarCamposENulosEElementosEmFalta() {
        assertThat(validar(new InformacaoBaseRequest(null, null))).isEmpty();
        assertThat(validar(new InformacaoBaseRequest(
                new InformacaoBaseDeliberacaoRequest(null, null, null, null, null, null),
                new InformacaoBaseTextoAtoRequest(null, null, null, null)))).isEmpty();
    }

    @Test
    void deveAceitarMesesEDiasAcimaDosLimitesDoCalendario() {
        InformacaoBaseRequest pedido = new InformacaoBaseRequest(
                new InformacaoBaseDeliberacaoRequest(null, 36, 400, null, null, null), null);

        assertThat(validar(pedido)).isEmpty();
    }

    @Test
    void deveRecusarPrazoNegativoNaDeliberacaoComOCaminhoDoCampo() {
        for (String campo : new String[]{"prazoAnos", "prazoMeses", "prazoDias"}) {
            InformacaoBaseDeliberacaoRequest deliberacao = new InformacaoBaseDeliberacaoRequest(
                    campo.equals("prazoAnos") ? -1 : null, campo.equals("prazoMeses") ? -1 : null,
                    campo.equals("prazoDias") ? -1 : null, null, null, null);

            Set<ConstraintViolation<InformacaoBaseRequest>> violacoes = validar(new InformacaoBaseRequest(deliberacao, null));

            assertThat(violacoes).hasSize(1).first().satisfies(violacao -> {
                assertThat(violacao.getPropertyPath()).hasToString("deliberacao." + campo);
                assertThat(violacao.getMessage()).isEqualTo(MENSAGEM_PRAZO);
            });
        }
    }

    @Test
    void deveRecusarPrazoNegativoNoTextoDoAtoComOCaminhoDoCampo() {
        for (String campo : new String[]{"prazoAnos", "prazoMeses", "prazoDias"}) {
            InformacaoBaseTextoAtoRequest textoAto = new InformacaoBaseTextoAtoRequest(
                    campo.equals("prazoAnos") ? -1 : null, campo.equals("prazoMeses") ? -1 : null,
                    campo.equals("prazoDias") ? -1 : null, null);

            Set<ConstraintViolation<InformacaoBaseRequest>> violacoes = validar(new InformacaoBaseRequest(null, textoAto));

            assertThat(violacoes).hasSize(1).first().satisfies(violacao -> {
                assertThat(violacao.getPropertyPath()).hasToString("textoAto." + campo);
                assertThat(violacao.getMessage()).isEqualTo(MENSAGEM_PRAZO);
            });
        }
    }

    @Test
    void deveRecusarTextoAPublicarComMaisDe2000Caracteres() {
        InformacaoBaseDeliberacaoRequest deliberacao = new InformacaoBaseDeliberacaoRequest(
                null, null, null, null, null, "x".repeat(2001));

        Set<ConstraintViolation<InformacaoBaseRequest>> violacoes = validar(new InformacaoBaseRequest(deliberacao, null));

        assertThat(violacoes).hasSize(1).first().satisfies(violacao -> {
            assertThat(violacao.getPropertyPath()).hasToString("deliberacao.textoPublicar");
            assertThat(violacao.getMessage()).isEqualTo("O texto a publicar excede 2000 caracteres.");
        });
    }

    @Test
    void deveAceitarTextoAPublicarComExatamente2000Caracteres() {
        InformacaoBaseDeliberacaoRequest deliberacao = new InformacaoBaseDeliberacaoRequest(
                null, null, null, null, null, "x".repeat(2000));

        assertThat(validar(new InformacaoBaseRequest(deliberacao, null))).isEmpty();
    }

    @Test
    void deveRecusarUmaDataDaDeliberacaoInvalidaAoLerOCorpo() {
        String corpo = "{\"deliberacao\":{\"dataDeliberacao\":\"2026-13-45\"}}";

        assertThatThrownBy(() -> objectMapper.readValue(corpo, InformacaoBaseRequest.class))
                .isInstanceOfSatisfying(JsonMappingException.class, erro -> assertThat(erro.getPath())
                        .extracting(referencia -> referencia.getFieldName())
                        .containsExactly("deliberacao", "dataDeliberacao"));
    }

    @Test
    void deveAceitarUmaDataDaDeliberacaoIsoValida() throws Exception {
        InformacaoBaseRequest pedido = objectMapper.readValue(
                "{\"deliberacao\":{\"dataDeliberacao\":\"2026-09-01\"}}", InformacaoBaseRequest.class);

        assertThat(pedido.deliberacao().dataDeliberacao()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}
