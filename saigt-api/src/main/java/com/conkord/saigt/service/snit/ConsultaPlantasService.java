package com.conkord.saigt.service.snit;

import com.conkord.saigt.dto.snit.ConsultaPlantasResponse;
import com.conkord.saigt.dto.snit.ConsultaPlantasResponse.PlantaSnit;
import com.conkord.saigt.dto.snit.ConsultaPlantasResponse.ProcessoPlantas;
import com.conkord.saigt.exception.ConsultaPlantasInvalidaException;
import com.conkord.saigt.exception.ProcessoDesconhecidoException;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** `consultaPlantas` (API-EXT-INT-017): as plantas de um processo depositado, lidas de `planta`. */
@Service
public class ConsultaPlantasService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPlantasService.class);
    private static final Pattern INTEIRO = Pattern.compile("\\d{1,18}");
    private static final Comparator<String> NULOS_NO_FIM = Comparator.nullsLast(Comparator.naturalOrder());

    private final ProcessoRepository processoRepository;
    private final PlantaRepository plantaRepository;
    private final IdusProcessoRepository idusProcessoRepository;

    public ConsultaPlantasService(ProcessoRepository processoRepository, PlantaRepository plantaRepository,
                                  IdusProcessoRepository idusProcessoRepository) {
        this.processoRepository = processoRepository;
        this.plantaRepository = plantaRepository;
        this.idusProcessoRepository = idusProcessoRepository;
    }

    @Transactional(readOnly = true)
    public ConsultaPlantasResponse consultar(String numeroDeposito, String saigtId, String emVigor) {
        String deposito = emBranco(numeroDeposito);
        String processoSaigt = emBranco(saigtId);
        Boolean filtroEmVigor = lerEmVigor(emVigor);
        if (deposito == null && processoSaigt == null) {
            throw new ConsultaPlantasInvalidaException("Indique pelo menos numeroDeposito ou saigtId.",
                    "numeroDeposito", "Obrigatório quando saigtId não é indicado.");
        }
        String numeroProcesso = processoSaigt == null ? null : lerSaigtId(processoSaigt);

        Processo processo = resolver(deposito, numeroProcesso);
        String idus = idusProcessoRepository.findById(processo.getId()).map(IdusProcesso::getIdus).orElse(null);
        return new ConsultaPlantasResponse(List.of(new ProcessoPlantas(Long.parseLong(processo.getNumeroProcesso()),
                processo.getNumeroDeposito(), idus, plantas(processo, filtroEmVigor))));
    }

    /** Só processos depositados; com os dois identificadores, ambos têm de dar o mesmo processo. */
    private Processo resolver(String deposito, String numeroProcesso) {
        Optional<Processo> peloDeposito = deposito == null ? Optional.empty()
                : processoRepository.findByNumeroDeposito(deposito);
        Optional<Processo> peloSaigtId = numeroProcesso == null ? Optional.empty()
                : processoRepository.findByNumeroProcesso(numeroProcesso).filter(p -> p.getNumeroDeposito() != null);
        if (deposito != null && numeroProcesso != null) {
            if (peloDeposito.isPresent() && peloSaigtId.isPresent()
                    && peloDeposito.get().getId().equals(peloSaigtId.get().getId())) {
                return peloDeposito.get();
            }
            if (peloDeposito.isEmpty() && peloSaigtId.isEmpty()) {
                throw new ProcessoDesconhecidoException("Não existe processo com os identificadores indicados.");
            }
            throw new ConsultaPlantasInvalidaException("numeroDeposito e saigtId não correspondem ao mesmo processo.",
                    "saigtId", "Não corresponde ao processo do numeroDeposito indicado.");
        }
        if (deposito != null) {
            return peloDeposito.orElseThrow(
                    () -> new ProcessoDesconhecidoException("Não existe processo com o nº de depósito indicado."));
        }
        return peloSaigtId.orElseThrow(
                () -> new ProcessoDesconhecidoException("Não existe processo depositado com o saigtId indicado."));
    }

    /**
     * O contrato torna o `idMotuCdg` e o `nomeTIFF` obrigatórios: a folha sem Conjunto e a linha sem `nome_tiff`
     * (a linha do ZIP de publicação não é uma folha) não se devolvem.
     */
    private List<PlantaSnit> plantas(Processo processo, Boolean filtroEmVigor) {
        List<Planta> linhas = plantaRepository.findByProcesso_Id(processo.getId());
        long semConjunto = linhas.stream().filter(p -> p.getMetadadosMotu() == null).count();
        if (semConjunto > 0) {
            log.warn("consultaPlantas: {} planta(s) do processo {} sem Conjunto MOTU, não devolvidas", semConjunto,
                    processo.getNumeroProcesso());
        }
        long semTiff = linhas.stream().filter(p -> p.getMetadadosMotu() != null && semValor(p.getNomeTiff())).count();
        if (semTiff > 0) {
            log.warn("consultaPlantas: {} planta(s) do processo {} sem nomeTIFF, não devolvidas", semTiff,
                    processo.getNumeroProcesso());
        }
        return linhas.stream()
                .filter(p -> p.getMetadadosMotu() != null)
                .filter(p -> !semValor(p.getNomeTiff()))
                .filter(p -> filtroEmVigor == null || p.isEmVigor() == filtroEmVigor)
                .sorted(Comparator.comparing(Planta::getNomeRegulamento, NULOS_NO_FIM)
                        .thenComparing(Planta::getNumeroFolha, NULOS_NO_FIM)
                        .thenComparing(Planta::getNomeTiff, NULOS_NO_FIM))
                .map(ConsultaPlantasService::paraPlanta)
                .toList();
    }

    private static PlantaSnit paraPlanta(Planta planta) {
        return new PlantaSnit(planta.getMetadadosMotu().getId().toString(), planta.getNomeRegulamento(),
                planta.getNumeroFolha(), planta.getNomeTiff(), planta.getNomeZip(), planta.getLinkJpeg(),
                planta.isEmVigor() ? 1 : 0, planta.getAtualizadoEm());
    }

    private static String lerSaigtId(String valor) {
        if (!INTEIRO.matcher(valor).matches() || Long.parseLong(valor) < 1) {
            throw new ConsultaPlantasInvalidaException("Parâmetros inválidos.", "saigtId", "Valor inválido.");
        }
        return String.format("%05d", Long.parseLong(valor));
    }

    private static Boolean lerEmVigor(String valor) {
        String texto = emBranco(valor);
        if (texto == null) {
            return null;
        }
        return switch (texto) {
            case "1" -> Boolean.TRUE;
            case "0" -> Boolean.FALSE;
            default -> throw new ConsultaPlantasInvalidaException("Parâmetros inválidos.", "emVigor",
                    "Valor inválido: use 0 ou 1.");
        };
    }

    private static boolean semValor(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String emBranco(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
