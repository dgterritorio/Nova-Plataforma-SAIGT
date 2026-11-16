package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencao;
import com.conkord.saigt.persistence.operacional.ArmazenamentoIntencaoRepository;
import com.conkord.saigt.persistence.operacional.OperacaoArmazenamento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntencaoArmazenamentoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntencaoArmazenamentoServiceIntegrationTest {

    private static final String CHAVE = "processos/00123/DEPOSITO/0b9e6d2c-5f1a-4c3e-9a7b-2d8f1e4c6a90-planta.pdf";

    @Autowired
    private IntencaoArmazenamentoService servico;

    @Autowired
    private ArmazenamentoIntencaoRepository repositorio;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    void deveManterAIntencaoDeGravacaoQuandoATransacaoDoChamadorReverte() {
        TransactionTemplate chamador = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> chamador.executeWithoutResult(estado -> {
            servico.registar(CHAVE, OperacaoArmazenamento.GRAVACAO);
            throw new IllegalStateException("falha depois de gravar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.findByChaveObjeto(CHAVE)).singleElement()
                .extracting(ArmazenamentoIntencao::getOperacao).isEqualTo(OperacaoArmazenamento.GRAVACAO);
    }

    @Test
    void deveManterAIntencaoDeCopiaQuandoATransacaoDoChamadorReverte() {
        TransactionTemplate chamador = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> chamador.executeWithoutResult(estado -> {
            servico.registar(CHAVE, OperacaoArmazenamento.COPIA);
            throw new IllegalStateException("falha depois de copiar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.findByChaveObjeto(CHAVE)).singleElement()
                .extracting(ArmazenamentoIntencao::getOperacao).isEqualTo(OperacaoArmazenamento.COPIA);
    }

    @Test
    void naoDeveManterAIntencaoDeApagamentoQuandoATransacaoDoChamadorReverte() {
        TransactionTemplate chamador = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> chamador.executeWithoutResult(estado -> {
            servico.registar(CHAVE, OperacaoArmazenamento.APAGAMENTO);
            throw new IllegalStateException("falha ao remover a linha");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.findByChaveObjeto(CHAVE)).isEmpty();
    }

    @Test
    void deveManterAIntencaoDeApagamentoQuandoATransacaoDoChamadorFazCommit() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(estado -> servico.registar(CHAVE, OperacaoArmazenamento.APAGAMENTO));

        assertThat(repositorio.findByChaveObjeto(CHAVE)).singleElement()
                .satisfies(intencao -> {
                    assertThat(intencao.getOperacao()).isEqualTo(OperacaoArmazenamento.APAGAMENTO);
                    assertThat(intencao.getRegistadaEm()).isNotNull();
                });
    }

    @Test
    void deveRecusarApagamentoForaDeUmaTransacao() {
        assertThatThrownBy(() -> servico.registar(CHAVE, OperacaoArmazenamento.APAGAMENTO))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repositorio.findByChaveObjeto(CHAVE)).isEmpty();
    }
}
