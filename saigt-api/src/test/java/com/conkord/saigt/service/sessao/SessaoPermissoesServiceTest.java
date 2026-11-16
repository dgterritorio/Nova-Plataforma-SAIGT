package com.conkord.saigt.service.sessao;

import com.conkord.saigt.dto.sessao.PermissoesResponse;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.PermissaoAcao;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.MotorRegrasService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessaoPermissoesServiceTest {

    private final PermissaoAcaoRepository permissaoAcaoRepository = mock(PermissaoAcaoRepository.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final AutorizacaoService autorizacaoService = new AutorizacaoService(permissaoAcaoRepository,
            mock(RegraRepository.class), mock(InstrumentoProcedimentoConfigRepository.class), mock(ProcessoRepository.class),
            mock(MotorRegrasService.class));
    private final SessaoPermissoesService sessaoPermissoesService =
            new SessaoPermissoesService(permissaoAcaoRepository, contextoUtilizadorResolver, autorizacaoService);

    private InstrumentoProcedimentoConfig procedimentoConfig(long id) {
        InstrumentoProcedimentoConfig config = new InstrumentoProcedimentoConfig();
        ReflectionTestUtils.setField(config, "id", id);
        return config;
    }

    @Test
    void deveDevolverOrigemAtorEProcedimentosPermitidosParaExterno() {
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "512523698", "ENTIDADE", null, null, null, null, TipoEntidade.CM, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        PermissaoAcao permissao1 = new PermissaoAcao(TipoEntidade.CM, procedimentoConfig(10L));
        PermissaoAcao permissao2 = new PermissaoAcao(TipoEntidade.CM, procedimentoConfig(20L));
        PermissaoAcao permissao3 = new PermissaoAcao(TipoEntidade.CM, procedimentoConfig(30L));
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.CM))
                .thenReturn(List.of(permissao1, permissao2, permissao3));

        PermissoesResponse resposta = sessaoPermissoesService.obterPermissoes();

        assertThat(resposta.origem()).isEqualTo("INCM");
        assertThat(resposta.ator()).isEqualTo("ENTIDADE");
        assertThat(resposta.perfilInterno()).isNull();
        assertThat(resposta.tipoEntidade()).isEqualTo(TipoEntidade.CM);
        assertThat(resposta.procedimentosPermitidos()).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void deveDevolverOTipoPcmEListaVaziaAPcmMesmoComLinhasPcmNaConfiguracao() {
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "600000001", "PCM", null, null, null, null, TipoEntidade.PCM, 9001L);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);
        when(permissaoAcaoRepository.findAllByTipoEntidadeAndAtivoTrue(TipoEntidade.PCM))
                .thenReturn(List.of(new PermissaoAcao(TipoEntidade.PCM, procedimentoConfig(10L))));

        PermissoesResponse resposta = sessaoPermissoesService.obterPermissoes();

        assertThat(resposta.ator()).isEqualTo("PCM");
        assertThat(resposta.tipoEntidade()).isEqualTo(TipoEntidade.PCM);
        assertThat(resposta.procedimentosPermitidos()).isNotNull().isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolverTipoNuloEListaVaziaParaEntidadeSemTipoSemConsultarPermissaoAcao() {
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "512523698", "ENTIDADE", null, null, null, null, null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);

        PermissoesResponse resposta = sessaoPermissoesService.obterPermissoes();

        assertThat(resposta.tipoEntidade()).isNull();
        assertThat(resposta.procedimentosPermitidos()).isNotNull().isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolverTipoNuloEListaVaziaParaMasterSemConsultarPermissaoAcao() {
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);

        PermissoesResponse resposta = sessaoPermissoesService.obterPermissoes();

        assertThat(resposta.tipoEntidade()).isNull();
        assertThat(resposta.procedimentosPermitidos()).isNotNull().isEmpty();
        verifyNoInteractions(permissaoAcaoRepository);
    }

    @Test
    void deveDevolverAtorDgtEListaVaziaParaTecnico() {
        ContextoUtilizador contexto = new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", PerfilInterno.TECNICO, null, "DGT", null, null, null, null, null, null);
        when(contextoUtilizadorResolver.resolver()).thenReturn(contexto);

        PermissoesResponse resposta = sessaoPermissoesService.obterPermissoes();

        assertThat(resposta.origem()).isEqualTo("AD_DGT");
        assertThat(resposta.ator()).isEqualTo("DGT");
        assertThat(resposta.perfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(resposta.tipoEntidade()).isNull();
        assertThat(resposta.procedimentosPermitidos()).isNotNull().isEmpty();
    }
}
