package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.incm.FalhaTecnicaIncm;
import com.conkord.saigt.integracao.incm.GetActDetailsResponse;
import com.conkord.saigt.integracao.incm.ResultadoChamadaIncm;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.SegmentoIdus;
import com.conkord.saigt.service.documento.MomentoRetencao;
import com.conkord.saigt.service.documento.RetencaoDocumentosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** O {@link ClienteIncm} é simulado: cada ramo da regra do BL-INT-003 sem depender do `incm-mock`. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class ConsultaIncmServiceRegrasTest extends ConsultaIncmServiceTestBase {

    private static final long ACT_IDENTIFIER = 900000000000001L;
    private static final String ACT = Long.toString(ACT_IDENTIFIER);
    private static final String IDUS_SUBMETIDO = "01.1111.01.000123.02.90001.01.031.01";

    @MockBean
    private ClienteIncm clienteIncm;

    @SpyBean
    private RetencaoDocumentosService retencaoDocumentosService;

    @Autowired
    private ConsultaIncmService service;

    @Autowired
    private IncmComunicacaoService comunicacaoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProcessoRejeicaoRepository processoRejeicaoRepository;

    @Autowired
    private IdusProcessoRepository idusProcessoRepository;

    @Autowired
    private IdusProcessoHistoricoRepository historicoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveDevolverSemAlteracaoSeAIncmAindaNaoPublicouNemRejeitou() {
        Processo processo = criarProcesso(EstadoProcesso.ENVIADO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Em edição", null, List.of()));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ENVIADO_INCM);
        assertThat(comunicacoes(processo)).hasSize(1);
        assertThat(auditorias(processo)).isEmpty();
    }

    @Test
    void devePublicarPassandoPrimeiroPorEmValidacaoIncmQuandoOProcessoEstavaEnviado() {
        Processo processo = criarProcesso(EstadoProcesso.ENVIADO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.PUBLICADO);

        Processo publicado = recarregar(processo);
        assertThat(publicado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(publicado.getNumeroDr()).isEqualTo("DR-2026-000001");
        assertThat(publicado.getDataPublicacaoDr()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(publicado.getLinkDr()).isEqualTo("https://exemplo.pt/dr/2026/000001");
        assertThat(auditorias(processo)).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes,
                Auditoria::getEstadoDepois, Auditoria::getUtilizadorId).containsExactlyInAnyOrder(
                tuple("RETER_DOCUMENTOS", null, null, null),
                tuple("VALIDACAO_INCM", "ENVIADO_INCM", "EM_VALIDACAO_INCM", null),
                tuple("PUBLICACAO_INCM", "EM_VALIDACAO_INCM", "PUBLICADO", null));
    }

    @Test
    void deveTerminarADinamicaQuandoAPublicacaoCompletaOsProcedimentosObrigatorios() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        service.consultar(processo.getId());

        Dinamica dinamica = dinamica(processo);
        assertThat(dinamica.getEstado()).isEqualTo(EstadoDinamica.TERMINADA);
        assertThat(dinamica.getDataConclusao()).isNotNull();
        assertThat(auditoriasDaDinamica(processo)).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes,
                Auditoria::getEstadoDepois, Auditoria::getUtilizadorId)
                .containsExactly(tuple("CONCLUSAO_AUTOMATICA", "EM_CURSO", "TERMINADA", null));
    }

    @Test
    void deveAtribuirONumeroDeDepositoNaMesmaTransacaoAoPublicarOProcedimentoFinal() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        jdbcTemplate.update("UPDATE tipo_dinamica SET codigo_idus = '02' WHERE id = (SELECT c.tipo_dinamica_id "
                + "FROM dinamica d JOIN instrumento_dinamica_config c ON c.id = d.instrumento_dinamica_config_id "
                + "WHERE d.id = ?)", processo.getDinamica().getId());
        jdbcTemplate.update("DELETE FROM municipio_codigo_deposito WHERE dtcc = '1111'");
        jdbcTemplate.update("INSERT INTO municipio_codigo_deposito (dtcc, codigo_regiao, codigo_municipio) "
                + "VALUES ('1111', '03', '03.11.11')");
        jdbcTemplate.update("INSERT INTO instrumento_concelho (instrumento_id, municipio_codigo) "
                + "SELECT instrumento_id, '1111' FROM dinamica WHERE id = ?", processo.getDinamica().getId());
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        service.consultar(processo.getId());

        Processo publicado = recarregar(processo);
        String sigla = processo.getDinamica().getInstrumento().getTipoInstrumento().getCodigo();
        int ano = LocalDate.now(ZoneId.of("Europe/Lisbon")).getYear();
        assertThat(publicado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(publicado.getNumeroDeposito()).matches("03\\.11\\.11/" + sigla + "/02/" + ano + "/[1-9][0-9]*");
        assertThat(auditorias(processo)).extracting(Auditoria::getAcao).contains("ATRIBUIR_NUMERO_DEPOSITO");
    }

    @Test
    void deveDeixarOProcedimentoFinalPublicadoSemNumeroDeDepositoQuandoFaltaOCodigoDaDinamica(CapturedOutput saida) {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        service.consultar(processo.getId());

        Processo publicado = recarregar(processo);
        assertThat(publicado.getEstado()).isEqualTo(EstadoProcesso.PUBLICADO);
        assertThat(publicado.getNumeroDeposito()).isNull();
        assertThat(saida.getOut()).contains("N.º de depósito por atribuir, falta tipo_dinamica.codigo_idus — processoId="
                + processo.getId());
    }

    @Test
    void deveAcrescentarOPubIncmAoIdusDoProcessoNaMesmaPublicacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        criarIdus(processo);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        service.consultar(processo.getId());

        IdusProcesso idus = idusProcessoRepository.findById(processo.getId()).orElseThrow();
        assertThat(idus.getPubIncm()).isEqualTo("20261024");
        assertThat(idus.getIdus()).isEqualTo(IDUS_SUBMETIDO + ".20261024");
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(processo.getId()))
                .extracting(IdusProcessoHistorico::getSegmento, IdusProcessoHistorico::getEvento,
                        IdusProcessoHistorico::getIdusAnterior, IdusProcessoHistorico::getIdusNovo)
                .containsExactly(tuple(SegmentoIdus.PUB_INCM, EventoIdus.PUBLICACAO_INCM, IDUS_SUBMETIDO,
                        IDUS_SUBMETIDO + ".20261024"));
    }

    @Test
    void naoDeveMexerNoIdusQuandoAIncmRejeitaOAto() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        criarIdus(processo);
        simular(processo, resposta(true, "SubmissionRejected", null, null, List.of("Entidade inexistente.")));

        service.consultar(processo.getId());

        IdusProcesso idus = idusProcessoRepository.findById(processo.getId()).orElseThrow();
        assertThat(idus.getPubIncm()).isNull();
        assertThat(idus.getIdus()).isEqualTo(IDUS_SUBMETIDO);
        assertThat(historicoRepository.findByProcessoIdOrderByRegistadoEmAsc(processo.getId())).isEmpty();
    }

    private void criarIdus(Processo processo) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                idusProcessoRepository.saveAndFlush(IdusProcesso.builder()
                        .processo(processoRepository.findById(processo.getId()).orElseThrow())
                        .idus(IDUS_SUBMETIDO)
                        .sistemaOrigem("01")
                        .idIgt("000123")
                        .idIgtDefinitivo(true)
                        .ordemDinamica("90001")
                        .tentativa((short) 1)
                        .procedimento("031")
                        .submissao((short) 1)
                        .atualizadoEm(Instant.now())
                        .build()));
    }

    @Test
    void deveAnularDiretamenteUmProcessoEnviadoQuandoAIncmRejeitaOAto() {
        Processo processo = criarProcesso(EstadoProcesso.ENVIADO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionRejected", null, null, List.of("Entidade inexistente.")));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        List<Auditoria> auditoria = auditorias(processo);
        assertThat(auditoria).extracting(Auditoria::getAcao, Auditoria::getEstadoAntes, Auditoria::getEstadoDepois)
                .containsExactly(tuple("REJEICAO_INCM", "ENVIADO_INCM", "ANULADO_INCM"));
        assertThat(auditoria.get(0).getDadosAlteracao()).contains("Entidade inexistente.");
        assertThat(comunicacoes(processo).get(0).getResposta()).contains("Entidade inexistente.");
        assertThat(dinamica(processo).getEstado()).isEqualTo(EstadoDinamica.EM_CURSO);
        assertThat(auditoriasDaDinamica(processo)).isEmpty();
    }

    @Test
    void deveAnularMesmoQueARespostaNaoTragaMensagens() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionRejected", null, null, null));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);

        assertThat(semEspacos(auditorias(processo).get(0).getDadosAlteracao())).contains("\"mensagens\":[]");
    }

    @Test
    void deveRegistarARecusaComASequenciaSeguinteEAsMensagensDeValidacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        processoRejeicaoRepository.saveAndFlush(ProcessoRejeicao.builder().processo(processo).sequencia(1)
                .origem(OrigemRejeicao.INCM).motivoTexto("Recusa anterior.").build());
        simular(processo, resposta(true, "SubmissionRejected", null, null,
                List.of("Entidade inexistente.", "Sumário em falta.")));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.ANULADO_INCM);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.ANULADO_INCM);
        assertThat(recusas(processo))
                .extracting(linha -> linha.get("sequencia"), linha -> linha.get("origem"),
                        linha -> linha.get("motivo_texto"), linha -> linha.get("criado_por"))
                .containsExactly(
                        tuple(1, "INCM", "Recusa anterior.", null),
                        tuple(2, "INCM", "Entidade inexistente.\nSumário em falta.", null));
        assertThat(auditorias(processo)).extracting(Auditoria::getAcao).containsExactly("REJEICAO_INCM");
    }

    @Test
    void deveRegistarARecusaComOTextoPorOmissaoQuandoAIncmNaoIndicaMotivo() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionRejected", null, null, null));

        service.consultar(processo.getId());

        assertThat(recusas(processo)).singleElement()
                .satisfies(linha -> assertThat(linha.get("motivo_texto")).isEqualTo("A INCM não indicou o motivo."));
    }

    @Test
    void deveUsarOErrorMessageQuandoNaoHaListaDeErrosDeValidacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, new GetActDetailsResponse(true, "Ato recusado pela INCM.", "SubmissionRejected", null, null,
                null, null, null, null, null, null, null));

        service.consultar(processo.getId());

        assertThat(recusas(processo)).singleElement()
                .satisfies(linha -> assertThat(linha.get("motivo_texto")).isEqualTo("Ato recusado pela INCM."));
    }

    @Test
    void naoDeveRegistarRecusaQuandoAIncmPublica() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));

        service.consultar(processo.getId());

        assertThat(recusas(processo)).isEmpty();
    }

    @Test
    void naoDeveFicarRecusaQuandoOProcessoMudaDuranteAConsulta() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        TransactionTemplate outraTransacao = new TransactionTemplate(transactionManager);
        outraTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(clienteIncm.obterDetalhesAto(processo.getId(), ACT_IDENTIFIER)).thenAnswer(invocacao -> {
            outraTransacao.executeWithoutResult(estado -> {
                Processo concorrente = processoRepository.findById(processo.getId()).orElseThrow();
                concorrente.setContactoTramitacao("concorrente@exemplo.pt");
                processoRepository.saveAndFlush(concorrente);
            });
            return new ResultadoChamadaIncm<>(200, resposta(true, "SubmissionRejected", null, null,
                    List.of("Entidade inexistente.")), null, 5L);
        });

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(recusas(processo)).isEmpty();
    }

    @Test
    void naoDeveEscreverOTextoDaIncmNosLogsDaConsulta(CapturedOutput output) {
        Processo rejeitado = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(rejeitado, resposta(true, "SubmissionRejected", null, null, List.of("Entidade inexistente.")));
        Processo inexistente = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(inexistente, resposta(false, null, null, null, null));

        service.consultar(rejeitado.getId());
        service.consultar(inexistente.getId());

        assertThat(recusas(rejeitado)).hasSize(1);
        assertThat(output.getAll()).contains(inexistente.getId().toString())
                .doesNotContain("Entidade inexistente.", "The act does not exist.");
    }

    @Test
    void deveManterOProcessoQuandoAIncmDizQueOAtoNaoExiste() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(false, null, null, null, null));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getIsSuccess()).isFalse();
        assertThat(linha.getFalhaTecnica()).isNull();
        assertThat(linha.getTransicaoDerivada()).isNull();
    }

    @ParameterizedTest
    @EnumSource(FalhaTecnicaIncm.class)
    void deveDevolverFalhaTecnicaSemAlterarOProcesso(FalhaTecnicaIncm falha) {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        when(clienteIncm.obterDetalhesAto(processo.getId(), ACT_IDENTIFIER))
                .thenReturn(new ResultadoChamadaIncm<>(null, null, falha, 7L));

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.FALHA_TECNICA);

        Processo inalterado = recarregar(processo);
        assertThat(inalterado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(inalterado.getVersao()).isEqualTo(processo.getVersao());
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo(falha.name());
        assertThat(linha.getResposta()).isNull();
        assertThat(linha.getIsSuccess()).isNull();
        assertThat(linha.getRecebidoEm()).isNull();
        assertThat(auditorias(processo)).isEmpty();
    }

    @Test
    void deveRegistarFalhaTecnicaQuandoARespostaVemSemCorpo() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, null);

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.FALHA_TECNICA);

        Processo inalterado = recarregar(processo);
        assertThat(inalterado.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(inalterado.getVersao()).isEqualTo(processo.getVersao());
        IncmComunicacao linha = comunicacoes(processo).get(0);
        assertThat(linha.getFalhaTecnica()).isEqualTo(FalhaTecnicaIncm.ERRO_HTTP.name());
        assertThat(linha.getHttpStatus()).isEqualTo(200);
        assertThat(linha.getTransicaoDerivada()).isNull();
        assertThat(auditorias(processo)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProcesso.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"ENVIADO_INCM", "EM_VALIDACAO_INCM"})
    void naoDeveChamarAIncmForaDosEstadosEnviadoEEmValidacaoIncm(EstadoProcesso estado) {
        Processo processo = criarProcesso(estado, ACT);

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        verifyNoInteractions(clienteIncm);
        assertThat(comunicacoes(processo)).isEmpty();
        assertThat(recarregar(processo).getEstado()).isEqualTo(estado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "12x", "99999999999999999999"})
    void naoDeveChamarAIncmSemUmActIdentifierNumerico(String actIdentifier) {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, actIdentifier);

        assertThat(service.consultar(processo.getId())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        verifyNoInteractions(clienteIncm);
        assertThat(comunicacoes(processo)).isEmpty();
    }

    @Test
    void naoDeveChamarAIncmParaUmProcessoQueNaoExiste() {
        assertThat(service.consultar(idInexistente())).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);

        verifyNoInteractions(clienteIncm);
    }

    @Test
    void deveGravarOPedidoEASubmissaoCorrenteNaComunicacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        comunicacaoService.registar(new RegistoComunicacaoIncm(processo.getId(), OperacaoIncm.SUBMIT_NEW_ACT, null, 2,
                "SEGUNDA_H", Map.of("ActTitle", "Aviso"), null, null, null, null, null, null, null, null,
                Instant.parse("2026-10-20T10:00:00Z"), null));
        simular(processo, resposta(true, "SubmissionAccepted", "Em edição", null, List.of()));

        service.consultar(processo.getId());

        IncmComunicacao linha = comunicacoes(processo).get(1);
        assertThat(linha.getOperacao()).isEqualTo("GET_ACT_DETAILS");
        assertThat(linha.getSubmissao()).isEqualTo((short) 2);
        assertThat(linha.getSerieDr()).isNull();
        assertThat(linha.getActIdentifier()).isEqualTo(ACT_IDENTIFIER);
        assertThat(semEspacos(linha.getPedido())).contains("\"ActIdentifier\":" + ACT);
        assertThat(linha.getHttpStatus()).isEqualTo(200);
        assertThat(linha.getRecebidoEm()).isNotNull();
    }

    @Test
    void deveDeixarOProcessoComoEstavaEAComunicacaoGravadaQuandoHaAlteracaoConcorrente() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        TransactionTemplate outraTransacao = new TransactionTemplate(transactionManager);
        outraTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Enquanto a INCM responde, outra transação altera o processo e sobe-lhe a versão.
        when(clienteIncm.obterDetalhesAto(processo.getId(), ACT_IDENTIFIER)).thenAnswer(invocacao -> {
            outraTransacao.executeWithoutResult(estado -> {
                Processo concorrente = processoRepository.findById(processo.getId()).orElseThrow();
                concorrente.setContactoTramitacao("concorrente@exemplo.pt");
                processoRepository.saveAndFlush(concorrente);
            });
            return new ResultadoChamadaIncm<>(200, resposta(true, "SubmissionAccepted", "Publicado", publicacao(),
                    List.of()), null, 5L);
        });

        DesfechoConsultaIncm desfecho = service.consultar(processo.getId());

        assertThat(desfecho).isEqualTo(DesfechoConsultaIncm.SEM_ALTERACAO);
        Processo atual = recarregar(processo);
        assertThat(atual.getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(atual.getNumeroDr()).isNull();
        assertThat(atual.getContactoTramitacao()).isEqualTo("concorrente@exemplo.pt");
        assertThat(comunicacoes(processo)).hasSize(1);
        assertThat(auditorias(processo)).isEmpty();
    }

    private List<Map<String, Object>> recusas(Processo processo) {
        return jdbcTemplate.queryForList("SELECT sequencia, origem, motivo_texto, criado_por "
                + "FROM processo_rejeicao WHERE processo_id = ? ORDER BY sequencia", processo.getId());
    }

    private void simular(Processo processo, GetActDetailsResponse resposta) {
        when(clienteIncm.obterDetalhesAto(eq(processo.getId()), anyLong()))
                .thenReturn(new ResultadoChamadaIncm<>(200, resposta, null, 5L));
    }

    private static GetActDetailsResponse resposta(boolean isSuccess, String processingStatus, String actStatus,
                                                  GetActDetailsResponse.Publication publicacao, List<String> erros) {
        return new GetActDetailsResponse(isSuccess, isSuccess ? null : "The act does not exist.", processingStatus,
                null, null, publicacao == null ? null : "DR-2026-000001", null, null, actStatus, erros, null,
                publicacao);
    }

    @Test
    void deveFicarNoEstadoAnteriorParaAExecucaoSeguinteQuandoARetencaoFalhaNaPublicacao() {
        Processo processo = criarProcesso(EstadoProcesso.EM_VALIDACAO_INCM, ACT);
        simular(processo, resposta(true, "SubmissionAccepted", "Publicado", publicacao(), List.of()));
        doThrow(new StorageIndisponivelException("Armazenamento indisponível.", new IllegalStateException()))
                .when(retencaoDocumentosService).reterDocumentosSemRetencao(any(), eq(MomentoRetencao.ENTRADA_PUBLICADO));

        assertThatThrownBy(() -> service.consultar(processo.getId())).isInstanceOf(StorageIndisponivelException.class);

        assertThat(recarregar(processo).getEstado()).isEqualTo(EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(auditorias(processo)).isEmpty();
    }

    private static GetActDetailsResponse.Publication publicacao() {
        return new GetActDetailsResponse.Publication("https://exemplo.pt/dr/2026/000001.pdf", "Aviso n.º 1/2026",
                "https://exemplo.pt/dr/2026/000001", "Diário da República n.º 206/2026", LocalDate.of(2026, 10, 24));
    }
}
