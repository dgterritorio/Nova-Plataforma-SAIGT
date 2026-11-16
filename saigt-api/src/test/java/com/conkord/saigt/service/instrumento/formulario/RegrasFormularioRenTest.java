package com.conkord.saigt.service.instrumento.formulario;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.instrumento.formulario.RegrasFormularioRen.CamposRen;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegrasFormularioRenTest {

    private static final String TIPO = "MOTIVO_PROCEDIMENTO_REN";

    private final ListaValoresRepository listaValoresRepository = mock(ListaValoresRepository.class);
    private final SnitReferenciaRepository snitReferenciaRepository = mock(SnitReferenciaRepository.class);
    private final RegrasFormularioRen regras = new RegrasFormularioRen(listaValoresRepository, snitReferenciaRepository,
            new ObjectMapper());

    RegrasFormularioRenTest() {
        when(listaValoresRepository.findById(anyLong())).thenReturn(Optional.empty());
        valor(1L, TIPO, true);
        valor(2L, TIPO, false);
        valor(3L, "MOTIVO_ALTERACAO_LEI", true);
        when(snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.NUT2, "PT17")).thenReturn(true);
        when(snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.NUT2, "PT11")).thenReturn(true);
        concelho("1111", "PT17", true);
        concelho("1312", "PT11", true);
        concelho("1112", "PT17", false);
    }

    private void valor(Long id, String tipo, boolean ativo) {
        ListaValores valor = mock(ListaValores.class);
        when(valor.getId()).thenReturn(id);
        when(valor.getTipo()).thenReturn(tipo);
        when(valor.isAtivo()).thenReturn(ativo);
        when(listaValoresRepository.findById(id)).thenReturn(Optional.of(valor));
    }

    private void concelho(String codigo, String nut2, boolean ativo) {
        SnitReferencia concelho = new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, codigo, "Concelho " + codigo,
                "{\"dtcc\":\"" + codigo + "\",\"nut2Code\":\"" + nut2 + "\"}", Instant.now());
        concelho.setAtivo(ativo);
        when(snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, codigo))
                .thenReturn(Optional.of(concelho));
    }

    private static Processo processo(String dados, boolean pedeDiploma, String tipoInformacaoFixo) {
        InstrumentoDinamicaConfig config = new InstrumentoDinamicaConfig();
        config.setPedeDiplomaCorrigido(pedeDiploma);
        config.setTipoInformacaoFixo(tipoInformacaoFixo);
        Processo processo = mock(Processo.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(processo.getDinamica().getInstrumentoDinamicaConfig()).thenReturn(config);
        when(processo.getDadosFormulario()).thenReturn(dados);
        return processo;
    }

    private static Processo processo(String dados) {
        return processo(dados, false, null);
    }

    private static List<String> campos(Throwable erro) {
        return ((ValidacaoException) erro).getCampos().stream().map(ValidacaoException.CampoErro::path).toList();
    }

    @Test
    void deveAceitarUmMotivoAtivoDaListaDaRen() {
        Map<String, Object> chaves = regras.chavesAGravar(processo(null), new CamposRen(1L, null, null, null));

        assertThat(chaves).containsEntry("motivoProcedimentoId", 1L);
    }

    @Test
    void deveRecusarUmMotivoInexistenteDeOutraListaOuDesativado() {
        for (long id : new long[]{99L, 3L, 2L}) {
            assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(id, null, null, null)))
                    .isInstanceOf(ValidacaoException.class)
                    .hasMessage("Motivo do procedimento inválido.")
                    .satisfies(erro -> assertThat(campos(erro)).containsExactly("motivoProcedimentoId"));
        }
    }

    @Test
    void deveManterOGravadoQuandoOsCamposVemOmitidos() {
        String dados = "{\"informacaoBase\":{},\"motivoProcedimentoId\":1,\"diplomaCorrigido\":\"Aviso 1/2026\","
                + "\"regiaoCodigo\":\"PT17\",\"municipioCodigo\":\"1111\"}";

        Map<String, Object> chaves = regras.chavesAGravar(processo(dados, true, null), new CamposRen(null, null, null, null));

        assertThat(chaves).containsEntry("motivoProcedimentoId", 1L).containsEntry("diplomaCorrigido", "Aviso 1/2026")
                .containsEntry("regiaoCodigo", "PT17").containsEntry("municipioCodigo", "1111");
    }

    @Test
    void deveExigirNaSubmissaoOMotivoQuandoAListaTemValoresAtivos() {
        when(listaValoresRepository.existsByTipoAndAtivoTrue(TIPO)).thenReturn(true);

        assertThatThrownBy(() -> regras.verificarNaSubmissao(processo("{}")))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Indique o motivo do procedimento.")
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("motivoProcedimentoId"));
    }

    @Test
    void deveRecusarNaSubmissaoUmMotivoEntretantoDesativado() {
        when(listaValoresRepository.existsByTipoAndAtivoTrue(TIPO)).thenReturn(true);

        assertThatThrownBy(() -> regras.verificarNaSubmissao(processo("{\"motivoProcedimentoId\":2}")))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Indique o motivo do procedimento.");
    }

    @Test
    void naoDeveExigirOMotivoComAListaSemValoresAtivos() {
        when(listaValoresRepository.existsByTipoAndAtivoTrue(TIPO)).thenReturn(false);

        assertThatCode(() -> regras.verificarNaSubmissao(processo(null))).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarNaSubmissaoUmMotivoAtivo() {
        when(listaValoresRepository.existsByTipoAndAtivoTrue(TIPO)).thenReturn(true);

        assertThatCode(() -> regras.verificarNaSubmissao(processo("{\"motivoProcedimentoId\":1}"))).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarOsCamposDaRenNumProcessoDeOutroFormulario() {
        assertThatThrownBy(() -> RegrasFormularioRen.verificarNaoAplicavel(new CamposRen(1L, "Diploma", "PT17", "1111")))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Campo não aplicável a este procedimento.")
                .satisfies(erro -> assertThat(campos(erro))
                        .containsExactly("motivoProcedimentoId", "diplomaCorrigido", "regiaoCodigo", "municipioCodigo"));
        assertThatCode(() -> RegrasFormularioRen.verificarNaoAplicavel(new CamposRen(null, " ", null, ""))).doesNotThrowAnyException();
    }

    @Test
    void deveAceitarODiplomaSoNaDinamicaQueOPede() {
        assertThat(regras.chavesAGravar(processo(null, true, null), new CamposRen(null, "Aviso 1/2026", null, null)))
                .containsEntry("diplomaCorrigido", "Aviso 1/2026");
        assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(null, "Aviso 1/2026", null, null)))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("diplomaCorrigido"));
    }

    @Test
    void deveImporOTipoDeInformacaoFixoDaDinamica() {
        Processo delimitacao = processo(null, false, "TEXTO_INTEGRAL");

        assertThat(regras.tipoInformacao(delimitacao, null)).isEqualTo("TEXTO_INTEGRAL");
        assertThat(regras.tipoInformacao(delimitacao, "TEXTO_INTEGRAL")).isEqualTo("TEXTO_INTEGRAL");
        assertThatThrownBy(() -> regras.tipoInformacao(delimitacao, "EXTRATO"))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("tipoInformacao"));
        assertThat(regras.tipoInformacao(processo(null), "EXTRATO")).isEqualTo("EXTRATO");
    }

    @Test
    void deveAceitarARegiaoEUmConcelhoAtivoDela() {
        Map<String, Object> chaves = regras.chavesAGravar(processo(null), new CamposRen(null, null, "PT17", "1111"));

        assertThat(chaves).containsEntry("regiaoCodigo", "PT17").containsEntry("municipioCodigo", "1111");
    }

    @Test
    void deveRecusarRegiaoInexistenteEConcelhoInativoOuDeOutraRegiao() {
        assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(null, null, "PT99", null)))
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("regiaoCodigo"));
        assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(null, null, "PT17", "1312")))
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("municipioCodigo"));
        assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(null, null, "PT17", "1112")))
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("municipioCodigo"));
        assertThatThrownBy(() -> regras.chavesAGravar(processo(null), new CamposRen(null, null, null, "1111")))
                .satisfies(erro -> assertThat(campos(erro)).containsExactly("municipioCodigo"));
    }

    @Test
    void deveApagarOGravadoComUmTextoEmBranco() {
        String dados = "{\"regiaoCodigo\":\"PT17\",\"municipioCodigo\":\"1111\"}";

        Map<String, Object> chaves = regras.chavesAGravar(processo(dados), new CamposRen(null, null, "", ""));

        assertThat(chaves.get("regiaoCodigo")).isNull();
        assertThat(chaves.get("municipioCodigo")).isNull();
    }

    @Test
    void naoDeveTerCodigosDeInstrumentoNemDeDinamica() throws IOException {
        String codigo = Files.readString(
                Path.of("src/main/java/com/conkord/saigt/service/instrumento/formulario/RegrasFormularioRen.java"));

        assertThat(codigo).doesNotContain("\"REN\"", "DELIMITACAO", "ALTERACAO\"", "CORRECAO_MATERIAL", "PUBLICACAO_REN");
    }
}
