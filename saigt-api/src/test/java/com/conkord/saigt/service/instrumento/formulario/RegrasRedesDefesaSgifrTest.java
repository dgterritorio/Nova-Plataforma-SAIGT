package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.exception.RedesDefesaInvalidasException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.SgifrRedeDefesaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegrasRedesDefesaSgifrTest {

    private static final List<String> REDES_PSA = List.of("AEMGC", "APPS", "RPA", "RSFGC", "RVDI", "RVF");

    private final SgifrRedeDefesaRepository repository = mock(SgifrRedeDefesaRepository.class);
    private final RegrasRedesDefesaSgifr regras = new RegrasRedesDefesaSgifr(repository);

    private final SgifrRedeDefesa rpfgc = new SgifrRedeDefesa("RPFGC", "Rede primária", true, false, true);
    private final List<SgifrRedeDefesa> redesPsa = REDES_PSA.stream()
            .map(codigo -> new SgifrRedeDefesa(codigo, "Rede " + codigo, false, true, true)).toList();

    RegrasRedesDefesaSgifrTest() {
        when(repository.findByAplicaPraTrueOrderByCodigo()).thenReturn(List.of(rpfgc));
        when(repository.findByAplicaPsaTrueOrderByCodigo()).thenReturn(redesPsa);
    }

    private static Processo processoDe(String programa) {
        Processo processo = mock(Processo.class, RETURNS_DEEP_STUBS);
        when(processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo()).thenReturn(programa);
        when(processo.getRedesDefesa()).thenReturn(new HashSet<>());
        return processo;
    }

    private static Set<String> codigos(Processo processo) {
        return processo.getRedesDefesa().stream().map(SgifrRedeDefesa::getCodigo).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void should_saveOnlyThePrimaryNetwork_when_praSavesWithoutNetworks() {
        Processo processo = processoDe("PRA");

        regras.gravar(processo, null);

        assertThat(codigos(processo)).containsExactly("RPFGC");
    }

    @Test
    void should_keepThePrimaryNetwork_when_praSendsIt() {
        Processo processo = processoDe("PRA");

        regras.gravar(processo, List.of("RPFGC"));

        assertThat(codigos(processo)).containsExactly("RPFGC");
    }

    @Test
    void should_rejectTheRequest_when_praSendsAnotherNetwork() {
        Processo processo = processoDe("PRA");

        assertThatThrownBy(() -> regras.gravar(processo, List.of("RSFGC")))
                .isInstanceOf(RedesDefesaInvalidasException.class)
                .hasMessage("Redes de defesa inválidas para este programa.")
                .satisfies(erro -> assertThat(((ValidacaoException) erro).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("redesDefesa"));
        assertThat(processo.getRedesDefesa()).isEmpty();
    }

    @Test
    void should_saveEachNetworkOnce_when_psaSendsRepeatedCodes() {
        Processo processo = processoDe("PSA");

        regras.gravar(processo, List.of("APPS", "RVF", "APPS"));

        assertThat(codigos(processo)).containsExactlyInAnyOrder("APPS", "RVF");
    }

    @Test
    void should_saveTheSixNetworks_when_psaSendsAllOfThem() {
        Processo processo = processoDe("PSA");

        regras.gravar(processo, REDES_PSA);

        assertThat(codigos(processo)).containsExactlyInAnyOrderElementsOf(REDES_PSA);
    }

    @Test
    void should_saveOneNetwork_when_psaSendsOne() {
        Processo processo = processoDe("PSA");

        regras.gravar(processo, List.of("RPA"));

        assertThat(codigos(processo)).containsExactly("RPA");
    }

    @Test
    void should_clearTheNetworks_when_psaSendsAnEmptyList() {
        Processo processo = processoDe("PSA");
        processo.getRedesDefesa().addAll(redesPsa);

        regras.gravar(processo, List.of());

        assertThat(processo.getRedesDefesa()).isEmpty();
    }

    @Test
    void should_leaveTheNetworksAlone_when_psaOmitsTheField() {
        Processo processo = processoDe("PSA");
        processo.getRedesDefesa().add(redesPsa.get(0));

        regras.gravar(processo, null);

        assertThat(codigos(processo)).containsExactly("AEMGC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RTFGC", "RPFGC", "XYZ"})
    void should_rejectTheRequest_when_psaSendsANetworkItDoesNotAccept(String codigo) {
        Processo processo = processoDe("PSA");
        processo.getRedesDefesa().add(redesPsa.get(0));

        assertThatThrownBy(() -> regras.gravar(processo, List.of(codigo)))
                .isInstanceOf(RedesDefesaInvalidasException.class);
        assertThat(codigos(processo)).containsExactly("AEMGC");
    }

    @Test
    void should_rejectTheRequest_when_oneOfTheCodesIsInvalid() {
        Processo processo = processoDe("PSA");

        assertThatThrownBy(() -> regras.gravar(processo, List.of("APPS", "RTFGC")))
                .isInstanceOf(RedesDefesaInvalidasException.class);
    }

    @Test
    void should_fillThePrimaryNetwork_when_praIsSubmittedWithoutSavedNetworks() {
        Processo processo = processoDe("PRA");

        regras.verificarNaSubmissao(processo);

        assertThat(codigos(processo)).containsExactly("RPFGC");
    }

    @Test
    void should_acceptNoNetworks_when_psaIsSubmittedWithoutThem() {
        Processo processo = processoDe("PSA");

        regras.verificarNaSubmissao(processo);

        assertThat(processo.getRedesDefesa()).isEmpty();
    }

    @Test
    void should_rejectTheSubmission_when_aSavedNetworkIsNoLongerAccepted() {
        Processo processo = processoDe("PSA");
        processo.getRedesDefesa().add(new SgifrRedeDefesa("RTFGC", "Rede terciária", false, false, false));

        assertThatThrownBy(() -> regras.verificarNaSubmissao(processo)).isInstanceOf(RedesDefesaInvalidasException.class);
    }

    @Test
    void should_returnTheAcceptedNetworksOfTheProgram_when_asked() {
        assertThat(regras.aceites(processoDe("PRA"))).containsExactly(rpfgc);
        assertThat(regras.aceites(processoDe("PSA"))).containsExactlyElementsOf(redesPsa);
    }

    @Test
    void should_returnNoNetworks_when_theProgramIsNeitherPraNorPsa() {
        assertThat(regras.aceites(processoDe("PDM"))).isEmpty();
    }

    @Test
    void should_alwaysReturnFullText_when_theRequestHasNoTypeOrAsksForIt() {
        assertThat(regras.tipoInformacao(null)).isEqualTo("TEXTO_INTEGRAL");
        assertThat(regras.tipoInformacao("TEXTO_INTEGRAL")).isEqualTo("TEXTO_INTEGRAL");
    }

    @Test
    void should_rejectTheRequest_when_theTypeIsExtract() {
        assertThatThrownBy(() -> regras.tipoInformacao("EXTRATO"))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(erro -> assertThat(((ValidacaoException) erro).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("tipoInformacao"));
    }

    @Test
    void should_notHardcodeNetworkCodes_when_theRulesAreInspected() throws IOException {
        Path fonte = Path.of("src/main/java/com/conkord/saigt/service/instrumento/formulario/RegrasRedesDefesaSgifr.java");
        String codigo = Files.readString(fonte);

        assertThat(codigo).doesNotContain("RPFGC", "RSFGC", "RTFGC", "APPS", "RVDI", "AEMGC", "RVF", "RPA\"");
    }
}
