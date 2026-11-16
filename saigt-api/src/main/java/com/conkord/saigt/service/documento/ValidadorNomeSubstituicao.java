package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.NomeSubstituicaoDiferenteException;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Uma planta recusada só se substitui por um ficheiro com o mesmo nome, e um ZIP com as mesmas folhas, para a tabela
 * das plantas continuar preenchida. Sem planta, o nome é livre.
 */
@Component
public class ValidadorNomeSubstituicao {

    private final PlantaRepository plantaRepository;

    public ValidadorNomeSubstituicao(PlantaRepository plantaRepository) {
        this.plantaRepository = plantaRepository;
    }

    public void validarNome(ProcessoDocumento recusado, String nomeNovo) {
        if (plantaRepository.findByProcessoDocumento_Id(recusado.getId()).isEmpty()) {
            return;
        }
        if (!recusado.getNomeFicheiro().equals(nomeNovo)) {
            throw new NomeSubstituicaoDiferenteException("A planta de substituição tem de ter exatamente o mesmo nome "
                    + "do ficheiro recusado: " + recusado.getNomeFicheiro() + ".");
        }
    }

    /** Os TIFF do ZIP novo, como os devolve {@link LeitorPlantas#tiffsDoZip}. */
    public void validarFolhas(ProcessoDocumento recusado, Collection<String> tiffsDoZipNovo) {
        Set<String> presentes = new HashSet<>(tiffsDoZipNovo);
        Set<String> emFalta = new TreeSet<>();
        List<Planta> plantas = plantaRepository.findByProcessoDocumento_Id(recusado.getId());
        plantas.stream().map(Planta::getNomeTiff).filter(Objects::nonNull)
                .filter(nome -> !presentes.contains(nome)).forEach(emFalta::add);
        if (!emFalta.isEmpty()) {
            throw new NomeSubstituicaoDiferenteException("As folhas de substituição têm de ter exatamente os mesmos "
                    + "nomes. Faltam: " + String.join(", ", emFalta) + ".");
        }
    }
}
