package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.dto.utilizador.AtualizarPerfilRequest;
import com.conkord.saigt.dto.utilizador.UtilizadorMeResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver.EntidadeDoToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilizadorMeServiceTest {

    @Mock
    private UtilizadorRepository utilizadorRepository;
    @Mock
    private EntidadeRepository entidadeRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private UtilizadorMeService service;

    private Utilizador utilizadorDgt(UUID id) {
        Utilizador utilizador = new Utilizador("ana.silva", "ana.silva@dgt.pt");
        utilizador.setPerfilInterno(PerfilInterno.TECNICO);
        ReflectionTestUtils.setField(utilizador, "id", id);
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(id, "AD_DGT", PerfilInterno.TECNICO, null, null, null, null, null, null, null, null));
        return utilizador;
    }

    private Utilizador utilizadorIncm(UUID id) {
        Utilizador utilizador = new Utilizador("Helena Sintra", "helena.sintra@cm-teste.pt", "INCM");
        utilizador.setCargo("Técnica Superior");
        ReflectionTestUtils.setField(utilizador, "id", id);
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(id, "INCM", null, "501234567", "ENTIDADE", null, null, null, null, TipoEntidade.CM, 7L));
        return utilizador;
    }

    private static AtualizarPerfilRequest pedido(String email, String telefone, String morada) {
        AtualizarPerfilRequest request = new AtualizarPerfilRequest();
        request.setEmail(email);
        request.setTelefone(telefone);
        request.setMorada(morada);
        return request;
    }

    @Test
    void oPerfilDgtTemNomeEmailTelefoneMoradaOrigemEPerfilSemCargoNemEntidade() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);
        utilizador.setTelefone("912345678");
        utilizador.setMorada("Rua da Artilharia Um, 107, Lisboa");
        utilizador.setCargo("Cargo antigo");

        UtilizadorMeResponse resposta = service.obterMe();

        assertThat(resposta.nome()).isEqualTo("ana.silva");
        assertThat(resposta.email()).isEqualTo("ana.silva@dgt.pt");
        assertThat(resposta.telefone()).isEqualTo("912345678");
        assertThat(resposta.morada()).isEqualTo("Rua da Artilharia Um, 107, Lisboa");
        assertThat(resposta.origemAutenticacao()).isEqualTo("AD_DGT");
        assertThat(resposta.perfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(resposta.cargo()).isNull();
        assertThat(resposta.entidade()).isNull();
        verifyNoInteractions(entidadeRepository);
    }

    @Test
    void oPerfilIncmTemCargoEAEntidadeDoTokenComNipcTelefoneEmailEResponsavelDaTabela() {
        UUID id = UUID.randomUUID();
        utilizadorIncm(id);
        when(contextoUtilizadorResolver.entidadeDoToken())
                .thenReturn(Optional.of(new EntidadeDoToken("3001", "Câmara Municipal de Teste", "CM")));
        Entidade entidade = new Entidade("3001", TipoEntidade.CM, "501234567", "CM Teste", "Câmara Municipal de Teste");
        entidade.setTelefone("219000000");
        entidade.setEmail("geral@cm-teste.pt");
        entidade.setResponsavelNome("Carlos Pereira");
        entidade.setResponsavelEmail("carlos.pereira@cm-teste.pt");
        entidade.setResponsavelCargo("Diretor de Departamento");
        when(entidadeRepository.findByCodigo("3001")).thenReturn(Optional.of(entidade));

        UtilizadorMeResponse resposta = service.obterMe();

        assertThat(resposta.nome()).isEqualTo("Helena Sintra");
        assertThat(resposta.email()).isEqualTo("helena.sintra@cm-teste.pt");
        assertThat(resposta.cargo()).isEqualTo("Técnica Superior");
        assertThat(resposta.origemAutenticacao()).isEqualTo("INCM");
        assertThat(resposta.telefone()).isNull();
        assertThat(resposta.morada()).isNull();
        assertThat(resposta.perfilInterno()).isNull();
        assertThat(resposta.entidade()).isEqualTo(new UtilizadorMeResponse.EntidadeSessao("3001",
                "Câmara Municipal de Teste", "CM", "501234567", "219000000", "geral@cm-teste.pt",
                new UtilizadorMeResponse.Responsavel("Carlos Pereira", "carlos.pereira@cm-teste.pt",
                        "Diretor de Departamento")));
    }

    @Test
    void aEntidadeDaSessaoNaoTemMorada() {
        assertThat(Arrays.stream(UtilizadorMeResponse.EntidadeSessao.class.getRecordComponents())
                .map(c -> c.getName()))
                .containsExactly("codigo", "nome", "tipoEntidade", "nipc", "telefone", "email", "responsavel");
    }

    @Test
    void deveGravarEmailTelefoneEMoradaEAuditarOAntesEODepoisSoDosAlterados() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);
        utilizador.setTelefone("210000000");

        UtilizadorMeResponse resposta = service.atualizar(pedido(" ana.nova@dgt.pt ", "+351 912 345 678",
                "Rua da Artilharia Um, 107"));

        assertThat(resposta.email()).isEqualTo("ana.nova@dgt.pt");
        assertThat(resposta.telefone()).isEqualTo("+351 912 345 678");
        assertThat(resposta.morada()).isEqualTo("Rua da Artilharia Um, 107");
        verify(utilizadorRepository).saveAndFlush(utilizador);
        verify(auditoriaService).registar("UTILIZADOR", id.toString(), "EDITAR_PERFIL", id, Map.of(
                "email", Map.of("antes", "ana.silva@dgt.pt", "depois", "ana.nova@dgt.pt"),
                "telefone", Map.of("antes", "210000000", "depois", "+351 912 345 678"),
                "morada", mapaComNulo("Rua da Artilharia Um, 107")));
    }

    private static Map<String, Object> mapaComNulo(String depois) {
        Map<String, Object> par = new java.util.HashMap<>();
        par.put("antes", null);
        par.put("depois", depois);
        return par;
    }

    @Test
    void campoOmitidoNaoMudaEVazioLimpa() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);
        utilizador.setTelefone("912345678");
        utilizador.setMorada("Morada antiga");

        UtilizadorMeResponse resposta = service.atualizar(pedido(null, null, ""));

        assertThat(resposta.email()).isEqualTo("ana.silva@dgt.pt");
        assertThat(resposta.telefone()).isEqualTo("912345678");
        assertThat(resposta.morada()).isNull();
    }

    @Test
    void semAlteracoesNaoGravaNemAudita() {
        UUID id = UUID.randomUUID();
        utilizadorDgt(id);

        service.atualizar(pedido("ana.silva@dgt.pt", null, null));

        verify(utilizadorRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void nomeOuCargoNoPedidoDa400SemMudarNada() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);
        AtualizarPerfilRequest request = pedido("outro@dgt.pt", null, null);
        request.campoNaoEditavel("nome", "Outro Nome");
        request.campoNaoEditavel("cargo", "Diretor");

        assertThatThrownBy(() -> service.atualizar(request))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Só pode alterar o email, o telefone e a morada.")
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("nome", "cargo"));
        assertThat(utilizador.getNome()).isEqualTo("ana.silva");
        assertThat(utilizador.getEmail()).isEqualTo("ana.silva@dgt.pt");
        verify(utilizadorRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void oServicoNaoTemComoEditarONomeNemOCargo() {
        assertThat(Arrays.stream(AtualizarPerfilRequest.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("setNome", "setCargo", "getNome", "getCargo");
    }

    @Test
    void validaOFormatoDoEmailEOsLimitesDeTelefoneEMorada() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);

        assertThatThrownBy(() -> service.atualizar(pedido("sem-arroba.pt", "9".repeat(31), "m".repeat(301))))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("email", "telefone", "morada"));
        assertThatThrownBy(() -> service.atualizar(pedido("a".repeat(196) + "@x.pt", "abc", null)))
                .isInstanceOf(ValidacaoException.class)
                .satisfies(e -> assertThat(((ValidacaoException) e).getCampos())
                        .extracting(ValidacaoException.CampoErro::path).containsExactly("email", "telefone"));
        assertThat(utilizador.getEmail()).isEqualTo("ana.silva@dgt.pt");
        verify(utilizadorRepository, never()).saveAndFlush(any());
    }

    @Test
    void aceitaOsLimitesExatos() {
        UUID id = UUID.randomUUID();
        utilizadorDgt(id);

        UtilizadorMeResponse resposta = service.atualizar(pedido("a".repeat(195) + "@x.pt", "9".repeat(30), "m".repeat(300)));

        assertThat(resposta.email()).hasSize(200);
        assertThat(resposta.telefone()).hasSize(30);
        assertThat(resposta.morada()).hasSize(300);
    }

    @Test
    void emailDeOutraContaDgtDa409NoCampo() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorDgt(id);
        when(utilizadorRepository.existsByEmailIgnoreCaseAndOrigemAutenticacaoAndIdNot("Carlos.Tecnico@dgt.pt", "AD_DGT", id))
                .thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(pedido("Carlos.Tecnico@dgt.pt", null, null)))
                .isInstanceOf(ReferenciaDuplicadaException.class)
                .satisfies(e -> {
                    assertThat(((ReferenciaDuplicadaException) e).getCodigo()).isEqualTo("REFERENCIA_DUPLICADA");
                    assertThat(((ReferenciaDuplicadaException) e).getCampo()).isEqualTo("email");
                });
        assertThat(utilizador.getEmail()).isEqualTo("ana.silva@dgt.pt");
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void aViolacaoDaUnicidadeNaGravacaoTambemDa409() {
        UUID id = UUID.randomUUID();
        utilizadorDgt(id);
        when(utilizadorRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_utilizador_email_origem"));

        assertThatThrownBy(() -> service.atualizar(pedido("concorrente@dgt.pt", null, null)))
                .isInstanceOf(ReferenciaDuplicadaException.class);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void utilizadorIncmRecebe403GeridoPelaIncmSemValidarNemGravar() {
        UUID id = UUID.randomUUID();
        utilizadorIncm(id);

        assertThatThrownBy(() -> service.atualizar(pedido("x@y.pt", null, null)))
                .isInstanceOf(AcaoNaoAutorizadaException.class)
                .hasMessage("Os seus dados são geridos pela INCM.");
        verify(utilizadorRepository, never()).saveAndFlush(any());
        verify(utilizadorRepository, never()).existsByEmailIgnoreCaseAndOrigemAutenticacaoAndIdNot(anyString(), anyString(), any());
    }

    @Test
    void deveDevolver404QuandoUtilizadorDaSessaoNaoExisteEmBd() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver())
                .thenReturn(new ContextoUtilizador(id, "AD_DGT", PerfilInterno.TECNICO, null, null, null, null, null, null, null, null));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(service::obterMe).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
