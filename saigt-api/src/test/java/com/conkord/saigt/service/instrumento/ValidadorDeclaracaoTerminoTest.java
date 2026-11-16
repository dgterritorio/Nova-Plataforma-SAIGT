package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ConfirmacaoEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ValidadorDeclaracaoTerminoTest {

    private static final String MENSAGEM = "Declare que a revogação termina os procedimentos em curso.";

    private final DinamicaRepository dinamicaRepository = mock(DinamicaRepository.class);
    private final ValidadorSubmissaoProcesso validador =
            new ValidadorSubmissaoProcesso(mock(ListaValoresRepository.class), new ObjectMapper(), dinamicaRepository);
    private final TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");

    private static <T> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }

    private Instrumento instrumento() {
        return comId(new Instrumento(pdm, "1", "PDM de teste", null, null));
    }

    private Dinamica dinamica(String codigoTipo, Instrumento instrumento, Instrumento... outros) {
        Dinamica dinamica = comId(new Dinamica(instrumento,
                new InstrumentoDinamicaConfig(pdm, new TipoDinamica(codigoTipo, "Dinâmica " + codigoTipo)), "Dinâmica", null));
        if (outros.length > 0) {
            List<Instrumento> todos = new java.util.ArrayList<>(List.of(instrumento));
            todos.addAll(Arrays.asList(outros));
            dinamica.substituirInstrumentos(todos);
        }
        return dinamica;
    }

    private static Processo processoDe(Dinamica dinamica) {
        return comId(new Processo(dinamica, null, null, null));
    }

    @Test
    void deveDevolverAsDinamicasEmCursoDosInstrumentosQueARevogacaoAbrangeSemElaPropria() {
        Instrumento plano = instrumento();
        Instrumento outro = instrumento();
        Dinamica revogacao = dinamica("REVOGACAO", plano, outro);
        Dinamica alteracao = dinamica("ALTERACAO", plano);
        when(dinamicaRepository.listarEmCursoQueAbrangem(anyCollection(), any())).thenReturn(List.of(alteracao));

        assertThat(validador.dinamicasATerminar(processoDe(revogacao))).containsExactly(alteracao);

        verify(dinamicaRepository).listarEmCursoQueAbrangem(
                org.mockito.ArgumentMatchers.argThat(ids -> Set.copyOf(ids).equals(Set.of(plano.getId(), outro.getId()))),
                org.mockito.ArgumentMatchers.eq(revogacao.getId()));
    }

    @Test
    void deveDevolverVaziaNaRevogacaoSemOutrasDinamicasEmCurso() {
        Dinamica revogacao = dinamica("REVOGACAO", instrumento());
        when(dinamicaRepository.listarEmCursoQueAbrangem(anyCollection(), any())).thenReturn(List.of());

        assertThat(validador.dinamicasATerminar(processoDe(revogacao))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALTERACAO", "REVISAO", "ELABORACAO", "REVOGACAO_ATO", "SUSPENSAO"})
    void naoDeveAplicarARegraForaDaRevogacaoDoIgt(String codigoTipo) {
        Processo processo = processoDe(dinamica(codigoTipo, instrumento()));

        assertThat(validador.dinamicasATerminar(processo)).isEmpty();
        assertThatCode(() -> validador.verificarDeclaracaoTermino(processo, null)).doesNotThrowAnyException();
        verifyNoInteractions(dinamicaRepository);
    }

    @Test
    void deveRecusarARevogacaoComDinamicasEmCursoSemADeclaracao() {
        Instrumento plano = instrumento();
        Processo processo = processoDe(dinamica("REVOGACAO", plano));
        when(dinamicaRepository.listarEmCursoQueAbrangem(anyCollection(), any())).thenReturn(List.of(dinamica("ALTERACAO", plano)));

        for (List<String> confirmacoes : Arrays.asList(null, List.<String>of(), List.of("CONTEUDO_PROCESSO"))) {
            assertThatThrownBy(() -> validador.verificarDeclaracaoTermino(processo, confirmacoes))
                    .isInstanceOfSatisfying(ConfirmacaoEmFaltaException.class, erro -> {
                        assertThat(erro.getCodigo()).isEqualTo("CONFIRMACAO_EM_FALTA");
                        assertThat(erro.getMessage()).isEqualTo(MENSAGEM);
                        assertThat(erro.getCampos()).containsExactly(new CampoErro("confirmacoes", MENSAGEM));
                    });
        }
    }

    @Test
    void deveAceitarARevogacaoComADeclaracaoEDevolverAsDinamicasQueElaAbrange() {
        Instrumento plano = instrumento();
        Processo processo = processoDe(dinamica("REVOGACAO", plano));
        Dinamica alteracao = dinamica("ALTERACAO", plano);
        when(dinamicaRepository.listarEmCursoQueAbrangem(anyCollection(), any())).thenReturn(List.of(alteracao));

        assertThat(validador.verificarDeclaracaoTermino(processo, List.of("CONTEUDO_PROCESSO", "DECLARACAO_TERMINO")))
                .containsExactly(alteracao);
    }

    @Test
    void deveIgnorarADeclaracaoQuandoNaoHaDinamicasATerminar() {
        Processo processo = processoDe(dinamica("REVOGACAO", instrumento()));
        when(dinamicaRepository.listarEmCursoQueAbrangem(anyCollection(), any())).thenReturn(List.of());

        assertThat(validador.verificarDeclaracaoTermino(processo, List.of("DECLARACAO_TERMINO"))).isEmpty();
        assertThat(validador.verificarDeclaracaoTermino(processo, null)).isEmpty();
    }
}
