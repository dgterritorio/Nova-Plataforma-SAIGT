package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Concelhos da base de dados local (entidades CM com `dico`), com o nome curado da SAIGT. A NUT II e a lista de
 * concelhos do SNIT estão na réplica ({@code ReferenciaSnitConsultaService}).
 */
@Service
public class TerritorioLocalService {

    private static final Pattern PREFIXO_MUNICIPIO = Pattern.compile("^(Município|Câmara Municipal) d(e|a|o|as|os)\\s+");

    private final EntidadeRepository entidadeRepository;

    public TerritorioLocalService(EntidadeRepository entidadeRepository) {
        this.entidadeRepository = entidadeRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemTerritorio> concelhos() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-PT"));
        return entidadeRepository.findByTipoEntidadeAndDicoIsNotNullAndAtivoTrue(TipoEntidade.CM).stream()
                .map(entidade -> new ItemTerritorio(entidade.getDico(), nomeConcelho(entidade)))
                .sorted(Comparator.comparing(ItemTerritorio::nome, collator))
                .toList();
    }

    public Optional<String> nomeConcelho(String codigo) {
        return concelhos().stream().filter(concelho -> concelho.codigo().equals(codigo)).map(ItemTerritorio::nome).findFirst();
    }

    /** Pela designação SAIGT, a lista curada: a da INCM muda com o login e repete nomes (Calheta). */
    static String nomeConcelho(Entidade entidade) {
        return PREFIXO_MUNICIPIO.matcher(entidade.getDescricaoSaigt().strip()).replaceFirst("");
    }
}
