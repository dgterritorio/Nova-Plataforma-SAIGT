package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.DestinoInvalidoException;
import com.conkord.saigt.exception.DestinoSoDepositoException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras do destino. O formulário da REN ainda não existe: o caso de um formulário que admite «só depósito» usa um
 * duplo do catálogo; o caso real (todos os formulários existentes) usa o catálogo verdadeiro.
 */
class DestinoProcessoServiceTest {

    private final CatalogoFormularios catalogo = spy(new CatalogoFormularios());
    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final DestinoProcessoService service = new DestinoProcessoService(catalogo, processoDocumentoRepository);
    private final TipoProcedimento tipoProcedimento = new TipoProcedimento("DECISAO_INICIO", "Decisão de início");

    private Processo processo;

    @BeforeEach
    void setUp() {
        InstrumentoProcedimentoConfig config = mock(InstrumentoProcedimentoConfig.class);
        when(config.getTipoProcedimento()).thenReturn(tipoProcedimento);
        processo = mock(Processo.class);
        when(processo.getId()).thenReturn(UUID.randomUUID());
        when(processo.getInstrumentoProcedimentoConfig()).thenReturn(config);
        when(processo.getDestino()).thenReturn(DestinoProcesso.PUBLICACAO_DEPOSITO);
    }

    private void formularioAdmite() {
        doReturn(true).when(catalogo).admiteSoDeposito(any());
    }

    @Test
    void aRegraVemDoFormularioDoTipoDeProcedimento() {
        assertThat(service.admiteSoDeposito(processo)).isFalse();
        formularioAdmite();
        assertThat(service.admiteSoDeposito(processo)).isTrue();
        verify(catalogo, org.mockito.Mockito.atLeastOnce()).admiteSoDeposito(tipoProcedimento);
    }

    @Test
    void soDepositoNumFormularioQueNaoAdmiteDa400DestinoInvalidoNoCampoDestino() {
        assertThatThrownBy(() -> service.verificarMudanca(processo, DestinoProcesso.SO_DEPOSITO))
                .isInstanceOfSatisfying(DestinoInvalidoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("DESTINO_INVALIDO");
                    assertThat(erro.getMessage()).isEqualTo("Este processo não pode ser só para depósito.");
                    assertThat(erro.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("destino");
                });
        verify(processoDocumentoRepository, never()).existsByProcesso_Id(any());
    }

    @Test
    void publicacaoEDepositoPassaSempreNumProcessoSemFicheiros() {
        assertThatCode(() -> service.verificarMudanca(processo, DestinoProcesso.PUBLICACAO_DEPOSITO)).doesNotThrowAnyException();
    }

    @Test
    void soDepositoNumFormularioQueAdmiteESemFicheirosPassa() {
        formularioAdmite();

        assertThatCode(() -> service.verificarMudanca(processo, DestinoProcesso.SO_DEPOSITO)).doesNotThrowAnyException();
    }

    @Test
    void mudarODestinoComFicheirosDa409EstadoInvalidoEOMesmoDestinoPassa() {
        formularioAdmite();
        when(processoDocumentoRepository.existsByProcesso_Id(processo.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.verificarMudanca(processo, DestinoProcesso.SO_DEPOSITO))
                .isExactlyInstanceOf(EstadoInvalidoException.class)
                .satisfies(erro -> assertThat(((EstadoInvalidoException) erro).getCodigo()).isEqualTo("ESTADO_INVALIDO"))
                .hasMessage("Remova os ficheiros carregados antes de mudar o destino.");
        assertThatCode(() -> service.verificarMudanca(processo, DestinoProcesso.PUBLICACAO_DEPOSITO)).doesNotThrowAnyException();
    }

    @Test
    void destinoOmitidoNaoVerificaNada() {
        assertThatCode(() -> service.verificarMudanca(processo, null)).doesNotThrowAnyException();
        verify(catalogo, never()).admiteSoDeposito(any());
        verify(processoDocumentoRepository, never()).existsByProcesso_Id(any());
    }

    @Test
    void naSubmissaoUmSoDepositoGravadoQueOFormularioJaNaoAdmiteERecusado() {
        assertThatThrownBy(() -> service.verificarAdmitido(processo, DestinoProcesso.SO_DEPOSITO))
                .isInstanceOf(DestinoInvalidoException.class);
        assertThatCode(() -> service.verificarAdmitido(processo, DestinoProcesso.PUBLICACAO_DEPOSITO)).doesNotThrowAnyException();
    }

    @Test
    void soDepositoNaoVaiAIncmComOCodigoDoLld() {
        when(processo.getDestino()).thenReturn(DestinoProcesso.SO_DEPOSITO);

        assertThatThrownBy(() -> service.verificarEnvioIncm(processo))
                .isInstanceOfSatisfying(DestinoSoDepositoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("DESTINO_SO_DEPOSITO");
                    assertThat(erro.getMessage()).isEqualTo("Este processo é só para depósito e não é enviado à INCM.");
                });
    }

    @ParameterizedTest
    @EnumSource(value = DestinoProcesso.class, names = "PUBLICACAO_DEPOSITO")
    void publicacaoEDepositoSegueParaAIncm(DestinoProcesso destino) {
        when(processo.getDestino()).thenReturn(destino);

        assertThatCode(() -> service.verificarEnvioIncm(processo)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,false,true",
            "false,true,false,false",
            "true,false,false,false",
            "true,true,true,false"})
    void destinoEditavelPedeEdicaoFormularioQueAdmiteESemFicheiros(boolean editavel, boolean admite, boolean comFicheiros,
                                                                   boolean esperado) {
        if (admite) {
            formularioAdmite();
        }
        when(processoDocumentoRepository.existsByProcesso_Id(processo.getId())).thenReturn(comFicheiros);

        assertThat(service.destinoEditavel(processo, editavel)).isEqualTo(esperado);
    }
}
