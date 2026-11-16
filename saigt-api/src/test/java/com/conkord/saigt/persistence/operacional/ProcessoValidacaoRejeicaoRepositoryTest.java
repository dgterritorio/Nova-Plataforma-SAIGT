package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.dto.instrumento.RejeicaoEmValidacaoResponse;
import com.conkord.saigt.dto.instrumento.TipoItemValidacao;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProcessoValidacaoRejeicaoRepositoryTest {

    @Autowired
    private ProcessoValidacaoRejeicaoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private Processo outroProcesso;
    private Utilizador autor;
    private ListaValores categoria;
    private ListaValores motivo;

    @BeforeEach
    void criarProcessos() {
        TipoInstrumento pdm = entityManager.persist(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = entityManager.persist(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = entityManager.persist(new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = entityManager.persist(new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig config = entityManager.persist(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        Entidade entidade = entityManager.persist(EntidadeTeste.nova("501234500", "Câmara Municipal"));
        autor = entityManager.persist(new Utilizador("Ana Ferreira", "ana.ferreira@cm.pt", "INCM"));
        Instrumento instrumento = entityManager.persist(
                new Instrumento(pdm, "1", "2026/PDM/1", autor, autor));
        Dinamica dinamica = entityManager.persist(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        processo = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        outroProcesso = entityManager.persist(new Processo(dinamica, config, autor, entidade));
        categoria = entityManager.persist(new ListaValores("CATEGORIA_REJEICAO_DGT", "FORMA", "Forma", 1));
        motivo = entityManager.persist(new ListaValores("MOTIVO_REJEICAO_DGT", "ILEGIVEL", "Ilegível", 1));
        entityManager.flush();
    }

    private ProcessoDocumento documento(Processo dono) {
        return entityManager.persist(new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "pdf",
                "texto.pdf", "privado/x", "a".repeat(64), "v1", 10, autor));
    }

    private ProcessoValidacaoRejeicao rejeitado(ProcessoValidacaoRejeicao rejeicao, String notas) {
        rejeicao.registar(categoria, motivo, notas, autor, Instant.now());
        return repository.save(rejeicao);
    }

    @Test
    void rascunhoDoProcessoLeSeNumaSoConsultaComOsIdsDosAlvosEDasListas() {
        ProcessoDocumento documento = documento(processo);
        ProcessoInformacaoBase deliberacao = entityManager.persist(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        rejeitado(ProcessoValidacaoRejeicao.doDocumento(documento), "Ilegível.");
        rejeitado(ProcessoValidacaoRejeicao.daInformacaoBase(deliberacao), "Sem data.");
        rejeitado(ProcessoValidacaoRejeicao.doDocumento(documento(outroProcesso)), "De outro processo.");
        entityManager.flush();
        entityManager.clear();
        Statistics estatisticas = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        List<RejeicaoEmValidacaoResponse> lidas = repository.findByProcesso_Id(processo.getId()).stream()
                .map(RejeicaoEmValidacaoResponse::de).toList();

        assertThat(lidas).containsExactlyInAnyOrder(
                new RejeicaoEmValidacaoResponse(TipoItemValidacao.DOCUMENTO, documento.getId(), categoria.getId(),
                        motivo.getId(), "Ilegível."),
                new RejeicaoEmValidacaoResponse(TipoItemValidacao.INFORMACAO_BASE, deliberacao.getId(),
                        categoria.getId(), motivo.getId(), "Sem data."));
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void encontraALinhaPeloAlvoEApagaSoAsDoProcesso() {
        ProcessoDocumento documento = documento(processo);
        ProcessoInformacaoBase deliberacao = entityManager.persist(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        ProcessoValidacaoRejeicao doDocumento = rejeitado(ProcessoValidacaoRejeicao.doDocumento(documento), "a");
        rejeitado(ProcessoValidacaoRejeicao.daInformacaoBase(deliberacao), "b");
        rejeitado(ProcessoValidacaoRejeicao.doDocumento(documento(outroProcesso)), "c");
        entityManager.flush();

        assertThat(repository.findByProcessoDocumento_Id(documento.getId())).map(ProcessoValidacaoRejeicao::getId)
                .contains(doDocumento.getId());
        assertThat(repository.findByProcessoInformacaoBase_Id(deliberacao.getId())).isPresent();

        repository.deleteByProcesso_Id(processo.getId());
        entityManager.flush();

        assertThat(repository.findByProcesso_Id(processo.getId())).isEmpty();
        assertThat(repository.findByProcesso_Id(outroProcesso.getId())).hasSize(1);
    }
}
