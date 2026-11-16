package com.conkord.saigt.service.incm;

import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.documento.LinkPublicoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Links INCM dos documentos de publicação, criados ou reutilizados com commit antes do `SubmitNewAct`: num reenvio
 * os URL são os mesmos. Cada planta fica com o URL do link INCM do JPEG de nome-base igual ao seu `nome_tiff`.
 */
@Service
public class LinksPublicosIncm {

    private static final Logger log = LoggerFactory.getLogger(LinksPublicosIncm.class);

    private static final Set<String> EXTENSOES_IMAGEM = Set.of("jpg", "jpeg");
    private static final List<String> EXTENSOES_RECONHECIDAS = List.of(".jpeg", ".jpg", ".tiff", ".tif");

    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final PlantaRepository plantaRepository;
    private final LinkPublicoService linkPublicoService;

    public LinksPublicosIncm(ProcessoDocumentoRepository processoDocumentoRepository,
                             PlantaRepository plantaRepository, LinkPublicoService linkPublicoService) {
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.plantaRepository = plantaRepository;
        this.linkPublicoService = linkPublicoService;
    }

    /** Os URL das imagens (`ImageURLList`) e dos restantes documentos de publicação (`FileURLList`), por ordem de criação. */
    public record LinksIncm(List<String> imagens, List<String> ficheiros) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LinksIncm preparar(Processo processo) {
        List<ProcessoDocumento> publicacao = processoDocumentoRepository
                .findByProcesso_IdOrderByCriadoEmAsc(processo.getId()).stream()
                .filter(documento -> documento.getCategoria() == CategoriaDocumento.PUBLICACAO)
                .toList();
        List<String> semLink = publicacao.stream().filter(documento -> !LinkPublicoService.permitido(documento))
                .map(ProcessoDocumento::getNomeFicheiro).toList();
        if (!semLink.isEmpty()) {
            throw new DadosIncmEmFaltaException("Há documentos de publicação sem link público permitido.",
                    List.of(new ValidacaoException.CampoErro("documentos",
                            "Sem link público permitido: " + String.join(", ", semLink) + ".")));
        }

        List<String> imagens = new ArrayList<>();
        List<String> ficheiros = new ArrayList<>();
        Map<String, String> urlDaImagemPorNome = new HashMap<>();
        for (ProcessoDocumento documento : publicacao) {
            String url = linkPublicoService.obterOuCriar(documento.getId(), DestinoLinkPublico.INCM).url();
            if (ehImagem(documento)) {
                imagens.add(url);
                urlDaImagemPorNome.putIfAbsent(nomeBase(documento.getNomeFicheiro()), url);
            } else {
                ficheiros.add(url);
            }
        }
        plantaRepository.findByProcesso_Id(processo.getId()).forEach(planta -> {
            String url = planta.getNomeTiff() == null ? null : urlDaImagemPorNome.get(nomeBase(planta.getNomeTiff()));
            if (!Objects.equals(url, planta.getLinkJpeg())) {
                planta.setLinkJpeg(url);
            }
        });
        log.info("Links INCM preparados | processoId={} imagens={} ficheiros={}", processo.getId(), imagens.size(),
                ficheiros.size());
        return new LinksIncm(List.copyOf(imagens), List.copyOf(ficheiros));
    }

    /** Sem item próprio do JPEG, a imagem de publicação reconhece-se pela extensão numa peça gráfica. */
    static boolean ehImagem(ProcessoDocumento documento) {
        DocumentoConfig item = documento.getDocumentoConfig();
        String extensao = documento.getExtensao() == null ? "" : documento.getExtensao().toLowerCase(Locale.ROOT);
        return item != null && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && EXTENSOES_IMAGEM.contains(extensao);
    }

    private static String nomeBase(String nome) {
        String minusculas = nome.toLowerCase(Locale.ROOT);
        for (String extensao : EXTENSOES_RECONHECIDAS) {
            if (minusculas.endsWith(extensao)) {
                return minusculas.substring(0, minusculas.length() - extensao.length());
            }
        }
        return minusculas;
    }
}
