package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoInformacaoBase;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RejeicaoProcessoServiceTest {

    private final ProcessoRejeicaoRepository repository = mock(ProcessoRejeicaoRepository.class);
    private final ProcessoRejeicaoItemRepository itemRepository = mock(ProcessoRejeicaoItemRepository.class);
    private final RejeicaoProcessoService service = new RejeicaoProcessoService(repository, itemRepository);

    private final Map<UUID, List<ProcessoRejeicao>> gravadas = new HashMap<>();

    @BeforeEach
    void simularTabela() {
        when(repository.save(any(ProcessoRejeicao.class))).thenAnswer(invocacao -> {
            ProcessoRejeicao linha = invocacao.getArgument(0);
            gravadas.computeIfAbsent(linha.getProcesso().getId(), id -> new ArrayList<>()).add(linha);
            return linha;
        });
        when(repository.maiorSequencia(any(UUID.class))).thenAnswer(invocacao -> Optional
                .ofNullable(gravadas.get(invocacao.<UUID>getArgument(0)))
                .flatMap(linhas -> linhas.stream().map(ProcessoRejeicao::getSequencia).max(Integer::compare)));
    }

    @Test
    void aSequenciaCresceDeUmEmUmPorProcessoEEIndependenteEntreProcessos() {
        Processo a = processo();
        Processo b = processo();

        service.registarRecusaIncm(a, List.of("a1"));
        service.registarRecusaIncm(a, List.of("a2"));
        service.registarRecusaIncm(b, List.of("b1"));
        service.registarRecusaIncm(a, List.of("a3"));

        assertThat(gravadas.get(a.getId())).extracting(ProcessoRejeicao::getSequencia).containsExactly(1, 2, 3);
        assertThat(gravadas.get(b.getId())).extracting(ProcessoRejeicao::getSequencia).containsExactly(1);
    }

    @Test
    void registaOrigemIncmComAsMensagensUmaPorLinhaESemAutor() {
        Processo processo = processo();

        ProcessoRejeicao linha = service.registarRecusaIncm(processo,
                List.of("ImageURLList cannot be empty.", "ActTypeId is invalid."));

        assertThat(linha.getProcesso()).isSameAs(processo);
        assertThat(linha.getOrigem()).isEqualTo(OrigemRejeicao.INCM);
        assertThat(linha.getMotivoTexto()).isEqualTo("ImageURLList cannot be empty.\nActTypeId is invalid.");
        assertThat(linha.getCriadoPor()).isNull();
        assertThat(linha.getNotasGerais()).isNull();
    }

    static Stream<List<String>> semMotivo() {
        return Stream.of(null, List.of(), Arrays.asList(null, " ", ""));
    }

    @ParameterizedTest
    @MethodSource("semMotivo")
    void semMensagensGravaOTextoPorOmissao(List<String> mensagens) {
        ProcessoRejeicao linha = service.registarRecusaIncm(processo(), mensagens);

        assertThat(linha.getMotivoTexto()).isEqualTo("A INCM não indicou o motivo.");
    }

    @Test
    void ignoraMensagensVaziasEntreAsOutras() {
        ProcessoRejeicao linha = service.registarRecusaIncm(processo(), Arrays.asList(" Primeira ", null, "", "Segunda"));

        assertThat(linha.getMotivoTexto()).isEqualTo("Primeira\nSegunda");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registaOrigemDgtComAsNotasOAutorEUmItemPorLinhaDoRascunho() {
        Processo processo = processo();
        service.registarRecusaIncm(processo, List.of("Anterior."));
        Utilizador autor = new Utilizador("Técnica DGT", "tecnica@dgt.pt", "AD_DGT");
        ListaValores categoria = new ListaValores("CATEGORIA_REJEICAO_DGT", "FORMA", "Forma", 1);
        ListaValores motivo = new ListaValores("MOTIVO_REJEICAO_DGT", "ILEGIVEL", "Ilegível", 1);
        ProcessoDocumento documento = mock(ProcessoDocumento.class);
        ProcessoInformacaoBase deliberacao = mock(ProcessoInformacaoBase.class);
        ProcessoValidacaoRejeicao linhaDocumento = linhaDoRascunho(documento, null, categoria, motivo, "Página ilegível.");
        ProcessoValidacaoRejeicao linhaElemento = linhaDoRascunho(null, deliberacao, null, null, "Falta a data.");

        ProcessoRejeicao recusa = service.registarRecusaDgt(processo, autor, "Notas gerais.",
                List.of(linhaDocumento, linhaElemento));

        assertThat(recusa.getSequencia()).isEqualTo(2);
        assertThat(recusa.getOrigem()).isEqualTo(OrigemRejeicao.DGT);
        assertThat(recusa.getNotasGerais()).isEqualTo("Notas gerais.");
        assertThat(recusa.getCriadoPor()).isSameAs(autor);
        assertThat(recusa.getMotivoTexto()).isNull();
        ArgumentCaptor<List<ProcessoRejeicaoItem>> itens = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(itens.capture());
        assertThat(itens.getValue()).hasSize(2);
        assertThat(itens.getValue().get(0)).satisfies(item -> {
            assertThat(item.getRejeicao()).isSameAs(recusa);
            assertThat(item.getProcessoDocumento()).isSameAs(documento);
            assertThat(item.getProcessoInformacaoBase()).isNull();
            assertThat(item.getCategoria()).isSameAs(categoria);
            assertThat(item.getMotivo()).isSameAs(motivo);
            assertThat(item.getNotas()).isEqualTo("Página ilegível.");
        });
        assertThat(itens.getValue().get(1)).satisfies(item -> {
            assertThat(item.getProcessoDocumento()).isNull();
            assertThat(item.getProcessoInformacaoBase()).isSameAs(deliberacao);
            assertThat(item.getCategoria()).isNull();
            assertThat(item.getMotivo()).isNull();
            assertThat(item.getNotas()).isEqualTo("Falta a data.");
        });
    }

    private static ProcessoValidacaoRejeicao linhaDoRascunho(ProcessoDocumento documento,
                                                             ProcessoInformacaoBase elemento, ListaValores categoria,
                                                             ListaValores motivo, String notas) {
        ProcessoValidacaoRejeicao linha = mock(ProcessoValidacaoRejeicao.class);
        when(linha.getProcessoDocumento()).thenReturn(documento);
        when(linha.getProcessoInformacaoBase()).thenReturn(elemento);
        when(linha.getCategoria()).thenReturn(categoria);
        when(linha.getMotivo()).thenReturn(motivo);
        when(linha.getNotas()).thenReturn(notas);
        return linha;
    }

    private static Processo processo() {
        Processo processo = new Processo();
        ReflectionTestUtils.setField(processo, "id", UUID.randomUUID());
        return processo;
    }
}
