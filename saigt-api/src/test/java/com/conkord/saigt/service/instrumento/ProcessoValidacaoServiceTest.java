package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.dto.instrumento.InformacaoBaseResponse;
import com.conkord.saigt.dto.instrumento.RejeicaoEmValidacaoResponse;
import com.conkord.saigt.dto.instrumento.TipoItemValidacao;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBaseRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.listavalores.ListaValoresService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessoValidacaoServiceTest {

    private final ProcessoDocumentoRepository processoDocumentoRepository = mock(ProcessoDocumentoRepository.class);
    private final ProcessoInformacaoBaseRepository processoInformacaoBaseRepository =
            mock(ProcessoInformacaoBaseRepository.class);
    private final DocumentoConfigRepository documentoConfigRepository = mock(DocumentoConfigRepository.class);
    private final ProcessoValidacaoRejeicaoRepository processoValidacaoRejeicaoRepository =
            mock(ProcessoValidacaoRejeicaoRepository.class);

    private final ProcessoValidacaoService service = new ProcessoValidacaoService(mock(ProcessoRepository.class),
            processoDocumentoRepository, processoInformacaoBaseRepository, documentoConfigRepository,
            processoValidacaoRejeicaoRepository, mock(ListaValoresService.class), mock(UtilizadorRepository.class),
            mock(AutorizacaoService.class), mock(ContextoUtilizadorResolver.class), mock(AuditoriaService.class));

    private void itensMarcados(ElementoInformacaoBase... elementos) {
        when(documentoConfigRepository.findElementosInformacaoBaseAtivos(any())).thenReturn(Set.of(elementos));
    }

    private Processo processo(String procedimento) {
        InstrumentoDinamicaConfig dinamicaConfig = new InstrumentoDinamicaConfig(null, null);
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig(dinamicaConfig,
                new TipoProcedimento(procedimento, procedimento), 1, "SEGUNDA_H");
        Utilizador autor = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM");
        Processo processo = new Processo(null, config, autor, null);
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        return processo;
    }

    private ProcessoDocumento documento(Processo processo, EstadoValidacao estado) {
        ProcessoDocumento documento = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "texto.pdf", "privado/texto.pdf", "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        documento.setEstadoValidacao(estado);
        return documento;
    }

    private ProcessoInformacaoBase elemento(Processo processo, ElementoInformacaoBase elemento, EstadoValidacao estado) {
        ProcessoInformacaoBase linha = new ProcessoInformacaoBase(processo, elemento);
        ReflectionTestUtils.setField(linha, "id", UUID.randomUUID());
        linha.setEstadoValidacao(estado);
        return linha;
    }

    private void dado(Processo processo, List<ProcessoDocumento> documentos, List<ProcessoInformacaoBase> elementos) {
        when(processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())).thenReturn(documentos);
        when(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).thenReturn(elementos);
    }

    @Test
    void deveDevolverVerdadeiroQuandoTodosOsDocumentosEOsDoisElementosEstaoValidados() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        dado(processo, List.of(documento(processo, EstadoValidacao.VALIDADO), documento(processo, EstadoValidacao.VALIDADO)),
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO),
                        elemento(processo, ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.VALIDADO)));

        assertThat(service.todosRevistos(processo)).isTrue();
    }

    @Test
    void deveDevolverFalsoQuandoUmDocumentoEstaPorValidar() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        dado(processo, List.of(documento(processo, EstadoValidacao.VALIDADO), documento(processo, EstadoValidacao.POR_VALIDAR)),
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO),
                        elemento(processo, ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.VALIDADO)));

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveIgnorarUmDocumentoSubstituidoQuandoOSubstitutoEstaValidado() {
        Processo processo = processo("ALTERACAO_SIMPLES");
        dado(processo, List.of(documento(processo, EstadoValidacao.SUBSTITUIDO), documento(processo, EstadoValidacao.VALIDADO)),
                List.of());

        assertThat(service.todosRevistos(processo)).isTrue();
    }

    @Test
    void deveDevolverFalsoQuandoOSubstitutoDeUmDocumentoSubstituidoAindaEstaPorValidar() {
        Processo processo = processo("ALTERACAO_SIMPLES");
        dado(processo, List.of(documento(processo, EstadoValidacao.SUBSTITUIDO), documento(processo, EstadoValidacao.POR_VALIDAR)),
                List.of());

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveDevolverFalsoQuandoUmDocumentoFoiRejeitado() {
        Processo processo = processo("ALTERACAO_SIMPLES");
        dado(processo, List.of(documento(processo, EstadoValidacao.REJEITADO)), List.of());

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveDevolverFalsoQuandoUmElementoDaInformacaoBaseEstaPorValidar() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        dado(processo, List.of(documento(processo, EstadoValidacao.VALIDADO)),
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO),
                        elemento(processo, ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR)));

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveContarComoPorReverUmElementoEsperadoSemLinha() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        dado(processo, List.of(),
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO)));

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveContarComoPorReverUmProcessoComElementosPrevistosSemNenhumaLinha() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        dado(processo, List.of(), List.of());

        assertThat(service.todosRevistos(processo)).isFalse();
    }

    @Test
    void deveDevolverVerdadeiroQuandoNaoHaDocumentosNemElementosEsperados() {
        Processo processo = processo("ALTERACAO_SIMPLES");
        dado(processo, List.of(), List.of());

        assertThat(service.todosRevistos(processo)).isTrue();
    }

    @Test
    void deveDevolverAInformacaoBaseOrdenadaPorElemento() {
        Processo processo = processo("DECISAO_INICIO");
        ProcessoInformacaoBase textoAto = elemento(processo, ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR);
        ProcessoInformacaoBase deliberacao = elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO);
        dado(processo, List.of(), List.of(textoAto, deliberacao));

        List<InformacaoBaseResponse> resposta = service.informacaoBase(processo);

        assertThat(resposta).containsExactly(
                new InformacaoBaseResponse(deliberacao.getId(), ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.VALIDADO),
                new InformacaoBaseResponse(textoAto.getId(), ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.POR_VALIDAR));
    }

    @Test
    void deveDevolverInformacaoBaseVaziaQuandoOProcessoAindaNaoTemElementos() {
        Processo processo = processo("DECISAO_INICIO");
        dado(processo, List.of(), List.of());

        assertThat(service.informacaoBase(processo)).isEmpty();
    }

    @Test
    void deveCriarAsLinhasDosElementosMarcadosNoProcedimento() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        when(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).thenReturn(List.of());

        service.criarItensInformacaoBase(processo);

        verify(processoInformacaoBaseRepository, times(2)).save(any(ProcessoInformacaoBase.class));
    }

    @Test
    void naoDeveCriarLinhasQuandoOProcedimentoNaoTemItensMarcados() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados();

        service.criarItensInformacaoBase(processo);

        verify(processoInformacaoBaseRepository, never()).save(any(ProcessoInformacaoBase.class));
    }

    @Test
    void deveCriarALinhaDeUmProcedimentoQueNaoEDecisaoDeInicioComItemMarcado() {
        Processo processo = processo("PUBLICACAO_FINAL");
        itensMarcados(ElementoInformacaoBase.TEXTO_ATO);
        when(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).thenReturn(List.of());

        service.criarItensInformacaoBase(processo);

        verify(processoInformacaoBaseRepository, times(1)).save(any(ProcessoInformacaoBase.class));
        verify(processoInformacaoBaseRepository).save(argThat(linha -> linha.getElemento() == ElementoInformacaoBase.TEXTO_ATO));
    }

    @Test
    void naoDeveDuplicarUmaLinhaQueOProcessoJaTem() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.DELIBERACAO, ElementoInformacaoBase.TEXTO_ATO);
        when(processoInformacaoBaseRepository.findByProcesso_Id(processo.getId())).thenReturn(
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.POR_VALIDAR)));

        service.criarItensInformacaoBase(processo);

        verify(processoInformacaoBaseRepository, times(1)).save(any(ProcessoInformacaoBase.class));
    }

    @Test
    void deveDevolverOsElementosPrevistosDoProcedimento() {
        Processo processo = processo("DECISAO_INICIO");
        itensMarcados(ElementoInformacaoBase.TEXTO_ATO);

        assertThat(service.elementosPrevistos(processo.getInstrumentoProcedimentoConfig()))
                .containsExactly(ElementoInformacaoBase.TEXTO_ATO);
    }

    @Test
    void reabrirRejeitadosPoeOsRejeitadosPorValidarEMantemOsValidadosEOsSubstituidos() {
        Processo processo = processo("DECISAO_INICIO");
        ProcessoDocumento rejeitado = documento(processo, EstadoValidacao.REJEITADO);
        ProcessoDocumento validado = documento(processo, EstadoValidacao.VALIDADO);
        ProcessoDocumento substituido = documento(processo, EstadoValidacao.SUBSTITUIDO);
        ProcessoInformacaoBase deliberacao = elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.REJEITADO);
        ProcessoInformacaoBase textoAto = elemento(processo, ElementoInformacaoBase.TEXTO_ATO, EstadoValidacao.VALIDADO);
        dado(processo, List.of(rejeitado, validado, substituido), List.of(deliberacao, textoAto));

        service.reabrirRejeitados(processo);

        assertThat(rejeitado.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(validado.getEstadoValidacao()).isEqualTo(EstadoValidacao.VALIDADO);
        assertThat(substituido.getEstadoValidacao()).isEqualTo(EstadoValidacao.SUBSTITUIDO);
        assertThat(deliberacao.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(textoAto.getEstadoValidacao()).isEqualTo(EstadoValidacao.VALIDADO);
        verify(processoDocumentoRepository).saveAll(List.of(rejeitado));
        verify(processoInformacaoBaseRepository).saveAll(List.of(deliberacao));
    }

    @Test
    void verificarRejeitadosCorrigidosRecusaQuandoFicaUmFicheiroRejeitado() {
        Processo processo = processo("DECISAO_INICIO");
        ProcessoDocumento rejeitado = documento(processo, EstadoValidacao.REJEITADO);
        ReflectionTestUtils.setField(rejeitado, "id", UUID.randomUUID());
        dado(processo, List.of(rejeitado, documento(processo, EstadoValidacao.SUBSTITUIDO)), List.of());

        assertThatThrownBy(() -> service.verificarRejeitadosCorrigidos(processo))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> assertThat(erro.getCampos())
                        .singleElement()
                        .satisfies(campo -> {
                            assertThat(campo.path()).isEqualTo("documentos[" + rejeitado.getId() + "]");
                            assertThat(campo.erro()).contains("texto.pdf");
                        }));
    }

    @Test
    void verificarRejeitadosCorrigidosAceitaQuandoOsRejeitadosForamSubstituidos() {
        Processo processo = processo("DECISAO_INICIO");
        dado(processo, List.of(documento(processo, EstadoValidacao.SUBSTITUIDO), documento(processo, EstadoValidacao.POR_VALIDAR)),
                List.of(elemento(processo, ElementoInformacaoBase.DELIBERACAO, EstadoValidacao.REJEITADO)));

        service.verificarRejeitadosCorrigidos(processo);
    }

    private static ContextoUtilizador contexto(PerfilInterno perfilInterno, String ator) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null, ator, null, null, null, null, null, null);
    }

    private ProcessoValidacaoRejeicao rejeicaoDoDocumento(Processo processo) {
        ProcessoDocumento documento = documento(processo, EstadoValidacao.REJEITADO);
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
        ListaValores categoria = new ListaValores("CATEGORIA_REJEICAO_DGT", "FORMA", "Forma", 1);
        ReflectionTestUtils.setField(categoria, "id", 7L);
        ProcessoValidacaoRejeicao rejeicao = ProcessoValidacaoRejeicao.doDocumento(documento);
        rejeicao.registar(categoria, null, "Página ilegível.", processo.getCriadoPor(), java.time.Instant.now());
        return rejeicao;
    }

    @Test
    void deveDevolverORascunhoDaValidacaoAoAtorDgtComOProcessoEmValidacao() {
        Processo processo = processo("DECISAO_INICIO");
        processo.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        ProcessoValidacaoRejeicao rejeicao = rejeicaoDoDocumento(processo);
        when(processoValidacaoRejeicaoRepository.findByProcesso_Id(processo.getId())).thenReturn(List.of(rejeicao));

        assertThat(service.rejeicoesEmValidacao(processo, contexto(PerfilInterno.TECNICO, "DGT")))
                .containsExactly(new RejeicaoEmValidacaoResponse(TipoItemValidacao.DOCUMENTO,
                        rejeicao.getProcessoDocumento().getId(), 7L, null, "Página ilegível."));
    }

    @Test
    void deveDevolverORascunhoVazioForaDoAtorDgtOuForaDeEmValidacaoDgt() {
        Processo processo = processo("DECISAO_INICIO");
        processo.setEstado(EstadoProcesso.EM_VALIDACAO_DGT);

        assertThat(service.rejeicoesEmValidacao(processo, contexto(PerfilInterno.VISITANTE, null))).isEmpty();
        assertThat(service.rejeicoesEmValidacao(processo, contexto(null, "ENTIDADE"))).isEmpty();

        processo.setEstado(EstadoProcesso.REJEITADO_DGT);
        assertThat(service.rejeicoesEmValidacao(processo, contexto(PerfilInterno.MASTER, "DGT"))).isEmpty();
        verify(processoValidacaoRejeicaoRepository, never()).findByProcesso_Id(any());
    }

    @Test
    void aRevalidacaoPcmSoValeParaADgtNumValidadoDevolvidoComDocumentosPorValidar() {
        Processo processo = processo("DECISAO_INICIO");
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processo.setPcmDevolvidoEm(java.time.Instant.now());
        when(processoDocumentoRepository.existsByProcessoIdAndEstadoValidacao(processo.getId(), EstadoValidacao.POR_VALIDAR))
                .thenReturn(true);

        assertThat(service.revalidacaoPcm(processo, contexto(PerfilInterno.TECNICO, "DGT"))).isTrue();
        assertThat(service.revalidacaoPcm(processo, contexto(PerfilInterno.VISITANTE, null))).isFalse();
        assertThat(service.revalidacaoPcm(processo, contexto(null, "ENTIDADE"))).isFalse();

        Utilizador pcm = new Utilizador("Técnico PCM", "pcm@pcm.gov.pt", "INCM");
        processo.setPcmAtribuidoA(pcm);
        assertThat(processo.situacaoPcm()).isEqualTo(com.conkord.saigt.persistence.operacional.SituacaoPcm.COM_PCM);
        assertThat(service.revalidacaoPcm(processo, contexto(PerfilInterno.TECNICO, "DGT"))).isFalse();
    }

    @Test
    void semDocumentosPorValidarOuSemDevolucaoNaoHaRevalidacaoPcm() {
        Processo processo = processo("DECISAO_INICIO");
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        assertThat(processo.situacaoPcm()).isEqualTo(com.conkord.saigt.persistence.operacional.SituacaoPcm.SEM_PCM);
        when(processoDocumentoRepository.existsByProcessoIdAndEstadoValidacao(processo.getId(), EstadoValidacao.POR_VALIDAR))
                .thenReturn(true);
        assertThat(service.revalidacaoPcm(processo, contexto(PerfilInterno.MASTER, "DGT"))).isFalse();

        processo.setPcmDevolvidoEm(java.time.Instant.now());
        assertThat(processo.situacaoPcm()).isEqualTo(com.conkord.saigt.persistence.operacional.SituacaoPcm.DEVOLVIDO);
        when(processoDocumentoRepository.existsByProcessoIdAndEstadoValidacao(processo.getId(), EstadoValidacao.POR_VALIDAR))
                .thenReturn(false);
        assertThat(service.revalidacaoPcm(processo, contexto(PerfilInterno.MASTER, "DGT"))).isFalse();
    }
}
