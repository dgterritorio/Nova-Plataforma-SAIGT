package com.conkord.saigt.service.listavalores;

import com.conkord.saigt.dto.instrumento.OpcaoListaResponse;
import com.conkord.saigt.dto.listavalores.AtualizarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.CriarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.ListaValoresResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListaValoresServiceTest {

    @Mock
    private ListaValoresRepository listaValoresRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private ListaValoresService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null,
                perfilInterno == PerfilInterno.MASTER ? "DGT" : null, null, null, null, null, null, null);
    }

    private static ListaValores item(Long id, String tipo, String codigo) {
        ListaValores item = new ListaValores(tipo, codigo, "Descrição", 1);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void deveListarPorTipoQuandoChamadorTemLeitura() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));
        when(listaValoresRepository.findByTipoOrderByOrdemAsc("MOTIVO_REJEICAO_DGT"))
                .thenReturn(List.of(item(1L, "MOTIVO_REJEICAO_DGT", "INCOMPLETO")));

        List<ListaValoresResponse> resultado = service.listar("MOTIVO_REJEICAO_DGT");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("INCOMPLETO");
    }

    @Test
    void opcoesDeFormularioDevolvemSoOsAtivosParaQualquerUtilizadorSemLerOContexto() {
        when(listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc("FREGUESIA"))
                .thenReturn(List.of(item(1L, "FREGUESIA", "F01"), item(2L, "FREGUESIA", "F02")));

        assertThat(service.opcoesFormulario("FREGUESIA")).extracting(OpcaoListaResponse::codigo).containsExactly("F01", "F02");
        verify(contextoUtilizadorResolver, never()).resolver();
    }

    @Test
    void opcoesAtivasDeUmaListaVaziaSaoVazias() {
        when(listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc("MOTIVO_ALTERACAO_PLANO")).thenReturn(List.of());

        assertThat(service.opcoesAtivas(TipoListaValores.MOTIVO_ALTERACAO_PLANO)).isEmpty();
    }

    @Test
    void opcoesDeFormularioRecusamOsTiposQueNaoSaoDeFormulario() {
        for (String tipo : new String[] {"MOTIVO_REJEICAO_DGT", "FUNDAMENTACAO_RJIGT", "INEXISTENTE", null}) {
            assertThatThrownBy(() -> service.opcoesFormulario(tipo))
                    .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                        assertThat(ex.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("tipo");
                        assertThat(ex.getMessage()).isEqualTo("Tipo de lista não disponível para formulários.");
                    });
        }
    }

    @Test
    void deveRejeitarListarQuandoChamadorSemPerfilInterno() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(null));

        assertThatThrownBy(() -> service.listar(null)).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveCriarItemNumTipoExistente() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.existsByTipoAndCodigo("MOTIVO_REJEICAO_DGT", "NOVO")).thenReturn(false);
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 10L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(new CriarListaValoresRequest("MOTIVO_REJEICAO_DGT", "NOVO", "Novo motivo", 5));

        assertThat(resposta.tipo()).isEqualTo("MOTIVO_REJEICAO_DGT");
        assertThat(resposta.codigo()).isEqualTo("NOVO");
        assertThat(resposta.ativo()).isTrue();
        verify(auditoriaService).registar(eq("LISTA_VALORES"), any(), eq("CRIAR"), any(), any());
    }

    @Test
    void deveRejeitarCriarQuandoChamadorNaoEAdmin() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));
        CriarListaValoresRequest pedido = new CriarListaValoresRequest("MOTIVO_REJEICAO_DGT", "NOVO", "desc", 0);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveCriarOPrimeiroItemDeUmTipoDoCatalogoAindaSemValores() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.existsByTipoAndCodigo("CATEGORIA_REJEICAO_DGT", "TESTE")).thenReturn(false);
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 11L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(
                new CriarListaValoresRequest("CATEGORIA_REJEICAO_DGT", "TESTE", "Categoria de teste", 1));

        assertThat(resposta.tipo()).isEqualTo("CATEGORIA_REJEICAO_DGT");
        verify(auditoriaService).registar(eq("LISTA_VALORES"), eq("11"), eq("CRIAR"), any(),
                eq(Map.of("tipo", "CATEGORIA_REJEICAO_DGT", "codigo", "TESTE", "descricao", "Categoria de teste")));
    }

    @Test
    void deveDeixarOMasterCriarOPrimeiroValorDaFundamentacaoRjigt() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.existsByTipoAndCodigo("FUNDAMENTACAO_RJIGT", "ART72A")).thenReturn(false);
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 12L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(
                new CriarListaValoresRequest("FUNDAMENTACAO_RJIGT", "ART72A", "Art. 72.º-A do RJIGT", 1));

        assertThat(resposta.tipo()).isEqualTo("FUNDAMENTACAO_RJIGT");
    }

    @Test
    void deveRejeitarCriarQuandoTipoNaoEstaNoCatalogo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        CriarListaValoresRequest pedido = new CriarListaValoresRequest("TIPO_INEXISTENTE", "X", "desc", 0);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(e.getMessage()).isEqualTo("Tipo inexistente.");
                    assertThat(e.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("tipo");
                });
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void temValoresAtivosPerguntaAoRepositorioPeloTipoDaLista() {
        when(listaValoresRepository.existsByTipoAndAtivoTrue("CATEGORIA_REJEICAO_DGT")).thenReturn(true);
        when(listaValoresRepository.existsByTipoAndAtivoTrue("MOTIVO_REJEICAO_DGT")).thenReturn(false);

        assertThat(service.temValoresAtivos(TipoListaValores.CATEGORIA_REJEICAO_DGT)).isTrue();
        assertThat(service.temValoresAtivos(TipoListaValores.MOTIVO_REJEICAO_DGT)).isFalse();
    }

    @Test
    void obterAtivoDevolveOValorAtivoDoTipoPedido() {
        ListaValores categoria = item(20L, "CATEGORIA_REJEICAO_DGT", "FORMA");
        when(listaValoresRepository.findById(20L)).thenReturn(Optional.of(categoria));

        assertThat(service.obterAtivo(TipoListaValores.CATEGORIA_REJEICAO_DGT, 20L, "categoriaId"))
                .isSameAs(categoria);
    }

    @Test
    void obterAtivoRecusaValorInexistente() {
        when(listaValoresRepository.findById(21L)).thenReturn(Optional.empty());

        assertRecusaNoCampo(() -> service.obterAtivo(TipoListaValores.CATEGORIA_REJEICAO_DGT, 21L, "categoriaId"),
                "categoriaId", "Não existe ou não é um valor ativo de CATEGORIA_REJEICAO_DGT.");
    }

    @Test
    void obterAtivoRecusaValorDeOutroTipo() {
        when(listaValoresRepository.findById(22L)).thenReturn(Optional.of(item(22L, "MOTIVO_REJEICAO_DGT", "X")));

        assertRecusaNoCampo(() -> service.obterAtivo(TipoListaValores.CATEGORIA_REJEICAO_DGT, 22L, "itens[0].categoriaId"),
                "itens[0].categoriaId", "Não existe ou não é um valor ativo de CATEGORIA_REJEICAO_DGT.");
    }

    @Test
    void obterAtivoRecusaValorInativo() {
        ListaValores inativo = item(23L, "MOTIVO_REJEICAO_DGT", "ANTIGO");
        inativo.setAtivo(false);
        when(listaValoresRepository.findById(23L)).thenReturn(Optional.of(inativo));

        assertRecusaNoCampo(() -> service.obterAtivo(TipoListaValores.MOTIVO_REJEICAO_DGT, 23L, "motivoId"),
                "motivoId", "Não existe ou não é um valor ativo de MOTIVO_REJEICAO_DGT.");
    }

    @Test
    void obterAtivoRecusaIdNuloSemConsultarABd() {
        assertRecusaNoCampo(() -> service.obterAtivo(TipoListaValores.MOTIVO_REJEICAO_DGT, null, "motivoId"),
                "motivoId", "Não existe ou não é um valor ativo de MOTIVO_REJEICAO_DGT.");
        verify(listaValoresRepository, never()).findById(any());
    }

    private static void assertRecusaNoCampo(ThrowingCallable chamada, String campo, String erro) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(ValidacaoException.class, e -> {
            assertThat(e.getCodigo()).isEqualTo("VALIDACAO");
            assertThat(e.getCampos()).containsExactly(new ValidacaoException.CampoErro(campo, erro));
        });
    }

    @Test
    void deveRejeitarCriarComCodigoDuplicadoNoMesmoTipo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.existsByTipoAndCodigo("MOTIVO_REJEICAO_DGT", "INCOMPLETO")).thenReturn(true);
        CriarListaValoresRequest pedido = new CriarListaValoresRequest("MOTIVO_REJEICAO_DGT", "INCOMPLETO", "desc", 0);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class);
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveAtualizarDescricaoOrdemEAtivo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores existente = item(7L, "MOTIVO_REJEICAO_DGT", "INCOMPLETO");
        when(listaValoresRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListaValoresResponse resposta = service.atualizar(7L, new AtualizarListaValoresRequest("Descrição nova", 9, false));

        assertThat(resposta.descricao()).isEqualTo("Descrição nova");
        assertThat(resposta.ordem()).isEqualTo(9);
        assertThat(resposta.ativo()).isFalse();
        assertThat(resposta.codigo()).isEqualTo("INCOMPLETO");
        verify(auditoriaService).registar(eq("LISTA_VALORES"), any(), eq("ATUALIZAR"), any(), any());
    }

    @Test
    void deveAceitarDesativarUmItemJaReferenciadoSemApagarNada() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores existente = item(8L, "MOTIVO_REJEICAO_DGT", "USADO_EM_AUDITORIA");
        when(listaValoresRepository.findById(8L)).thenReturn(Optional.of(existente));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListaValoresResponse resposta = service.atualizar(8L, new AtualizarListaValoresRequest(null, null, false));

        assertThat(resposta.ativo()).isFalse();
        assertThat(resposta.codigo()).isEqualTo("USADO_EM_AUDITORIA");
        verify(listaValoresRepository, never()).deleteById(any());
    }

    @Test
    void naoDeveAlterarCamposOmitidos() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores existente = item(9L, "MOTIVO_REJEICAO_DGT", "INCOMPLETO");
        when(listaValoresRepository.findById(9L)).thenReturn(Optional.of(existente));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ListaValoresResponse resposta = service.atualizar(9L, new AtualizarListaValoresRequest(null, null, null));

        assertThat(resposta.descricao()).isEqualTo("Descrição");
        assertThat(resposta.ordem()).isEqualTo(1);
        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void deveRejeitarAtualizarQuandoChamadorNaoEAdmin() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));
        AtualizarListaValoresRequest pedido = new AtualizarListaValoresRequest("x", null, null);

        assertThatThrownBy(() -> service.atualizar(1L, pedido))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveRejeitarAtualizarQuandoItemNaoExiste() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.findById(999L)).thenReturn(Optional.empty());
        AtualizarListaValoresRequest pedido = new AtualizarListaValoresRequest("x", null, null);

        assertThatThrownBy(() -> service.atualizar(999L, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static ListaValores filho(Long id, String tipo, String codigo, ListaValores pai) {
        ListaValores item = new ListaValores(tipo, codigo, "Descrição", 1, pai);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private void assertErroNoPai(ThrowingCallable chamada, String mensagem) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(ValidacaoException.class, ex -> {
            assertThat(ex.getCampos()).extracting(ValidacaoException.CampoErro::path).containsExactly("paiId");
            assertThat(ex.getCampos()).extracting(ValidacaoException.CampoErro::erro).containsExactly(mensagem);
        });
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveCriarTipologiaComPaiClasseAtivaEAuditarOPai() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores classe = item(30L, "CLASSE_SRUP", "C30");
        when(listaValoresRepository.findById(30L)).thenReturn(Optional.of(classe));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 75L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(
                new CriarListaValoresRequest("TIPOLOGIA_SRUP", "T075", "Pedreira", 75, 30L));

        assertThat(resposta.paiId()).isEqualTo(30L);
        verify(auditoriaService).registar(eq("LISTA_VALORES"), eq("75"), eq("CRIAR"), any(),
                eq(Map.of("tipo", "TIPOLOGIA_SRUP", "codigo", "T075", "descricao", "Pedreira", "paiId", 30L)));
    }

    @Test
    void deveRecusarCriarValorDeTipoComHierarquiaSemPai() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));

        assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("TIPOLOGIA_SRUP", "T075", "Pedreira", 75, null)),
                "Indique o valor de que este depende.");
        assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("CLASSE_SRUP", "C34", "Nova", 34, null)),
                "Indique o valor de que este depende.");
    }

    @Test
    void deveRecusarPaiDeNivelErradoInexistenteOuInativo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.findById(10L)).thenReturn(Optional.of(item(10L, "DOMINIO_SRUP", "D08")));
        when(listaValoresRepository.findById(99L)).thenReturn(Optional.empty());
        ListaValores classeInativa = item(31L, "CLASSE_SRUP", "C31");
        classeInativa.setAtivo(false);
        when(listaValoresRepository.findById(31L)).thenReturn(Optional.of(classeInativa));

        for (Long paiId : new Long[] {10L, 99L, 31L}) {
            assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("TIPOLOGIA_SRUP", "T075", "Pedreira", 75, paiId)),
                    "O valor indicado não é do nível acima.");
        }
    }

    @Test
    void deveRecusarPaiNumTipoSemHierarquia() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));

        assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("MOTIVO_REJEICAO_DGT", "NOVO", "Novo motivo", 5, 10L)),
                "Este tipo de lista não tem valores dependentes.");
    }

    @Test
    void deveRecusarDesativarUmValorComDependentesAtivos() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores classe = item(17L, "CLASSE_SRUP", "C17");
        when(listaValoresRepository.findById(17L)).thenReturn(Optional.of(classe));
        when(listaValoresRepository.existsByPaiAndAtivoTrue(classe)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(17L, new AtualizarListaValoresRequest(null, null, false)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Desative primeiro os valores que dependem deste.");

        assertThat(classe.isAtivo()).isTrue();
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveDesativarUmValorSemDependentesAtivos() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores classe = item(17L, "CLASSE_SRUP", "C17");
        when(listaValoresRepository.findById(17L)).thenReturn(Optional.of(classe));
        when(listaValoresRepository.existsByPaiAndAtivoTrue(classe)).thenReturn(false);

        ListaValoresResponse resposta = service.atualizar(17L, new AtualizarListaValoresRequest(null, null, false));

        assertThat(resposta.ativo()).isFalse();
    }

    @Test
    void deveRecusarAtivarUmValorCujoPaiEstaInativo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores classe = item(17L, "CLASSE_SRUP", "C17");
        classe.setAtivo(false);
        ListaValores tipologia = filho(200L, "TIPOLOGIA_SRUP", "T200", classe);
        tipologia.setAtivo(false);
        when(listaValoresRepository.findById(200L)).thenReturn(Optional.of(tipologia));

        assertThatThrownBy(() -> service.atualizar(200L, new AtualizarListaValoresRequest(null, null, true)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Ative primeiro o valor de que este depende.");

        assertThat(tipologia.isAtivo()).isFalse();
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void deveAtivarUmValorCujoPaiEstaAtivo() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores classe = item(17L, "CLASSE_SRUP", "C17");
        ListaValores tipologia = filho(200L, "TIPOLOGIA_SRUP", "T200", classe);
        tipologia.setAtivo(false);
        when(listaValoresRepository.findById(200L)).thenReturn(Optional.of(tipologia));

        ListaValoresResponse resposta = service.atualizar(200L, new AtualizarListaValoresRequest(null, null, true));

        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.paiId()).isEqualTo(17L);
    }

    // US-486: a Região e a CIM/AM do formulário SGIFR; a CIM/AM depende da Região como a Classe do Domínio.

    @Test
    void deveCriarRegiaoSemPaiEAuditarSemPaiId() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 501L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(new CriarListaValoresRequest("REGIAO", "R01", "Norte", 1, null));

        assertThat(resposta.tipo()).isEqualTo("REGIAO");
        assertThat(resposta.paiId()).isNull();
        verify(auditoriaService).registar(eq("LISTA_VALORES"), eq("501"), eq("CRIAR"), any(),
                eq(Map.of("tipo", "REGIAO", "codigo", "R01", "descricao", "Norte")));
    }

    @Test
    void deveCriarCimAmComPaiNumaRegiaoAtivaEAuditarOPai() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.findById(501L)).thenReturn(Optional.of(item(501L, "REGIAO", "R01")));
        when(listaValoresRepository.save(any())).thenAnswer(inv -> {
            ListaValores argumento = inv.getArgument(0);
            ReflectionTestUtils.setField(argumento, "id", 502L);
            return argumento;
        });

        ListaValoresResponse resposta = service.criar(
                new CriarListaValoresRequest("CIM_AM", "C01", "CIM do Alto Minho", 1, 501L));

        assertThat(resposta.tipo()).isEqualTo("CIM_AM");
        assertThat(resposta.paiId()).isEqualTo(501L);
        verify(auditoriaService).registar(eq("LISTA_VALORES"), eq("502"), eq("CRIAR"), any(),
                eq(Map.of("tipo", "CIM_AM", "codigo", "C01", "descricao", "CIM do Alto Minho", "paiId", 501L)));
    }

    @Test
    void deveRecusarCimAmSemPaiOuComPaiQueNaoEUmaRegiaoAtiva() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(listaValoresRepository.findById(10L)).thenReturn(Optional.of(item(10L, "DOMINIO_SRUP", "D08")));
        ListaValores regiaoInativa = item(503L, "REGIAO", "R03");
        regiaoInativa.setAtivo(false);
        when(listaValoresRepository.findById(503L)).thenReturn(Optional.of(regiaoInativa));

        assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("CIM_AM", "C01", "CIM", 1, null)),
                "Indique o valor de que este depende.");
        for (Long paiId : new Long[] {10L, 503L}) {
            assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("CIM_AM", "C01", "CIM", 1, paiId)),
                    "O valor indicado não é do nível acima.");
        }
    }

    @Test
    void deveRecusarRegiaoComPai() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));

        assertErroNoPai(() -> service.criar(new CriarListaValoresRequest("REGIAO", "R01", "Norte", 1, 10L)),
                "Este tipo de lista não tem valores dependentes.");
    }

    @Test
    void deveRecusarDesativarUmaRegiaoComCimAmAtivas() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        ListaValores regiao = item(501L, "REGIAO", "R01");
        when(listaValoresRepository.findById(501L)).thenReturn(Optional.of(regiao));
        when(listaValoresRepository.existsByPaiAndAtivoTrue(regiao)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(501L, new AtualizarListaValoresRequest(null, null, false)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Desative primeiro os valores que dependem deste.");
        assertThat(regiao.isAtivo()).isTrue();
    }

    @Test
    void tecnicoNaoCriaRegiaoNemCimAm() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.criar(new CriarListaValoresRequest("REGIAO", "R01", "Norte", 1, null)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.criar(new CriarListaValoresRequest("CIM_AM", "C01", "CIM", 1, 501L)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(listaValoresRepository, never()).save(any());
    }

    @Test
    void regiaoECimAmNaoSaoListasDeFormularioAbertasATodos() {
        for (String tipo : new String[] {"REGIAO", "CIM_AM"}) {
            assertThatThrownBy(() -> service.opcoesFormulario(tipo))
                    .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos())
                            .extracting(ValidacaoException.CampoErro::path).containsExactly("tipo"));
        }
        verify(listaValoresRepository, never()).findByTipoAndAtivoTrueOrderByOrdemAsc(any());
    }
}
