package com.conkord.saigt.service.relatorio;

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
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Processos para os testes dos KPIs, em PostgreSQL e dentro da transação do teste. {@link #esvaziar()} usa
 * `TRUNCATE`, que no PostgreSQL é transacional: o rollback do teste repõe os processos dos outros testes.
 */
public final class ProcessosKpiFixture {

    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final TipoDinamicaRepository tipoDinamicaRepository;
    private final TipoProcedimentoRepository tipoProcedimentoRepository;
    private final InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    private final EntidadeRepository entidadeRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final ProcessoRepository processoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Dinamica> dinamicaPorTipo = new HashMap<>();
    private final Map<String, Long> itensDocumentais = new HashMap<>();
    private final String sufixo = UUID.randomUUID().toString().substring(0, 8);
    private Entidade entidade;
    private Utilizador autor;
    private int sequencia;

    public ProcessosKpiFixture(ApplicationContext contexto) {
        this.tipoInstrumentoRepository = contexto.getBean(TipoInstrumentoRepository.class);
        this.tipoDinamicaRepository = contexto.getBean(TipoDinamicaRepository.class);
        this.tipoProcedimentoRepository = contexto.getBean(TipoProcedimentoRepository.class);
        this.dinamicaConfigRepository = contexto.getBean(InstrumentoDinamicaConfigRepository.class);
        this.procedimentoConfigRepository = contexto.getBean(InstrumentoProcedimentoConfigRepository.class);
        this.entidadeRepository = contexto.getBean(EntidadeRepository.class);
        this.utilizadorRepository = contexto.getBean(UtilizadorRepository.class);
        this.instrumentoRepository = contexto.getBean(InstrumentoRepository.class);
        this.dinamicaRepository = contexto.getBean(DinamicaRepository.class);
        this.processoRepository = contexto.getBean(ProcessoRepository.class);
        this.jdbcTemplate = contexto.getBean(JdbcTemplate.class);
    }

    /** Também as submissões da auditoria, que não têm chave estrangeira para o processo. */
    public void esvaziar() {
        jdbcTemplate.execute("TRUNCATE processo CASCADE");
        jdbcTemplate.update("DELETE FROM auditoria WHERE tipo_ocorrencia = 'PROCESSO' AND acao = 'SUBMETER'");
    }

    /** Estado e avisos de caducidade (`alertas_enviados`, JSON; nulo deixa a coluna vazia) da Dinâmica do processo. */
    public void alterarDinamica(Processo processo, EstadoDinamica estado, String alertasEnviados) {
        jdbcTemplate.update("UPDATE dinamica SET estado = ?, alertas_enviados = CAST(? AS jsonb) "
                + "WHERE id = (SELECT dinamica_id FROM processo WHERE id = ?)", estado.name(), alertasEnviados,
                processo.getId());
    }

    public void alterarAtualizadoEm(Processo processo, Instant atualizadoEm) {
        jdbcTemplate.update("UPDATE processo SET atualizado_em = ? WHERE id = ?", Timestamp.from(atualizadoEm),
                processo.getId());
    }

    /** Linha `SUBMETER` da auditoria com a data pedida, como o `ProcessoService` a grava. */
    public void registarSubmissao(Processo processo, Instant em) {
        jdbcTemplate.update("INSERT INTO auditoria (tipo_ocorrencia, id_ocorrencia, acao, estado_antes, estado_depois, "
                        + "criado_em) VALUES ('PROCESSO', ?, 'SUBMETER', 'EM_SUBMISSAO', 'ENVIADO_DGT', ?)",
                processo.getId().toString(), Timestamp.from(em));
    }

    public void rejeitar(Processo processo, OrigemRejeicao origem) {
        jdbcTemplate.update("INSERT INTO processo_rejeicao (processo_id, sequencia, origem, motivo_texto, criado_em) "
                        + "VALUES (?, (SELECT COALESCE(MAX(sequencia), 0) + 1 FROM processo_rejeicao WHERE processo_id = ?), "
                        + "?, 'Motivo de teste', now())",
                processo.getId(), processo.getId(), origem.name());
    }

    /** Ficheiro do item documental com esse nome no Procedimento do processo (criado se não existir). */
    public void carregarDocumento(Processo processo, String nomeItem, long tamanhoBytes) {
        Long configId = jdbcTemplate.queryForObject("SELECT instrumento_procedimento_config_id FROM processo WHERE id = ?",
                Long.class, processo.getId());
        Long itemId = itensDocumentais.computeIfAbsent(configId + "|" + nomeItem, chave -> jdbcTemplate.queryForObject(
                "INSERT INTO documento_config (instrumento_procedimento_config_id, codigo, nome, separador, categoria, "
                        + "tipo_elemento, formato, obrigatorio, extensoes_aceites, link_publico_permitido, pagina, "
                        + "regra_nomenclatura, independente_elementos_afetados, ordem, ativo) VALUES (?, ?, ?, 'Documentos', "
                        + "'PUBLICACAO', 'PECA_ESCRITA', 'NAO_EDITAVEL', false, '[\"pdf\"]'::jsonb, false, 'DOCUMENTOS', "
                        + "'GERAL', false, ?, true) RETURNING id",
                Long.class, configId, "KPI_" + (++sequencia), nomeItem, sequencia));
        inserirDocumento(processo, itemId, tamanhoBytes);
    }

    /** Documento extra, sem item documental. */
    public void carregarDocumentoExtra(Processo processo, long tamanhoBytes) {
        inserirDocumento(processo, null, tamanhoBytes);
    }

    private void inserirDocumento(Processo processo, Long itemId, long tamanhoBytes) {
        int n = ++sequencia;
        // Todas as colunas: nos testes o esquema vem do Hibernate, sem os valores por omissão do Liquibase.
        jdbcTemplate.update("INSERT INTO processo_documento (id, processo_id, documento_config_id, categoria, extensao, "
                        + "nome_ficheiro, caminho_ficheiro, hash_sha256, versao_objeto, tamanho_bytes, criado_por, origem, "
                        + "versao, bloqueado_edicao, estado_validacao, criado_em, atualizado_em) "
                        + "SELECT gen_random_uuid(), p.id, ?, ?, 'pdf', ?, ?, ?, 'v1', ?, p.criado_por, 'CARREGADO', 0, false, "
                        + "'POR_VALIDAR', now(), now() FROM processo p WHERE p.id = ?",
                itemId, itemId == null ? "DEPOSITO" : "PUBLICACAO", "ficheiro-" + n + ".pdf",
                "processos/kpi/" + sufixo + "/" + n, "a".repeat(64), tamanhoBytes, processo.getId());
    }

    public Processo criar(String tipoInstrumento, EstadoProcesso estado) {
        return criar(tipoInstrumento, estado, null);
    }

    /** Uma Dinâmica por tipo de Instrumento, com Instrumento de partida. */
    public Processo criar(String tipoInstrumento, EstadoProcesso estado, LocalDate dataPublicacaoDr) {
        return processoNa(dinamicaPorTipo.computeIfAbsent(tipoInstrumento, this::novaDinamica), estado, dataPublicacaoDr);
    }

    /** Numa Dinâmica iniciada sem IGT: sem Instrumento de partida. */
    public Processo criarSemIgt(String tipoInstrumento, EstadoProcesso estado) {
        Dinamica dinamica = dinamicaPorTipo.computeIfAbsent("semIgt:" + tipoInstrumento,
                chave -> dinamicaRepository.save(new Dinamica(config(tipoInstrumento), "Elaboração", autor())));
        return processoNa(dinamica, estado, null);
    }

    private Processo processoNa(Dinamica dinamica, EstadoProcesso estado, LocalDate dataPublicacaoDr) {
        int n = ++sequencia;
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.save(
                new TipoProcedimento("KPI-" + sufixo + "-" + n, "Procedimento " + n));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.save(
                new InstrumentoProcedimentoConfig(dinamica.getInstrumentoDinamicaConfig(), tipoProcedimento, n, "SEGUNDA_H"));
        Processo processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor(), entidade()));
        jdbcTemplate.update("UPDATE processo SET estado = ?, data_publicacao_dr = ? WHERE id = ?", estado.name(),
                dataPublicacaoDr, processo.getId());
        return processo;
    }

    private Dinamica novaDinamica(String codigoTipo) {
        InstrumentoDinamicaConfig config = config(codigoTipo);
        Instrumento instrumento = instrumentoRepository.save(new Instrumento(config.getTipoInstrumento(), "1",
                "2026/" + codigoTipo + "/" + sufixo, autor(), autor()));
        return dinamicaRepository.save(new Dinamica(instrumento, config, "Alteração", autor()));
    }

    private InstrumentoDinamicaConfig config(String codigoTipo) {
        TipoInstrumento tipo = tipoInstrumentoRepository.findAll().stream()
                .filter(existente -> existente.getCodigo().equals(codigoTipo))
                .findFirst()
                .orElseGet(() -> tipoInstrumentoRepository.save(new TipoInstrumento(codigoTipo, "Tipo " + codigoTipo, "PLANO")));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.save(
                new TipoDinamica("KPI-" + sufixo + "-" + (++sequencia), "Alteração"));
        return dinamicaConfigRepository.save(new InstrumentoDinamicaConfig(tipo, tipoDinamica));
    }

    private Entidade entidade() {
        if (entidade == null) {
            entidade = entidadeRepository.save(EntidadeTeste.nova(
                    "5" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)), "Câmara KPI " + sufixo));
        }
        return entidade;
    }

    private Utilizador autor() {
        if (autor == null) {
            autor = utilizadorRepository.save(new Utilizador("Autor KPI " + sufixo, "kpi." + sufixo + "@cm-teste.pt", "INCM"));
        }
        return autor;
    }
}
