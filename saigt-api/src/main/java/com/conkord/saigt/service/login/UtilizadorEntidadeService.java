package com.conkord.saigt.service.login;

import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstrói `utilizador_entidade` a partir da lista devolvida pelo `ValidateUser`. Bean à parte de
 * {@link PreAutenticacaoIncmService} para ter transação própria: tornar o login transacional desfaria os
 * `LOGIN_RECUSADO` auditados antes de cada recusa.
 */
@Service
public class UtilizadorEntidadeService {

    private final UtilizadorEntidadeRepository repository;

    public UtilizadorEntidadeService(UtilizadorEntidadeRepository repository) {
        this.repository = repository;
    }

    /**
     * Insere os vínculos em falta, atualiza `sincronizadoEm` nos que se mantêm e apaga os que já não vêm.
     * Entidades repetidas (reconciliação por nome de duas adesões para a mesma linha) contam uma vez.
     */
    @Transactional
    public void sincronizar(Utilizador utilizador, List<Entidade> entidades) {
        Instant agora = Instant.now();
        Map<Long, Entidade> porId = new LinkedHashMap<>();
        entidades.forEach(entidade -> porId.putIfAbsent(entidade.getId(), entidade));

        List<UtilizadorEntidade> existentes = repository.findByIdUtilizadorId(utilizador.getId());
        for (UtilizadorEntidade vinculo : existentes) {
            if (porId.remove(vinculo.getId().getEntidadeId()) != null) {
                vinculo.setSincronizadoEm(agora);
            } else {
                repository.delete(vinculo);
            }
        }
        porId.values().forEach(entidade -> repository.save(new UtilizadorEntidade(utilizador, entidade, agora)));
    }
}
