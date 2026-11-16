package com.conkord.saigt.service.regras;

import com.conkord.saigt.service.regras.avaliador.Avaliador;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Os avaliadores implementados, por {@code codigo()}: um só mapa para o motor, a validação de arranque e a gravação
 * em backoffice, para que os três aceitem exatamente os mesmos códigos. Dois avaliadores com o mesmo código falham o
 * arranque.
 */
@Component
public class RegistoAvaliadores {

    private final Map<String, Avaliador> porCodigo;

    public RegistoAvaliadores(List<Avaliador> avaliadores) {
        this.porCodigo = avaliadores.stream().collect(Collectors.toUnmodifiableMap(Avaliador::codigo, Function.identity()));
    }

    public Optional<Avaliador> porCodigo(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(porCodigo.get(codigo));
    }

    public Set<String> codigos() {
        return porCodigo.keySet();
    }
}
