package com.conkord.saigt.service.entidade;

import com.conkord.saigt.dto.entidade.AtualizarContactoRequest;
import com.conkord.saigt.dto.entidade.ContactoRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntidadeContactoServiceTest {

    @Mock
    private EntidadeContactoRepository repository;
    @Mock
    private EntidadeRepository entidadeRepository;
    @Mock
    private UtilizadorRepository utilizadorRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private EntidadeContactoService service;

    private Entidade entidade;

    @BeforeEach
    void preparar() {
        entidade = EntidadeTeste.nova("501234567", "Câmara Municipal de Sintra");
        ReflectionTestUtils.setField(entidade, "id", 1L);
    }

    private void comoPerfil(PerfilInterno perfilInterno) {
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT",
                perfilInterno, null, perfilInterno == PerfilInterno.MASTER ? "DGT" : null, null, null, null, null, null,
                null));
    }

    private EntidadeContacto contacto(long id, boolean principal, boolean ativo) {
        EntidadeContacto contacto = new EntidadeContacto(entidade, "contacto" + id + "@cm-sintra.pt", mock(Utilizador.class));
        ReflectionTestUtils.setField(contacto, "id", id);
        contacto.setPrincipal(principal);
        contacto.setAtivo(ativo);
        return contacto;
    }

    private void prepararCriacao() {
        when(entidadeRepository.findById(1L)).thenReturn(Optional.of(entidade));
        when(utilizadorRepository.findById(any())).thenReturn(Optional.of(mock(Utilizador.class)));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenAnswer(inv -> {
            EntidadeContacto guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", 50L);
            return guardado;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dadosAuditados(String acao) {
        ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
        verify(auditoriaService).registar(eq("ENTIDADE_CONTACTO"), eq("1"), eq(acao), any(UUID.class), dados.capture());
        return (Map<String, Object>) dados.getValue();
    }

    // --- listar

    @Test
    void listaOsContactosDaEntidadePelaOrdemDoRepositorio() {
        comoPerfil(PerfilInterno.MASTER);
        List<EntidadeContacto> ordenados = List.of(contacto(1L, true, true), contacto(2L, false, true),
                contacto(3L, false, false));
        when(entidadeRepository.existsById(1L)).thenReturn(true);
        when(repository.findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(1L)).thenReturn(ordenados);

        assertThat(service.listar(1L)).isEqualTo(ordenados);
    }

    @Test
    void listarEntidadeInexistenteDa404() {
        comoPerfil(PerfilInterno.MASTER);
        when(entidadeRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarSemSerMasterDa403() {
        comoPerfil(PerfilInterno.TECNICO);

        assertThatThrownBy(() -> service.listar(1L)).isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(entidadeRepository, never()).existsById(any());
    }

    // --- criarContacto

    @Test
    void criaSemPrincipalComoNaoPrincipalEAtivoMesmoComPrincipalNaEntidade() {
        comoPerfil(PerfilInterno.MASTER);
        prepararCriacao();

        EntidadeContacto criado = service.criarContacto(1L,
                new ContactoRequest("urbanismo@cm-sintra.pt", "Urbanismo", "219238500", null));

        assertThat(criado.isPrincipal()).isFalse();
        assertThat(criado.isAtivo()).isTrue();
        assertThat(criado.getTelefone()).isEqualTo("219238500");
        verify(repository, never()).findByEntidadeIdAndPrincipalTrueAndAtivoTrue(any());
    }

    @Test
    void criaPrincipalQuandoAEntidadeNaoTemNenhumEAuditaOAntesEODepois() {
        comoPerfil(PerfilInterno.MASTER);
        prepararCriacao();
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.empty());

        EntidadeContacto criado = service.criarContacto(1L,
                new ContactoRequest("geral@cm-sintra.pt", "", null, true));

        assertThat(criado.isPrincipal()).isTrue();
        assertThat(criado.getDescricao()).isNull();
        Map<String, Object> dados = dadosAuditados("CRIAR_CONTACTO");
        assertThat(dados).containsEntry("contactoId", 50L);
        assertThat(((Map<?, ?>) dados.get("email")).get("antes")).isNull();
        assertThat(((Map<?, ?>) dados.get("email")).get("depois")).isEqualTo("geral@cm-sintra.pt");
        assertThat(((Map<?, ?>) dados.get("principal")).get("depois")).isEqualTo(true);
        assertThat(((Map<?, ?>) dados.get("ativo")).get("depois")).isEqualTo(true);
        assertThat(dados).doesNotContainKeys("descricao", "telefone");
    }

    @Test
    void criarPrincipalComOutroPrincipalAtivoDa409ENadaGrava() {
        comoPerfil(PerfilInterno.MASTER);
        when(entidadeRepository.findById(1L)).thenReturn(Optional.of(entidade));
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.of(contacto(7L, true, true)));
        ContactoRequest pedido = new ContactoRequest("outro@cm-sintra.pt", null, null, true);

        assertThatThrownBy(() -> service.criarContacto(1L, pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .hasMessage("Contacto duplicado ou principal já definido.");
        verify(repository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(UUID.class), any());
    }

    @Test
    void criarComEmailRepetidoNaEntidadeDa409SemAuditoria() {
        comoPerfil(PerfilInterno.MASTER);
        when(entidadeRepository.findById(1L)).thenReturn(Optional.of(entidade));
        when(utilizadorRepository.findById(any())).thenReturn(Optional.of(mock(Utilizador.class)));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenThrow(new DataIntegrityViolationException("dup"));
        ContactoRequest pedido = new ContactoRequest("geral@cm-sintra.pt", null, null, null);

        assertThatThrownBy(() -> service.criarContacto(1L, pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class);
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(UUID.class), any());
    }

    @Test
    void criarParaEntidadeInexistenteDa404() {
        comoPerfil(PerfilInterno.MASTER);
        when(entidadeRepository.findById(9L)).thenReturn(Optional.empty());
        ContactoRequest pedido = new ContactoRequest("geral@cm-sintra.pt", null, null, true);

        assertThatThrownBy(() -> service.criarContacto(9L, pedido)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void criarComDadosInvalidosDa400ComOsCampos() {
        comoPerfil(PerfilInterno.MASTER);
        ContactoRequest pedido = new ContactoRequest("nao-e-email", "x".repeat(201), "12345", null);

        assertThatThrownBy(() -> service.criarContacto(1L, pedido))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getCampos())
                        .extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("email", "descricao", "telefone"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void criarSemEmailOuComEmailLongoDa400() {
        comoPerfil(PerfilInterno.MASTER);
        ContactoRequest semEmail = new ContactoRequest(" ", null, null, null);
        assertThatThrownBy(() -> service.criarContacto(1L, semEmail)).isInstanceOf(ValidacaoException.class);

        ContactoRequest longo = new ContactoRequest("a".repeat(190) + "@cm-sintra.pt", null, null, null);
        assertThatThrownBy(() -> service.criarContacto(1L, longo)).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void criarSemSerMasterDa403() {
        comoPerfil(PerfilInterno.TECNICO);
        ContactoRequest pedido = new ContactoRequest("geral@cm-sintra.pt", null, null, null);

        assertThatThrownBy(() -> service.criarContacto(1L, pedido)).isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    // --- editarContacto

    @Test
    void marcarComoPrincipalComOutroPrincipalAtivoDa409SemGravar() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto b = contacto(2L, false, true);
        when(repository.findById(2L)).thenReturn(Optional.of(b));
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.of(contacto(1L, true, true)));
        AtualizarContactoRequest pedido = new AtualizarContactoRequest(null, null, null, true, null);

        assertThatThrownBy(() -> service.editarContacto(1L, 2L, pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class);
        assertThat(b.isPrincipal()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void desmarcarEDepoisMarcarTrocaOPrincipal() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto a = contacto(1L, true, true);
        EntidadeContacto b = contacto(2L, false, true);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.findById(2L)).thenReturn(Optional.of(b));
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenAnswer(inv -> inv.getArgument(0));

        service.editarContacto(1L, 1L, new AtualizarContactoRequest(null, null, null, false, null));
        EntidadeContacto resultado = service.editarContacto(1L, 2L,
                new AtualizarContactoRequest(null, null, null, true, null));

        assertThat(a.isPrincipal()).isFalse();
        assertThat(resultado.isPrincipal()).isTrue();
        assertThat(resultado.isAtivo()).isTrue();
    }

    @Test
    void manterOPrincipalAtivoNaoConflituaConsigoProprio() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto a = contacto(1L, true, true);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.of(a));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenAnswer(inv -> inv.getArgument(0));

        EntidadeContacto resultado = service.editarContacto(1L, 1L,
                new AtualizarContactoRequest("novo@cm-sintra.pt", null, null, true, null));

        assertThat(resultado.getEmail()).isEqualTo("novo@cm-sintra.pt");
    }

    @Test
    void desativarOPrincipalDeixaAEntidadeSemPrincipalEAuditaOAntesEODepois() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto a = contacto(1L, true, true);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenAnswer(inv -> inv.getArgument(0));

        EntidadeContacto resultado = service.editarContacto(1L, 1L,
                new AtualizarContactoRequest(null, null, null, null, false));

        assertThat(resultado.isAtivo()).isFalse();
        verify(repository, never()).findByEntidadeIdAndPrincipalTrueAndAtivoTrue(any());
        Map<String, Object> dados = dadosAuditados("EDITAR_CONTACTO");
        assertThat(dados).containsEntry("contactoId", 1L).containsOnlyKeys("contactoId", "ativo");
        Map<?, ?> ativo = (Map<?, ?>) dados.get("ativo");
        assertThat(ativo.get("antes")).isEqualTo(true);
        assertThat(ativo.get("depois")).isEqualTo(false);
    }

    @Test
    void reativarUmPrincipalInativoComOutroPrincipalAtivoDa409() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto inativo = contacto(3L, true, false);
        when(repository.findById(3L)).thenReturn(Optional.of(inativo));
        when(repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(1L)).thenReturn(Optional.of(contacto(1L, true, true)));
        AtualizarContactoRequest pedido = new AtualizarContactoRequest(null, null, null, null, true);

        assertThatThrownBy(() -> service.editarContacto(1L, 3L, pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class);
        assertThat(inativo.isAtivo()).isFalse();
    }

    @Test
    void telefoneVazioLimpaEAusenteMantem() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto contacto = contacto(2L, false, true);
        contacto.setTelefone("219238500");
        contacto.setDescricao("Urbanismo");
        when(repository.findById(2L)).thenReturn(Optional.of(contacto));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenAnswer(inv -> inv.getArgument(0));

        service.editarContacto(1L, 2L, new AtualizarContactoRequest("novo@cm-sintra.pt", null, null, null, null));
        assertThat(contacto.getTelefone()).isEqualTo("219238500");
        assertThat(contacto.getDescricao()).isEqualTo("Urbanismo");

        service.editarContacto(1L, 2L, new AtualizarContactoRequest(null, "", "", null, null));
        assertThat(contacto.getTelefone()).isNull();
        assertThat(contacto.getDescricao()).isNull();
    }

    @Test
    void telefoneInvalidoOuEmailVazioDa400SemGravar() {
        comoPerfil(PerfilInterno.MASTER);
        AtualizarContactoRequest telefone = new AtualizarContactoRequest(null, null, "12345", null, null);
        assertThatThrownBy(() -> service.editarContacto(1L, 2L, telefone))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("telefone"));

        AtualizarContactoRequest email = new AtualizarContactoRequest("", null, null, null, null);
        assertThatThrownBy(() -> service.editarContacto(1L, 2L, email))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(ex -> assertThat(((ValidacaoException) ex).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("email"));
        verify(repository, never()).findById(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void corpoVazioDa400() {
        comoPerfil(PerfilInterno.MASTER);
        AtualizarContactoRequest vazio = new AtualizarContactoRequest(null, null, null, null, null);

        assertThatThrownBy(() -> service.editarContacto(1L, 2L, vazio)).isInstanceOf(ValidacaoException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void semAlteracoesNaoGravaNemAudita() {
        comoPerfil(PerfilInterno.MASTER);
        EntidadeContacto contacto = contacto(2L, false, true);
        when(repository.findById(2L)).thenReturn(Optional.of(contacto));

        service.editarContacto(1L, 2L, new AtualizarContactoRequest(contacto.getEmail(), null, null, false, true));

        verify(repository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(UUID.class), any());
    }

    @Test
    void contactoDeOutraEntidadeDa404() {
        comoPerfil(PerfilInterno.MASTER);
        when(repository.findById(2L)).thenReturn(Optional.of(contacto(2L, false, true)));
        AtualizarContactoRequest pedido = new AtualizarContactoRequest(null, "Urbanismo", null, null, null);

        assertThatThrownBy(() -> service.editarContacto(99L, 2L, pedido))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void violacaoDeUnicidadeAoGuardarDa409() {
        comoPerfil(PerfilInterno.MASTER);
        when(repository.findById(2L)).thenReturn(Optional.of(contacto(2L, false, true)));
        when(repository.saveAndFlush(any(EntidadeContacto.class))).thenThrow(new DataIntegrityViolationException("dup"));
        AtualizarContactoRequest pedido = new AtualizarContactoRequest("ja-existe@cm-sintra.pt", null, null, null, null);

        assertThatThrownBy(() -> service.editarContacto(1L, 2L, pedido))
                .isInstanceOf(ReferenciaDuplicadaException.class);
    }

    @Test
    void editarSemSerMasterDa403() {
        comoPerfil(PerfilInterno.TECNICO);
        AtualizarContactoRequest pedido = new AtualizarContactoRequest(null, null, null, true, null);

        assertThatThrownBy(() -> service.editarContacto(1L, 2L, pedido)).isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(repository, never()).findById(any());
    }
}
