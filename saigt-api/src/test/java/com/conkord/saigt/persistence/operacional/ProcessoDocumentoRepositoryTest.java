package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoDocumentoRepositoryTest {


    @Autowired
    private ProcessoDocumentoRepository repository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private Processo processo;
    private DocumentoConfig documentoConfig;

    private Processo criarProcesso() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade cmLisboa = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador ferreira = new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt");
        ferreira.setPerfilInterno(PerfilInterno.MASTER);
        Utilizador criadoPor = utilizadorRepository.saveAndFlush(ferreira);
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", criadoPor, criadoPor));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", criadoPor));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        documentoConfig = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "TEXTO_ATO", "Texto do ato", "Texto do ato",
                        CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                        "[\"docx\",\"pdf\"]", 1));

        return processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, criadoPor, cmLisboa));
    }

    @Test
    void deveGuardarELerProcessoDocumento() {
        processo = criarProcesso();

        ProcessoDocumento novo = new ProcessoDocumento(processo, documentoConfig, CategoriaDocumento.PUBLICACAO, "pdf",
                "decisao-elaboracao.pdf", "privado/instr-1/proc-1/publicacao/decisao-elaboracao.pdf",
                "a".repeat(64), "v1", 204_800L, processo.getCriadoPor());
        ProcessoDocumento guardado = repository.saveAndFlush(novo);

        Optional<ProcessoDocumento> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getProcesso().getId()).isEqualTo(processo.getId());
        assertThat(lido.get().getDocumentoConfig().getId()).isEqualTo(documentoConfig.getId());
        assertThat(lido.get().getNomeFicheiro()).isEqualTo("decisao-elaboracao.pdf");
        assertThat(lido.get().getCaminhoFicheiro()).isEqualTo("privado/instr-1/proc-1/publicacao/decisao-elaboracao.pdf");
        assertThat(lido.get().getHashSha256()).isEqualTo("a".repeat(64));
        assertThat(lido.get().getTamanhoBytes()).isEqualTo(204_800L);
        assertThat(lido.get().getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
        assertThat(lido.get().getExtensao()).isEqualTo("pdf");
        assertThat(lido.get().getOrigem()).isEqualTo(OrigemDocumento.CARREGADO);
        assertThat(lido.get().isBloqueadoEdicao()).isFalse();
        assertThat(lido.get().getVersaoObjeto()).isEqualTo("v1");
        assertThat(lido.get().getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
    }

    private void documentoNoEstado(Processo dono, EstadoValidacao estado) {
        ProcessoDocumento documento = new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "pdf", "f.pdf",
                "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, dono.getCriadoPor());
        documento.setEstadoValidacao(estado);
        repository.saveAndFlush(documento);
    }

    @Test
    void deveDizerSeOProcessoTemDocumentosForaDosEstadosIndicados() {
        processo = criarProcesso();
        UUID semDocumentos = UUID.randomUUID();
        List<EstadoValidacao> resolvidos = List.of(EstadoValidacao.VALIDADO, EstadoValidacao.SUBSTITUIDO);
        documentoNoEstado(processo, EstadoValidacao.VALIDADO);
        documentoNoEstado(processo, EstadoValidacao.SUBSTITUIDO);

        assertThat(repository.existsByProcessoIdAndEstadoValidacaoNotIn(processo.getId(), resolvidos)).isFalse();
        assertThat(repository.existsByProcessoIdAndEstadoValidacaoNotIn(semDocumentos, resolvidos)).isFalse();

        documentoNoEstado(processo, EstadoValidacao.REJEITADO);

        assertThat(repository.existsByProcessoIdAndEstadoValidacaoNotIn(processo.getId(), resolvidos)).isTrue();
        assertThat(repository.existsByProcessoIdAndEstadoValidacaoNotIn(semDocumentos, resolvidos)).isFalse();
    }

    @Test
    void deveContarUmDocumentoPorValidarComoForaDosEstadosResolvidos() {
        processo = criarProcesso();
        documentoNoEstado(processo, EstadoValidacao.POR_VALIDAR);

        assertThat(repository.existsByProcessoIdAndEstadoValidacaoNotIn(processo.getId(),
                List.of(EstadoValidacao.VALIDADO, EstadoValidacao.SUBSTITUIDO))).isTrue();
    }

    @Test
    void deveGravarERelerAsDuasTagsDoDocumentoNaMesmaConsulta() {
        processo = criarProcesso();
        DocumentoConfig parecer = documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                processo.getInstrumentoProcedimentoConfig(), "PARECER_DEP", "Parecer", "Pareceres",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 2));
        DocumentoConfig relatorio = documentoConfigRepository.saveAndFlush(new DocumentoConfig(
                processo.getInstrumentoProcedimentoConfig(), "RELATORIO_DEP", "Relatório", "Relatórios",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", 3));
        ProcessoDocumento novo = new ProcessoDocumento(processo, documentoConfig, CategoriaDocumento.PUBLICACAO, "pdf",
                "texto.pdf", "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        novo.associarTags(List.of(relatorio, parecer));
        UUID id = repository.saveAndFlush(novo).getId();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForList("SELECT documento_config_id FROM processo_documento_tag "
                + "WHERE processo_documento_id = ?", Long.class, id))
                .containsExactlyInAnyOrder(parecer.getId(), relatorio.getId());
        ProcessoDocumento lido = repository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).get(0);
        assertThat(Hibernate.isInitialized(lido.getTags())).isTrue();
        assertThat(lido.idsDasTags()).containsExactly(
                Math.min(parecer.getId(), relatorio.getId()), Math.max(parecer.getId(), relatorio.getId()));
    }

    @Test
    void devePesquisarPaginaComTotalCertoEUtilizadorCarregado() {
        processo = criarProcesso();
        for (int i = 0; i < 25; i++) {
            repository.save(new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf", "f" + i + ".pdf",
                    "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L, processo.getCriadoPor()));
        }
        repository.flush();
        entityManager.clear();

        Page<ProcessoDocumento> pagina = repository.pesquisar(processo.getId(), null, null, null, PageRequest.of(1, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(25);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
        assertThat(pagina.getContent()).hasSize(5)
                .allSatisfy(d -> assertThat(Hibernate.isInitialized(d.getCriadoPor())).isTrue());
    }

    @Test
    void deveDevolverOsFicheirosDeVariosProcessosPorCriadoEmComOAutorCarregado() {
        processo = criarProcesso();
        processo.setEstado(EstadoProcesso.ANULADO);
        Processo segundo = new Processo(processo.getDinamica(), processo.getInstrumentoProcedimentoConfig(),
                processo.getCriadoPor(), processo.getEntidade());
        segundo.setOcorrencia(2);
        segundo = processoRepository.saveAndFlush(segundo);
        Processo foraDoLote = processoRepository.saveAndFlush(new Processo(processo.getDinamica(),
                processo.getInstrumentoProcedimentoConfig(), processo.getCriadoPor(), processo.getEntidade()));

        ProcessoDocumento primeiro = guardarComData(processo, "primeiro.pdf", "2026-10-01T10:00:00Z");
        ProcessoDocumento terceiro = guardarComData(segundo, "terceiro.pdf", "2026-10-03T10:00:00Z");
        ProcessoDocumento segundoDoc = guardarComData(processo, "segundo.pdf", "2026-10-02T10:00:00Z");
        guardarComData(foraDoLote, "fora.pdf", "2026-10-04T10:00:00Z");
        entityManager.clear();

        List<ProcessoDocumento> resultado = repository.findByProcesso_IdInOrderByCriadoEmAsc(
                List.of(processo.getId(), segundo.getId()));

        assertThat(resultado).extracting(ProcessoDocumento::getId)
                .containsExactly(primeiro.getId(), segundoDoc.getId(), terceiro.getId());
        assertThat(resultado).allSatisfy(d -> assertThat(Hibernate.isInitialized(d.getCriadoPor())).isTrue());
    }

    private ProcessoDocumento guardarComData(Processo dono, String nome, String criadoEm) {
        ProcessoDocumento documento = repository.saveAndFlush(new ProcessoDocumento(dono, null,
                CategoriaDocumento.DEPOSITO, "pdf", nome, "privado/p/" + UUID.randomUUID(), "a".repeat(64), "v1", 10L,
                dono.getCriadoPor()));
        jdbcTemplate.update("UPDATE processo_documento SET criado_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse(criadoEm)), documento.getId());
        return documento;
    }

    @Test
    void devePesquisarComFiltrosCombinados() {
        processo = criarProcesso();
        ProcessoDocumento validado = new ProcessoDocumento(processo, documentoConfig, CategoriaDocumento.PUBLICACAO, "pdf",
                "a.pdf", "privado/p/a", "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        validado.setEstadoValidacao(EstadoValidacao.VALIDADO);
        ProcessoDocumento porValidar = new ProcessoDocumento(processo, documentoConfig, CategoriaDocumento.PUBLICACAO, "pdf",
                "b.pdf", "privado/p/b", "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        ProcessoDocumento extra = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "c.pdf", "privado/p/c", "a".repeat(64), "v1", 10L, processo.getCriadoPor());
        repository.saveAllAndFlush(List.of(validado, porValidar, extra));

        Page<ProcessoDocumento> resultado = repository.pesquisar(processo.getId(), CategoriaDocumento.PUBLICACAO,
                documentoConfig.getId(), EstadoValidacao.VALIDADO, PageRequest.of(0, 20));

        assertThat(resultado.getContent()).extracting(ProcessoDocumento::getNomeFicheiro).containsExactly("a.pdf");
        assertThat(repository.pesquisar(processo.getId(), CategoriaDocumento.DEPOSITO, null, null, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void deveSomarEContarOsDocumentosDoItemEDoProcessoSemOsSubstituidosNemOExcluido() {
        processo = criarProcesso();
        String pasta = "processos/" + processo.getNumeroProcesso() + "/";
        ProcessoDocumento noItem = documento(documentoConfig, pasta + "PUBLICACAO/a.pdf", 100L);
        ProcessoDocumento outroNoItem = documento(documentoConfig, pasta + "PUBLICACAO/b.pdf", 200L);
        ProcessoDocumento substituido = documento(documentoConfig, pasta + "SUBSTITUIDOS/c.pdf", 400L);
        ProcessoDocumento nosOutros = documento(null, pasta + "DEPOSITO/d.pdf", 800L);
        repository.saveAllAndFlush(List.of(noItem, outroNoItem, substituido, nosOutros));
        Processo outroProcesso = processoRepository.saveAndFlush(new Processo(processo.getDinamica(),
                processo.getInstrumentoProcedimentoConfig(), processo.getCriadoPor(), processo.getEntidade()));
        repository.saveAndFlush(new ProcessoDocumento(outroProcesso, documentoConfig, CategoriaDocumento.PUBLICACAO,
                "pdf", "e.pdf", "processos/outro/PUBLICACAO/e.pdf", "a".repeat(64), "v1", 1600L,
                processo.getCriadoPor()));

        assertThat(repository.somaTamanhoDoProcesso(processo.getId(), null)).isEqualTo(1100L);
        assertThat(repository.somaTamanhoDoItem(processo.getId(), documentoConfig.getId(), null)).isEqualTo(300L);
        assertThat(repository.contarDoItem(processo.getId(), documentoConfig.getId(), null)).isEqualTo(2L);

        assertThat(repository.somaTamanhoDoProcesso(processo.getId(), noItem.getId())).isEqualTo(1000L);
        assertThat(repository.somaTamanhoDoItem(processo.getId(), documentoConfig.getId(), noItem.getId()))
                .isEqualTo(200L);
        assertThat(repository.contarDoItem(processo.getId(), documentoConfig.getId(), noItem.getId())).isEqualTo(1L);
    }

    @Test
    void deveDevolverZeroQuandoOProcessoNaoTemDocumentos() {
        processo = criarProcesso();

        assertThat(repository.somaTamanhoDoProcesso(processo.getId(), null)).isZero();
        assertThat(repository.somaTamanhoDoItem(processo.getId(), documentoConfig.getId(), null)).isZero();
        assertThat(repository.contarDoItem(processo.getId(), documentoConfig.getId(), null)).isZero();
    }

    @Test
    void deveDevolverSoOsDocumentosAtuaisDoProcessoNosItensComOsCodigosDados() {
        processo = criarProcesso();
        String pasta = "processos/" + processo.getNumeroProcesso() + "/";
        ProcessoDocumento atual = documento(documentoConfig, pasta + "PUBLICACAO/a.pdf", 100L);
        ProcessoDocumento substituido = documento(documentoConfig, pasta + "SUBSTITUIDOS/b.pdf", 100L);
        ProcessoDocumento semItem = documento(null, pasta + "DEPOSITO/c.pdf", 100L);
        repository.saveAllAndFlush(List.of(atual, substituido, semItem));
        Processo outroProcesso = processoRepository.saveAndFlush(new Processo(processo.getDinamica(),
                processo.getInstrumentoProcedimentoConfig(), processo.getCriadoPor(), processo.getEntidade()));
        repository.saveAndFlush(new ProcessoDocumento(outroProcesso, documentoConfig, CategoriaDocumento.PUBLICACAO,
                "pdf", "e.pdf", "processos/outro/PUBLICACAO/e.pdf", "a".repeat(64), "v1", 1L, processo.getCriadoPor()));

        assertThat(repository.findAtuaisDoProcessoPorCodigos(processo.getId(), List.of("TEXTO_ATO", "OUTRO")))
                .extracting(ProcessoDocumento::getId).containsExactly(atual.getId());
        assertThat(repository.findAtuaisDoProcessoPorCodigos(processo.getId(), List.of("OUTRO"))).isEmpty();
    }

    private ProcessoDocumento documento(DocumentoConfig item, String caminho, long tamanhoBytes) {
        CategoriaDocumento categoria = item == null ? CategoriaDocumento.DEPOSITO : CategoriaDocumento.PUBLICACAO;
        return new ProcessoDocumento(processo, item, categoria, "pdf", "f.pdf", caminho, "a".repeat(64), "v1",
                tamanhoBytes, processo.getCriadoPor());
    }

    private ProcessoDocumento documentoEm(Processo dono, String nome, EstadoValidacao estado, boolean bloqueado) {
        return documentoEm(dono, nome, estado, bloqueado, "privado/p/" + UUID.randomUUID());
    }

    private ProcessoDocumento documentoEm(Processo dono, String nome, EstadoValidacao estado, boolean bloqueado,
                                          String caminho) {
        ProcessoDocumento documento = new ProcessoDocumento(dono, null, CategoriaDocumento.DEPOSITO, "pdf", nome,
                caminho, "a".repeat(64), "v1", 10L, dono.getCriadoPor());
        documento.setEstadoValidacao(estado);
        documento.setBloqueadoEdicao(bloqueado);
        return repository.saveAndFlush(documento);
    }

    private boolean bloqueado(ProcessoDocumento documento) {
        return repository.findById(documento.getId()).orElseThrow().isBloqueadoEdicao();
    }

    @Test
    void deveBloquearSoOsValidadosEDesbloquearOsRestantesSemTocarNoutroProcesso() {
        processo = criarProcesso();
        Processo outro = processoRepository.saveAndFlush(new Processo(processo.getDinamica(),
                processo.getInstrumentoProcedimentoConfig(), processo.getCriadoPor(), processo.getEntidade()));
        ProcessoDocumento validado = documentoEm(processo, "validado.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitadoAntesBloqueado = documentoEm(processo, "rejeitado.pdf", EstadoValidacao.REJEITADO, true);
        ProcessoDocumento porValidar = documentoEm(processo, "por-validar.pdf", EstadoValidacao.POR_VALIDAR, false);
        ProcessoDocumento validadoDoOutro = documentoEm(outro, "outro-validado.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento rejeitadoDoOutro = documentoEm(outro, "outro-rejeitado.pdf", EstadoValidacao.REJEITADO, true);
        long versaoAntes = validado.getVersao();
        entityManager.clear();

        int tocados = repository.bloquearSoValidados(processo.getId(), EstadoValidacao.VALIDADO);
        entityManager.clear();

        assertThat(tocados).isEqualTo(3);
        assertThat(bloqueado(validado)).isTrue();
        assertThat(bloqueado(rejeitadoAntesBloqueado)).isFalse();
        assertThat(bloqueado(porValidar)).isFalse();
        assertThat(bloqueado(validadoDoOutro)).isFalse();
        assertThat(bloqueado(rejeitadoDoOutro)).isTrue();
        assertThat(repository.contarBloqueadosAtuais(processo.getId())).isEqualTo(1L);
        assertThat(repository.findById(validado.getId()).orElseThrow().getVersao()).isEqualTo(versaoAntes);
    }

    @Test
    void deveBloquearTodosOsFicheirosDoProcessoSemTocarNoutroProcesso() {
        processo = criarProcesso();
        Processo outro = processoRepository.saveAndFlush(new Processo(processo.getDinamica(),
                processo.getInstrumentoProcedimentoConfig(), processo.getCriadoPor(), processo.getEntidade()));
        List<ProcessoDocumento> doProcesso = List.of(
                documentoEm(processo, "a.pdf", EstadoValidacao.VALIDADO, false),
                documentoEm(processo, "b.pdf", EstadoValidacao.REJEITADO, false),
                documentoEm(processo, "c.pdf", EstadoValidacao.POR_VALIDAR, false));
        ProcessoDocumento doOutro = documentoEm(outro, "d.pdf", EstadoValidacao.VALIDADO, false);
        entityManager.clear();

        int tocados = repository.bloquearTodos(processo.getId());
        entityManager.clear();

        assertThat(tocados).isEqualTo(3);
        assertThat(doProcesso).allSatisfy(d -> assertThat(bloqueado(d)).isTrue());
        assertThat(bloqueado(doOutro)).isFalse();
        assertThat(repository.contarBloqueadosAtuais(processo.getId())).isEqualTo(3L);
        assertThat(repository.contarBloqueadosAtuais(outro.getId())).isZero();
    }

    @Test
    void naoDeveTocarNemContarOsSubstituidosNoBloqueio() {
        processo = criarProcesso();
        ProcessoDocumento atual = documentoEm(processo, "atual.pdf", EstadoValidacao.VALIDADO, false);
        ProcessoDocumento emEstadoSubstituido = documentoEm(processo, "estado.pdf", EstadoValidacao.SUBSTITUIDO, true);
        ProcessoDocumento naPastaBloqueado = documentoEm(processo, "pasta-a.pdf", EstadoValidacao.REJEITADO, true,
                "privado/p/SUBSTITUIDOS/" + UUID.randomUUID());
        ProcessoDocumento naPastaDesbloqueado = documentoEm(processo, "pasta-b.pdf", EstadoValidacao.VALIDADO, false,
                "privado/p/SUBSTITUIDOS/" + UUID.randomUUID());
        entityManager.clear();

        assertThat(repository.bloquearSoValidados(processo.getId(), EstadoValidacao.VALIDADO)).isEqualTo(1);
        entityManager.clear();
        assertThat(bloqueado(atual)).isTrue();
        assertThat(bloqueado(emEstadoSubstituido)).isTrue();
        assertThat(bloqueado(naPastaBloqueado)).isTrue();
        assertThat(bloqueado(naPastaDesbloqueado)).isFalse();
        assertThat(repository.contarBloqueadosAtuais(processo.getId())).isEqualTo(1L);

        assertThat(repository.bloquearTodos(processo.getId())).isEqualTo(1);
        entityManager.clear();
        assertThat(bloqueado(naPastaDesbloqueado)).isFalse();
        assertThat(repository.contarBloqueadosAtuais(processo.getId())).isEqualTo(1L);
    }

    @Test
    void deveRejeitarValorForaDoDominio() {
        processo = criarProcesso();
        UUID id = repository.saveAndFlush(new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "shp",
                "planta.shp", "privado/instr-1/proc-1/OUTROS/planta.shp", "b".repeat(64), "v1", 51_200L,
                processo.getCriadoPor())).getId();

        for (String coluna : new String[] {"categoria", "origem", "estado_validacao"}) {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE processo_documento SET " + coluna + " = 'INVALIDO' WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void deveRecusarDocumentoSemVersaoDoObjeto() {
        processo = criarProcesso();
        ProcessoDocumento semVersao = new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "a.pdf", "processos/00001/DEPOSITO/a.pdf", "a".repeat(64), null, 10L, processo.getCriadoPor());

        assertThatThrownBy(() -> repository.saveAndFlush(semVersao)).isInstanceOf(Exception.class);
    }

    @Test
    void deveFalharPorConflitoDeVersaoQuandoASegundaAtualizacaoPartiDaMesmaVersao() {
        processo = criarProcesso();
        UUID id = repository.saveAndFlush(new ProcessoDocumento(processo, null, CategoriaDocumento.DEPOSITO, "pdf",
                "a.pdf", "processos/00001/DEPOSITO/a.pdf", "a".repeat(64), "v1", 10L, processo.getCriadoPor())).getId();
        entityManager.clear();
        ProcessoDocumento primeira = repository.findById(id).orElseThrow();
        entityManager.detach(primeira);
        ProcessoDocumento segunda = repository.findById(id).orElseThrow();

        segunda.setNomeFicheiro("segunda.pdf");
        repository.saveAndFlush(segunda);
        primeira.setNomeFicheiro("primeira.pdf");

        assertThatThrownBy(() -> repository.saveAndFlush(primeira))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(segunda.getVersao()).isEqualTo(1L);
    }
}
