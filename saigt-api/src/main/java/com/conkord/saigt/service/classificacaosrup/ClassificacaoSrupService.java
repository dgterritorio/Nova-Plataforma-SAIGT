package com.conkord.saigt.service.classificacaosrup;

import com.conkord.saigt.dto.classificacaosrup.ClassificacaoSrupDominioResponse;
import com.conkord.saigt.dto.classificacaosrup.ClassificacaoSrupDominioResponse.Classe;
import com.conkord.saigt.dto.classificacaosrup.ClassificacaoSrupDominioResponse.Tipologia;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catálogo público Domínio → Classe → Tipologia: sem dados de entidade, lê-o qualquer utilizador autenticado. */
@Service
public class ClassificacaoSrupService {

    private final ListaValoresRepository listaValoresRepository;

    public ClassificacaoSrupService(ListaValoresRepository listaValoresRepository) {
        this.listaValoresRepository = listaValoresRepository;
    }

    /** Só entram as Classes com pelo menos uma Tipologia ativa e os Domínios com pelo menos uma dessas Classes. */
    @Transactional(readOnly = true)
    public List<ClassificacaoSrupDominioResponse> listar() {
        List<ListaValores> ativos = listaValoresRepository.findByTipoInAndAtivoTrueOrderByOrdemAsc(List.of(
                TipoListaValores.DOMINIO_SRUP.name(), TipoListaValores.CLASSE_SRUP.name(),
                TipoListaValores.TIPOLOGIA_SRUP.name()));

        Map<Long, List<Tipologia>> tipologiasPorClasse = new LinkedHashMap<>();
        Map<Long, List<ListaValores>> classesPorDominio = new LinkedHashMap<>();
        List<ListaValores> dominios = new ArrayList<>();
        for (ListaValores valor : ativos) {
            Long paiId = valor.getPai() == null ? null : valor.getPai().getId();
            switch (TipoListaValores.valueOf(valor.getTipo())) {
                case DOMINIO_SRUP -> dominios.add(valor);
                case CLASSE_SRUP -> classesPorDominio.computeIfAbsent(paiId, id -> new ArrayList<>()).add(valor);
                default -> tipologiasPorClasse.computeIfAbsent(paiId, id -> new ArrayList<>())
                        .add(new Tipologia(valor.getId(), valor.getCodigo(), valor.getDescricao()));
            }
        }

        List<ClassificacaoSrupDominioResponse> resposta = new ArrayList<>();
        for (ListaValores dominio : dominios) {
            List<Classe> classes = classesPorDominio.getOrDefault(dominio.getId(), List.of()).stream()
                    .filter(classe -> tipologiasPorClasse.containsKey(classe.getId()))
                    .map(classe -> new Classe(classe.getId(), classe.getCodigo(), classe.getDescricao(),
                            tipologiasPorClasse.get(classe.getId())))
                    .toList();
            if (!classes.isEmpty()) {
                resposta.add(new ClassificacaoSrupDominioResponse(dominio.getId(), dominio.getCodigo(),
                        dominio.getDescricao(), classes));
            }
        }
        return resposta;
    }
}
