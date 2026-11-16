package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.DocumentosObrigatoriosEmFaltaException;
import com.conkord.saigt.exception.PlantasMetadadosEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Itens obrigatórios do Procedimento ainda sem ficheiro. Só a estrutura documental decide o que é obrigatório
 * (incluindo o filtro por elementos afetados), para o ecrã e a submissão nunca divergirem. Sem GeoConform, também a
 * associação das plantas e a ficha de metadados.
 */
@Service
public class CompletudeDocumentalService {

    private static final String PREFIXO_CAMPO = "documentos.";

    private final EstruturaDocumentalService estruturaDocumentalService;
    private final PlantasService plantasService;
    private final MetadadosService metadadosService;
    private final MetadadosMotuService metadadosMotuService;

    public CompletudeDocumentalService(EstruturaDocumentalService estruturaDocumentalService, PlantasService plantasService,
                                       MetadadosService metadadosService, MetadadosMotuService metadadosMotuService) {
        this.metadadosMotuService = metadadosMotuService;
        this.estruturaDocumentalService = estruturaDocumentalService;
        this.plantasService = plantasService;
        this.metadadosService = metadadosService;
    }

    @Transactional(readOnly = true)
    public List<Item> pendencias(Processo processo) {
        return estruturaDocumentalService.montar(processo).pendencias();
    }

    /** Os itens obrigatórios sem ficheiro, com o caminho e a mensagem de {@code campos[]}. */
    @Transactional(readOnly = true)
    public List<CampoErro> camposEmFalta(Processo processo) {
        return pendencias(processo).stream().map(CompletudeDocumentalService::campo).toList();
    }

    /** Linhas das plantas sem TIFF, cartograma em falta e campos obrigatórios da ficha única de metadados;
     * onde se aplica a ficha MOTU, a Série e os Conjuntos não opcionais por completar (e não a ficha única). */
    @Transactional(readOnly = true)
    public List<CampoErro> pendenciasPlantasEMetadados(Processo processo) {
        List<CampoErro> pendencias = new ArrayList<>(pendenciasPlantas(processo));
        pendencias.addAll(pendenciasMetadados(processo));
        return pendencias;
    }

    @Transactional(readOnly = true)
    public List<CampoErro> pendenciasPlantas(Processo processo) {
        return plantasService.pendencias(processo);
    }

    @Transactional(readOnly = true)
    public List<CampoErro> pendenciasMetadados(Processo processo) {
        return exigeFichaMotu(processo) ? metadadosMotuService.pendencias(processo) : metadadosService.pendencias(processo);
    }

    /** Sem peça gráfica de publicação aplicável (p. ex. só «Peças Escritas») não há ficha MOTU a exigir. */
    private boolean exigeFichaMotu(Processo processo) {
        return metadadosMotuService.aplicaFichaMotu(processo)
                && estruturaDocumentalService.temPecaGraficaDePublicacaoAplicavel(processo);
    }

    @Transactional(readOnly = true)
    public boolean exigePlantasEMetadados(Processo processo) {
        return estruturaDocumentalService.exigePlantas(processo);
    }

    @Transactional(readOnly = true)
    public void verificar(Processo processo) {
        List<CampoErro> campos = camposEmFalta(processo);
        if (!campos.isEmpty()) {
            throw new DocumentosObrigatoriosEmFaltaException("Faltam documentos obrigatórios para a submissão.", campos);
        }
        List<CampoErro> plantasEMetadados = pendenciasPlantasEMetadados(processo);
        if (!plantasEMetadados.isEmpty()) {
            throw new PlantasMetadadosEmFaltaException("Faltam as plantas ou a ficha de metadados para a submissão.",
                    plantasEMetadados);
        }
    }

    private static CampoErro campo(Item item) {
        DocumentoConfig config = item.config();
        return new CampoErro(PREFIXO_CAMPO + config.getCodigo(),
                "Falta carregar: " + config.getNome() + " (" + config.getCategoria().name() + ").");
    }
}
