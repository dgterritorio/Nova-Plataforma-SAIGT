package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ReferenciaInvalidaException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AtosReferiveisTest {

    private final ProcessoRepository processoRepository = mock(ProcessoRepository.class);
    private final AtosReferiveis atosReferiveis = new AtosReferiveis(processoRepository);

    private InstrumentoDinamicaConfig dinamicaConfig;
    private Dinamica dinamica;
    private Utilizador autor;

    @BeforeEach
    void setUp() {
        TipoInstrumento pdm = new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO");
        dinamicaConfig = new InstrumentoDinamicaConfig(pdm, new TipoDinamica("ALTERACAO", "Alteração"));
        autor = new Utilizador("Autor", "autor@cm-teste.pt", "INCM");
        dinamica = dinamica();
    }

    @Test
    void osCodigosReferiveisSaoADecisaoDeInicioEAsQuatroDiscussoesPublicas() {
        assertThat(AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS).containsExactlyInAnyOrder("DECISAO_INICIO",
                "ABERTURA_DISCUSSAO_PUBLICA", "ABERTURA_DISCUSSAO_PUBLICA_RERAE", "ABERTURA_DISCUSSAO_PUBLICA_DL10");
    }

    @Test
    void listarPedeSoOsProcessosPublicadosDaDinamicaDosCodigosReferiveis() {
        Processo decisao = processo(dinamica, "DECISAO_INICIO", EstadoProcesso.PUBLICADO);
        when(processoRepository.findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
                dinamica, EstadoProcesso.PUBLICADO, AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS)).thenReturn(List.of(decisao));

        assertThat(atosReferiveis.listar(dinamica)).containsExactly(decisao);
        verify(processoRepository).findByDinamicaAndEstadoAndInstrumentoProcedimentoConfig_TipoProcedimento_CodigoInOrderByDataPublicacaoDrAsc(
                dinamica, EstadoProcesso.PUBLICADO, AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS);
    }

    @Test
    void verificarDevolveADecisaoDeInicioEADiscussaoPublicaPublicadasDaMesmaDinamica() {
        for (String codigo : AtosReferiveis.CODIGOS_PROCEDIMENTO_REFERIVEIS) {
            Processo publicado = processo(dinamica, codigo, EstadoProcesso.PUBLICADO);

            assertThat(atosReferiveis.verificar(publicado.getId(), dinamica)).isSameAs(publicado);
        }
    }

    @Test
    void verificarAceitaUmRegistoExterno() {
        Processo externo = processo(dinamica, "DECISAO_INICIO", EstadoProcesso.PUBLICADO);
        externo.setOrigemDados("REGISTO_EXTERNO");

        assertThat(atosReferiveis.verificar(externo.getId(), dinamica)).isSameAs(externo);
    }

    @Test
    void verificarRecusaOsCasosInvalidosSemOsDistinguir() {
        Processo deOutraDinamica = processo(dinamica(), "DECISAO_INICIO", EstadoProcesso.PUBLICADO);
        Processo emSubmissao = processo(dinamica, "DECISAO_INICIO", EstadoProcesso.EM_SUBMISSAO);
        Processo enviadoDgt = processo(dinamica, "ABERTURA_DISCUSSAO_PUBLICA", EstadoProcesso.ENVIADO_DGT);
        Processo daConstituicaoCc = processo(dinamica, "CONSTITUICAO_CC", EstadoProcesso.PUBLICADO);
        Processo deOutroAtoPontual = processo(dinamica, "RETIFICACAO_ATO_ANEXOS", EstadoProcesso.PUBLICADO);

        for (UUID id : List.of(deOutraDinamica.getId(), emSubmissao.getId(), enviadoDgt.getId(), daConstituicaoCc.getId(),
                deOutroAtoPontual.getId(), UUID.randomUUID())) {
            assertThatThrownBy(() -> atosReferiveis.verificar(id, dinamica)).isInstanceOf(ReferenciaInvalidaException.class)
                    .hasMessage("Ato referido inválido.");
        }
        assertThatThrownBy(() -> atosReferiveis.verificar(null, dinamica)).isInstanceOf(ReferenciaInvalidaException.class);
    }

    private Dinamica dinamica() {
        Instrumento instrumento = new Instrumento();
        ReflectionTestUtils.setField(instrumento, "id", UUID.randomUUID());
        Dinamica nova = new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor);
        ReflectionTestUtils.setField(nova, "id", UUID.randomUUID());
        return nova;
    }

    private Processo processo(Dinamica daDinamica, String codigoProcedimento, EstadoProcesso estado) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento(codigoProcedimento, codigoProcedimento), 1, "SEGUNDA_H");
        Processo processo = new Processo(daDinamica, config, autor, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        processo.setEstado(estado);
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        return processo;
    }
}
