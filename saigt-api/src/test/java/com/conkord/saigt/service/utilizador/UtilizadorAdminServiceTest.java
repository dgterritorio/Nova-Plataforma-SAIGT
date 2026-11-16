package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.dto.utilizador.AlterarPerfilInternoRequest;
import com.conkord.saigt.dto.utilizador.RevogarSessoesResponse;
import com.conkord.saigt.dto.utilizador.UtilizadorFiltro;
import com.conkord.saigt.dto.utilizador.UtilizadorResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.dto.utilizador.EntidadeVinculoResponse;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.sessao.SessaoService;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilizadorAdminServiceTest {

    @Mock
    private UtilizadorRepository utilizadorRepository;
    @Mock
    private AuditoriaRepository auditoriaRepository;
    @Mock
    private UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private SessaoService sessaoService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private UtilizadorAdminService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null,
                perfilInterno == PerfilInterno.MASTER ? "DGT" : null, null, null, null, null, null, null);
    }

    private static Utilizador utilizadorAdGdt(UUID id, PerfilInterno perfilInterno) {
        Utilizador utilizador = new Utilizador(null, "utilizador" + id + "@dgt.pt");
        utilizador.setPerfilInterno(perfilInterno);
        ReflectionTestUtils.setField(utilizador, "id", id);
        return utilizador;
    }

    @Test
    void deveAlterarPerfilInternoERevogarSessoes() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UtilizadorResponse resposta = service.alterarPerfilInterno(id, new AlterarPerfilInternoRequest("MASTER"));

        assertThat(resposta.perfilInterno()).isEqualTo(PerfilInterno.MASTER);
        verify(sessaoService).revogarSessoesAtivas(id);
        verify(auditoriaService).registar(eq("UTILIZADOR"), eq(id.toString()), eq("ALTERAR_PERFIL_INTERNO"), any(), any());
    }

    @Test
    void deveDespromoverMasterATecnicoRevogarSessoesEAuditarPerfilAntesEDepois() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.MASTER);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(2L);
        when(utilizadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UtilizadorResponse resposta = service.alterarPerfilInterno(id, new AlterarPerfilInternoRequest("TECNICO"));

        assertThat(resposta.perfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(utilizador.getPerfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        verify(sessaoService).revogarSessoesAtivas(id);
        ArgumentCaptor<Object> dados = ArgumentCaptor.forClass(Object.class);
        verify(auditoriaService).registar(eq("UTILIZADOR"), eq(id.toString()), eq("ALTERAR_PERFIL_INTERNO"), any(), dados.capture());
        assertThat(dados.getValue()).asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("perfilInternoAntes", "MASTER")
                .containsEntry("perfilInternoDepois", "TECNICO")
                .doesNotContainKey("motivo");
    }

    @Test
    void deveRejeitarAlterarPerfilInternoParaValorAntigoAdminDgt() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizadorAdGdt(id, PerfilInterno.TECNICO)));
        AlterarPerfilInternoRequest request = new AlterarPerfilInternoRequest("ADMIN_DGT");

        assertThatThrownBy(() -> service.alterarPerfilInterno(id, request))
                .isInstanceOfSatisfying(ValidacaoException.class, ex -> assertThat(ex.getCampos())
                        .extracting(ValidacaoException.CampoErro::path)
                        .containsExactly("perfilInterno"));
        verify(utilizadorRepository, never()).save(any());
        verify(sessaoService, never()).revogarSessoesAtivas(any());
    }

    @Test
    void deveRejeitarAlterarPerfilInternoDeUtilizadorIncm() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = new Utilizador("Nome INCM", "incm@empresa.pt", "INCM");
        ReflectionTestUtils.setField(utilizador, "id", id);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        AlterarPerfilInternoRequest request = new AlterarPerfilInternoRequest("TECNICO");

        assertThatThrownBy(() -> service.alterarPerfilInterno(id, request))
                .isInstanceOf(ValidacaoException.class);
        verify(sessaoService, never()).revogarSessoesAtivas(any());
    }

    @Test
    void deveRejeitarAlterarPerfilInternoQuandoDeixaZeroAdminsAtivos() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.MASTER);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(1L);
        AlterarPerfilInternoRequest request = new AlterarPerfilInternoRequest("TECNICO");

        assertThatThrownBy(() -> service.alterarPerfilInterno(id, request))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Operação deixaria a plataforma sem administrador.");
        verify(utilizadorRepository, never()).save(any());
        verify(sessaoService, never()).revogarSessoesAtivas(any());
    }

    @Test
    void devePermitirAlterarPerfilInternoDeUmAdminQuandoHaOutroMaster() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.MASTER);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(2L);
        when(utilizadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UtilizadorResponse resposta = service.alterarPerfilInterno(id, new AlterarPerfilInternoRequest("TECNICO"));

        assertThat(resposta.perfilInterno()).isEqualTo(PerfilInterno.TECNICO);
    }

    @Test
    void naoContaOsMastersQuandoOAlvoNaoEMasterOuContinuaMaster() {
        UUID id = UUID.randomUUID();
        Utilizador tecnico = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(tecnico));
        when(utilizadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.alterarPerfilInterno(id, new AlterarPerfilInternoRequest("VISITANTE"));

        verify(utilizadorRepository, never()).countByPerfilInterno(any());
    }

    @Test
    void deveRevogarSessoesSemMotivoEDevolverQuantasRevogou() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(sessaoService.revogarSessoesAtivas(id)).thenReturn(2);

        RevogarSessoesResponse resposta = service.revogarSessoes(id);

        assertThat(resposta.sessoesRevogadas()).isEqualTo(2);
        verify(utilizadorRepository, never()).save(any());
        verify(auditoriaService).registar(eq("UTILIZADOR"), eq(id.toString()), eq("REVOGAR_SESSOES"), any(),
                eq(Map.of("sessoesRevogadas", 2)));
    }

    @Test
    void deveAceitarRevogarSessoesQuandoSemSessaoAtiva() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));

        assertThat(service.revogarSessoes(id).sessoesRevogadas()).isZero();
    }

    @Test
    void deveRejeitarRevogarSessoesQuandoChamadorNaoEAdmin() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.revogarSessoes(id))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        verify(sessaoService, never()).revogarSessoesAtivas(any());
    }

    @Test
    void deveDevolver404AoRevogarSessoesDeUtilizadorInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revogarSessoes(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(sessaoService, never()).revogarSessoesAtivas(any());
    }

    @Test
    void deveRejeitarListarEObterQuandoChamadoPorTecnico() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));
        UtilizadorFiltro filtro = new UtilizadorFiltro(null, null, null, null);
        Pageable pagina = Pageable.unpaged();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.listar(filtro, pagina)).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.obter(id)).isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(utilizadorRepository);
    }

    @Test
    void deveRejeitarListarComFiltroDePerfilInternoForaDoEnum() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        UtilizadorFiltro filtro = new UtilizadorFiltro(null, "ADMIN_DGT", null, null);
        Pageable pagina = Pageable.unpaged();

        assertThatThrownBy(() -> service.listar(filtro, pagina))
                .isInstanceOf(ValidacaoException.class);
        verify(utilizadorRepository, never()).findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class));
    }

    @Test
    void deveRejeitarListarQuandoChamadorNaoTemPerfilInternoDeLeitura() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(null));
        UtilizadorFiltro filtro = new UtilizadorFiltro(null, null, null, null);
        Pageable pageable = Pageable.unpaged();

        assertThatThrownBy(() -> service.listar(filtro, pageable))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
    }

    @Test
    void deveDevolver404QuandoObterUtilizadorInexistente() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveDevolverEntidadesLigadasNoDetalheDeUtilizadorIncm() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = new Utilizador("Ana INCM", "ana@empresa.pt", "INCM");
        ReflectionTestUtils.setField(utilizador, "id", id);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));

        Entidade entidade1 = entidade(1L, "Camara A", utilizador);
        Entidade entidade2 = entidade(2L, "Camara B", null);
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id)))
                .thenReturn(List.of(vinculo(utilizador, entidade1), vinculo(utilizador, entidade2)));
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of());

        UtilizadorResponse resposta = service.obter(id);

        assertThat(resposta.entidades()).containsExactlyInAnyOrder(
                new EntidadeVinculoResponse(1L, "Camara A", true), new EntidadeVinculoResponse(2L, "Camara B", false));
    }

    @Test
    void deveDevolverListaVaziaDeEntidadesParaUtilizadorSemLigacao() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))).thenReturn(List.of());
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of());

        UtilizadorResponse resposta = service.obter(id);

        assertThat(resposta.entidades()).isEmpty();
    }

    @Test
    void naoDeveDevolverEntidadeParaAdminQueApenasValidouProcessosSemSerOTitularDaAdesao() {
        UUID anaId = UUID.randomUUID();
        Utilizador ana = utilizadorAdGdt(anaId, PerfilInterno.MASTER);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(anaId)).thenReturn(Optional.of(ana));
        // A auditoria de Sintra não cria vínculo: a Ana não vem em nenhum `ValidateUser`.
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(anaId))).thenReturn(List.of());
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(anaId))).thenReturn(List.of());

        UtilizadorResponse resposta = service.obter(anaId);

        assertThat(resposta.entidades()).isEmpty();
    }

    @Test
    void deveDevolverUltimoLoginNoDetalheDeUtilizadorAdDgt() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.MASTER);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))).thenReturn(List.of());
        Instant ultimoLogin = Instant.parse("2026-09-16T09:00:00Z");
        // Sem `projecaoUltimoLogin`: o detalhe não indexa por utilizador e o stub de `getUtilizadorId()` ficaria por usar.
        // Fora do `thenReturn`: stubbing encaixado dá UnfinishedStubbing.
        AuditoriaRepository.UltimoLoginProjecao projecao =
                mock(AuditoriaRepository.UltimoLoginProjecao.class);
        when(projecao.getUltimoLoginEm()).thenReturn(ultimoLogin);
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of(projecao));

        UtilizadorResponse resposta = service.obter(id);

        assertThat(resposta.ultimoLoginEm()).isEqualTo(ultimoLogin);
    }

    @Test
    void deveDevolverUltimoLoginNuloQuandoUtilizadorNuncaFezLogin() {
        UUID id = UUID.randomUUID();
        Utilizador utilizador = utilizadorAdGdt(id, PerfilInterno.TECNICO);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findById(id)).thenReturn(Optional.of(utilizador));
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))).thenReturn(List.of());
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of());

        UtilizadorResponse resposta = service.obter(id);

        assertThat(resposta.ultimoLoginEm()).isNull();
    }

    @Test
    void deveResolverFiltroEntidadeIdPelosVinculosENaoPelaAuditoria() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorEntidadeRepository.findUtilizadorIdsByEntidadeId(1L)).thenReturn(List.of(UUID.randomUUID()));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(new UtilizadorFiltro(null, null, 1L, null), Pageable.unpaged());

        verify(utilizadorEntidadeRepository).findUtilizadorIdsByEntidadeId(1L);
        verify(auditoriaRepository, never()).findUtilizadorIdsQueAtuaramNaEntidadeDesde(any(), any());
    }

    /** Roster: quem atuou pela entidade desde a data (início do dia em Lisboa), não os vínculos. */
    @Test
    void deveResolverEntidadeIdComAtuouDesdePelaAuditoriaDaEntidade() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(auditoriaRepository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(any(), any())).thenReturn(List.of(UUID.randomUUID()));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(new UtilizadorFiltro(null, null, 1L, LocalDate.of(2026, 9, 1)), Pageable.unpaged());

        verify(auditoriaRepository).findUtilizadorIdsQueAtuaramNaEntidadeDesde(1L, Instant.parse("2026-08-31T23:00:00Z"));
        verify(utilizadorEntidadeRepository, never()).findUtilizadorIdsByEntidadeId(any());
    }

    @Test
    void deveRecusarAtuouDesdeSemEntidadeComIndiqueAEntidade() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));

        assertThatThrownBy(() -> service.listar(new UtilizadorFiltro(null, null, null, LocalDate.of(2026, 9, 1)), Pageable.unpaged()))
                .isInstanceOf(ValidacaoException.class)
                .hasMessage("Indique a entidade.");
        verifyNoInteractions(auditoriaRepository);
        verify(utilizadorRepository, never()).findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class));
    }

    @Test
    void deveDevolverUltimaAtuacaoNaEntidadeSoQuandoFiltraPorEntidade() {
        UUID id = UUID.randomUUID();
        Utilizador ana = new Utilizador("Ana", "ana.roster@empresa.pt", "INCM");
        ReflectionTestUtils.setField(ana, "id", id);
        Instant ultimaAtuacao = Instant.parse("2026-09-10T10:00:00Z");
        AuditoriaRepository.UltimaAtuacaoProjecao projecao = mock(AuditoriaRepository.UltimaAtuacaoProjecao.class);
        when(projecao.getUtilizadorId()).thenReturn(id);
        when(projecao.getUltimaAtuacaoEm()).thenReturn(ultimaAtuacao);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(auditoriaRepository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(any(), any())).thenReturn(List.of(id));
        when(auditoriaRepository.findUltimaAtuacaoNaEntidadeEmLote(7L, List.of(id))).thenReturn(List.of(projecao));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ana)));

        Page<UtilizadorResponse> comEntidade = service.listar(new UtilizadorFiltro("INCM", null, 7L, LocalDate.of(2026, 9, 1)), Pageable.unpaged());
        Page<UtilizadorResponse> semEntidade = service.listar(new UtilizadorFiltro("INCM", null, null, null), Pageable.unpaged());

        assertThat(comEntidade.getContent().get(0).ultimaAtuacaoEm()).isEqualTo(ultimaAtuacao);
        assertThat(semEntidade.getContent().get(0).ultimaAtuacaoEm()).isNull();
        verify(auditoriaRepository, times(1)).findUltimaAtuacaoNaEntidadeEmLote(any(), any());
    }

    @Test
    void naoDeveConsultarAuditoriaAoListarSemFiltrosDeAtuacao() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(new UtilizadorFiltro(null, null, null, null), Pageable.unpaged());

        verify(auditoriaRepository, never()).findUtilizadorIdsQueAtuaramNaEntidadeDesde(any(), any());
        verify(utilizadorEntidadeRepository, never()).findUtilizadorIdsByEntidadeId(any());
    }

    @Test
    void deveAgruparEntidadesPorUtilizadorAoListar() {
        UUID utilizadorA = UUID.randomUUID();
        UUID utilizadorB = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        Utilizador ana = new Utilizador("Ana", "ana@empresa.pt", "INCM");
        Utilizador bruno = new Utilizador("Bruno", "bruno@empresa.pt", "INCM");
        ReflectionTestUtils.setField(ana, "id", utilizadorA);
        ReflectionTestUtils.setField(bruno, "id", utilizadorB);
        Page<Utilizador> pagina = new PageImpl<>(List.of(ana, bruno));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(pagina);

        // Bruno é técnico da Camara A sem ser o responsável, e responsável da Camara B.
        Entidade entidade1 = entidade(1L, "Camara A", ana);
        Entidade entidade2 = entidade(2L, "Camara B", bruno);
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(utilizadorA, utilizadorB)))
                .thenReturn(List.of(vinculo(ana, entidade1), vinculo(bruno, entidade1), vinculo(bruno, entidade2)));
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(utilizadorA, utilizadorB))).thenReturn(List.of());

        Page<UtilizadorResponse> resultado = service.listar(new UtilizadorFiltro(null, null, null, null), Pageable.unpaged());

        Map<UUID, UtilizadorResponse> porId =
                resultado.getContent().stream().collect(java.util.stream.Collectors.toMap(UtilizadorResponse::id, r -> r));
        assertThat(porId.get(utilizadorA).entidades()).containsExactly(new EntidadeVinculoResponse(1L, "Camara A", true));
        assertThat(porId.get(utilizadorB).entidades()).containsExactly(
                new EntidadeVinculoResponse(1L, "Camara A", false), new EntidadeVinculoResponse(2L, "Camara B", true));
        verify(utilizadorEntidadeRepository, times(1)).findComEntidadeByUtilizadorIdIn(any());
    }

    @Test
    void deveDevolverListaVaziaQuandoUtilizadorDaPaginaNaoTemEntidadeLigada() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        Page<Utilizador> pagina = new PageImpl<>(List.of(utilizadorAdGdt(id, PerfilInterno.TECNICO)));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(pagina);
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))).thenReturn(List.of());
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of());

        Page<UtilizadorResponse> resultado = service.listar(new UtilizadorFiltro(null, null, null, null), Pageable.unpaged());

        assertThat(resultado.getContent().get(0).entidades()).isEmpty();
    }

    @Test
    void deveDevolverUltimoLoginPorUtilizadorAoListar() {
        UUID id = UUID.randomUUID();
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        Page<Utilizador> pagina = new PageImpl<>(List.of(utilizadorAdGdt(id, PerfilInterno.TECNICO)));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(pagina);
        when(utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))).thenReturn(List.of());
        Instant ultimoLogin = Instant.parse("2026-09-16T09:00:00Z");
        // Fora do `thenReturn`: stubbing encaixado dá UnfinishedStubbing.
        AuditoriaRepository.UltimoLoginProjecao projecao = projecaoUltimoLogin(id, ultimoLogin);
        when(auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id))).thenReturn(List.of(projecao));

        Page<UtilizadorResponse> resultado = service.listar(new UtilizadorFiltro(null, null, null, null), Pageable.unpaged());

        assertThat(resultado.getContent().get(0).ultimoLoginEm()).isEqualTo(ultimoLogin);
    }

    @Test
    void naoDeveConsultarEntidadeNemAuditoriaEmLoteQuandoAPaginaEstaVazia() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(utilizadorRepository.findAll(ArgumentMatchers.<Specification<Utilizador>>any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(new UtilizadorFiltro(null, null, null, null), Pageable.unpaged());

        verify(utilizadorEntidadeRepository, never()).findComEntidadeByUtilizadorIdIn(any());
        verify(auditoriaRepository, never()).findUltimoLoginEmLotePorUtilizador(any());
    }

    private static Entidade entidade(Long id, String nome, Utilizador responsavel) {
        Entidade entidade = EntidadeTeste.nova("50" + id, nome);
        ReflectionTestUtils.setField(entidade, "id", id);
        entidade.setResponsavelEmail(responsavel == null ? null : responsavel.getEmail().toUpperCase());
        return entidade;
    }

    private static UtilizadorEntidade vinculo(Utilizador utilizador, Entidade entidade) {
        return new UtilizadorEntidade(utilizador, entidade, Instant.now());
    }

    private static AuditoriaRepository.UltimoLoginProjecao projecaoUltimoLogin(UUID utilizadorId, Instant ultimoLoginEm) {
        AuditoriaRepository.UltimoLoginProjecao projecao = mock(AuditoriaRepository.UltimoLoginProjecao.class);
        when(projecao.getUtilizadorId()).thenReturn(utilizadorId);
        when(projecao.getUltimoLoginEm()).thenReturn(ultimoLoginEm);
        return projecao;
    }
}
