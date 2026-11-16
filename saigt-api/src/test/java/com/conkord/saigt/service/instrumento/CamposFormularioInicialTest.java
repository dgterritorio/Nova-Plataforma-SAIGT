package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AtualizarDadosFormularioRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseDeliberacaoRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseRequest;
import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.exception.CampoBloqueadoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.operacional.Processo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CamposFormularioInicialTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Processo processoSubmetido() {
        Processo processo = new Processo();
        processo.setDataSubmissao(Instant.now());
        processo.setSumario("Resumo do ato.");
        processo.setDataAssinatura(LocalDate.of(2026, 10, 1));
        processo.setNormaLegal("Norma Legal X");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setPublicarSuplemento(false);
        processo.setContactoTramitacao("tramitacao@cm-teste.pt");
        processo.setEntidadeEmitente("Câmara Municipal de Teste");
        processo.setContactoPublicacao("Gabinete de Planeamento, 210 000 000");
        processo.setElementosAfetados(List.of("PECAS_ESCRITAS", "OUTROS"));
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2,\"textoPublicar\":\"Texto\","
                + "\"dataDeliberacao\":\"2026-09-15\"},\"textoAto\":{\"avaliacaoAmbiental\":true}}}");
        return processo;
    }

    private static AtualizarDadosFormularioRequest pedidoIgualAoGravado() {
        return pedido("Resumo do ato.", List.of("PECAS_ESCRITAS", "OUTROS"), "Texto");
    }

    private static AtualizarDadosFormularioRequest pedido(String sumario, List<String> elementosAfetados, String textoPublicar) {
        return new AtualizarDadosFormularioRequest(1L, sumario, LocalDate.of(2026, 10, 1), "Norma Legal X", "TEXTO_INTEGRAL",
                false, null, "tramitacao@cm-teste.pt", "Câmara Municipal de Teste", "Gabinete de Planeamento, 210 000 000",
                elementosAfetados, null,
                new InformacaoBaseRequest(
                        new InformacaoBaseDeliberacaoRequest(2, null, null, null, LocalDate.of(2026, 9, 15), textoPublicar),
                        new com.conkord.saigt.dto.instrumento.InformacaoBaseTextoAtoRequest(null, null, null, true)));
    }

    private static void verificar(Processo processo, AtualizarDadosFormularioRequest pedido) {
        CamposFormularioInicial.verificarAlteracoes(processo, CamposFormularioInicial.pedidos(pedido), OBJECT_MAPPER);
    }

    @Test
    void camposBloqueados_semSubmissao_vazio() {
        Processo processo = processoSubmetido();
        processo.setDataSubmissao(null);

        assertThat(CamposFormularioInicial.camposBloqueados(processo)).isEmpty();
    }

    @Test
    void camposBloqueados_depoisDaSubmissao_sao31() {
        assertThat(CamposFormularioInicial.camposBloqueados(processoSubmetido()))
                .hasSize(31)
                .containsExactlyElementsOf(CamposFormularioInicial.CHAVES)
                .contains("entidadeEmitente", "entidadeInstrutora", "contactoPublicacao", "fundamentacaoRjigt", "introduzPlantaNova", "localizacao",
                        "conformaHierarquiaSuperior", "motivosAlteracao", "dinamicaAssociadaId",
                        "dadosFormulario.simultaneoComPmot", "modalidadeRatificacao", "tipoAtoId", "serieDr");
    }

    private static AtualizarDadosFormularioRequest pedidoComSerie(String serieDr) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, null, null, null, null, null, null, null, null, null, serieDr);
    }

    @Test
    void verificarAlteracoes_serieSegueARegraGeralDoBloqueioEOmitidaNaoConta() {
        Processo processo = processoSubmetido();
        processo.setSerieDr("SEGUNDA_H");

        assertThatCode(() -> verificar(processo, pedidoComSerie("SEGUNDA_H"))).doesNotThrowAnyException();
        assertThatCode(() -> verificar(processo, pedidoComSerie(null))).doesNotThrowAnyException();
        assertThatThrownBy(() -> verificar(processo, pedidoComSerie("PRIMEIRA")))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("serieDr"));

        processo.setCamposDesbloqueados(List.of("serieDr"));
        assertThatCode(() -> verificar(processo, pedidoComSerie("PRIMEIRA"))).doesNotThrowAnyException();
        assertThat(CamposFormularioInicial.alteracoesDesbloqueadas(processo,
                CamposFormularioInicial.pedidos(pedidoComSerie("PRIMEIRA")), OBJECT_MAPPER))
                .containsExactly(new CamposFormularioInicial.Alteracao("serieDr", "SEGUNDA_H", "PRIMEIRA"));
        assertThat(CamposFormularioInicial.grupo("serieDr")).containsExactly("serieDr");
    }

    private static AtualizarDadosFormularioRequest pedidoComTipoAto(Long tipoAtoId) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, null, null, null, null, null, null, null, null, tipoAtoId);
    }

    private static Processo processoSubmetidoComTipoAto(long id) {
        Processo processo = processoSubmetido();
        com.conkord.saigt.persistence.core.TipoAto tipoAto = new com.conkord.saigt.persistence.core.TipoAto("AVISO", "Aviso", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(tipoAto, "id", id);
        processo.setTipoAto(tipoAto);
        return processo;
    }

    @Test
    void verificarAlteracoes_tipoAtoSegueARegraGeralDoBloqueioEOmitidoNaoConta() {
        assertThatCode(() -> verificar(processoSubmetidoComTipoAto(5L), pedidoComTipoAto(5L))).doesNotThrowAnyException();
        assertThatCode(() -> verificar(processoSubmetidoComTipoAto(5L), pedidoComTipoAto(null))).doesNotThrowAnyException();
        assertThatThrownBy(() -> verificar(processoSubmetidoComTipoAto(5L), pedidoComTipoAto(6L)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("tipoAtoId"));
        assertThatThrownBy(() -> verificar(processoSubmetido(), pedidoComTipoAto(6L)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("tipoAtoId"));

        Processo desbloqueado = processoSubmetidoComTipoAto(5L);
        desbloqueado.setCamposDesbloqueados(List.of("tipoAtoId"));
        assertThatCode(() -> verificar(desbloqueado, pedidoComTipoAto(6L))).doesNotThrowAnyException();
        assertThat(CamposFormularioInicial.alteracoesDesbloqueadas(desbloqueado,
                CamposFormularioInicial.pedidos(pedidoComTipoAto(6L)), OBJECT_MAPPER))
                .containsExactly(new CamposFormularioInicial.Alteracao("tipoAtoId", "5", "6"));
        assertThat(CamposFormularioInicial.grupo("tipoAtoId")).containsExactly("tipoAtoId");
    }

    @Test
    void camposBloqueados_antesDaSubmissaoNaoIncluiOTipoAto() {
        Processo processo = processoSubmetidoComTipoAto(5L);
        processo.setDataSubmissao(null);

        assertThat(CamposFormularioInicial.camposBloqueados(processo)).doesNotContain("tipoAtoId");
    }

    private static AtualizarDadosFormularioRequest pedidoComSimultaneoComPmot(String resposta) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, null, null, null, null, null,
                resposta == null ? null : new com.conkord.saigt.dto.instrumento.DadosFormularioRequest(resposta));
    }

    private static Processo processoSubmetidoComSimultaneoComPmot(String resposta) {
        Processo processo = processoSubmetido();
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2,\"textoPublicar\":\"Texto\","
                + "\"dataDeliberacao\":\"2026-09-15\"},\"textoAto\":{\"avaliacaoAmbiental\":true}},"
                + "\"simultaneoComPmot\":\"" + resposta + "\"}");
        return processo;
    }

    @Test
    void verificarAlteracoes_simultaneoComPmotSegueARegraGeralDoBloqueio() {
        assertThatCode(() -> verificar(processoSubmetidoComSimultaneoComPmot("SIM"), pedidoComSimultaneoComPmot("SIM")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verificar(processoSubmetidoComSimultaneoComPmot("SIM"), pedidoComSimultaneoComPmot("NAO")))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("dadosFormulario.simultaneoComPmot"));
        assertThatThrownBy(() -> verificar(processoSubmetidoComSimultaneoComPmot("NAO"), pedidoComSimultaneoComPmot(null)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("dadosFormulario.simultaneoComPmot"));

        Processo desbloqueado = processoSubmetidoComSimultaneoComPmot("SIM");
        desbloqueado.setCamposDesbloqueados(List.of("dadosFormulario.simultaneoComPmot"));
        assertThatCode(() -> verificar(desbloqueado, pedidoComSimultaneoComPmot("NAO"))).doesNotThrowAnyException();
    }

    @Test
    void lerSimultaneoComPmot_devolveOTextoGravadoENuloNosOutrosCasos() {
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot("{\"simultaneoComPmot\":\"SIM\"}", OBJECT_MAPPER)).isEqualTo("SIM");
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot("{\"simultaneoComPmot\":\"NAO\"}", OBJECT_MAPPER)).isEqualTo("NAO");
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot("{\"simultaneoComPmot\":true}", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot("{\"informacaoBase\":{}}", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot("{isto não é json", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerSimultaneoComPmot(null, OBJECT_MAPPER)).isNull();
    }

    private static AtualizarDadosFormularioRequest pedidoComDinamicaAssociada(java.util.UUID dinamicaAssociadaId) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, null, null, null, null, dinamicaAssociadaId);
    }

    @Test
    void verificarAlteracoes_dinamicaAssociadaSegueARegraGeralDoBloqueio() {
        Processo processo = processoSubmetido();
        com.conkord.saigt.persistence.operacional.Dinamica associada = new com.conkord.saigt.persistence.operacional.Dinamica();
        java.util.UUID id = java.util.UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(associada, "id", id);
        processo.setDinamicaAssociada(associada);

        assertThatCode(() -> verificar(processo, pedidoComDinamicaAssociada(id))).doesNotThrowAnyException();
        assertThatThrownBy(() -> verificar(processo, pedidoComDinamicaAssociada(java.util.UUID.randomUUID())))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("dinamicaAssociadaId"));
    }

    @Test
    void camposBloqueados_excluiOsDesbloqueados() {
        Processo processo = processoSubmetido();
        processo.setCamposDesbloqueados(List.of("sumario"));

        assertThat(CamposFormularioInicial.camposBloqueados(processo)).hasSize(30).doesNotContain("sumario");
    }

    private static AtualizarDadosFormularioRequest pedidoComLocalizacaoEMotivos(LocalizacaoRequest localizacao, Boolean conforma,
                                                                              List<MotivoAlteracaoRequest> motivos) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, localizacao, conforma, motivos);
    }

    private static Processo processoSubmetidoComLocalizacaoEMotivos() {
        Processo processo = processoSubmetido();
        processo.setDadosFormulario("{\"localizacao\":{\"freguesia\":\"F01\",\"toponimoTexto\":\"Bairro\"},"
                + "\"conformaHierarquiaSuperior\":true,\"motivosAlteracao\":[{\"tipo\":\"MOTIVO_ALTERACAO_LEI\",\"codigo\":\"L1\"},"
                + "{\"tipo\":\"MOTIVO_ALTERACAO_PLANO\",\"codigo\":\"P1\"}],"
                + "\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2,\"textoPublicar\":\"Texto\","
                + "\"dataDeliberacao\":\"2026-09-15\"},\"textoAto\":{\"avaliacaoAmbiental\":true}}}");
        return processo;
    }

    @Test
    void verificarAlteracoes_localizacaoEMotivosIguais_passamMesmoComOutraOrdemEBrancos() {
        AtualizarDadosFormularioRequest pedido = pedidoComLocalizacaoEMotivos(new LocalizacaoRequest(false, " F01 ", null, "Bairro"), true,
                List.of(new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_PLANO", "P1"), new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_LEI", "L1")));

        assertThatCode(() -> verificar(processoSubmetidoComLocalizacaoEMotivos(), pedido)).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_localizacaoRespostaEMotivosAlterados_saoRecusados() {
        AtualizarDadosFormularioRequest pedido = pedidoComLocalizacaoEMotivos(new LocalizacaoRequest(true, null, null, null), false,
                List.of());

        assertThatThrownBy(() -> verificar(processoSubmetidoComLocalizacaoEMotivos(), pedido))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path)
                        .containsExactlyInAnyOrder("localizacao", "conformaHierarquiaSuperior", "motivosAlteracao"));
    }

    @Test
    void lerLocalizacaoEMotivos_devolvemOGravadoOuVazio() {
        Processo processo = processoSubmetidoComLocalizacaoEMotivos();

        assertThat(CamposFormularioInicial.lerLocalizacao(processo.getDadosFormulario(), OBJECT_MAPPER))
                .isEqualTo(new LocalizacaoRequest(null, "F01", null, "Bairro"));
        assertThat(CamposFormularioInicial.lerConformaHierarquiaSuperior(processo.getDadosFormulario(), OBJECT_MAPPER)).isTrue();
        assertThat(CamposFormularioInicial.lerMotivosAlteracao(processo.getDadosFormulario(), OBJECT_MAPPER)).containsExactly(
                new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_LEI", "L1"), new MotivoAlteracaoRequest("MOTIVO_ALTERACAO_PLANO", "P1"));
        assertThat(CamposFormularioInicial.lerLocalizacao(null, OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerMotivosAlteracao("{}", OBJECT_MAPPER)).isEmpty();
    }

    @Test
    void verificarAlteracoes_mesmosValores_passa() {
        assertThatCode(() -> verificar(processoSubmetido(), pedidoIgualAoGravado())).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_nuloEBrancoNaoSaoAlteracao() {
        Processo processo = processoSubmetido();
        processo.setNormaLegal(null);
        processo.setContactoTramitacao(null);
        processo.setContactoPublicacao(null);
        AtualizarDadosFormularioRequest pedido = new AtualizarDadosFormularioRequest(1L, "  Resumo do ato.  ",
                LocalDate.of(2026, 10, 1), "   ", "TEXTO_INTEGRAL", false, null, "", "  Câmara Municipal de Teste  ", "  ",
                List.of("PECAS_ESCRITAS", "OUTROS"), null, pedidoIgualAoGravado().informacaoBase());

        assertThatCode(() -> verificar(processo, pedido)).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_elementosAfetadosComOrdemDiferente_naoSaoAlteracao() {
        assertThatCode(() -> verificar(processoSubmetido(), pedido("Resumo do ato.", List.of("OUTROS", "PECAS_ESCRITAS"), "Texto")))
                .doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_elementosAfetadosNulosEVazios_saoIguais() {
        Processo processo = processoSubmetido();
        processo.setElementosAfetados(null);

        assertThatCode(() -> verificar(processo, pedido("Resumo do ato.", List.of(), "Texto"))).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_sumarioAlterado_listaApenasEsseCampo() {
        assertThatThrownBy(() -> verificar(processoSubmetido(), pedido("Outro resumo.", List.of("PECAS_ESCRITAS", "OUTROS"), "Texto")))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CAMPO_BLOQUEADO");
                    assertThat(e.getMessage()).isEqualTo("Desbloqueie o campo antes de o alterar.");
                    assertThat(e.getCampos()).containsExactly(
                            new CampoErro("sumario", "Campo bloqueado depois da primeira submissão."));
                });
    }

    @Test
    void verificarAlteracoes_informacaoBaseEElementosAlterados_listaAsDuasChaves() {
        assertThatThrownBy(() -> verificar(processoSubmetido(), pedido("Resumo do ato.", List.of("PECAS_GRAFICAS"), "Outro texto")))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path)
                                .containsExactly("elementosAfetados", "informacaoBase.deliberacao.textoPublicar"));
    }

    @Test
    void verificarAlteracoes_informacaoBaseOmitida_contaComoAlteracaoDoGravado() {
        AtualizarDadosFormularioRequest semInformacaoBase = new AtualizarDadosFormularioRequest(1L, "Resumo do ato.",
                LocalDate.of(2026, 10, 1), "Norma Legal X", "TEXTO_INTEGRAL", false, null, "tramitacao@cm-teste.pt",
                "Câmara Municipal de Teste", "Gabinete de Planeamento, 210 000 000",
                List.of("PECAS_ESCRITAS", "OUTROS"), null, null);

        assertThatThrownBy(() -> verificar(processoSubmetido(), semInformacaoBase))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).contains(
                                "informacaoBase.deliberacao.prazoAnos", "informacaoBase.deliberacao.textoPublicar",
                                "informacaoBase.deliberacao.dataDeliberacao", "informacaoBase.textoAto.avaliacaoAmbiental"));
    }

    @Test
    void verificarAlteracoes_entidadeEmitenteEContactoPublicacaoAlterados_listaAsDuasChaves() {
        Map<String, Object> pedidos = CamposFormularioInicial.pedidos(pedidoIgualAoGravado());
        pedidos.put("entidadeEmitente", "Outra entidade");
        pedidos.put("contactoPublicacao", null);

        assertThatThrownBy(() -> CamposFormularioInicial.verificarAlteracoes(processoSubmetido(), pedidos, OBJECT_MAPPER))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path)
                                .containsExactly("entidadeEmitente", "contactoPublicacao"));
    }

    @Test
    void verificarAlteracoes_entidadeInstrutoraAlterada_listaApenasEssaChaveSemContarOsEspacosDasPontas() {
        Processo processo = processoSubmetido();
        processo.setEntidadeInstrutora("Comissão de Coordenação de Teste");
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        AtualizarDadosFormularioRequest comInstrutora = new AtualizarDadosFormularioRequest(base.versao(), base.sumario(),
                base.dataAssinatura(), base.normaLegal(), base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(),
                base.contactoTramitacao(), base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(),
                base.fundamentacaoRjigt(), base.informacaoBase(), null, null, null, null, null,
                " Comissão de Coordenação de Teste ");
        Map<String, Object> pedidos = CamposFormularioInicial.pedidos(comInstrutora);

        assertThatCode(() -> CamposFormularioInicial.verificarAlteracoes(processo, pedidos, OBJECT_MAPPER))
                .doesNotThrowAnyException();

        pedidos.put("entidadeInstrutora", "Outra entidade");
        assertThatThrownBy(() -> CamposFormularioInicial.verificarAlteracoes(processo, pedidos, OBJECT_MAPPER))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly("entidadeInstrutora"));
    }

    @Test
    void verificarAlteracoes_dadosFormularioIlegivel_recusaValoresPedidos() {
        Processo processo = processoSubmetido();
        processo.setDadosFormulario("{isto não é json");

        assertThatThrownBy(() -> verificar(processo, pedidoIgualAoGravado())).isInstanceOf(CampoBloqueadoException.class);
    }

    @Test
    void verificarAlteracoes_campoDesbloqueado_podeMudar() {
        Processo processo = processoSubmetido();
        processo.setCamposDesbloqueados(List.of("sumario"));

        assertThatCode(() -> verificar(processo, pedido("Outro resumo.", List.of("PECAS_ESCRITAS", "OUTROS"), "Texto")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CamposFormularioInicial.verificarAlteracoes(processo,
                Map.of("normaLegal", "Outra norma"), OBJECT_MAPPER))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly("normaLegal"));
    }

    @Test
    void verificarAlteracoes_semSubmissao_nuncaRecusa() {
        Processo processo = processoSubmetido();
        processo.setDataSubmissao(null);

        assertThatCode(() -> verificar(processo, pedido("Outro resumo.", List.of(), "Outro texto"))).doesNotThrowAnyException();
    }

    @Test
    void submeter_soCompararOsCamposQueOPedidoTraz() {
        Processo processo = processoSubmetido();
        SubmeterProcessoRequest igual = new SubmeterProcessoRequest(1L, "Resumo do ato.", LocalDate.of(2026, 10, 1),
                "Norma Legal X", "TEXTO_INTEGRAL", false, null);
        SubmeterProcessoRequest dataDiferente = new SubmeterProcessoRequest(1L, "Resumo do ato.", LocalDate.of(2026, 10, 2),
                "Norma Legal X", "TEXTO_INTEGRAL", false, null);

        assertThat(CamposFormularioInicial.pedidos(igual)).hasSize(6).doesNotContainKeys("contactoTramitacao", "entidadeEmitente", "contactoPublicacao");
        assertThatCode(() -> CamposFormularioInicial.verificarAlteracoes(processo, CamposFormularioInicial.pedidos(igual), OBJECT_MAPPER))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CamposFormularioInicial.verificarAlteracoes(processo, CamposFormularioInicial.pedidos(dataDiferente), OBJECT_MAPPER))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly("dataAssinatura"));
    }

    private static AtualizarDadosFormularioRequest pedidoComPlantaNova(Boolean introduzPlantaNova) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(1L, base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), null, base.contactoTramitacao(), base.entidadeEmitente(),
                base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(), null, introduzPlantaNova);
    }

    private static Processo processoSubmetidoComPlantaNova(String valor) {
        Processo processo = processoSubmetido();
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2,\"textoPublicar\":\"Texto\","
                + "\"dataDeliberacao\":\"2026-09-15\"},\"textoAto\":{\"avaliacaoAmbiental\":true}},\"introduzPlantaNova\":" + valor + "}");
        return processo;
    }

    @Test
    void verificarAlteracoes_introduzPlantaNovaIgualAoGravado_passa() {
        assertThatCode(() -> verificar(processoSubmetidoComPlantaNova("true"), pedidoComPlantaNova(true))).doesNotThrowAnyException();
        assertThatCode(() -> verificar(processoSubmetidoComPlantaNova("false"), pedidoComPlantaNova(false))).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_introduzPlantaNovaAlterada_listaApenasEssaChave() {
        assertThatThrownBy(() -> verificar(processoSubmetidoComPlantaNova("true"), pedidoComPlantaNova(false)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).containsExactly(
                                new CampoErro("introduzPlantaNova", "Campo bloqueado depois da primeira submissão.")));
        assertThatThrownBy(() -> verificar(processoSubmetidoComPlantaNova("false"), pedidoComPlantaNova(null)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly("introduzPlantaNova"));
        assertThatThrownBy(() -> verificar(processoSubmetido(), pedidoComPlantaNova(true)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e ->
                        assertThat(e.getCampos()).extracting(CampoErro::path).containsExactly("introduzPlantaNova"));
    }

    @Test
    void verificarAlteracoes_introduzPlantaNovaDesbloqueada_podeMudar() {
        Processo processo = processoSubmetidoComPlantaNova("true");
        processo.setCamposDesbloqueados(List.of("introduzPlantaNova"));

        assertThatCode(() -> verificar(processo, pedidoComPlantaNova(false))).doesNotThrowAnyException();
    }

    @Test
    void lerIntroduzPlantaNova_devolveOBooleanoGravadoENuloNosOutrosCasos() {
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova("{\"introduzPlantaNova\":true}", OBJECT_MAPPER)).isTrue();
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova("{\"introduzPlantaNova\":false}", OBJECT_MAPPER)).isFalse();
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova("{\"informacaoBase\":{}}", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova("{\"introduzPlantaNova\":\"true\"}", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova("{isto não é json", OBJECT_MAPPER)).isNull();
        assertThat(CamposFormularioInicial.lerIntroduzPlantaNova(null, OBJECT_MAPPER)).isNull();
    }

    private static AtualizarDadosFormularioRequest pedidoComModalidade(String modalidade) {
        AtualizarDadosFormularioRequest base = pedidoIgualAoGravado();
        return new AtualizarDadosFormularioRequest(base.versao(), base.sumario(), base.dataAssinatura(), base.normaLegal(),
                base.tipoInformacao(), base.publicarSuplemento(), base.dataSuplemento(), base.contactoTramitacao(),
                base.entidadeEmitente(), base.contactoPublicacao(), base.elementosAfetados(), null, base.informacaoBase(),
                null, null, null, null, null, null, null, null, null, modalidade);
    }

    private static Processo processoSubmetidoComModalidade(String modalidade) {
        Processo processo = processoSubmetido();
        processo.setDadosFormulario("{\"informacaoBase\":{\"deliberacao\":{\"prazoAnos\":2,\"textoPublicar\":\"Texto\","
                + "\"dataDeliberacao\":\"2026-09-15\"},\"textoAto\":{\"avaliacaoAmbiental\":true}},"
                + "\"modalidadeRatificacao\":\"" + modalidade + "\"}");
        return processo;
    }

    @Test
    void verificarAlteracoes_modalidadeRatificacaoSegueARegraGeralDoBloqueio() {
        assertThatCode(() -> verificar(processoSubmetidoComModalidade("ALTERACAO"), pedidoComModalidade("ALTERACAO")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verificar(processoSubmetidoComModalidade("ALTERACAO"), pedidoComModalidade("PUBLICACAO")))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("modalidadeRatificacao"));
        assertThatThrownBy(() -> verificar(processoSubmetidoComModalidade("ALTERACAO"), pedidoComModalidade(null)))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("modalidadeRatificacao"));

        Processo desbloqueado = processoSubmetidoComModalidade("ALTERACAO");
        desbloqueado.setCamposDesbloqueados(List.of("modalidadeRatificacao"));
        assertThatCode(() -> verificar(desbloqueado, pedidoComModalidade("PUBLICACAO"))).doesNotThrowAnyException();
    }

    @Test
    void verificarAlteracoes_noSubmeterAModalidadeOmitidaFicaAGravada() {
        Processo processo = processoSubmetidoComModalidade("PUBLICACAO");
        SubmeterProcessoRequest semModalidade = new SubmeterProcessoRequest(1L, "Resumo do ato.", LocalDate.of(2026, 10, 1),
                "Norma Legal X", "TEXTO_INTEGRAL", false, null, null, null, null);
        SubmeterProcessoRequest outraModalidade = new SubmeterProcessoRequest(1L, "Resumo do ato.", LocalDate.of(2026, 10, 1),
                "Norma Legal X", "TEXTO_INTEGRAL", false, null, null, null, "ALTERACAO");

        assertThat(CamposFormularioInicial.pedidos(semModalidade)).doesNotContainKey("modalidadeRatificacao");
        assertThatThrownBy(() -> CamposFormularioInicial.verificarAlteracoes(processo,
                CamposFormularioInicial.pedidos(outraModalidade), OBJECT_MAPPER))
                .isInstanceOfSatisfying(CampoBloqueadoException.class, e -> assertThat(e.getCampos())
                        .extracting(CampoErro::path).containsExactly("modalidadeRatificacao"));
    }

    @Test
    void grupo_aChaveSozinhaAsLigadasJuntasEVazioForaDoFormulario() {
        assertThat(CamposFormularioInicial.grupo("sumario")).containsExactly("sumario");
        assertThat(CamposFormularioInicial.grupo("dinamicaAssociadaId"))
                .containsExactly("dinamicaAssociadaId", "dadosFormulario.simultaneoComPmot");
        assertThat(CamposFormularioInicial.grupo("dadosFormulario.simultaneoComPmot"))
                .containsExactly("dinamicaAssociadaId", "dadosFormulario.simultaneoComPmot");
        assertThat(CamposFormularioInicial.grupo("motivosAlteracao"))
                .containsExactly("conformaHierarquiaSuperior", "motivosAlteracao");
        assertThat(CamposFormularioInicial.grupo("publicarSuplemento")).containsExactly("publicarSuplemento", "dataSuplemento");
        assertThat(CamposFormularioInicial.grupo("informacaoBase.deliberacao.prazoMeses")).containsExactly(
                "informacaoBase.deliberacao.prazoAnos", "informacaoBase.deliberacao.prazoMeses",
                "informacaoBase.deliberacao.prazoDias");
        assertThat(CamposFormularioInicial.grupo("entidade")).isEmpty();
        assertThat(CamposFormularioInicial.grupo("numeroProcesso")).isEmpty();
        assertThat(CamposFormularioInicial.grupo(null)).isEmpty();
    }

    @Test
    void alteracoesDesbloqueadas_soAsDesbloqueadasComValorDiferente() {
        Processo processo = processoSubmetido();
        processo.setCamposDesbloqueados(List.of("sumario", "normaLegal"));

        List<CamposFormularioInicial.Alteracao> alteracoes = CamposFormularioInicial.alteracoesDesbloqueadas(processo,
                CamposFormularioInicial.pedidos(pedido("Outro resumo.", List.of("PECAS_ESCRITAS", "OUTROS"), "Texto")),
                OBJECT_MAPPER);

        assertThat(alteracoes).containsExactly(new CamposFormularioInicial.Alteracao("sumario", "Resumo do ato.", "Outro resumo."));
        assertThat(CamposFormularioInicial.alteracoesDesbloqueadas(processo,
                CamposFormularioInicial.pedidos(pedidoIgualAoGravado()), OBJECT_MAPPER)).isEmpty();
    }

    @Test
    void alteracoesDesbloqueadas_vaziaSemSubmissaoOuSemDesbloqueios() {
        Processo semDesbloqueios = processoSubmetido();
        Processo naoSubmetido = processoSubmetido();
        naoSubmetido.setDataSubmissao(null);
        naoSubmetido.setCamposDesbloqueados(List.of("sumario"));
        Map<String, Object> pedidos = CamposFormularioInicial.pedidos(pedido("Outro resumo.", List.of("PECAS_ESCRITAS"), "Texto"));

        assertThat(CamposFormularioInicial.alteracoesDesbloqueadas(semDesbloqueios, pedidos, OBJECT_MAPPER)).isEmpty();
        assertThat(CamposFormularioInicial.alteracoesDesbloqueadas(naoSubmetido, pedidos, OBJECT_MAPPER)).isEmpty();
    }

    @Test
    void semAsAlteradas_tiraAAlteradaEOSeuGrupoEMantemAsOutras() {
        List<String> desbloqueados = List.of("sumario", "dinamicaAssociadaId", "dadosFormulario.simultaneoComPmot", "normaLegal");

        assertThat(CamposFormularioInicial.semAsAlteradas(desbloqueados,
                List.of(new CamposFormularioInicial.Alteracao("dinamicaAssociadaId", null, "x"))))
                .containsExactly("sumario", "normaLegal");
        assertThat(CamposFormularioInicial.semAsAlteradas(desbloqueados, List.of())).isEqualTo(desbloqueados);
        assertThat(CamposFormularioInicial.semAsAlteradas(null, List.of())).isNull();
    }
}
