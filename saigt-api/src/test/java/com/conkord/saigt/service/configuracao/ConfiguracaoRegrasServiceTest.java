package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.CondicaoConfigRequest;
import com.conkord.saigt.dto.configuracao.RegraConfigRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.regras.RegistoAvaliadores;
import com.conkord.saigt.service.regras.avaliador.Avaliador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A validação usa os avaliadores pelo {@link RegistoAvaliadores}, como o arranque; nada se grava se falhar. */
class ConfiguracaoRegrasServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegraRepository regraRepository = mock(RegraRepository.class);
    private final CondicaoRepository condicaoRepository = mock(CondicaoRepository.class);
    private final InstrumentoDinamicaConfigRepository dinamicaConfigRepository = mock(InstrumentoDinamicaConfigRepository.class);
    private final AuditoriaService auditoriaService = mock(AuditoriaService.class);
    private final AutorizacaoService autorizacaoService = mock(AutorizacaoService.class);
    private final ContextoUtilizadorResolver contextoUtilizadorResolver = mock(ContextoUtilizadorResolver.class);
    private final Avaliador emVigor = mock(Avaliador.class);
    private ConfiguracaoRegrasService service;

    @BeforeEach
    void preparar() {
        when(emVigor.codigo()).thenReturn("INSTRUMENTO_EM_VIGOR");
        when(contextoUtilizadorResolver.resolver()).thenReturn(new ContextoUtilizador(UUID.randomUUID(), "AD_DGT",
                PerfilInterno.MASTER, null, "DGT", null, null, null, null, null, null));
        InstrumentoDinamicaConfig dinamica = new InstrumentoDinamicaConfig();
        ReflectionTestUtils.setField(dinamica, "id", 7L);
        when(dinamicaConfigRepository.findById(7L)).thenReturn(Optional.of(dinamica));
        service = new ConfiguracaoRegrasService(regraRepository, condicaoRepository, dinamicaConfigRepository,
                mock(InstrumentoProcedimentoConfigRepository.class), new RegistoAvaliadores(List.of(emVigor)),
                auditoriaService, autorizacaoService, contextoUtilizadorResolver, objectMapper);
    }

    private RegraConfigRequest regra(String parametros, boolean ativa) throws Exception {
        return new RegraConfigRequest(7L, null, false, "AND", null, true,
                List.of(new CondicaoConfigRequest(null, "INSTRUMENTO_EM_VIGOR", objectMapper.readTree(parametros),
                        (short) 1, ativa)));
    }

    @Test
    void osMotivosDoAvaliadorVoltamUmPorCampoENadaSeGrava() throws Exception {
        when(emVigor.validarParametros("{\"a\":1,\"b\":2}")).thenReturn(List.of("chave desconhecida: a", "chave desconhecida: b"));

        assertThatThrownBy(() -> service.criar(regra("{\"a\":1,\"b\":2}", true)))
                .isInstanceOfSatisfying(ValidacaoException.class, erro -> assertThat(erro.getCampos())
                        .extracting(ValidacaoException.CampoErro::path, ValidacaoException.CampoErro::erro)
                        .containsExactly(tuple("condicoes[0].parametros", "chave desconhecida: a"),
                                tuple("condicoes[0].parametros", "chave desconhecida: b")));

        verify(regraRepository, never()).save(any());
        verify(condicaoRepository, never()).saveAll(any());
        verify(auditoriaService, never()).registar(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void asCondicoesInativasTambemSaoValidadas() throws Exception {
        when(emVigor.validarParametros("{\"x\":1}")).thenReturn(List.of("chave desconhecida: x"));
        RegraConfigRequest inativa = new RegraConfigRequest(7L, null, false, "AND", null, false,
                List.of(new CondicaoConfigRequest(null, "INSTRUMENTO_EM_VIGOR", objectMapper.readTree("{\"x\":1}"),
                        (short) 1, false)));

        assertThatThrownBy(() -> service.criar(inativa)).isInstanceOf(ValidacaoException.class);
        verify(regraRepository, never()).save(any());
    }

    @Test
    void semPerfilMasterNaoTocaNosRepositoriosNemAudita() {
        doThrow(new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação.")).when(autorizacaoService)
                .verificarPerfilMaster(any());

        assertThatThrownBy(() -> service.listar(null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.criar(regra("{}", true))).isInstanceOf(AcaoNaoAutorizadaException.class);
        assertThatThrownBy(() -> service.atualizar(1L, regra("{}", true))).isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(regraRepository, condicaoRepository, auditoriaService);
    }
}
