package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.db.PostgresTestcontainersBase;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Os métodos novos dos repositórios que sustentam a manutenção dos itens documentais, na base real. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentoConfigManutencaoRepositoryTest extends PostgresTestcontainersBase {

    @Autowired
    private DocumentoConfigRepository documentoConfigRepository;
    @Autowired
    private ProcessoDocumentoRepository processoDocumentoRepository;
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

    private static String codigoUnico() {
        return "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private InstrumentoProcedimentoConfig novoProcedimento() {
        String codigo = codigoUnico();
        TipoInstrumento tipoInstrumento = tipoInstrumentoRepository.saveAndFlush(
                new TipoInstrumento(codigo.substring(0, 10), "Instrumento", "PLANO"));
        TipoDinamica tipoDinamica = tipoDinamicaRepository.saveAndFlush(new TipoDinamica(codigo, "Dinâmica"));
        InstrumentoDinamicaConfig dinamica = dinamicaConfigRepository.saveAndFlush(
                new InstrumentoDinamicaConfig(tipoInstrumento, tipoDinamica));
        TipoProcedimento tipoProcedimento = tipoProcedimentoRepository.saveAndFlush(
                new TipoProcedimento(codigo, "Procedimento"));
        return procedimentoConfigRepository.saveAndFlush(
                new InstrumentoProcedimentoConfig(dinamica, tipoProcedimento, 1, "SEGUNDA_H"));
    }

    private DocumentoConfig novoItem(InstrumentoProcedimentoConfig procedimento, String codigo, String separador,
                                     int ordem, boolean ativo) {
        DocumentoConfig item = new DocumentoConfig(procedimento, codigo, "Item " + codigo, separador,
                CategoriaDocumento.PUBLICACAO, TipoElemento.PECA_ESCRITA, FormatoDocumento.EDITAVEL, "[\"pdf\"]", ordem);
        item.setAtivo(ativo);
        return documentoConfigRepository.saveAndFlush(item);
    }

    @Test
    void existeCodigoDeveDistinguirProcedimentos() {
        InstrumentoProcedimentoConfig a = novoProcedimento();
        InstrumentoProcedimentoConfig b = novoProcedimento();
        novoItem(a, "DELIBERACAO_PUB", "Deliberação", 1, true);

        assertThat(documentoConfigRepository.existsByInstrumentoProcedimentoConfig_IdAndCodigo(a.getId(),
                "DELIBERACAO_PUB")).isTrue();
        assertThat(documentoConfigRepository.existsByInstrumentoProcedimentoConfig_IdAndCodigo(b.getId(),
                "DELIBERACAO_PUB")).isFalse();
        assertThat(documentoConfigRepository.existsByInstrumentoProcedimentoConfig_IdAndCodigo(a.getId(),
                "OUTRO")).isFalse();
    }

    @Test
    void listarDeveFiltrarPorProcedimentoEAtivoEOrdenarPorOrdem() {
        InstrumentoProcedimentoConfig a = novoProcedimento();
        InstrumentoProcedimentoConfig b = novoProcedimento();
        novoItem(a, "SEGUNDO", "S", 2, true);
        novoItem(a, "PRIMEIRO", "S", 1, true);
        novoItem(a, "INATIVO", "S", 3, false);
        novoItem(b, "DE_OUTRO", "S", 1, true);
        PageRequest pagina = PageRequest.of(0, 20, Sort.by("ordem", "id"));

        Page<DocumentoConfig> todos = documentoConfigRepository.findByInstrumentoProcedimentoConfig_Id(a.getId(), pagina);
        Page<DocumentoConfig> ativos = documentoConfigRepository
                .findByInstrumentoProcedimentoConfig_IdAndAtivo(a.getId(), true, pagina);
        Page<DocumentoConfig> inativos = documentoConfigRepository
                .findByInstrumentoProcedimentoConfig_IdAndAtivo(a.getId(), false, pagina);

        assertThat(todos).extracting(DocumentoConfig::getCodigo).containsExactly("PRIMEIRO", "SEGUNDO", "INATIVO");
        assertThat(ativos).extracting(DocumentoConfig::getCodigo).containsExactly("PRIMEIRO", "SEGUNDO");
        assertThat(inativos).extracting(DocumentoConfig::getCodigo).containsExactly("INATIVO");
    }

    @Test
    void itensAtivosDoSeparadorDeveIgnorarInativosEOutrosSeparadores() {
        InstrumentoProcedimentoConfig a = novoProcedimento();
        novoItem(a, "DELIBERACAO_PUB", "Deliberação", 1, true);
        novoItem(a, "DELIBERACAO_DEP", "Deliberação", 2, false);
        novoItem(a, "REGULAMENTO_PUB", "Regulamento", 3, true);

        assertThat(documentoConfigRepository.findByInstrumentoProcedimentoConfig_IdAndSeparadorAndAtivoTrue(a.getId(),
                "Deliberação")).extracting(DocumentoConfig::getCodigo).containsExactly("DELIBERACAO_PUB");
    }

    @Test
    void itemSemFicheirosNaoEstaEmUso() {
        DocumentoConfig item = novoItem(novoProcedimento(), "DELIBERACAO_PUB", "Deliberação", 1, true);

        assertThat(processoDocumentoRepository.existsByDocumentoConfig_Id(item.getId())).isFalse();
    }
}
