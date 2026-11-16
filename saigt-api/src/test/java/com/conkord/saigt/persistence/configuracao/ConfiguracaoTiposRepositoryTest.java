package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.regras.Condicao;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Os métodos novos dos repositórios que sustentam a configuração dos tipos, na base real (`jsonb`). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConfiguracaoTiposRepositoryTest extends PostgresTestcontainersBase {

    @Autowired
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Autowired
    private TipoDinamicaRepository tipoDinamicaRepository;
    @Autowired
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Autowired
    private InstrumentoDinamicaConfigRepository dinamicaConfigRepository;
    @Autowired
    private InstrumentoProcedimentoConfigRepository procedimentoConfigRepository;
    @Autowired
    private RegraRepository regraRepository;
    @Autowired
    private CondicaoRepository condicaoRepository;

    private static String codigoUnico() {
        return "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    @Test
    void existsByCodigoAndIdNotDeveIgnorarOProprioRegisto() {
        TipoInstrumento a = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "A", "PLANO"));
        TipoInstrumento b = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "B", "PLANO"));

        assertThat(tipoInstrumentoRepository.existsByCodigo(a.getCodigo())).isTrue();
        assertThat(tipoInstrumentoRepository.existsByCodigoAndIdNot(a.getCodigo(), a.getId())).isFalse();
        assertThat(tipoInstrumentoRepository.existsByCodigoAndIdNot(a.getCodigo(), b.getId())).isTrue();

        TipoDinamica d = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "D"));
        assertThat(tipoDinamicaRepository.existsByCodigoAndIdNot(d.getCodigo(), d.getId())).isFalse();
        assertThat(tipoDinamicaRepository.existsByCodigoAndIdNot(d.getCodigo(), d.getId() + 1)).isTrue();

        TipoProcedimento p = tipoProcedimentoRepository.saveAndFlush(new TipoProcedimento(codigoUnico(), "P"));
        assertThat(tipoProcedimentoRepository.existsByCodigoAndIdNot(p.getCodigo(), p.getId())).isFalse();
        assertThat(tipoProcedimentoRepository.existsByCodigoAndIdNot(p.getCodigo(), p.getId() + 1)).isTrue();
    }

    @Test
    void findByAtivoDeveFiltrarOrdenarEPaginar() {
        TipoDinamica ativa = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Ativa"));
        TipoDinamica inativa = new TipoDinamica(codigoUnico(), "Inativa");
        inativa.setAtivo(false);
        inativa = tipoDinamicaRepository.saveAndFlush(inativa);

        var inativas = tipoDinamicaRepository.findByAtivo(false, PageRequest.of(0, 1000, Sort.by("codigo")));
        var ativas = tipoDinamicaRepository.findByAtivo(true, PageRequest.of(0, 1000, Sort.by("codigo")));

        assertThat(inativas.getContent()).extracting(TipoDinamica::getId).contains(inativa.getId())
                .doesNotContain(ativa.getId());
        assertThat(ativas.getContent()).extracting(TipoDinamica::getId).contains(ativa.getId())
                .doesNotContain(inativa.getId());
        assertThat(tipoDinamicaRepository.findAll(PageRequest.of(0, 1, Sort.by("codigo"))).getContent()).hasSize(1);
    }

    @Test
    void existsPorTipoDeveDistinguirTipoEmUsoDeTipoNovo() {
        TipoInstrumento emUso = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "Em uso", "PLANO"));
        TipoInstrumento novo = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "Novo", "PLANO"));
        TipoDinamica dinamicaEmUso = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Em uso"));
        TipoDinamica dinamicaNova = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Nova"));
        TipoProcedimento procedimentoEmUso = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento(codigoUnico(), "Em uso"));
        TipoProcedimento procedimentoNovo = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento(codigoUnico(), "Novo"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(emUso, dinamicaEmUso));
        procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, procedimentoEmUso, 1, "SEGUNDA_H"));

        assertThat(dinamicaConfigRepository.existsByTipoInstrumento_Id(emUso.getId())).isTrue();
        assertThat(dinamicaConfigRepository.existsByTipoInstrumento_Id(novo.getId())).isFalse();
        assertThat(dinamicaConfigRepository.existsByTipoDinamica_Id(dinamicaEmUso.getId())).isTrue();
        assertThat(dinamicaConfigRepository.existsByTipoDinamica_Id(dinamicaNova.getId())).isFalse();
        assertThat(procedimentoConfigRepository.existsByTipoProcedimento_Id(procedimentoEmUso.getId())).isTrue();
        assertThat(procedimentoConfigRepository.existsByTipoProcedimento_Id(procedimentoNovo.getId())).isFalse();
    }

    @Test
    void existeParametroCodigoDeveEncontrarCondicoesAtivasEInativas() {
        TipoInstrumento tipo = tipoInstrumentoRepository.saveAndFlush(new TipoInstrumento(codigoUnico(), "Tipo", "PLANO"));
        TipoDinamica dinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigoUnico(), "Dinâmica"));
        Regra regra = regraRepository.saveAndFlush(Regra.paraDinamica(
                dinamicaConfigRepository.saveAndFlush(new InstrumentoDinamicaConfig(tipo, dinamica))));
        String citadoAtivo = codigoUnico();
        String citadoInativo = codigoUnico();
        condicaoRepository.saveAndFlush(new Condicao(regra, "OCORRENCIA_EM_ESTADO", "{\"codigo\":\"" + citadoAtivo + "\"}"));
        Condicao inativa = new Condicao(regra, "OCORRENCIA_EM_ESTADO", "{\"codigo\":\"" + citadoInativo + "\"}");
        inativa.setAtivo(false);
        condicaoRepository.saveAndFlush(inativa);
        condicaoRepository.saveAndFlush(new Condicao(regra, "OUTRO", "{\"outro\":\"" + codigoUnico() + "\"}"));

        assertThat(condicaoRepository.existeParametroCodigo(citadoAtivo)).isTrue();
        assertThat(condicaoRepository.existeParametroCodigo(citadoInativo)).isTrue();
        assertThat(condicaoRepository.existeParametroCodigo(codigoUnico())).isFalse();
    }
}
