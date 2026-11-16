package com.conkord.saigt.service.sessao;

import com.conkord.saigt.exception.SessaoInvalidaException;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Sessao;
import com.conkord.saigt.persistence.operacional.SessaoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessaoServiceTest {

    private final SessaoRepository sessaoRepository = mock(SessaoRepository.class);
    private final SessaoService sessaoService = new SessaoService(sessaoRepository, new ObjectMapper());

    private Utilizador utilizador;

    @BeforeEach
    void setUp() {
        utilizador = new Utilizador("Ana Silva", "ana.silva@dgt.pt");
        ReflectionTestUtils.setField(utilizador, "id", UUID.randomUUID());
        when(sessaoRepository.save(any(Sessao.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ClaimsUtilizador.ClaimsEntidade claimsEntidade(String nif, String nome, String incmEnrollmentId) {
        return new ClaimsUtilizador.ClaimsEntidade(7L, "5245", "CM", "1111", nif, nome, incmEnrollmentId, false, null, null,
                null,
                new ClaimsUtilizador.DrParte(1234567891234567L, "C", null),
                new ClaimsUtilizador.AtosAutorizados(List.of(), List.of(new ClaimsUtilizador.AtoAutorizado(9L, null, true))));
    }

    @Test
    void deveReconstruirOsClaimsInternosDoUtilizadorEmBdSemCargo() {
        utilizador.setPerfilInterno(PerfilInterno.MASTER);

        ClaimsUtilizador claims = sessaoService.claimsDaSessao(utilizador, null);

        assertThat(claims.utilizadorId()).isEqualTo(utilizador.getId());
        assertThat(claims.perfilInterno()).isEqualTo(PerfilInterno.MASTER);
        assertThat(claims.entidade()).isNull();
        assertThat(claims.cargo()).isNull();
    }

    @Test
    void deveReconstruirClaimsExternosDoContextoDaSessaoSemChamarAIncm() throws Exception {
        utilizador.setCargo("Técnica Superior de Urbanismo");
        ContextoSessaoAtiva contexto = new ContextoSessaoAtiva(claimsEntidade("512523698", "Município de Lisboa", "2015482"));

        ClaimsUtilizador claims = sessaoService.claimsDaSessao(utilizador, new ObjectMapper().writeValueAsString(contexto));

        assertThat(claims.perfilInterno()).isNull();
        assertThat(claims.origem()).isEqualTo(utilizador.getOrigemAutenticacao());
        assertThat(claims.entidade().nipc()).isEqualTo("512523698");
        assertThat(claims.entidade().id()).isEqualTo(7L);
        assertThat(claims.entidade().dico()).isEqualTo("1111");
        assertThat(claims.entidade().nome()).isEqualTo("Município de Lisboa");
        assertThat(claims.entidade().incmEnrollmentId()).isEqualTo("2015482");
        assertThat(claims.cargo()).isEqualTo("Técnica Superior de Urbanismo");
    }

    @Test
    void deveRecusarOsClaimsDeUmaSessaoGravadaSemEntidadeSaigtNoContexto() {
        assertThatThrownBy(() -> sessaoService.claimsDaSessao(utilizador,
                "{\"entidade\":{\"nome\":\"Município de Lisboa\"}}"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    @Test
    void deveRevogarALinhaDePreAutenticacao() {
        Sessao preAutenticacao = Sessao.paraPreAutenticacao("jti-pre-auth", utilizador, Instant.now().plusSeconds(300), "{}");
        when(sessaoRepository.findById("jti-pre-auth")).thenReturn(Optional.of(preAutenticacao));

        sessaoService.revogarPreAutenticacao("jti-pre-auth");

        assertThat(preAutenticacao.getRevogadoEm()).isNotNull();
        verify(sessaoRepository).save(preAutenticacao);
    }

    @Test
    void deveRecusarRevogarUmaPreAutenticacaoDesconhecida() {
        when(sessaoRepository.findById("jti-desconhecido")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessaoService.revogarPreAutenticacao("jti-desconhecido"))
                .isInstanceOf(SessaoInvalidaException.class);
        verify(sessaoRepository, never()).save(any(Sessao.class));
    }

    @Test
    void deveLevarARevogacaoABdAntesDeOChamadorGravarASessaoNova() {
        Sessao ativa = new Sessao("jti-ativa", utilizador, Instant.now().plusSeconds(1000));
        when(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizador.getId(), "ATIVA"))
                .thenReturn(List.of(ativa));

        sessaoService.revogarSessoesAtivas(utilizador.getId());

        InOrder ordem = inOrder(sessaoRepository);
        ordem.verify(sessaoRepository).save(ativa);
        ordem.verify(sessaoRepository).flush();
    }

    @Test
    void naoDeveFazerFlushQuandoNaoHaSessaoAtiva() {
        when(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizador.getId(), "ATIVA"))
                .thenReturn(List.of());

        assertThat(sessaoService.revogarSessoesAtivas(utilizador.getId())).isZero();

        verify(sessaoRepository).findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizador.getId(), "ATIVA");
        verify(sessaoRepository, never()).flush();
    }

    @Test
    void deveRevogarApenasASessaoAtivaMantendoORevogadoEmOriginalDaJaRevogada() {
        Instant revogadoOriginal = Instant.now().minusSeconds(3600);
        Sessao sessaoAtiva = new Sessao("jti-ativo", utilizador, Instant.now().plusSeconds(1000));
        Sessao sessaoJaRevogada = new Sessao("jti-antigo", utilizador, Instant.now().minusSeconds(100));
        sessaoJaRevogada.setRevogadoEm(revogadoOriginal);
        when(sessaoRepository.findAllByUtilizadorIdAndTipoAndRevogadoEmIsNull(utilizador.getId(), "ATIVA"))
                .thenReturn(List.of(sessaoAtiva));

        int revogadas = sessaoService.revogarSessoesAtivas(utilizador.getId());

        assertThat(revogadas).isEqualTo(1);
        assertThat(sessaoAtiva.getRevogadoEm()).isNotNull();
        assertThat(sessaoJaRevogada.getRevogadoEm()).isEqualTo(revogadoOriginal);
        verify(sessaoRepository).save(sessaoAtiva);
    }

    @Test
    void devePurgarDelegandoNoRepositorioComOInstanteCorrente() {
        when(sessaoRepository.deleteByRevogadoEmIsNotNullOrExpiraEmBefore(any(Instant.class))).thenReturn(3L);

        sessaoService.purgar();

        ArgumentCaptor<Instant> instanteCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(sessaoRepository).deleteByRevogadoEmIsNotNullOrExpiraEmBefore(instanteCaptor.capture());
        assertThat(instanteCaptor.getValue()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }
}
