package com.conkord.saigt.service.login;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrespondenciaEntidadeServiceTest {

    private final EntidadeRepository entidadeRepository = mock(EntidadeRepository.class);
    private final CorrespondenciaEntidadeService correspondencia = new CorrespondenciaEntidadeService(entidadeRepository);

    private static Entidade entidade(String codigo, String nipc, String descricaoIncm) {
        return new Entidade(codigo, TipoEntidade.CM, nipc, descricaoIncm, descricaoIncm);
    }

    @Test
    void comUmaSoEntidadeDoNipcDevolveEssa() {
        Entidade cascais = entidade("1105", "505187531", "Município de Cascais");
        when(entidadeRepository.findAllByNipc("505187531")).thenReturn(List.of(cascais));

        assertThat(correspondencia.corresponder("505187531", "Outro nome qualquer")).contains(cascais);
    }

    @Test
    void comONipcEmDuasEntidadesDesempataPelaDescricaoIncmSemEspacosNemMaiusculas() {
        Entidade gabineteSecretaria = entidade("3001", "600025861", "Secretaria-Geral da Administração Interna");
        Entidade gabineteMinistro = entidade("3007", "600025861", "Gabinete do Ministro da Administração Interna");
        when(entidadeRepository.findAllByNipc("600025861")).thenReturn(List.of(gabineteSecretaria, gabineteMinistro));

        assertThat(correspondencia.corresponder("600025861", "Gabinete do Ministro da Administração Interna"))
                .contains(gabineteMinistro);
        assertThat(correspondencia.corresponder("600025861", "  GABINETE DO MINISTRO DA ADMINISTRAÇÃO INTERNA "))
                .contains(gabineteMinistro);
    }

    @Test
    void asDuasAdesoesDaLagoaDaoAEntidade5126() {
        Entidade lagoa = entidade("5126", "506804240", "Município de Lagoa (Algarve)");
        when(entidadeRepository.findAllByNipc("506804240")).thenReturn(List.of(lagoa));

        assertThat(correspondencia.corresponder("506804240", "Município de Lagoa")).contains(lagoa);
        assertThat(correspondencia.corresponder("506804240", "Município de Lagoa (Algarve)")).contains(lagoa);
    }

    @Test
    void comONipcEmDuasEntidadesENomeSemCoincidenciaDevolveVazio() {
        when(entidadeRepository.findAllByNipc("600025861")).thenReturn(List.of(
                entidade("3001", "600025861", "Secretaria-Geral da Administração Interna"),
                entidade("3007", "600025861", "Gabinete do Ministro da Administração Interna")));

        assertThat(correspondencia.corresponder("600025861", "Entidade que não existe")).isEmpty();
        assertThat(correspondencia.corresponder("600025861", null)).isEmpty();
    }

    @Test
    void semNipcOuComUmNipcQueNaoExisteDevolveVazio() {
        when(entidadeRepository.findAllByNipc("999999999")).thenReturn(List.of());

        assertThat(correspondencia.corresponder(null, "Município de Cascais")).isEmpty();
        assertThat(correspondencia.corresponder("  ", "Município de Cascais")).isEmpty();
        assertThat(correspondencia.corresponder("999999999", "Município de Cascais")).isEmpty();
        verify(entidadeRepository, never()).findAllByDescricaoIncm(any());
    }
}
