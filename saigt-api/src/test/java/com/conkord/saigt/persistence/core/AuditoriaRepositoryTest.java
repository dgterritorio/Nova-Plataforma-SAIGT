package com.conkord.saigt.persistence.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditoriaRepositoryTest {

    @Autowired
    private AuditoriaRepository repository;

    @Test
    void deveGuardarELerAuditoriaPorTipoOcorrenciaEIdOcorrencia() {
        UUID utilizadorId = UUID.randomUUID();
        Long entidadeId = 7L;
        repository.save(Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia("42")
                .acao("CRIACAO")
                .estadoDepois("EM_SUBMISSAO")
                .utilizadorId(utilizadorId)
                .entidadeId(entidadeId)
                .ipOrigem("127.0.0.1")
                .dadosAlteracao("{\"campo\":\"valor\"}")
                .build());

        List<Auditoria> registos = repository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", "42");

        assertThat(registos).hasSize(1);
        Auditoria registo = registos.get(0);
        assertThat(registo.getAcao()).isEqualTo("CRIACAO");
        assertThat(registo.getEstadoDepois()).isEqualTo("EM_SUBMISSAO");
        assertThat(registo.getUtilizadorId()).isEqualTo(utilizadorId);
        assertThat(registo.getEntidadeId()).isEqualTo(entidadeId);
        assertThat(registo.getIpOrigem()).isEqualTo("127.0.0.1");
        assertThat(registo.getCriadoEm()).isNotNull();
    }

    @Test
    void chamadasRepetidasComMesmaOcorrenciaNuncaSubstituemRegistoAnterior() {
        UUID utilizadorId = UUID.randomUUID();
        repository.save(Auditoria.builder()
                .tipoOcorrencia("UTILIZADOR")
                .idOcorrencia("u-1")
                .acao("LOGIN")
                .utilizadorId(utilizadorId)
                .build());
        repository.save(Auditoria.builder()
                .tipoOcorrencia("UTILIZADOR")
                .idOcorrencia("u-1")
                .acao("LOGIN")
                .utilizadorId(utilizadorId)
                .build());

        List<Auditoria> registos = repository.findByTipoOcorrenciaAndIdOcorrencia("UTILIZADOR", "u-1");

        assertThat(registos).hasSize(2);
        assertThat(registos.get(0).getId()).isNotEqualTo(registos.get(1).getId());
    }

    @Test
    void deveDevolverAEntradaMaisRecenteDasAcoesIndicadas() {
        guardar("SUBMISSAO_INCM_REJEITADA", "{\"mensagens\":[\"antiga\"]}");
        guardar("CAPTURAR", null);
        guardar("REJEICAO_INCM", "{\"mensagens\":[\"recente\"]}");
        guardar("CAPTURAR", null);

        Auditoria registo = repository
                .findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
                        "PROCESSO", "77", List.of("SUBMISSAO_INCM_REJEITADA", "REJEICAO_INCM"))
                .orElseThrow();

        assertThat(registo.getAcao()).isEqualTo("REJEICAO_INCM");
        assertThat(registo.getDadosAlteracao()).contains("recente");
    }

    @Test
    void deveDevolverVazioSemEntradasDasAcoesIndicadas() {
        guardar("CAPTURAR", null);

        assertThat(repository.findFirstByTipoOcorrenciaAndIdOcorrenciaAndAcaoInOrderByCriadoEmDescIdDesc(
                "PROCESSO", "77", List.of("REJEICAO_INCM"))).isEmpty();
    }

    private void guardar(String acao, String dadosAlteracao) {
        repository.save(Auditoria.builder()
                .tipoOcorrencia("PROCESSO")
                .idOcorrencia("77")
                .acao(acao)
                .dadosAlteracao(dadosAlteracao)
                .build());
    }

    @Test
    void deveFiltrarPorIntervaloDeCriadoEm() {
        repository.save(Auditoria.builder()
                .tipoOcorrencia("INSTRUMENTO")
                .idOcorrencia("1")
                .acao("CRIACAO")
                .utilizadorId(UUID.randomUUID())
                .build());
        Instant antes = Instant.now().minusSeconds(60);
        Instant depois = Instant.now().plusSeconds(60);

        List<Auditoria> registos = repository.findByCriadoEmBetween(antes, depois);

        assertThat(registos).isNotEmpty();
    }

    @Test
    /** Ações do sistema (#146): a BD aceita; é o AuditoriaService que recusa ações de utilizador sem utilizador. */
    void deveAceitarUtilizadorIdNuloParaAcoesDoSistema() {
        Auditoria semUtilizador = Auditoria.builder()
                .tipoOcorrencia("OBJETO_STORAGE")
                .idOcorrencia("abc-anexo.pdf")
                .acao("REMOVER_OBJETO_ORFAO")
                .build();

        Auditoria gravada = repository.save(semUtilizador);

        assertThat(gravada.getId()).isNotNull();
        assertThat(gravada.getUtilizadorId()).isNull();
    }

    @Test
    void deveContarSoAsTransicoesDoTipoIdsEEstadoPedidos() {
        String primeiro = UUID.randomUUID().toString();
        String segundo = UUID.randomUUID().toString();
        String foraDoPedido = UUID.randomUUID().toString();
        transicao("PROCESSO", primeiro, "REJEITADO_DGT");
        transicao("PROCESSO", primeiro, "REJEITADO_DGT");
        transicao("PROCESSO", primeiro, "ANULADO_INCM");
        transicao("PROCESSO", segundo, "REJEITADO_DGT");
        transicao("PROCESSO", segundo, "EM_SUBMISSAO");
        transicao("PROCESSO", foraDoPedido, "REJEITADO_DGT");
        transicao("DINAMICA", primeiro, "REJEITADO_DGT");

        List<AuditoriaRepository.TransicoesPorOcorrenciaProjecao> totais = repository.contarTransicoesPorOcorrencia(
                "PROCESSO", List.of(primeiro, segundo), "REJEITADO_DGT");

        assertThat(totais)
                .extracting(AuditoriaRepository.TransicoesPorOcorrenciaProjecao::getIdOcorrencia,
                        AuditoriaRepository.TransicoesPorOcorrenciaProjecao::getTotal)
                .containsExactlyInAnyOrder(tuple(primeiro, 2L), tuple(segundo, 1L));
    }

    private void transicao(String tipoOcorrencia, String idOcorrencia, String estadoDepois) {
        repository.save(Auditoria.builder()
                .tipoOcorrencia(tipoOcorrencia)
                .idOcorrencia(idOcorrencia)
                .acao("TRANSICAO")
                .estadoDepois(estadoDepois)
                .build());
    }

    @Test
    void interfaceNaoExpoeMetodosDeUpdateOuDelete() {
        List<String> nomesMetodos = Arrays.stream(AuditoriaRepository.class.getMethods())
                .map(Method::getName)
                .toList();

        assertThat(nomesMetodos)
                .noneMatch(nome -> nome.toLowerCase().contains("delete"))
                .noneMatch(nome -> nome.toLowerCase().contains("update"));
    }
}
