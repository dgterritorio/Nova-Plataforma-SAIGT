package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PlantaService {

    private static final Logger log = LoggerFactory.getLogger(PlantaService.class);
    private static final String EXTENSAO_ZIP = "zip";
    private static final Set<String> EXTENSOES_TIFF = Set.of("tif", "tiff");

    private final PlantaRepository plantaRepository;

    public PlantaService(PlantaRepository plantaRepository) {
        this.plantaRepository = plantaRepository;
    }

    /** Uma folha de um ZIP da página das Plantas: a linha do TXT associada ao seu TIFF. */
    public record LinhaDaPagina(ProcessoDocumento zip, int ordem, String nomeRegulamento, String numeroFolha,
                                String nomeTiff) {
    }

    /** As linhas dos ZIP da página das Plantas nascem da associação, não do carregamento. */
    public void registarPecaGrafica(ProcessoDocumento documento) {
        if (!isPlanta(documento) || naPaginaDasPlantas(documento)) {
            return;
        }
        Planta planta = new Planta(documento.getProcesso(), documento);
        preencherNome(planta, documento);
        plantaRepository.save(planta);
        log.info("Planta registada — plantaId={} processoDocumentoId={}", planta.getId(), documento.getId());
    }

    public void atualizarAposSubstituicao(ProcessoDocumento documento) {
        if (naPaginaDasPlantas(documento) && ehTxtDosNomes(documento)) {
            apagarDaPagina(documento.getProcesso().getId());
            return;
        }
        if (naPaginaDasPlantas(documento)) {
            // O ZIP mudou: as folhas associadas saem e as linhas do TXT voltam a ficar por associar.
            plantaRepository.deleteByProcessoDocumento_Id(documento.getId());
            log.info("Plantas do ZIP removidas na substituição — processoDocumentoId={}", documento.getId());
            return;
        }
        List<Planta> existentes = plantaRepository.findByProcessoDocumento_Id(documento.getId());
        if (!isPlanta(documento)) {
            existentes.forEach(planta -> {
                plantaRepository.delete(planta);
                log.info("Planta removida, o ficheiro substituto não é planta — plantaId={} processoDocumentoId={}",
                        planta.getId(), documento.getId());
            });
            return;
        }
        if (existentes.isEmpty()) {
            registarPecaGrafica(documento);
        } else {
            existentes.forEach(planta -> preencherNome(planta, documento));
        }
    }

    /** O ficheiro de substituição tem o nome do recusado (ver {@link ValidadorNomeSubstituicao}): as linhas só mudam de documento. */
    public void substituirRecusada(ProcessoDocumento recusado, ProcessoDocumento novo) {
        List<Planta> plantas = plantaRepository.findByProcessoDocumento_Id(recusado.getId());
        plantas.forEach(planta -> {
            planta.setProcessoDocumento(novo);
            if (EXTENSAO_ZIP.equals(novo.getExtensao())) {
                planta.setNomeZip(novo.getNomeFicheiro());
            }
        });
        plantaRepository.saveAll(plantas);
        log.info("Plantas passadas ao ficheiro de substituição — recusadoId={} novoId={} linhas={}", recusado.getId(),
                novo.getId(), plantas.size());
    }

    /** Sem o TXT as folhas da página perdem a origem: saem, e as linhas voltam a ficar por associar. */
    public void removerDoDocumento(ProcessoDocumento documento) {
        if (naPaginaDasPlantas(documento) && ehTxtDosNomes(documento)) {
            apagarDaPagina(documento.getProcesso().getId());
            return;
        }
        removerDoDocumento(documento.getId());
    }

    public void removerDoDocumento(UUID processoDocumentoId) {
        plantaRepository.deleteByProcessoDocumento_Id(processoDocumentoId);
    }

    public void removerDoProcesso(UUID processoId) {
        plantaRepository.deleteByProcesso_Id(processoId);
    }

    /**
     * Grava as linhas dos ZIP da página das Plantas, uma por associação. A linha cujo TXT continua associado ao mesmo
     * TIFF no mesmo ZIP fica como está, com as correções e a ligação ao Conjunto; as novas nascem do TXT e as que
     * deixaram de ter associação saem. As das outras peças gráficas ficam.
     */
    public void substituirDaPaginaDasPlantas(Processo processo, List<LinhaDaPagina> linhas) {
        List<Planta> existentes = new ArrayList<>(plantaRepository.findByProcesso_Id(processo.getId()).stream()
                .filter(planta -> naPaginaDasPlantas(planta.getProcessoDocumento())).toList());
        List<Planta> gravadas = new ArrayList<>();
        for (LinhaDaPagina linha : linhas) {
            Planta mantida = existentes.stream().filter(planta -> mesmaAssociacao(planta, linha)).findFirst().orElse(null);
            if (mantida != null) {
                existentes.remove(mantida);
                mantida.setLinhaTxt(linha.ordem());
                gravadas.add(mantida);
                continue;
            }
            Planta planta = new Planta(processo, linha.zip());
            planta.setLinhaTxt(linha.ordem());
            planta.setNomeRegulamento(linha.nomeRegulamento());
            planta.setNumeroFolha(linha.numeroFolha());
            planta.setNomeTiff(linha.nomeTiff());
            planta.setNomeZip(linha.zip().getNomeFicheiro());
            gravadas.add(planta);
        }
        plantaRepository.deleteAll(existentes);
        plantaRepository.saveAll(gravadas);
        plantaRepository.flush();
        log.info("Plantas da página das Plantas gravadas — processoId={} linhas={} removidas={}", processo.getId(),
                gravadas.size(), existentes.size());
    }

    /** Linhas gravadas antes da `linha_txt` (nula) reconhecem-se pelo nome e pela folha do TXT, como antes. */
    private static boolean mesmaAssociacao(Planta planta, LinhaDaPagina linha) {
        boolean mesmaLinha = planta.getLinhaTxt() != null
                ? planta.getLinhaTxt() == linha.ordem()
                : Objects.equals(planta.getNomeRegulamento(), linha.nomeRegulamento())
                        && Objects.equals(planta.getNumeroFolha(), linha.numeroFolha());
        return mesmaLinha && planta.getProcessoDocumento().getId().equals(linha.zip().getId())
                && Objects.equals(planta.getNomeTiff(), linha.nomeTiff());
    }

    private void apagarDaPagina(UUID processoId) {
        plantaRepository.apagarDaPagina(processoId, PaginaDocumento.PLANTAS);
        log.info("Plantas da página das Plantas removidas, o TXT dos nomes mudou — processoId={}", processoId);
    }

    private static boolean ehTxtDosNomes(ProcessoDocumento documento) {
        return PlantasService.CODIGO_TXT.equals(documento.getDocumentoConfig().getCodigo());
    }

    private static boolean naPaginaDasPlantas(ProcessoDocumento documento) {
        DocumentoConfig item = documento.getDocumentoConfig();
        return item != null && item.getPagina() == PaginaDocumento.PLANTAS;
    }

    static boolean isPlanta(ProcessoDocumento documento) {
        return isPlanta(documento.getDocumentoConfig(), documento.getExtensao());
    }

    private static boolean isPlanta(DocumentoConfig item, String extensao) {
        return item != null && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && (EXTENSAO_ZIP.equals(extensao) || EXTENSOES_TIFF.contains(extensao));
    }

    /** Linha que nasce do carregamento: peça gráfica fora da página das Plantas. */
    private static boolean temLinhaPropria(DocumentoConfig item, String extensao) {
        return isPlanta(item, extensao) && item.getPagina() != PaginaDocumento.PLANTAS;
    }

    /**
     * Reclassificação para outro item, chamada antes de o documento mudar: as linhas saem quando deixa de ser peça
     * gráfica ou sai da página das Plantas (cujas linhas nascem da associação). Entre duas peças gráficas a linha
     * mantém-se com as correções feitas no backoffice.
     */
    public boolean perdeLinhasAoReclassificar(ProcessoDocumento documento, DocumentoConfig destino) {
        String extensao = documento.getExtensao();
        return naPaginaDasPlantas(documento)
                || temLinhaPropria(documento.getDocumentoConfig(), extensao) && !temLinhaPropria(destino, extensao);
    }

    public boolean ganhaLinhaAoReclassificar(ProcessoDocumento documento, DocumentoConfig destino) {
        String extensao = documento.getExtensao();
        return temLinhaPropria(destino, extensao) && !temLinhaPropria(documento.getDocumentoConfig(), extensao);
    }

    private static void preencherNome(Planta planta, ProcessoDocumento documento) {
        boolean zip = EXTENSAO_ZIP.equals(documento.getExtensao());
        planta.setNomeZip(zip ? documento.getNomeFicheiro() : null);
        planta.setNomeTiff(zip ? null : documento.getNomeFicheiro());
    }
}
