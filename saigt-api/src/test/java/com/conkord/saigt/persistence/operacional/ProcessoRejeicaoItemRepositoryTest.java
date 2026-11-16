package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProcessoRejeicaoItemRepositoryTest {

    @Autowired
    private ProcessoRejeicaoItemRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private Utilizador autor;
    private ListaValores categoria;
    private ListaValores motivo;

    @BeforeEach
    void criarProcesso() {
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
        categoria = entityManager.persist(new ListaValores("CATEGORIA_REJEICAO_DGT", "FORMA", "Forma", 1));
        motivo = entityManager.persist(new ListaValores("MOTIVO_REJEICAO_DGT", "ILEGIVEL", "Ilegível", 1));
        entityManager.flush();
    }

    @Test
    void recusaComUmFicheiroEUmaInformacaoBaseDevolveOsItensPelaOrdemDeInsercao() {
        ProcessoRejeicao rejeicao = rejeicao(1);
        ProcessoRejeicaoItem ficheiro = repository.save(itemDeFicheiro(rejeicao, "Página 3 ilegível."));
        ProcessoRejeicaoItem informacaoBase = repository.save(new ProcessoRejeicaoItem(rejeicao, null,
                entityManager.persist(new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO)),
                categoria, motivo, "Deliberação sem data."));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByRejeicao_IdOrderByIdAsc(rejeicao.getId()))
                .extracting(ProcessoRejeicaoItem::getId)
                .containsExactly(ficheiro.getId(), informacaoBase.getId());

        ProcessoRejeicao lida = entityManager.find(ProcessoRejeicao.class, rejeicao.getId());
        assertThat(lida.getItens())
                .extracting(ProcessoRejeicaoItem::getNotas)
                .containsExactly("Página 3 ilegível.", "Deliberação sem data.");
        assertThat(lida.getItens().get(0).getProcessoDocumento()).isNotNull();
        assertThat(lida.getItens().get(0).getProcessoInformacaoBase()).isNull();
        assertThat(lida.getItens().get(1).getProcessoDocumento()).isNull();
        assertThat(lida.getItens().get(1).getProcessoInformacaoBase().getElemento())
                .isEqualTo(ElementoInformacaoBase.DELIBERACAO);
    }

    @Test
    void itemSemCategoriaNemMotivoGravaSeEnquantoAsListasEstaoVazias() {
        ProcessoRejeicao rejeicao = rejeicao(1);
        ProcessoRejeicaoItem item = itemDeFicheiro(rejeicao, "Ficheiro corrompido.");
        ProcessoRejeicaoItem semListas = repository.saveAndFlush(new ProcessoRejeicaoItem(rejeicao,
                item.getProcessoDocumento(), null, null, null, "Ficheiro corrompido."));
        entityManager.clear();

        ProcessoRejeicaoItem lido = repository.findByRejeicao_IdOrderByIdAsc(rejeicao.getId()).get(0);
        assertThat(lido.getId()).isEqualTo(semListas.getId());
        assertThat(lido.getCategoria()).isNull();
        assertThat(lido.getMotivo()).isNull();
    }

    @Test
    void itensDeVariasRecusasLeemSeNumaSoConsulta() {
        ProcessoRejeicao primeira = rejeicao(1);
        ProcessoRejeicao segunda = rejeicao(2);
        ProcessoRejeicao terceira = rejeicao(3);
        ProcessoRejeicao outra = rejeicao(4);
        repository.save(itemDeFicheiro(primeira, "a"));
        repository.save(itemDeFicheiro(segunda, "b"));
        repository.save(itemDeFicheiro(terceira, "c"));
        repository.save(itemDeFicheiro(primeira, "d"));
        repository.save(itemDeFicheiro(outra, "fora"));
        entityManager.flush();
        entityManager.clear();
        Statistics estatisticas = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        List<ProcessoRejeicaoItem> itens = repository.findByRejeicao_IdInOrderByIdAsc(
                List.of(primeira.getId(), segunda.getId(), terceira.getId()));

        assertThat(itens).extracting(ProcessoRejeicaoItem::getNotas).containsExactly("a", "b", "c", "d");
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void entidadeNaoTemSettersNemColunasAtualizaveis() {
        assertThat(Arrays.stream(ProcessoRejeicaoItem.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(nome -> nome.startsWith("set"));
        assertThat(Arrays.stream(ProcessoRejeicaoItem.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(Column.class)))
                .allSatisfy(campo -> assertThat(campo.getAnnotation(Column.class).updatable())
                        .as(campo.getName()).isFalse());
        assertThat(Arrays.stream(ProcessoRejeicaoItem.class.getDeclaredFields())
                .filter(campo -> campo.isAnnotationPresent(JoinColumn.class)))
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("rejeicao", "processoDocumento", "processoInformacaoBase", "categoria",
                        "motivo")
                .allSatisfy(nome -> assertThat(campoJoin(nome).updatable()).as(nome).isFalse());
    }

    private ProcessoRejeicao rejeicao(int sequencia) {
        return entityManager.persist(ProcessoRejeicao.builder()
                .processo(processo)
                .sequencia(sequencia)
                .origem(OrigemRejeicao.DGT)
                .criadoPor(autor)
                .build());
    }

    private ProcessoRejeicaoItem itemDeFicheiro(ProcessoRejeicao rejeicao, String notas) {
        ProcessoDocumento documento = entityManager.persist(new ProcessoDocumento(processo, null,
                CategoriaDocumento.DEPOSITO, "pdf", "texto.pdf", "privado/x", "a".repeat(64), "v1", 10, autor));
        return new ProcessoRejeicaoItem(rejeicao, documento, null, categoria, motivo, notas);
    }

    private static JoinColumn campoJoin(String nome) {
        try {
            return ProcessoRejeicaoItem.class.getDeclaredField(nome).getAnnotation(JoinColumn.class);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
