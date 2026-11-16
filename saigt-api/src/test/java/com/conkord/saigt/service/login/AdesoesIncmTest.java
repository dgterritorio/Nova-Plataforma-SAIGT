package com.conkord.saigt.service.login;

import com.conkord.saigt.integracao.incm.AdesaoIncm;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdesoesIncmTest {

    private static final String CORPO = "{\"PublishingEntityName\":\"Nome na INCM\",\"PublishingEntityTaxId\":\"500000001\"}";

    private final EntidadeRepository entidadeRepository = mock(EntidadeRepository.class);
    private final CorrespondenciaEntidadeService correspondencia = mock(CorrespondenciaEntidadeService.class);
    private final AdesoesIncm adesoes = new AdesoesIncm(mock(UtilizadorRepository.class), entidadeRepository,
            correspondencia, mock(AuditoriaService.class), mock(ApplicationEventPublisher.class));

    private final Entidade entidade = EntidadeTeste.nova("500000001", "Câmara Municipal de Teste");

    @BeforeEach
    void setUp() {
        when(correspondencia.corresponder(any(), any())).thenReturn(Optional.of(entidade));
        when(entidadeRepository.save(any(Entidade.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void oLoginGuardaTodosOsCamposDaAdesaoEAAdesaoInteiraSemMexerNoNipcNemNaDescricaoIncm() {
        AdesaoIncm adesao = new AdesaoIncm("599999999", "Nome na INCM",
                new AdesaoIncm.Morada("Rua Exemplo", "10", "2.º", "1000-001", "Lisboa", "Lisboa"),
                "210000000", "geral@teste.invalid", "Responsável Fictício", "responsavel@teste.invalid", "Diretor",
                "ADESAO-1", 7L, "C", "Parte C", List.of(1L, 2L),
                List.of(new AdesaoIncm.AtoAutorizado(9L, "Despacho", true)), CORPO);

        Entidade atualizada = adesoes.atualizarEntidade(adesao).orElseThrow();

        assertThat(atualizada.getNipc()).isEqualTo("500000001");
        assertThat(atualizada.getDescricaoIncm()).isEqualTo("Câmara Municipal de Teste");
        assertThat(atualizada.getTelefone()).isEqualTo("210000000");
        assertThat(atualizada.getEmail()).isEqualTo("geral@teste.invalid");
        assertThat(atualizada.getMoradaArteria()).isEqualTo("Rua Exemplo");
        assertThat(atualizada.getMoradaNumero()).isEqualTo("10");
        assertThat(atualizada.getMoradaPiso()).isEqualTo("2.º");
        assertThat(atualizada.getMoradaCodigoPostal()).isEqualTo("1000-001");
        assertThat(atualizada.getMoradaDesignacaoPostal()).isEqualTo("Lisboa");
        assertThat(atualizada.getMoradaLocalidade()).isEqualTo("Lisboa");
        assertThat(atualizada.getIncmEnrollmentId()).isEqualTo("ADESAO-1");
        assertThat(atualizada.getDrParteId()).isEqualTo(7L);
        assertThat(atualizada.getDrParteLetra()).isEqualTo("C");
        assertThat(atualizada.getDrParteDescricao()).isEqualTo("Parte C");
        assertThat(atualizada.getAtosSerie1()).isEqualTo("[1,2]");
        assertThat(atualizada.getAtosSerie2()).isEqualTo("[{\"id\":9,\"nome\":\"Despacho\",\"extrato\":true}]");
        assertThat(atualizada.getDadosIncm()).isEqualTo(CORPO);
        assertThat(atualizada.getAtualizadoEm()).isNotNull();
    }

    @Test
    void semMoradaNaRespostaAsColunasDaMoradaFicamVazias() {
        entidade.setMoradaArteria("Rua Antiga");
        AdesaoIncm adesao = new AdesaoIncm("500000001", "Nome na INCM", null, null, null, null, null, null,
                "ADESAO-1", null, null, null, List.of(), List.of(), CORPO);

        Entidade atualizada = adesoes.atualizarEntidade(adesao).orElseThrow();

        assertThat(atualizada.getMoradaArteria()).isNull();
        assertThat(atualizada.getMoradaLocalidade()).isNull();
        assertThat(atualizada.getDadosIncm()).isEqualTo(CORPO);
    }
}
