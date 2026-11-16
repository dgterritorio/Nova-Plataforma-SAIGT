package com.conkord.saigt.service.login;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Única regra que identifica a entidade SAIGT de uma adesão INCM. O NIPC não é único (o mesmo NIPC serve mais de um
 * código), por isso, com mais de uma entidade, desempata pela descrição INCM. Fica isolada para poder mudar sem tocar
 * no login.
 */
@Service
public class CorrespondenciaEntidadeService {

    private final EntidadeRepository entidadeRepository;

    public CorrespondenciaEntidadeService(EntidadeRepository entidadeRepository) {
        this.entidadeRepository = entidadeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Entidade> corresponder(String nipc, String nomeIncm) {
        if (nipc == null || nipc.isBlank()) {
            return Optional.empty();
        }
        List<Entidade> entidades = entidadeRepository.findAllByNipc(nipc);
        if (entidades.size() == 1) {
            return Optional.of(entidades.get(0));
        }
        String nome = normalizar(nomeIncm);
        List<Entidade> pelaDescricao = entidades.stream()
                .filter(entidade -> nome != null && nome.equals(normalizar(entidade.getDescricaoIncm())))
                .toList();
        return pelaDescricao.size() == 1 ? Optional.of(pelaDescricao.get(0)) : Optional.empty();
    }

    private static String normalizar(String texto) {
        return texto == null ? null : texto.strip().toLowerCase(Locale.ROOT);
    }
}
