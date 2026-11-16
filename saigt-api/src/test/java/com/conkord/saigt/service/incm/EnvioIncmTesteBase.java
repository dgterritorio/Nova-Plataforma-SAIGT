package com.conkord.saigt.service.incm;

import com.conkord.saigt.integracao.incm.ClienteIncm;
import com.conkord.saigt.integracao.storage.StorageTestcontainersBase;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.FormatoDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoAtoRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IncmComunicacao;
import com.conkord.saigt.persistence.operacional.IncmComunicacaoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.CaminhoDocumento;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class EnvioIncmTesteBase extends StorageTestcontainersBase {

    protected static final byte[] CONTEUDO = "%PDF-1.4 ato a publicar".getBytes(StandardCharsets.UTF_8);
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(100);

    @Autowired
    protected EnvioIncmService service;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected ProcessoRepository processoRepository;

    @Autowired
    protected ProcessoDocumentoRepository processoDocumentoRepository;

    @Autowired
    protected IncmComunicacaoRepository incmComunicacaoRepository;

    @Autowired
    protected AuditoriaRepository auditoriaRepository;

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private DocumentoLinkPublicoRepository documentoLinkPublicoRepository;

    @Autowired
    private PlantaRepository plantaRepository;

    @Autowired
    private ArmazenamentoDocumentos armazenamento;

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;

    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;

    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;

    @Autowired
    private TipoAtoRepository tipoAtoRepository;

    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;

    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;

    @Autowired
    private EntidadeRepository entidadeRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private InstrumentoRepository instrumentoRepository;

    @Autowired
    private DinamicaRepository dinamicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected record Cenario(Processo processo, UUID utilizadorId, ProcessoDocumento publicacao,
                             ProcessoDocumento deposito) {
    }

    @BeforeEach
    @AfterEach
    void reporCircuito() {
        circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_SUBMISSAO).reset();
        circuitBreakerRegistry.circuitBreaker(ClienteIncm.CIRCUITO_AUTENTICACAO).reset();
    }

    protected Cenario criarCenario(String serieDr, String sumario) {
        return criarCenario(serieDr, sumario, null, null);
    }

    /** Com {@code codigoTipoProcedimento} nulo cria um tipo próprio do cenário. */
    protected Cenario criarCenario(String serieDr, String sumario, String codigoTipoProcedimento, String dadosFormulario) {
        String codigo = String.valueOf(SEQUENCIA.incrementAndGet());
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("ENV-" + codigo, "Plano Diretor Municipal", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELAB-ENV-" + codigo, "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = codigoTipoProcedimento == null
                ? tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento("PROC-ENV-" + codigo, "Procedimento " + codigo))
                : tipoProcedimento(codigoTipoProcedimento);
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, serieDr));
        DocumentoConfig regulamento = new DocumentoConfig(procedimentoConfig, "REGULAMENTO", "Regulamento",
                "Regulamento", CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                "[\"pdf\"]", 1);
        regulamento.setLinkPublicoPermitido(true);
        DocumentoConfig configPublicacao = documentoConfigRepository.saveAndFlush(regulamento);
        DocumentoConfig configDeposito = documentoConfigRepository.saveAndFlush(
                new DocumentoConfig(procedimentoConfig, "PLANTA", "Planta", "Planta",
                        CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                        "[\"pdf\"]", 2));
        TipoAto tipoAto = new TipoAto("AE-" + codigo, "Aviso " + codigo, 1);
        tipoAto.setIncmActTypeIdSerie2(10L);
        tipoAtoRepository.saveAndFlush(tipoAto);

        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("508000" + codigo, "Câmara " + codigo));
        Utilizador autor = utilizadorRepository.saveAndFlush(
                new Utilizador("Autor " + codigo, "autor" + codigo + "@cm-env.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(tipoInstrumento, "1", "2026/ENV/" + codigo, autor, autor));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        Processo processo = new Processo(dinamica, procedimentoConfig, autor, entidade);
        processo.setEstado(EstadoProcesso.VALIDADO_DGT);
        processo.setTipoAto(tipoAto);
        processo.setIncmEnrollmentId("ENR-001");
        processo.setDrParteId(3L);
        processo.setNormaLegal("Artigo 76.º do RJIGT");
        processo.setTipoInformacao("TEXTO_INTEGRAL");
        processo.setSumario(sumario);
        processo.setDataAssinatura(LocalDate.of(2026, 10, 20));
        processo.setDadosFormulario(dadosFormulario);
        processo = processoRepository.saveAndFlush(processo);

        return new Cenario(processo, autor.getId(),
                documento(processo, instrumento, configPublicacao, "Regulamento.pdf", autor),
                documento(processo, instrumento, configDeposito, "Planta.pdf", autor));
    }

    protected Cenario criarCenario(String sumario) {
        return criarCenario("SEGUNDA_H", sumario);
    }

    private ProcessoDocumento documento(Processo processo, Instrumento instrumento, DocumentoConfig config, String nome,
                                        Utilizador autor) {
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, config,
                config.getCategoria(), "pdf", nome, "", "hash", "v1", CONTEUDO.length, autor));
        String caminho = CaminhoDocumento.privado(processo.getNumeroProcesso(), config.getCategoria(),
                OrigemDocumento.CARREGADO, documento.getId(), nome);
        documento.setCaminhoFicheiro(caminho);
        documento.setVersaoObjeto(versaoGravada(armazenamento.guardar(caminho, new ByteArrayInputStream(CONTEUDO),
                "application/pdf")));
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    private TipoProcedimento tipoProcedimento(String codigo) {
        return tipoProcedimentoRepository.findAll().stream().filter(tipo -> tipo.getCodigo().equals(codigo)).findFirst()
                .orElseGet(() -> tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigo, codigo)));
    }

    /** Lida da BD e não da entidade: prova que a data ficou gravada com o commit. */
    protected LocalDate dataCaducidadeGravada(Processo processo) {
        return jdbcTemplate.queryForObject("SELECT data_caducidade FROM dinamica WHERE id = ?", LocalDate.class,
                processo.getDinamica().getId());
    }

    protected void gravarDataCaducidade(Processo processo, LocalDate data) {
        jdbcTemplate.update("UPDATE dinamica SET data_caducidade = ? WHERE id = ?", data, processo.getDinamica().getId());
    }

    protected Processo enviar(Cenario cenario) {
        return transactionTemplate.execute(estado -> service.enviar(cenario.processo().getId(), cenario.utilizadorId()));
    }

    protected Processo recarregar(Processo processo) {
        return processoRepository.findById(processo.getId()).orElseThrow();
    }

    protected ProcessoDocumento recarregar(ProcessoDocumento documento) {
        return processoDocumentoRepository.findById(documento.getId()).orElseThrow();
    }

    protected List<IncmComunicacao> comunicacoes(Processo processo) {
        return incmComunicacaoRepository.findByProcessoIdOrderByEnviadoEmAsc(processo.getId());
    }

    protected List<Auditoria> auditoria(Processo processo) {
        return auditoriaRepository.findByTipoOcorrenciaAndIdOcorrencia("PROCESSO", processo.getId().toString());
    }

    protected List<DocumentoLinkPublico> linksAtivos(ProcessoDocumento documento) {
        return documentoLinkPublicoRepository.findByProcessoDocumento_IdAndRevogadoEmIsNull(documento.getId());
    }

    /** Outro documento de publicação, num item próprio; `planta` cria a peça gráfica ZIP e a sua linha de planta. */
    protected ProcessoDocumento adicionarDocumentoDePublicacao(Cenario cenario, String nome, boolean planta) {
        ProcessoDocumento documento = adicionarPublicacao(cenario, nome, planta ? "zip" : "pdf",
                planta ? TipoElemento.PECA_GRAFICA : TipoElemento.PECA_ESCRITA);
        if (planta) {
            plantaRepository.saveAndFlush(new Planta(recarregar(cenario.processo()), documento));
        }
        return documento;
    }

    /** Peça gráfica de publicação em JPEG, num item de teste que a aceita (o seed ainda não tem o item do JPEG). */
    protected ProcessoDocumento adicionarImagemDePublicacao(Cenario cenario, String nome) {
        return adicionarPublicacao(cenario, nome, "jpg", TipoElemento.PECA_GRAFICA);
    }

    /** Uma planta (ZIP) cujo `nome_tiff` aponta para o JPEG de nome-base igual. */
    protected ProcessoDocumento adicionarPlantaComNomeTiff(Cenario cenario, String nomeZip, String nomeTiff) {
        ProcessoDocumento zip = adicionarDocumentoDePublicacao(cenario, nomeZip, true);
        Planta planta = plantaRepository.findByProcessoDocumento_Id(zip.getId()).get(0);
        planta.setNomeTiff(nomeTiff);
        plantaRepository.saveAndFlush(planta);
        return zip;
    }

    /** Uma planta cujo ficheiro de publicação é o próprio TIFF (peça gráfica), com o seu `nome_tiff`. */
    protected ProcessoDocumento adicionarPlantaTiffDePublicacao(Cenario cenario, String nomeTiff) {
        ProcessoDocumento tiff = adicionarPublicacao(cenario, nomeTiff + ".tif", "tif", TipoElemento.PECA_GRAFICA);
        Planta planta = new Planta(recarregar(cenario.processo()), tiff);
        planta.setNomeTiff(nomeTiff);
        plantaRepository.saveAndFlush(planta);
        return tiff;
    }

    protected ProcessoDocumento adicionarPublicacao(Cenario cenario, String nome, String extensao, TipoElemento tipo) {
        Processo processo = recarregar(cenario.processo());
        DocumentoConfig item = new DocumentoConfig(processo.getInstrumentoProcedimentoConfig(),
                "PUB-" + SEQUENCIA.incrementAndGet(), nome, nome, CategoriaDocumento.PUBLICACAO,
                tipo, FormatoDocumento.NAO_EDITAVEL, "[\"" + extensao + "\"]", 3);
        item.setLinkPublicoPermitido(true);
        documentoConfigRepository.saveAndFlush(item);
        ProcessoDocumento documento = processoDocumentoRepository.saveAndFlush(new ProcessoDocumento(processo, item,
                CategoriaDocumento.PUBLICACAO, extensao, nome, "", "hash", "v1", CONTEUDO.length,
                processo.getCriadoPor()));
        String caminho = CaminhoDocumento.privado(processo.getNumeroProcesso(), CategoriaDocumento.PUBLICACAO,
                OrigemDocumento.CARREGADO, documento.getId(), nome);
        documento.setCaminhoFicheiro(caminho);
        documento.setVersaoObjeto(versaoGravada(armazenamento.guardar(caminho, new ByteArrayInputStream(CONTEUDO),
                "application/octet-stream")));
        return processoDocumentoRepository.saveAndFlush(documento);
    }

    /** O versionId que o storage devolve; um bucket sem versionamento lê-se pela versão "null". */
    private static String versaoGravada(String versao) {
        return versao == null ? "null" : versao;
    }

    protected String linkJpeg(ProcessoDocumento documento) {
        return plantaRepository.findByProcessoDocumento_Id(documento.getId()).get(0).getLinkJpeg();
    }

    protected void retirarLinkPublicoPermitido(ProcessoDocumento documento) {
        jdbcTemplate.update("UPDATE documento_config SET link_publico_permitido = FALSE WHERE id = ("
                + "SELECT documento_config_id FROM processo_documento WHERE id = ?)", documento.getId());
    }
}
