package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentoConfigRepositoryTest {

    @Autowired
    private DocumentoConfigRepository repository;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private InstrumentoProcedimentoConfig criarProcedimentoConfig() {
        TipoInstrumento pdm = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO"));
        TipoDinamica elaboracao = tipoDinamicaRepository.saveAndFlush(new TipoDinamica("ELABORACAO", "Elaboração"));
        InstrumentoDinamicaConfig dinamicaConfig = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(pdm, elaboracao));
        TipoProcedimento decisao = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento("DECISAO_INICIO", "Decisão de Início"));
        return procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamicaConfig, decisao, 1, "SEGUNDA_H"));
    }

    private static DocumentoConfig item(InstrumentoProcedimentoConfig procedimentoConfig, String codigo) {
        return new DocumentoConfig(procedimentoConfig, codigo, "Texto do ato", "Texto do ato",
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL,
                "[\"docx\",\"pdf\"]", 1);
    }

    @Test
    void deveGuardarELerDocumentoConfig() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        DocumentoConfig novo = item(procedimentoConfig, "TEXTO_ATO");
        novo.setObrigatorio(true);
        DocumentoConfig guardado = repository.saveAndFlush(novo);

        Optional<DocumentoConfig> lido = repository.findById(guardado.getId());
        assertThat(lido).isPresent();
        assertThat(lido.get().getCodigo()).isEqualTo("TEXTO_ATO");
        assertThat(lido.get().getNome()).isEqualTo("Texto do ato");
        assertThat(lido.get().getSeparador()).isEqualTo("Texto do ato");
        assertThat(lido.get().getCategoria()).isEqualTo(CategoriaDocumento.PUBLICACAO);
        assertThat(lido.get().getTipoElemento()).isEqualTo(TipoElemento.PECA_ESCRITA);
        assertThat(lido.get().getFormato()).isEqualTo(FormatoDocumento.EDITAVEL);
        assertThat(lido.get().getExtensoesAceites()).contains("docx");
        assertThat(lido.get().isObrigatorio()).isTrue();
        assertThat(lido.get().isAtivo()).isTrue();
    }

    @Test
    void deveAplicarObrigatorioFalsoPorDefeito() {
        DocumentoConfig guardado = repository.saveAndFlush(item(criarProcedimentoConfig(), "ATA"));

        assertThat(guardado.isObrigatorio()).isFalse();
    }

    @Test
    void deveRejeitarCodigoRepetidoNoMesmoProcedimento() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        repository.saveAndFlush(item(procedimentoConfig, "TEXTO_ATO"));

        assertThatThrownBy(() -> repository.saveAndFlush(item(procedimentoConfig, "TEXTO_ATO")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarValorForaDoDominio() {
        Long id = repository.saveAndFlush(item(criarProcedimentoConfig(), "TEXTO_ATO")).getId();

        for (String coluna : new String[] {"categoria", "tipo_elemento", "formato"}) {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE documento_config SET " + coluna + " = 'INVALIDO' WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void deveDevolverSoOsElementosDistintosDosItensAtivosMarcados() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        repository.saveAndFlush(marcado(procedimentoConfig, "DELIBERACAO_PUB", ElementoInformacaoBase.DELIBERACAO, true));
        repository.saveAndFlush(marcado(procedimentoConfig, "DELIBERACAO_DEP", ElementoInformacaoBase.DELIBERACAO, true));
        repository.saveAndFlush(marcado(procedimentoConfig, "TEXTO_ATO_PUB", ElementoInformacaoBase.TEXTO_ATO, false));
        repository.saveAndFlush(marcado(procedimentoConfig, "PLANTA_PUB", null, true));

        assertThat(repository.findElementosInformacaoBaseAtivos(procedimentoConfig))
                .containsExactly(ElementoInformacaoBase.DELIBERACAO);
    }

    @Test
    void deveDevolverVazioQuandoNenhumItemAtivoEstaMarcado() {
        InstrumentoProcedimentoConfig procedimentoConfig = criarProcedimentoConfig();
        repository.saveAndFlush(item(procedimentoConfig, "TEXTO_ATO"));

        assertThat(repository.findElementosInformacaoBaseAtivos(procedimentoConfig)).isEmpty();
    }

    @Test
    void deveRejeitarElementoDeInformacaoBaseForaDoDominio() {
        Long id = repository.saveAndFlush(item(criarProcedimentoConfig(), "TEXTO_ATO")).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE documento_config SET elemento_informacao_base = 'OUTRO' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private DocumentoConfig marcado(InstrumentoProcedimentoConfig procedimentoConfig, String codigo,
                                    ElementoInformacaoBase elemento, boolean ativo) {
        DocumentoConfig item = item(procedimentoConfig, codigo);
        item.setElementoInformacaoBase(elemento);
        item.setAtivo(ativo);
        return item;
    }

    @Test
    void deveGuardarELerAsRegrasTiffDeUmaPecaGrafica() {
        DocumentoConfig planta = new DocumentoConfig(criarProcedimentoConfig(), "PLANTA_ORD", "Planta", "Planta",
                CategoriaDocumento.DEPOSITO, TipoElemento.PECA_GRAFICA, FormatoDocumento.NAO_EDITAVEL,
                "[\"tif\"]", 1);
        planta.setRegrasTiff(RegrasTiff.inicialSgifr());
        Long id = repository.saveAndFlush(planta).getId();
        entityManager.clear();

        DocumentoConfig lido = repository.findById(id).orElseThrow();
        assertThat(lido.getRegrasTiff()).isEqualTo(RegrasTiff.inicialSgifr());
        assertThat(lido.regrasTiffEfetivas()).contains(RegrasTiff.inicialSgifr());
    }

    @Test
    void deveRecusarRegrasTiffNumItemQueNaoEPecaGrafica() {
        DocumentoConfig texto = item(criarProcedimentoConfig(), "TEXTO_ATO");
        texto.setRegrasTiff(RegrasTiff.inicialGeral());

        assertThatThrownBy(() -> repository.saveAndFlush(texto)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveDarOCodigoDoElementoAfetadoDeCadaTipo() {
        assertThat(TipoElemento.PECA_ESCRITA.codigoElementoAfetado()).isEqualTo("PECAS_ESCRITAS");
        assertThat(TipoElemento.PECA_GRAFICA.codigoElementoAfetado()).isEqualTo("PECAS_GRAFICAS");
        assertThat(TipoElemento.OUTROS.codigoElementoAfetado()).isEqualTo("OUTROS");
    }
}
