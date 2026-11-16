package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.PedeLocalizacao;
import com.conkord.saigt.persistence.configuracao.PedeMotivoAlteracao;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorLocalizacaoMotivoAlteracaoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ListaValores> valores = new ArrayList<>();
    private final ValidadorSubmissaoProcesso validador = new ValidadorSubmissaoProcesso(repositorioSobre(valores), objectMapper);

    @SuppressWarnings("unchecked")
    private static ListaValoresRepository repositorioSobre(List<ListaValores> valores) {
        ListaValoresRepository repositorio = mock(ListaValoresRepository.class);
        when(repositorio.findByTipoInAndCodigoIn(anyCollection(), anyCollection())).thenAnswer(invocacao -> {
            Collection<String> tipos = invocacao.getArgument(0);
            Collection<String> codigos = invocacao.getArgument(1);
            return valores.stream().filter(v -> tipos.contains(v.getTipo()) && codigos.contains(v.getCodigo())).toList();
        });
        when(repositorio.existsByTipoAndAtivoTrue(anyString())).thenAnswer(invocacao ->
                valores.stream().anyMatch(v -> v.getTipo().equals(invocacao.getArgument(0)) && v.isAtivo()));
        return repositorio;
    }

    private ListaValores valor(String tipo, String codigo) {
        ListaValores item = new ListaValores(tipo, codigo, "Descrição " + codigo, valores.size() + 1);
        valores.add(item);
        return item;
    }

    private static Processo processo(PedeLocalizacao pedeLocalizacao, PedeMotivoAlteracao pedeMotivo) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(null,
                new TipoProcedimento("DECISAO_INICIO", "Decisão de início"), 1, "SEGUNDA_H");
        config.setPedeLocalizacao(pedeLocalizacao);
        config.setPedeMotivoAlteracao(pedeMotivo);
        return new Processo(null, config, null, null);
    }

    private static Processo processoDaAlteracaoPdm() {
        return processo(PedeLocalizacao.OBRIGATORIA, PedeMotivoAlteracao.COM_PERGUNTA);
    }

    private void gravar(Processo processo, Map<String, Object> dados) {
        try {
            processo.setDadosFormulario(objectMapper.writeValueAsString(dados));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void recusaCom(Runnable chamada, String path, String erro) {
        assertThatThrownBy(chamada::run).isInstanceOfSatisfying(ValidacaoException.class, ex -> {
            assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
            assertThat(ex.getCampos()).contains(new CampoErro(path, erro));
        });
    }

    private static MotivoAlteracaoRequest lei(String codigo) {
        return new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_LEI", codigo);
    }

    // Gravação: regras 1 a 5

    @Test
    void regra1LocalizacaoNumProcedimentoQueNaoAPedeERecusada() {
        valor("FREGUESIA", "F01");
        Processo processo = processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO);

        recusaCom(() -> validador.verificarLocalizacao(processo, new LocalizacaoRequest(null, "F01", null, null)),
                "localizacao", "Este procedimento não pede a localização.");
    }

    @Test
    void localizacaoVaziaNumProcedimentoQueNaoAPedePassaComoNula() {
        Processo processo = processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO);

        assertThat(validador.verificarLocalizacao(processo, new LocalizacaoRequest(false, " ", "", null))).isNull();
        assertThat(validador.verificarLocalizacao(processo, null)).isNull();
    }

    @Test
    void regra2AplicavelATodaAAreaComFreguesiaERecusada() {
        valor("FREGUESIA", "F01");

        recusaCom(() -> validador.verificarLocalizacao(processoDaAlteracaoPdm(), new LocalizacaoRequest(true, "F01", null, null)),
                "localizacao.aplicavelTodaArea", "Escolha \"Aplicável a toda a área do IGT\" ou indique a freguesia.");
    }

    @Test
    void regra3ToponimoSemFreguesiaERecusado() {
        recusaCom(() -> validador.verificarLocalizacao(processoDaAlteracaoPdm(), new LocalizacaoRequest(null, null, null, "Bairro")),
                "localizacao.toponimoCodigo", "O topónimo acompanha a freguesia.");
    }

    @Test
    void regra4ToponimoDaListaEEscritoAoMesmoTempoERecusado() {
        valor("FREGUESIA", "F01");
        valor("TOPONIMO", "T01");

        recusaCom(() -> validador.verificarLocalizacao(processoDaAlteracaoPdm(), new LocalizacaoRequest(null, "F01", "T01", "Bairro")),
                "localizacao.toponimoTexto", "Escolha um topónimo da lista ou escreva-o, não ambos.");
    }

    @Test
    void regra5FreguesiaOuToponimoQueNaoEValorAtivoSaoRecusados() {
        valor("FREGUESIA", "F01").setAtivo(false);

        recusaCom(() -> validador.verificarLocalizacao(processoDaAlteracaoPdm(), new LocalizacaoRequest(null, "F01", null, null)),
                "localizacao.freguesia", "Valor inválido.");
        valor("FREGUESIA", "F02");
        recusaCom(() -> validador.verificarLocalizacao(processoDaAlteracaoPdm(), new LocalizacaoRequest(null, "F02", "T99", null)),
                "localizacao.toponimoCodigo", "Valor inválido.");
    }

    @ParameterizedTest
    @EnumSource(value = PedeLocalizacao.class, names = {"OPCIONAL", "OBRIGATORIA"})
    void freguesiaComToponimoEscritoGravaNormalizada(PedeLocalizacao pede) {
        valor("FREGUESIA", "F01");

        LocalizacaoRequest gravada = validador.verificarLocalizacao(processo(pede, PedeMotivoAlteracao.NAO),
                new LocalizacaoRequest(false, " F01 ", null, " Bairro da Estação "));

        assertThat(gravada).isEqualTo(new LocalizacaoRequest(null, "F01", null, "Bairro da Estação"));
    }

    // Gravação: regras 6 a 9

    @Test
    void regra6MotivoNumProcedimentoQueNaoOPedeERecusado() {
        Processo processo = processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO);

        recusaCom(() -> validador.verificarMotivoAlteracao(processo, true, null),
                "motivosAlteracao", "Este procedimento não pede o motivo da alteração.");
        assertThat(validador.verificarMotivoAlteracao(processo, null, List.of())).isNull();
    }

    @Test
    void regra7PerguntaRespondidaNumProcedimentoObrigatorioERecusada() {
        Processo processo = processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.OBRIGATORIO);

        recusaCom(() -> validador.verificarMotivoAlteracao(processo, true, null),
                "conformaHierarquiaSuperior", "Este procedimento não faz a pergunta da conformação.");
    }

    @Test
    void regra8MotivosComRespostaNaoSaoRecusados() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");

        recusaCom(() -> validador.verificarMotivoAlteracao(processoDaAlteracaoPdm(), false, List.of(lei("L1"))),
                "motivosAlteracao", "Sem conformação, não há motivos a indicar.");
    }

    @Test
    void regra9MotivoDeTipoForaDosTresOuInativoERecusadoNoIndice() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");
        valor("MOTIVO_ALTERACAO_PLANO", "P1").setAtivo(false);
        valor("FREGUESIA", "F01");

        assertThatThrownBy(() -> validador.verificarMotivoAlteracao(processoDaAlteracaoPdm(), true,
                List.of(lei("L1"), new MotivoAlteracaoRequest("FREGUESIA", "F01"), new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_PLANO", "P1"))))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos()).containsExactly(
                        new CampoErro("motivosAlteracao[1]", "Motivo inválido."),
                        new CampoErro("motivosAlteracao[2]", "Motivo inválido.")));
    }

    @Test
    void motivosRepetidosGuardamSeUmaVezPelaOrdemDoPedido() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");
        valor("MOTIVO_ALTERACAO_LEI", "L2");
        valor("MOTIVO_ALTERACAO_PROGRAMA", "G1");

        List<MotivoAlteracaoRequest> gravados = validador.verificarMotivoAlteracao(processoDaAlteracaoPdm(), true,
                List.of(lei("L2"), lei("L1"), lei("L2"), new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_PROGRAMA", "G1")));

        assertThat(gravados).containsExactly(lei("L2"), lei("L1"), new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_PROGRAMA", "G1"));
    }

    // Submissão

    @Test
    void submissaoSemLocalizacaoFalhaComFreguesiasAtivas() {
        valor("FREGUESIA", "F01");

        assertThat(validador.pendenciasLocalizacao(processoDaAlteracaoPdm())).containsExactly(
                new CampoErro("localizacao", "Indique a freguesia ou escolha \"Aplicável a toda a área do IGT\"."));
    }

    @Test
    void submissaoSemLocalizacaoPassaComAListaFreguesiaVaziaOuInativa() {
        assertThat(validador.pendenciasLocalizacao(processoDaAlteracaoPdm())).isEmpty();
        valor("FREGUESIA", "F01").setAtivo(false);
        assertThat(validador.pendenciasLocalizacao(processoDaAlteracaoPdm())).isEmpty();
    }

    @Test
    void submissaoComLocalizacaoOpcionalOuNaoPedidaNuncaExige() {
        valor("FREGUESIA", "F01");

        assertThat(validador.pendenciasLocalizacao(processo(PedeLocalizacao.OPCIONAL, PedeMotivoAlteracao.NAO))).isEmpty();
        assertThat(validador.pendenciasLocalizacao(processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO))).isEmpty();
    }

    @Test
    void submissaoAplicavelATodaAAreaDispensaAFreguesia() {
        valor("FREGUESIA", "F01");
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("localizacao", Map.of("aplicavelTodaArea", true)));

        assertThat(validador.pendenciasLocalizacao(processo)).isEmpty();
    }

    @Test
    void submissaoComFreguesiaDesativadaDepoisDaGravacaoFalha() {
        valor("FREGUESIA", "F01").setAtivo(false);
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("localizacao", Map.of("freguesia", "F01")));

        assertThat(validador.pendenciasLocalizacao(processo)).containsExactly(new CampoErro("localizacao.freguesia", "Valor inválido."));
    }

    @Test
    void submissaoSemRespostaAPerguntaFalhaMesmoComAsListasDeMotivosVazias() {
        assertThat(validador.pendenciasMotivoAlteracao(processoDaAlteracaoPdm())).containsExactly(new CampoErro(
                "conformaHierarquiaSuperior",
                "Responda se a alteração conforma o plano com uma lei, programa ou plano de hierarquia superior."));
    }

    @Test
    void submissaoComSimEMotivosAtivosExigePeloMenosUmMotivo() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("conformaHierarquiaSuperior", true));

        assertThat(validador.pendenciasMotivoAlteracao(processo))
                .containsExactly(new CampoErro("motivosAlteracao", "Escolha pelo menos um motivo da alteração."));
    }

    @Test
    void submissaoComSimEListasDeMotivosVaziasPassa() {
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("conformaHierarquiaSuperior", true));

        assertThat(validador.pendenciasMotivoAlteracao(processo)).isEmpty();
    }

    @Test
    void submissaoComNaoPassaSemMotivos() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("conformaHierarquiaSuperior", false));

        assertThat(validador.pendenciasMotivoAlteracao(processo)).isEmpty();
    }

    @Test
    void submissaoObrigatorioSemMotivosFalhaSoComValoresAtivos() {
        Processo processo = processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.OBRIGATORIO);
        assertThat(validador.pendenciasMotivoAlteracao(processo)).isEmpty();

        valor("MOTIVO_ALTERACAO_PROGRAMA", "G1");
        assertThat(validador.pendenciasMotivoAlteracao(processo))
                .containsExactly(new CampoErro("motivosAlteracao", "Escolha pelo menos um motivo da alteração."));
    }

    @Test
    void submissaoNumProcedimentoQueNaoPedeMotivosNaoExigeNada() {
        valor("MOTIVO_ALTERACAO_LEI", "L1");

        assertThat(validador.pendenciasMotivoAlteracao(processo(PedeLocalizacao.NAO, PedeMotivoAlteracao.NAO))).isEmpty();
    }

    @Test
    void submissaoComMotivoDesativadoDepoisDaGravacaoFalhaNoIndice() {
        valor("MOTIVO_ALTERACAO_LEI", "L1").setAtivo(false);
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("conformaHierarquiaSuperior", true,
                "motivosAlteracao", List.of(Map.of("tipo", "MOTIVO_ALTERACAO_LEI", "codigo", "L1"))));

        assertThat(validador.pendenciasMotivoAlteracao(processo)).containsExactly(new CampoErro("motivosAlteracao[0]", "Motivo inválido."));
    }

    @Test
    void depoisDaSubmissaoOsCodigosBloqueadosNaoVoltamASerConferidos() {
        valor("FREGUESIA", "F01").setAtivo(false);
        valor("MOTIVO_ALTERACAO_LEI", "L1").setAtivo(false);
        Processo processo = processoDaAlteracaoPdm();
        gravar(processo, Map.of("localizacao", Map.of("freguesia", "F01"), "conformaHierarquiaSuperior", true,
                "motivosAlteracao", List.of(Map.of("tipo", "MOTIVO_ALTERACAO_LEI", "codigo", "L1"))));
        processo.setDataSubmissao(Instant.now());

        assertThat(validador.pendenciasLocalizacao(processo)).isEmpty();
        assertThat(validador.pendenciasMotivoAlteracao(processo)).isEmpty();
        assertThatCode(() -> validador.verificarMotivoAlteracao(processo, true, List.of(lei("L1")))).doesNotThrowAnyException();
    }
}
