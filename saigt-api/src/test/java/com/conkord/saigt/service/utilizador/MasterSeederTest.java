package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterSeederTest {

    @Mock
    private UtilizadorRepository utilizadorRepository;

    @Test
    void naoFazNadaQuandoOUsernameDeArranqueNaoEstaConfigurado() {
        MasterSeeder seeder = new MasterSeeder(utilizadorRepository, "");

        seeder.run(null);

        verify(utilizadorRepository, never()).countByPerfilInterno(any());
        verify(utilizadorRepository, never()).save(any());
    }

    @Test
    void criaOMasterAdDgtComONomeDoUsernameESemEmailQuandoNaoHaNenhumMaster() {
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(0L);
        when(utilizadorRepository.findByNomeAndOrigemAutenticacao("ana.silva", "AD_DGT"))
                .thenReturn(Optional.empty());
        when(utilizadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MasterSeeder seeder = new MasterSeeder(utilizadorRepository, " ana.silva ");

        seeder.run(null);

        ArgumentCaptor<Utilizador> captor = ArgumentCaptor.forClass(Utilizador.class);
        verify(utilizadorRepository).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("ana.silva");
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getPerfilInterno()).isEqualTo(PerfilInterno.MASTER);
        assertThat(captor.getValue().getOrigemAutenticacao()).isEqualTo("AD_DGT");
    }

    @Test
    void naoCriaNadaQuandoJaExisteUmMaster() {
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(1L);
        MasterSeeder seeder = new MasterSeeder(utilizadorRepository, "ana.silva");

        seeder.run(null);

        verify(utilizadorRepository, never()).save(any());
    }

    @Test
    void naoDuplicaNemPromoveAContaQueJaTemOUsername() {
        Utilizador existente = new Utilizador("ana.silva", null);
        existente.setPerfilInterno(PerfilInterno.VISITANTE);
        when(utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER)).thenReturn(0L);
        when(utilizadorRepository.findByNomeAndOrigemAutenticacao("ana.silva", "AD_DGT"))
                .thenReturn(Optional.of(existente));
        MasterSeeder seeder = new MasterSeeder(utilizadorRepository, "ana.silva");

        seeder.run(null);

        verify(utilizadorRepository, never()).save(any());
        assertThat(existente.getPerfilInterno()).isEqualTo(PerfilInterno.VISITANTE);
    }
}
