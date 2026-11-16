package com.conkord.saigt.service.snit;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.integracao.core.FalhaTecnicaExterna;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.FicheirosRecebidosSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifesto;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinha;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinhaRepository;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ManifestoDepositoSnitServiceTest extends PostgresTestcontainersBase {

    private static final ReferenciaChamada REFERENCIA = new ReferenciaChamada(null, null, null);
    private static final ResultadoChamadaExterna<DepositoRecebidoSnit> DEPOSITO_ACEITE = new ResultadoChamadaExterna<>(
            201, new DepositoRecebidoSnit(88100L, "1", 14230L, false, "pending_start", "process_registration", null,
            null), null, 5);
    private static final ResultadoChamadaExterna<DepositoRecebidoSnit> DEPOSITO_COM_5XX =
            new ResultadoChamadaExterna<>(503, null, FalhaTecnicaExterna.ERRO_HTTP, 5);
    private static final ResultadoChamadaExterna<FicheirosRecebidosSnit> FICHEIROS_ACEITES =
            new ResultadoChamadaExterna<>(201, new FicheirosRecebidosSnit(88100L, 1), null, 5);

    // Montado à mão, sem @MockBean: um contexto Spring a mais na cache fecha outros e o create-drop apaga as tabelas
    // do H2 partilhado.
    private final ClienteSnit clienteSnit = mock(ClienteSnit.class);
    private ManifestoDepositoSnitService service;

    @Autowired
    private DepositoSnitProperties propriedades;

    @Autowired
    private DepositoSnitManifestoRepository manifestoRepository;

    @Autowired
    private DepositoSnitManifestoLinhaRepository linhaRepository;

    @Autowired
    private ProcessoDocumentoRepository documentoRepository;

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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock relogio;

    private static final AtomicLong SEQUENCIA = new AtomicLong(ThreadLocalRandom.current().nextLong(1_000_000));

    private UUID processoId;
    private ProcessoDocumento depositoTexto;
    private ProcessoDocumento depositoZip;
    private ProcessoDocumento depositoGeoconform;
    private ProcessoDocumento substituido;
    private ProcessoDocumento publicacaoTexto;
    private ProcessoDocumento publicacaoRegulamento;

    @BeforeEach
    void processoComDocumentosNasDuasCaixas() {
        service = new ManifestoDepositoSnitService(documentoRepository, manifestoRepository, linhaRepository,
                clienteSnit, propriedades, transactionManager, relogio);
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            String sufixo = Long.toString(SEQUENCIA.incrementAndGet(), 36);
            Utilizador novoAutor = new Utilizador("Autor de Teste", "autor." + sufixo + "@exemplo.pt");
            novoAutor.setPerfilInterno(PerfilInterno.MASTER);
            Utilizador autor = utilizadorRepository.saveAndFlush(novoAutor);
            Entidade entidade = entidadeRepository.saveAndFlush(
                    EntidadeTeste.nova(String.format("9%08d", SEQUENCIA.get() % 100_000_000), "Câmara de Teste"));
            TipoInstrumento tipo = tipoInstrumentoRepository.saveAndFlush(
                    new TipoInstrumento("T" + sufixo, "Tipo de teste", "PLANO"));
            TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("D" + sufixo, "Dinâmica"));
            TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                    new TipoProcedimento("P" + sufixo, "Procedimento final"));
            Instrumento instrumento = instrumentoRepository.saveAndFlush(
                    new Instrumento(tipo, "1", "Instrumento " + sufixo, autor, autor));
            InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                    new InstrumentoDinamicaConfig(tipo, tipoDinamica));
            Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Teste", autor));
            InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                    new InstrumentoProcedimentoConfig(dinamicaConfig, tipoProcedimento, 1, "SEGUNDA_H"));
            Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
            processoId = processo.getId();
            String pasta = "processos/" + processo.getNumeroProcesso() + "/";

            depositoTexto = documento(processo, autor, CategoriaDocumento.DEPOSITO, pasta + "DEPOSITO/", "relatorio.pdf");
            depositoZip = documento(processo, autor, CategoriaDocumento.DEPOSITO, pasta + "DEPOSITO/", "planta.zip");
            depositoGeoconform = documento(processo, autor, CategoriaDocumento.DEPOSITO, pasta + "DEPOSITO/GEOCONFORM/",
                    "assinado.zip");
            substituido = documento(processo, autor, CategoriaDocumento.DEPOSITO, pasta + "SUBSTITUIDOS/",
                    "antigo.pdf");
            publicacaoTexto = documento(processo, autor, CategoriaDocumento.PUBLICACAO, pasta + "PUBLICACAO/",
                    "aviso.pdf");
            publicacaoRegulamento = documento(processo, autor, CategoriaDocumento.PUBLICACAO, pasta + "PUBLICACAO/",
                    "regulamento.pdf");
        });
    }

    private ProcessoDocumento documento(Processo processo, Utilizador autor, CategoriaDocumento categoria,
                                        String pasta, String nome) {
        UUID id = UUID.randomUUID();
        String extensao = nome.substring(nome.lastIndexOf('.') + 1);
        return documentoRepository.saveAndFlush(new ProcessoDocumento(id, processo, null, categoria, extensao, nome,
                pasta + id + "-" + nome, "a".repeat(63) + Math.abs(id.hashCode() % 10), "versao-" + id,
                nome.length() * 100L, autor));
    }

    private Map<UUID, DepositoSnitManifestoLinha> linhas(UUID manifestoId) {
        return linhaRepository.findByIdManifestoId(manifestoId).stream()
                .collect(Collectors.toMap(linha -> linha.getId().getProcessoDocumentoId(), Function.identity()));
    }

    private static DepositoSnit deposito() {
        return new DepositoSnit(88100L, "03.11.11/PDM/02/2026/7", Instant.parse("2026-11-10T10:00:00Z"), null, null,
                null);
    }

    private static FicheiroDepositoSnit ficheiro(ProcessoDocumento documento) {
        return new FicheiroDepositoSnit(documento.getId().toString(), "planta", null,
                "https://saigt.exemplo.pt/ficheiros/" + documento.getId(), true, Instant.parse("2026-11-10T08:00:00Z"));
    }

    private int manifestosCommitados() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM deposito_snit_manifesto WHERE processo_id = ?", Integer.class, processoId);
        return total == null ? 0 : total;
    }

    @Test
    void deveFixarSoOsDocumentosAtuaisDaCaixaDepositoPorOmissao() {
        DepositoSnitManifesto manifesto = service.gravar(processoId, Set.of());

        assertThat(manifesto.getCaixasIncluidas()).containsExactly(CategoriaDocumento.DEPOSITO);
        assertThat(jdbcTemplate.queryForObject("SELECT caixas_incluidas FROM deposito_snit_manifesto WHERE id = ?",
                String.class, manifesto.getId())).isEqualTo("DEPOSITO");
        Map<UUID, DepositoSnitManifestoLinha> linhas = linhas(manifesto.getId());
        assertThat(linhas.keySet()).containsExactlyInAnyOrder(depositoTexto.getId(), depositoZip.getId(),
                depositoGeoconform.getId());
        DepositoSnitManifestoLinha geoconform = linhas.get(depositoGeoconform.getId());
        assertThat(geoconform.getChaveObjeto()).isEqualTo(depositoGeoconform.getCaminhoFicheiro()).contains("/GEOCONFORM/");
        assertThat(geoconform.getVersaoObjeto()).isEqualTo(depositoGeoconform.getVersaoObjeto());
        assertThat(geoconform.getTamanhoBytes()).isEqualTo(depositoGeoconform.getTamanhoBytes());
        assertThat(geoconform.getHashSha256()).isEqualTo(depositoGeoconform.getHashSha256());
        assertThat(linhas.values()).noneMatch(DepositoSnitManifestoLinha::isComunicadoUmAUm);
    }

    @Test
    void deveIncluirOsDocumentosDePublicacaoQuandoAsDuasCaixasEstaoConfiguradas() {
        ManifestoDepositoSnitService comAsDuasCaixas = new ManifestoDepositoSnitService(documentoRepository,
                manifestoRepository, linhaRepository, clienteSnit,
                new DepositoSnitProperties(EnumSet.of(CategoriaDocumento.PUBLICACAO, CategoriaDocumento.DEPOSITO)),
                transactionManager, relogio);

        DepositoSnitManifesto manifesto = comAsDuasCaixas.gravar(processoId, Set.of());

        assertThat(jdbcTemplate.queryForObject("SELECT caixas_incluidas FROM deposito_snit_manifesto WHERE id = ?",
                String.class, manifesto.getId())).isEqualTo("PUBLICACAO,DEPOSITO");
        assertThat(linhas(manifesto.getId()).keySet()).containsExactlyInAnyOrder(depositoTexto.getId(),
                depositoZip.getId(), depositoGeoconform.getId(), publicacaoTexto.getId(),
                publicacaoRegulamento.getId()).doesNotContain(substituido.getId());
    }

    @Test
    void deveMarcarOsZipComunicadosUmAUm() {
        DepositoSnitManifesto manifesto = service.gravar(processoId, Set.of(depositoZip.getId()));

        Map<UUID, DepositoSnitManifestoLinha> linhas = linhas(manifesto.getId());
        assertThat(linhas.get(depositoZip.getId()).isComunicadoUmAUm()).isTrue();
        assertThat(linhas.get(depositoTexto.getId()).isComunicadoUmAUm()).isFalse();
        assertThat(linhas.get(depositoGeoconform.getId()).isComunicadoUmAUm()).isFalse();
    }

    @Test
    void deveRecusarUmZipComunicadoUmAUmForaDasCaixasSemGravarNada() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.gravar(processoId, Set.of(publicacaoTexto.getId())));

        assertThat(manifestosCommitados()).isZero();
    }

    @Test
    void deveTerOManifestoCommitadoQuandoOPedidoDeDepositoSai() {
        AtomicLong vistosNoPedido = new AtomicLong(-1);
        when(clienteSnit.registarDeposito(any(), any())).thenAnswer(chamada -> {
            vistosNoPedido.set(manifestosCommitados());
            return DEPOSITO_ACEITE;
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(estado ->
                service.comunicarDeposito(processoId, deposito(), List.of(), REFERENCIA));

        assertThat(vistosNoPedido.get()).isEqualTo(1);
    }

    @Test
    void deveComunicarOsFicheirosSoDepoisDoDepositoAceiteEMarcaLosNoManifesto() {
        when(clienteSnit.registarDeposito(any(), any())).thenReturn(DEPOSITO_ACEITE);
        when(clienteSnit.registarFicheirosDeposito(anyLong(), anyList(), any())).thenReturn(FICHEIROS_ACEITES);
        List<FicheiroDepositoSnit> ficheiros = List.of(ficheiro(depositoZip));

        ComunicacaoDepositoSnit comunicacao = service.comunicarDeposito(processoId, deposito(), ficheiros, REFERENCIA);

        verify(clienteSnit).registarFicheirosDeposito(88100L, ficheiros, REFERENCIA);
        assertThat(comunicacao.ficheiros()).isEqualTo(FICHEIROS_ACEITES);
        assertThat(linhas(comunicacao.manifestoId()).get(depositoZip.getId()).isComunicadoUmAUm()).isTrue();
    }

    @Test
    void deveManterOManifestoDoDepositoFalhadoENaoEnviarOsFicheiros() {
        when(clienteSnit.registarDeposito(any(), any())).thenReturn(DEPOSITO_COM_5XX);

        ComunicacaoDepositoSnit comunicacao =
                service.comunicarDeposito(processoId, deposito(), List.of(ficheiro(depositoZip)), REFERENCIA);

        assertThat(comunicacao.deposito()).isEqualTo(DEPOSITO_COM_5XX);
        assertThat(comunicacao.ficheiros()).isNull();
        verify(clienteSnit, never()).registarFicheirosDeposito(anyLong(), anyList(), any());
        assertThat(manifestoRepository.findById(comunicacao.manifestoId())).isPresent();
    }

    @Test
    void deveGravarUmManifestoNovoEmCadaTentativaESemMexerNosAnteriores() {
        when(clienteSnit.registarDeposito(any(), any())).thenReturn(DEPOSITO_COM_5XX, DEPOSITO_ACEITE);

        UUID primeiro = service.comunicarDeposito(processoId, deposito(), List.of(), REFERENCIA).manifestoId();
        transacao(() -> documentoRepository.findById(depositoTexto.getId()).orElseThrow()
                .setVersaoObjeto("versao-nova"));
        UUID segundo = service.comunicarDeposito(processoId, deposito(), List.of(), REFERENCIA).manifestoId();

        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(manifestoRepository.findFirstByProcessoIdOrderByCriadoEmDesc(processoId))
                .map(DepositoSnitManifesto::getId).contains(segundo);
        assertThat(linhas(primeiro).get(depositoTexto.getId()).getVersaoObjeto())
                .isEqualTo(depositoTexto.getVersaoObjeto());
        assertThat(linhas(segundo).get(depositoTexto.getId()).getVersaoObjeto()).isEqualTo("versao-nova");
        assertThat(manifestoRepository.findByProcessoIdOrderByCriadoEmAsc(processoId)).hasSize(2);
    }

    private void transacao(Runnable acao) {
        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> acao.run());
    }
}
