package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeTeste;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdusProcessoRepositoryTest {

    private static final Instant ATUALIZADO_EM = Instant.parse("2026-11-03T10:00:00Z");

    @Autowired
    private IdusProcessoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

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

        return processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, criadoPor, cmLisboa));
    }

    private IdusProcesso.IdusProcessoBuilder idus(Processo processo, String idus) {
        return IdusProcesso.builder()
                .processo(processo)
                .idus(idus)
                .sistemaOrigem("SAIGT")
                .idIgt("1001")
                .ordemDinamica("T001")
                .tentativa((short) 1)
                .procedimento("031")
                .atualizadoEm(ATUALIZADO_EM);
    }

    @Test
    void deveGravarELerTodasAsColunas() {
        Processo processo = criarProcesso();
        repository.saveAndFlush(idus(processo, "SAIGT-1001-T001-1-031-00")
                .idIgtDefinitivo(true)
                .ordemDefinitiva(true)
                .tentativa((short) 2)
                .procedimento("131")
                .submissao((short) 3)
                .pubIncm("INCM-2026-1")
                .build());
        entityManager.clear();

        IdusProcesso lido = repository.findById(processo.getId()).orElseThrow();

        assertThat(lido.getProcessoId()).isEqualTo(processo.getId());
        assertThat(lido.getProcesso().getId()).isEqualTo(processo.getId());
        assertThat(lido.getIdus()).isEqualTo("SAIGT-1001-T001-1-031-00");
        assertThat(lido.getSistemaOrigem()).isEqualTo("SAIGT");
        assertThat(lido.getIdIgt()).isEqualTo("1001");
        assertThat(lido.isIdIgtDefinitivo()).isTrue();
        assertThat(lido.getOrdemDinamica()).isEqualTo("T001");
        assertThat(lido.isOrdemDefinitiva()).isTrue();
        assertThat(lido.getTentativa()).isEqualTo((short) 2);
        assertThat(lido.getProcedimento()).isEqualTo("131");
        assertThat(lido.getSubmissao()).isEqualTo((short) 3);
        assertThat(lido.getPubIncm()).isEqualTo("INCM-2026-1");
        assertThat(lido.getAtualizadoEm()).isEqualTo(ATUALIZADO_EM);
    }

    @Test
    void deveGravarSemPubIncmEComSubmissaoZero() {
        Processo processo = criarProcesso();
        repository.saveAndFlush(idus(processo, "SAIGT-1001-T001-1-031-00").build());
        entityManager.clear();

        IdusProcesso lido = repository.findById(processo.getId()).orElseThrow();

        assertThat(lido.isIdIgtDefinitivo()).isFalse();
        assertThat(lido.isOrdemDefinitiva()).isFalse();
        assertThat(lido.getSubmissao()).isZero();
        assertThat(lido.getPubIncm()).isNull();
    }

    @Test
    void deveEncontrarOProcessoPeloIdus() {
        Processo processo = criarProcesso();
        repository.saveAndFlush(idus(processo, "SAIGT-1001-T001-1-031-00").build());

        Optional<IdusProcesso> encontrado = repository.findByIdus("SAIGT-1001-T001-1-031-00");

        assertThat(encontrado).map(IdusProcesso::getProcessoId).contains(processo.getId());
        assertThat(repository.findByIdus("SAIGT-9999-T001-1-031-00")).isEmpty();
        assertThat(repository.existsByIdus("SAIGT-1001-T001-1-031-00")).isTrue();
        assertThat(repository.existsByIdus("SAIGT-9999-T001-1-031-00")).isFalse();
    }

    @Test
    void deveEncontrarOIdusDeOutroProcessoDaMesmaDinamica() {
        Processo primeiro = criarProcesso();
        Processo segundo = new Processo(primeiro.getDinamica(), primeiro.getInstrumentoProcedimentoConfig(),
                primeiro.getCriadoPor(), primeiro.getEntidade());
        segundo.setOcorrencia(2);
        processoRepository.saveAndFlush(segundo);
        repository.saveAndFlush(idus(primeiro, "SAIGT-1001-T001-1-031-00").build());
        repository.saveAndFlush(idus(segundo, "SAIGT-1001-T001-1-131-00").procedimento("131").build());
        entityManager.clear();

        Optional<IdusProcesso> encontrado = repository.findFirstByProcessoDinamicaId(primeiro.getDinamica().getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getOrdemDinamica()).isEqualTo("T001");
        assertThat(repository.findFirstByProcessoDinamicaId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void naoDeveEncontrarIdusNaDinamicaCujosProcessosAindaNaoTemIdus() {
        Processo processo = criarProcesso();

        assertThat(repository.findFirstByProcessoDinamicaId(processo.getDinamica().getId())).isEmpty();
    }

    @Test
    void deveAtualizarOIdusCorrente() {
        Processo processo = criarProcesso();
        IdusProcesso gravado = repository.saveAndFlush(idus(processo, "SAIGT-1001-T001-1-031-00").build());

        gravado.setIdus("SAIGT-1001-T001-1-031-01");
        gravado.setSubmissao((short) 1);
        gravado.setAtualizadoEm(ATUALIZADO_EM.plusSeconds(60));
        repository.saveAndFlush(gravado);
        entityManager.clear();

        IdusProcesso lido = repository.findById(processo.getId()).orElseThrow();
        assertThat(lido.getIdus()).isEqualTo("SAIGT-1001-T001-1-031-01");
        assertThat(lido.getSubmissao()).isEqualTo((short) 1);
        assertThat(lido.getAtualizadoEm()).isEqualTo(ATUALIZADO_EM.plusSeconds(60));
        assertThat(repository.existsByIdus("SAIGT-1001-T001-1-031-00")).isFalse();
    }
}
