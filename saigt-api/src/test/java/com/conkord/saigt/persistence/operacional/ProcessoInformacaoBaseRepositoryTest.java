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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoInformacaoBaseRepositoryTest {

    @Autowired
    private ProcessoInformacaoBaseRepository repository;

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
    private JdbcTemplate jdbcTemplate;

    private Processo processo;
    private Processo outroProcesso;

    @BeforeEach
    void setUp() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        Entidade entidade = entidadeRepository.saveAndFlush(EntidadeTeste.nova("501234567", "Câmara Municipal de Lisboa"));
        Utilizador autor = utilizadorRepository.saveAndFlush(new Utilizador("Ana Ferreira", "ana.ferreira@cm-lisboa.pt", "INCM"));
        Instrumento instrumento = instrumentoRepository.saveAndFlush(
                new Instrumento(pdm, "1", "2026/PDM/001", autor, autor));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        Dinamica dinamica = dinamicaRepository.saveAndFlush(new Dinamica(instrumento, dinamicaConfig, "Alteração do PDM", autor));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        InstrumentoProcedimentoConfig procedimentoConfig = procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
        processo = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
        outroProcesso = processoRepository.saveAndFlush(new Processo(dinamica, procedimentoConfig, autor, entidade));
    }

    @Test
    void deveGuardarComEstadoPorValidarELerPorProcesso() {
        ProcessoInformacaoBase guardado = repository.saveAndFlush(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));
        repository.saveAndFlush(new ProcessoInformacaoBase(outroProcesso, ElementoInformacaoBase.DELIBERACAO));

        assertThat(guardado.getEstadoValidacao()).isEqualTo(EstadoValidacao.POR_VALIDAR);
        assertThat(repository.findByProcesso_Id(processo.getId()))
                .extracting(ProcessoInformacaoBase::getId).containsExactly(guardado.getId());
    }

    @Test
    void deveEncontrarOItemSoNoProcessoAQuePertence() {
        UUID id = repository.saveAndFlush(new ProcessoInformacaoBase(processo, ElementoInformacaoBase.TEXTO_ATO)).getId();

        assertThat(repository.findByIdAndProcesso_Id(id, processo.getId())).isPresent();
        assertThat(repository.findByIdAndProcesso_Id(id, outroProcesso.getId())).isEmpty();
    }

    @Test
    void deveRecusarSegundoRegistoDoMesmoElementoNoMesmoProcesso() {
        repository.saveAndFlush(new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));

        assertThatThrownBy(() -> repository.saveAndFlush(
                new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void devePermitirOMesmoElementoEmProcessosDiferentes() {
        repository.saveAndFlush(new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO));

        assertThat(repository.saveAndFlush(new ProcessoInformacaoBase(outroProcesso, ElementoInformacaoBase.DELIBERACAO)))
                .isNotNull();
    }

    @Test
    void deveRecusarEstadoForaDoDominio() {
        UUID id = repository.saveAndFlush(new ProcessoInformacaoBase(processo, ElementoInformacaoBase.DELIBERACAO)).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE processo_informacao_base SET estado_validacao = 'INVALIDO' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
