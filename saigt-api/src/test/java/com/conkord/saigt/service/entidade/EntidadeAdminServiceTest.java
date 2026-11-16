package com.conkord.saigt.service.entidade;

import com.conkord.saigt.dto.entidade.AtualizarEntidadeRequest;
import com.conkord.saigt.dto.entidade.CriarEntidadeRequest;
import com.conkord.saigt.dto.entidade.EntidadeFiltro;
import com.conkord.saigt.dto.entidade.EntidadeResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntidadeAdminServiceTest {

    /** NIPC válido (dígito de controlo módulo 11): 5,0,1,4,4,2,6,0 -> soma=122, resto=1, dígito=0. */
    private static final String NIPC_VALIDO = "501442600";
    private static final String NIPC_VALIDO_2 = "505187531";
    private static final UUID MASTER_ID = UUID.randomUUID();

    @Mock
    private EntidadeRepository entidadeRepository;
    @Mock
    private EntidadeContactoRepository entidadeContactoRepository;
    @Mock
    private SnitReferenciaRepository snitReferenciaRepository;
    @Mock
    private ProcessoRepository processoRepository;
    @Mock
    private AuditoriaRepository auditoriaRepository;
    @Mock
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private EntidadeAdminService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(MASTER_ID, "AD_DGT", perfilInterno, null,
                perfilInterno == PerfilInterno.MASTER ? "DGT" : null, null, null, null, null, null, null);
    }

    private static ContextoUtilizador contextoIncm() {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, NIPC_VALIDO, "ENTIDADE", null, null, null, null,
                TipoEntidade.DGT, 1L);
    }

    private void comoMaster() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
    }

    private void gravacaoDevolveComId(long id) {
        lenient().when(entidadeRepository.saveAndFlush(any(Entidade.class))).thenAnswer(invocacao -> {
            Entidade entidade = invocacao.getArgument(0);
            if (entidade.getId() == null) {
                ReflectionTestUtils.setField(entidade, "id", id);
            }
            return entidade;
        });
    }

    private static Entidade entidadeGravada(long id, TipoEntidade tipo, String dico) {
        Entidade entidade = new Entidade("5245", tipo, NIPC_VALIDO, "Município Teste", "CM Teste");
        ReflectionTestUtils.setField(entidade, "id", id);
        entidade.setDico(dico);
        return entidade;
    }

    private static CriarEntidadeRequest pedidoCm(String nipc, String dico) {
        return new CriarEntidadeRequest("9001", "Município Teste", "CM Teste", "CM", nipc, dico);
    }

    private static List<String> camposEmErro(Throwable ex) {
        return ((ValidacaoException) ex).getCampos().stream().map(ValidacaoException.CampoErro::path).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dadosAuditados(String acao) {
        ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
        verify(auditoriaService).registar(eq("ENTIDADE"), anyString(), eq(acao), eq(MASTER_ID), dados.capture());
        return (Map<String, Object>) dados.getValue();
    }

    // ---------- criar ----------

    @Test
    void criaCmSemNipcNemDicoEAuditaOsSeisCampos() {
        comoMaster();
        gravacaoDevolveComId(42L);

        EntidadeResponse resposta = service.criar(pedidoCm(null, null));

        assertThat(resposta.id()).isEqualTo(42L);
        assertThat(resposta.codigo()).isEqualTo("9001");
        assertThat(resposta.tipoEntidade()).isEqualTo(TipoEntidade.CM);
        assertThat(resposta.nipc()).isNull();
        assertThat(resposta.dico()).isNull();
        assertThat(resposta.telefone()).isNull();
        assertThat(resposta.email()).isNull();
        assertThat(dadosAuditados("CRIAR")).containsOnlyKeys("codigo", "nipc", "dico", "descricaoIncm",
                "descricaoSaigt", "tipoEntidade").containsEntry("codigo", "9001").containsEntry("tipoEntidade", "CM");
    }

    @Test
    void aceitaNipcRepetidoNoutraEntidade() {
        comoMaster();
        gravacaoDevolveComId(43L);

        EntidadeResponse resposta = service.criar(pedidoCm(NIPC_VALIDO, ""));

        assertThat(resposta.nipc()).isEqualTo(NIPC_VALIDO);
        verify(entidadeRepository, never()).findAllByNipc(anyString());
    }

    @Test
    void guardaOCodigoSemEspacosNasPontas() {
        comoMaster();
        gravacaoDevolveComId(44L);

        EntidadeResponse resposta = service.criar(
                new CriarEntidadeRequest("  9002 ", "Município Teste", "CM Teste", "CM", null, null));

        assertThat(resposta.codigo()).isEqualTo("9002");
    }

    @Test
    void recusaCodigoDeOutraEntidadeCom409() {
        comoMaster();
        when(entidadeRepository.existsByCodigo("9001")).thenReturn(true);
        CriarEntidadeRequest pedido = pedidoCm(null, null);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessage("Código de entidade já existe.");
        verify(entidadeRepository, never()).saveAndFlush(any());
    }

    @Test
    void recusaCamposObrigatoriosEmFaltaComUmItemPorCampo() {
        comoMaster();
        CriarEntidadeRequest pedido = new CriarEntidadeRequest(" ", null, "", null, null, null);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("VALIDACAO");
                    assertThat(camposEmErro(ex)).containsExactly("codigo", "descricaoIncm", "descricaoSaigt",
                            "tipoEntidade");
                });
        verify(entidadeRepository, never()).saveAndFlush(any());
    }

    @Test
    void recusaCamposAcimaDoLimite() {
        comoMaster();
        CriarEntidadeRequest pedido = new CriarEntidadeRequest("1".repeat(21), "x".repeat(201), "CM Teste", "CM",
                null, null);

        assertThatThrownBy(() -> service.criar(pedido))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("codigo", "descricaoIncm"));
    }

    @Test
    void recusaTipoForaDaLista() {
        comoMaster();
        CriarEntidadeRequest pedido = new CriarEntidadeRequest("9001", "Município Teste", "CM Teste", "XPTO", null,
                null);

        assertThatThrownBy(() -> service.criar(pedido))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> {
                    assertThat(camposEmErro(ex)).containsExactly("tipoEntidade");
                    assertThat(ex.getCampos().get(0).erro()).isEqualTo("Tipo de entidade inválido.");
                });
    }

    @Test
    void recusaNipcComOitoDigitosOuDigitoDeControloErrado() {
        comoMaster();
        CriarEntidadeRequest oitoDigitos = pedidoCm("50144260", null);
        CriarEntidadeRequest controloErrado = pedidoCm("501442601", null);

        assertThatThrownBy(() -> service.criar(oitoDigitos))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("nipc"));
        assertThatThrownBy(() -> service.criar(controloErrado))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("nipc"));
    }

    @Test
    void validaODigitoDeControloDoNipc() {
        assertThat(EntidadeAdminService.nipcValido(NIPC_VALIDO)).isTrue();
        assertThat(EntidadeAdminService.nipcValido(NIPC_VALIDO_2)).isTrue();
        assertThat(EntidadeAdminService.nipcValido("123456789")).isTrue();
        assertThat(EntidadeAdminService.nipcValido("123456780")).isFalse();
        assertThat(EntidadeAdminService.nipcValido("12345678a")).isFalse();
    }

    @Test
    void criaCmComDicoAtivoNaReplica() {
        comoMaster();
        gravacaoDevolveComId(45L);
        when(snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES, "1111"))
                .thenReturn(true);

        assertThat(service.criar(pedidoCm(null, "1111")).dico()).isEqualTo("1111");
    }

    @Test
    void recusaDicoInexistenteOuSemQuatroAlgarismos() {
        comoMaster();
        CriarEntidadeRequest inexistente = pedidoCm(null, "9999");
        CriarEntidadeRequest tresAlgarismos = pedidoCm(null, "111");

        assertThatThrownBy(() -> service.criar(inexistente)).isInstanceOfSatisfying(ValidacaoException.class, ex -> {
            assertThat(camposEmErro(ex)).containsExactly("dico");
            assertThat(ex.getCampos().get(0).erro()).isEqualTo("Concelho inexistente.");
        });
        assertThatThrownBy(() -> service.criar(tresAlgarismos))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("dico"));
        verify(snitReferenciaRepository, never()).existsByListaAndCodigoAndAtivoTrue(any(), eq("111"));
    }

    @Test
    void recusaDicoNumaEntidadeQueNaoECm() {
        comoMaster();
        CriarEntidadeRequest pedido = new CriarEntidadeRequest("9001", "CIM Teste", "CIM Teste", "CIM", null, "1111");

        assertThatThrownBy(() -> service.criar(pedido)).isInstanceOfSatisfying(ValidacaoException.class, ex -> {
            assertThat(camposEmErro(ex)).containsExactly("dico");
            assertThat(ex.getCampos().get(0).erro()).isEqualTo("O concelho só se indica nas câmaras municipais.");
        });
        verifyNoInteractions(snitReferenciaRepository);
    }

    // ---------- atualizar ----------

    @Test
    void corrigeNipcEDescricaoIncmEAuditaOAntesEODepois() {
        comoMaster();
        Entidade entidade = entidadeGravada(7L, TipoEntidade.CM, null);
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidade));
        gravacaoDevolveComId(7L);

        EntidadeResponse resposta = service.atualizar(7L,
                new AtualizarEntidadeRequest(null, "Município Corrigido", null, null, NIPC_VALIDO_2, null));

        assertThat(resposta.nipc()).isEqualTo(NIPC_VALIDO_2);
        assertThat(resposta.descricaoIncm()).isEqualTo("Município Corrigido");
        assertThat(resposta.codigo()).isEqualTo("5245");
        assertThat(dadosAuditados("ATUALIZAR"))
                .containsOnlyKeys("nipc", "descricaoIncm")
                .containsEntry("nipc", Map.of("antes", NIPC_VALIDO, "depois", NIPC_VALIDO_2))
                .containsEntry("descricaoIncm", Map.of("antes", "Município Teste", "depois", "Município Corrigido"));
    }

    @Test
    void nipcVazioLimpaONipc() {
        comoMaster();
        Entidade entidade = entidadeGravada(7L, TipoEntidade.CM, null);
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidade));
        gravacaoDevolveComId(7L);

        assertThat(service.atualizar(7L, new AtualizarEntidadeRequest(null, null, null, null, "", null)).nipc())
                .isNull();
    }

    @Test
    void semAlteracoesEfetivasNaoGravaNemAudita() {
        comoMaster();
        Entidade entidade = entidadeGravada(7L, TipoEntidade.CM, null);
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidade));

        EntidadeResponse resposta = service.atualizar(7L,
                new AtualizarEntidadeRequest("5245", null, "CM Teste", "CM", null, null));

        assertThat(resposta.codigo()).isEqualTo("5245");
        verify(entidadeRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void oProprioCodigoNaoEConflitoMasODeOutraEntidadeE() {
        comoMaster();
        Entidade entidade = entidadeGravada(7L, TipoEntidade.CM, null);
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidade));
        when(entidadeRepository.existsByCodigoAndIdNot("5245", 7L)).thenReturn(false);
        when(entidadeRepository.existsByCodigoAndIdNot("3001", 7L)).thenReturn(true);

        assertThat(service.atualizar(7L, new AtualizarEntidadeRequest("5245", null, null, null, null, null)).codigo())
                .isEqualTo("5245");
        AtualizarEntidadeRequest outroCodigo = new AtualizarEntidadeRequest("3001", null, null, null, null, null);
        assertThatThrownBy(() -> service.atualizar(7L, outroCodigo))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessage("Código de entidade já existe.");
    }

    @Test
    void gravaDicoAtivoNumaCm() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        when(snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES, "1111"))
                .thenReturn(true);
        gravacaoDevolveComId(7L);

        assertThat(service.atualizar(7L, new AtualizarEntidadeRequest(null, null, null, null, null, "1111")).dico())
                .isEqualTo("1111");
    }

    @Test
    void recusaDicoNumaCimEMudarDeCmParaCimLimpaODico() {
        comoMaster();
        when(entidadeRepository.findById(8L)).thenReturn(Optional.of(entidadeGravada(8L, TipoEntidade.CIM, null)));
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, "1111")));
        gravacaoDevolveComId(7L);

        AtualizarEntidadeRequest dicoNaCim = new AtualizarEntidadeRequest(null, null, null, null, null, "1111");
        assertThatThrownBy(() -> service.atualizar(8L, dicoNaCim))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("dico"));

        EntidadeResponse resposta = service.atualizar(7L,
                new AtualizarEntidadeRequest(null, null, null, "CIM", null, null));
        assertThat(resposta.tipoEntidade()).isEqualTo(TipoEntidade.CIM);
        assertThat(resposta.dico()).isNull();
        assertThat(dadosAuditados("ATUALIZAR")).containsOnlyKeys("tipoEntidade", "dico");
    }

    @Test
    void recusaDicoQueFicaNumTipoQueNaoECm() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        AtualizarEntidadeRequest pedido = new AtualizarEntidadeRequest(null, null, null, "CIM", null, "1111");

        assertThatThrownBy(() -> service.atualizar(7L, pedido))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("dico"));
    }

    @Test
    void recusaCamposObrigatoriosVaziosNoPatch() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        AtualizarEntidadeRequest pedido = new AtualizarEntidadeRequest("", " ", "", "", null, null);

        assertThatThrownBy(() -> service.atualizar(7L, pedido))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("codigo", "descricaoIncm",
                        "descricaoSaigt", "tipoEntidade"));
    }

    @Test
    void recusaPatchSemCampos() {
        comoMaster();
        AtualizarEntidadeRequest vazio = new AtualizarEntidadeRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(7L, vazio))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Indique pelo menos um campo a alterar.");
        verifyNoInteractions(entidadeRepository);
    }

    @Test
    void patchDeEntidadeInexistenteDa404() {
        comoMaster();
        when(entidadeRepository.findById(99L)).thenReturn(Optional.empty());
        AtualizarEntidadeRequest pedido = new AtualizarEntidadeRequest(null, null, "X", null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Entidade não encontrada.");
    }

    // ---------- eliminar ----------

    @Test
    void eliminaEntidadeSemRegistosEAuditaComOsDados() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, "1111")));

        service.eliminar(7L);

        verify(entidadeRepository).deleteById(7L);
        verify(entidadeRepository).flush();
        assertThat(dadosAuditados("ELIMINAR")).containsEntry("codigo", "5245").containsEntry("nipc", NIPC_VALIDO)
                .containsEntry("dico", "1111").containsEntry("tipoEntidade", "CM");
    }

    @Test
    void recusaEliminarComProcesso() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        when(processoRepository.existsByEntidadeId(7L)).thenReturn(true);

        assertRecusaEliminar();
        verifyNoInteractions(entidadeContactoRepository);
    }

    @Test
    void recusaEliminarComContacto() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        when(entidadeContactoRepository.existsByEntidadeId(7L)).thenReturn(true);

        assertRecusaEliminar();
    }

    @Test
    void recusaEliminarComAuditoriaDeSessaoDaEntidade() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        when(auditoriaRepository.existsByEntidadeId(7L)).thenReturn(true);

        assertRecusaEliminar();
    }

    @Test
    void recusaEliminarComVinculoDeUtilizador() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        when(utilizadorEntidadeRepository.existsByIdEntidadeId(7L)).thenReturn(true);

        assertRecusaEliminar();
    }

    private void assertRecusaEliminar() {
        assertThatThrownBy(() -> service.eliminar(7L))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("A entidade tem registos associados e não pode ser eliminada.");
        verify(entidadeRepository, never()).deleteById(anyLong());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void chaveEstrangeiraNoFlushDa409() {
        comoMaster();
        when(entidadeRepository.findById(7L)).thenReturn(Optional.of(entidadeGravada(7L, TipoEntidade.CM, null)));
        doThrow(new DataIntegrityViolationException("fk")).when(entidadeRepository).flush();

        assertThatThrownBy(() -> service.eliminar(7L))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("A entidade tem registos associados e não pode ser eliminada.");
    }

    @Test
    void eliminarEntidadeInexistenteDa404() {
        comoMaster();
        when(entidadeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------- autorização ----------

    @ParameterizedTest
    @EnumSource(value = PerfilInterno.class, names = {"TECNICO", "VISITANTE"})
    void recusaEscritasAPerfisInternosQueNaoSaoMaster(PerfilInterno perfil) {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(perfil));
        assertEscritasRecusadas();
    }

    @Test
    void recusaEscritasAUtilizadorIncm() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoIncm());
        assertEscritasRecusadas();
    }

    private void assertEscritasRecusadas() {
        CriarEntidadeRequest criar = pedidoCm(null, null);
        AtualizarEntidadeRequest atualizar = new AtualizarEntidadeRequest(null, null, "X", null, null, null);

        assertThatThrownBy(() -> service.criar(criar)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizar(7L, atualizar)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.eliminar(7L)).isInstanceOf(AcaoNaoAutorizadaException.class);
        verifyNoInteractions(entidadeRepository, auditoriaService);
    }

    // ---------- listar ----------

    @Test
    void recusaListarAQuemNaoEMaster() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));
        EntidadeFiltro filtro = new EntidadeFiltro(null, null, null);
        Pageable pagina = Pageable.unpaged();

        assertThatThrownBy(() -> service.listar(filtro, pagina)).isInstanceOf(AcaoNaoAutorizadaException.class);
        verifyNoInteractions(entidadeRepository);
    }

    @Test
    void recusaOFiltroDeTipoAQuemNaoEMasterAntesDeValidarOValor() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.converterTipoFiltro("PCM")).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.converterTipoFiltro("XPTO")).isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void listarComOFiltroTipoEntidadeAplicaAIgualdadeNaEspecificacao() {
        comoMaster();
        when(entidadeRepository.findAll(ArgumentMatchers.<Specification<Entidade>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(new EntidadeFiltro(true, null, TipoEntidade.PCM), Pageable.unpaged());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<Entidade>> especificacao = ArgumentCaptor.forClass(Specification.class);
        verify(entidadeRepository).findAll(especificacao.capture(), any(Pageable.class));
        Root<Entidade> root = mock();
        CriteriaQuery<?> query = mock();
        CriteriaBuilder cb = mock();
        Path<Object> caminhoTipo = mock();
        Path<Object> caminhoAtivo = mock();
        when(root.get("tipoEntidade")).thenReturn(caminhoTipo);
        when(root.get("ativo")).thenReturn(caminhoAtivo);

        especificacao.getValue().toPredicate(root, query, cb);

        verify(cb).equal(caminhoTipo, TipoEntidade.PCM);
        verify(cb).equal(caminhoAtivo, true);
    }

    @Test
    void recusaFiltroDeTipoForaDaLista() {
        comoMaster();

        assertThatThrownBy(() -> service.converterTipoFiltro("XPTO"))
                .satisfies(ex -> assertThat(camposEmErro(ex)).containsExactly("tipoEntidade"));
        assertThat(service.converterTipoFiltro("PCM")).isEqualTo(TipoEntidade.PCM);
    }

    @Test
    void listagemTrazNipcDicoResponsavelEContactoPrincipal() {
        comoMaster();
        Entidade entidade = EntidadeTeste.nova(NIPC_VALIDO, "Camara de Teste");
        ReflectionTestUtils.setField(entidade, "id", 1L);
        entidade.setDico("1111");
        entidade.setResponsavelNome("Ana Ferreira");
        EntidadeContacto contacto = new EntidadeContacto(entidade, "geral@camara.pt", mock(Utilizador.class));
        ReflectionTestUtils.setField(contacto, "id", 10L);
        contacto.setPrincipal(true);
        when(entidadeRepository.findAll(ArgumentMatchers.<Specification<Entidade>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entidade)));
        when(entidadeContactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(List.of(1L)))
                .thenReturn(List.of(contacto));

        EntidadeResponse resposta = service.listar(new EntidadeFiltro(null, null, null), Pageable.unpaged())
                .getContent().get(0);

        assertThat(resposta.nipc()).isEqualTo(NIPC_VALIDO);
        assertThat(resposta.dico()).isEqualTo("1111");
        assertThat(resposta.utilizadorNome()).isEqualTo("Ana Ferreira");
        assertThat(resposta.contactoPrincipal().email()).isEqualTo("geral@camara.pt");
    }
}
