package com.conkord.saigt.service.snit;

import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Ciclo completo do job contra a BD, com o {@link ClienteSnit} simulado. */
@SpringBootTest
@ActiveProfiles("test")
class SincronizacaoReferenciaSnitJobIntegrationTest {

    @MockBean
    private ClienteSnit clienteSnit;

    @Autowired
    private SincronizacaoReferenciaSnitJob job;

    @Autowired
    private SnitReferenciaRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    /** A réplica volta a ficar vazia: com concelhos, a criação de Instrumentos dos outros testes passa a validar por ela. */
    @AfterEach
    void esvaziarReplica() {
        repository.deleteAll();
    }

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    @Test
    void deveManterAReplicaDaListaQueFalhaESincronizarAsOutras() throws Exception {
        when(clienteSnit.obterTabelaReferencia(any())).thenReturn(List.of());
        when(clienteSnit.obterTabelaReferencia(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(List.of(
                objectMapper.readTree("{\"dtcc\": \"1111\", \"name\": \"SINTRA\", \"nut2Code\": \"LISBOA E VALE DO TEJO\"}")));
        job.executar();
        SnitReferencia sintra = repository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, "1111").orElseThrow();

        when(clienteSnit.obterTabelaReferencia(ListaReferenciaSnit.MUNICIPALITIES))
                .thenThrow(new SnitIndisponivelException("Serviço do SNIT temporariamente indisponível."));
        when(clienteSnit.obterTabelaReferencia(ListaReferenciaSnit.NUT2)).thenReturn(List.of(
                objectMapper.readTree("{\"code\": \"NORTE\", \"label\": \"Norte\"}")));
        job.executar();

        SnitReferencia depois = repository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, "1111").orElseThrow();
        assertThat(depois.isAtivo()).isTrue();
        assertThat(depois.getSincronizadoEm()).isEqualTo(sintra.getSincronizadoEm());
        assertThat(repository.findByListaAndCodigo(ListaReferenciaSnit.NUT2, "NORTE")).get()
                .extracting(SnitReferencia::isAtivo).isEqualTo(true);
    }
}
