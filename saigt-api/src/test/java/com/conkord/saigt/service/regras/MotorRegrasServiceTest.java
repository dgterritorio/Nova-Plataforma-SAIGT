package com.conkord.saigt.service.regras;

import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.regras.avaliador.Avaliador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MotorRegrasServiceTest {

    private static final String COD_A = "AVALIADOR_A";
    private static final String COD_B = "AVALIADOR_B";
    private static final String COD_C = "AVALIADOR_C";

    private final RegraRepository regraRepository = mock(RegraRepository.class);
    private final CondicaoRepository condicaoRepository = mock(CondicaoRepository.class);
    private final Avaliador avaliadorA = mock(Avaliador.class);
    private final Avaliador avaliadorB = mock(Avaliador.class);
    private final Avaliador avaliadorC = mock(Avaliador.class);
    private MotorRegrasService motor;
    private UUID instrumentoId;

    @BeforeEach
    void setUp() {
        when(avaliadorA.codigo()).thenReturn(COD_A);
        when(avaliadorB.codigo()).thenReturn(COD_B);
        when(avaliadorC.codigo()).thenReturn(COD_C);
        motor = new MotorRegrasService(new RegistoAvaliadores(List.of(avaliadorA, avaliadorB, avaliadorC)),
                condicaoRepository, regraRepository);
        instrumentoId = UUID.randomUUID();
    }

    private Condicao condicao(Regra regra, String avaliador, short grupo) {
        Condicao condicao = new Condicao(regra, avaliador, "{}");
        condicao.setGrupo(grupo);
        return condicao;
    }

    @Test
    void deveDevolverTrueQuandoAmbasCondicoesDoGrupoUnicoPassam() {
        Regra regra = new Regra();
        regra.setOperador("AND");
        Condicao condicaoA = condicao(regra, COD_A, (short) 1);
        Condicao condicaoB = condicao(regra, COD_B, (short) 1);
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicaoA, condicaoB));
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(true);
        when(avaliadorB.avaliar(instrumentoId, "{}")).thenReturn(true);

        assertThat(motor.avaliar(regra, instrumentoId)).isTrue();
    }

    @Test
    void deveDevolverFalseQuandoSoUmaCondicaoDoGrupoUnicoPassa() {
        Regra regra = new Regra();
        regra.setOperador("AND");
        Condicao condicaoA = condicao(regra, COD_A, (short) 1);
        Condicao condicaoB = condicao(regra, COD_B, (short) 1);
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicaoA, condicaoB));
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(true);
        when(avaliadorB.avaliar(instrumentoId, "{}")).thenReturn(false);

        assertThat(motor.avaliar(regra, instrumentoId)).isFalse();
    }

    @Test
    void deveDevolverTrueComOperadorOrQuandoGrupo1FalhaEGrupo2Passa() {
        Regra regra = new Regra();
        regra.setOperador("OR");
        Condicao condicaoA = condicao(regra, COD_A, (short) 1);
        Condicao condicaoB = condicao(regra, COD_B, (short) 1);
        Condicao condicaoC = condicao(regra, COD_C, (short) 2);
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicaoA, condicaoB, condicaoC));
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(true);
        when(avaliadorB.avaliar(instrumentoId, "{}")).thenReturn(false);
        when(avaliadorC.avaliar(instrumentoId, "{}")).thenReturn(true);

        assertThat(motor.avaliar(regra, instrumentoId)).isTrue();
    }

    /** O grupo 2 só tem condições inativas, que o repositório já filtra: nunca chega ao motor. */
    @Test
    void grupoSemCondicoesAtivasNuncaPassaVacuamenteComOperadorOr() {
        Regra regra = new Regra();
        regra.setOperador("OR");
        Condicao condicaoA = condicao(regra, COD_A, (short) 1);
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicaoA));
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(false);

        assertThat(motor.avaliar(regra, instrumentoId)).isFalse();
    }

    @Test
    void deveDevolverFalseQuandoRegraNaoTemNenhumaCondicaoAtiva() {
        Regra regra = new Regra();
        regra.setOperador("AND");
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of());

        assertThat(motor.avaliar(regra, instrumentoId)).isFalse();
    }

    @Test
    void deveLancarExcecaoQuandoCondicaoReferenciaAvaliadorSemImplementacao() {
        Regra regra = new Regra();
        regra.setOperador("AND");
        Condicao condicaoDesconhecida = condicao(regra, "TYPO_INEXISTENTE", (short) 1);
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicaoDesconhecida));

        assertThatThrownBy(() -> motor.avaliar(regra, instrumentoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TYPO_INEXISTENTE");
    }

    @Test
    void deveDevolverTrueNaElegibilidadeDeDinamicaQuandoNaoHaNenhumaRegraConfigurada() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig.class);
        when(regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(config)).thenReturn(List.of());

        assertThat(motor.avaliarElegibilidadeDinamica(config, instrumentoId)).isTrue();
    }

    @Test
    void deveExigirQueTodasAsRegrasDeDinamicaPassem() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig.class);
        Regra regraQuePassa = new Regra();
        regraQuePassa.setOperador("AND");
        Regra regraQueFalha = new Regra();
        regraQueFalha.setOperador("AND");
        when(regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(config))
                .thenReturn(List.of(regraQuePassa, regraQueFalha));
        when(condicaoRepository.findByRegraAndAtivoTrue(regraQuePassa))
                .thenReturn(List.of(condicao(regraQuePassa, COD_A, (short) 1)));
        when(condicaoRepository.findByRegraAndAtivoTrue(regraQueFalha)).thenReturn(List.of());
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(true);

        assertThat(motor.avaliarElegibilidadeDinamica(config, instrumentoId)).isFalse();
    }

    @Test
    void deveDevolverTrueNaElegibilidadeDeProcedimentoQuandoNaoExisteRegra() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig.class);
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(config)).thenReturn(Optional.empty());

        assertThat(motor.avaliarElegibilidadeProcedimento(config, instrumentoId)).isTrue();
    }

    @Test
    void deveAvaliarAUnicaRegraDeProcedimentoQuandoExiste() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig.class);
        Regra regra = new Regra();
        regra.setOperador("AND");
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(config)).thenReturn(Optional.of(regra));
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicao(regra, COD_A, (short) 1)));
        when(avaliadorA.avaliar(instrumentoId, "{}")).thenReturn(false);

        assertThat(motor.avaliarElegibilidadeProcedimento(config, instrumentoId)).isFalse();
    }

    @Test
    void semInstrumentoSoAvaliaAsRegrasQueNaoDependemDeInstrumento() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig.class);
        Regra deInstrumento = new Regra();
        deInstrumento.setOperador("AND");
        Regra semInstrumento = new Regra();
        semInstrumento.setOperador("AND");
        when(regraRepository.findByInstrumentoDinamicaConfigAndAtivoTrue(config)).thenReturn(List.of(deInstrumento, semInstrumento));
        when(condicaoRepository.findByRegraAndAtivoTrue(deInstrumento)).thenReturn(List.of(condicao(deInstrumento, COD_A, (short) 1)));
        when(condicaoRepository.findByRegraAndAtivoTrue(semInstrumento)).thenReturn(List.of(condicao(semInstrumento, COD_B, (short) 1)));
        when(avaliadorA.dependeDeInstrumento()).thenReturn(true);
        when(avaliadorB.dependeDeInstrumento()).thenReturn(false);
        when(avaliadorB.avaliar(null, "{}")).thenReturn(true);

        assertThat(motor.avaliarElegibilidadeDinamicaSemInstrumento(config)).isTrue();
        verify(avaliadorA, never()).avaliar(any(), any());

        when(avaliadorB.avaliar(null, "{}")).thenReturn(false);
        assertThat(motor.avaliarElegibilidadeDinamicaSemInstrumento(config)).isFalse();
    }

    @Test
    void semInstrumentoARegraDoProcedimentoQueDependeDeInstrumentoNaoBloqueia() {
        var config = mock(com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig.class);
        Regra regra = new Regra();
        regra.setOperador("AND");
        when(regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(config)).thenReturn(Optional.of(regra));
        when(condicaoRepository.findByRegraAndAtivoTrue(regra)).thenReturn(List.of(condicao(regra, COD_A, (short) 1)));
        when(avaliadorA.dependeDeInstrumento()).thenReturn(true);

        assertThat(motor.avaliarElegibilidadeProcedimentoSemInstrumento(config)).isTrue();
        verify(avaliadorA, never()).avaliar(any(), any());
    }
}
