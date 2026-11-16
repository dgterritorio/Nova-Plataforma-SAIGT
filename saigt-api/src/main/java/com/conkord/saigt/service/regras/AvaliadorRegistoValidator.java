package com.conkord.saigt.service.regras;

import com.conkord.saigt.persistence.regras.CondicaoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code condicao.avaliador} não tem FK e {@code condicao.parametros} não tem schema: um valor inválido falha
 * o arranque, não a avaliação. A gravação em backoffice faz as mesmas verificações, com o mesmo
 * {@link RegistoAvaliadores}, para que uma regra gravada pela API nunca impeça o arranque.
 */
@Component
public class AvaliadorRegistoValidator implements ApplicationRunner {

    private final CondicaoRepository condicaoRepository;
    private final RegistoAvaliadores registoAvaliadores;

    public AvaliadorRegistoValidator(CondicaoRepository condicaoRepository, RegistoAvaliadores registoAvaliadores) {
        this.condicaoRepository = condicaoRepository;
        this.registoAvaliadores = registoAvaliadores;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> semImplementacao = condicaoRepository.findAvaliadoresDistintos().stream()
                .filter(avaliador -> !registoAvaliadores.codigos().contains(avaliador))
                .toList();
        if (!semImplementacao.isEmpty()) {
            throw new IllegalStateException(
                    "condicao.avaliador sem implementação: " + semImplementacao);
        }

        // Todas de uma vez: corrigir um seed à primeira falha de cada arranque não escala.
        List<String> invalidas = condicaoRepository.findByAtivoTrue().stream()
                .flatMap(condicao -> registoAvaliadores.porCodigo(condicao.getAvaliador()).orElseThrow()
                        .validarParametros(condicao.getParametros()).stream()
                        .map(motivo -> "condicao.id=" + condicao.getId() + " (" + condicao.getAvaliador() + "): " + motivo))
                .toList();
        if (!invalidas.isEmpty()) {
            throw new IllegalStateException(
                    "condicao.parametros inválidos:\n" + String.join("\n", invalidas));
        }
    }
}
