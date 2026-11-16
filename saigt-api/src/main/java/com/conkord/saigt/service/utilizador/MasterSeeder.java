package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro `MASTER` se não houver nenhum e `app.admin.email-arranque` estiver definido. O valor é o
 * username com que a pessoa se autentica no AD (o `nome` da conta `AD_DGT`); o nome da propriedade ficou o antigo.
 */
@Component
public class MasterSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterSeeder.class);
    private static final String ORIGEM_AD_DGT = "AD_DGT";

    private final UtilizadorRepository utilizadorRepository;
    private final String usernameArranque;

    public MasterSeeder(UtilizadorRepository utilizadorRepository,
                        @Value("${app.admin.email-arranque:}") String usernameArranque) {
        this.utilizadorRepository = utilizadorRepository;
        this.usernameArranque = usernameArranque == null ? null : usernameArranque.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usernameArranque == null || usernameArranque.isEmpty()) {
            return;
        }
        if (utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER) > 0) {
            return;
        }
        // Uma conta com esse username já existe: não se duplica nem se promove sem decisão de um MASTER.
        if (utilizadorRepository.findByNomeAndOrigemAutenticacao(usernameArranque, ORIGEM_AD_DGT).isPresent()) {
            log.warn("Sem MASTER e a conta do username de arranque já existe: nenhum MASTER criado.");
            return;
        }

        Utilizador master = new Utilizador(usernameArranque, null);
        master.setPerfilInterno(PerfilInterno.MASTER);
        master = utilizadorRepository.save(master);
        // Sem o username: é dado pessoal.
        log.info("Primeiro MASTER criado a partir de app.admin.email-arranque — utilizadorId={}", master.getId());
    }
}
