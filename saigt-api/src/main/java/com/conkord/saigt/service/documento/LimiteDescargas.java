package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Descargas em simultâneo: um limite global, partilhado por todas as descargas desta instância, e outro por token
 * de link público e por cliente do SNIT. Quem ocupa um lugar liberta-o sempre, aconteça o que acontecer ao envio.
 * Semáforos e contadores em memória, e não um Bulkhead do Resilience4j: o lugar só se liberta quando o envio acaba,
 * não quando o método devolve, e o limite é por chave.
 */
@Component
public class LimiteDescargas {

    private final Semaphore global;
    private final int porToken;
    private final int porClienteSnit;
    private final Map<UUID, Integer> emCursoPorToken = new ConcurrentHashMap<>();
    private final Map<String, Integer> emCursoPorCliente = new ConcurrentHashMap<>();

    public LimiteDescargas(DescargaProperties propriedades) {
        this.global = new Semaphore(propriedades.limiteSimultaneas());
        this.porToken = propriedades.limitePorToken();
        this.porClienteSnit = propriedades.limitePorClienteSnit();
    }

    public boolean ocupar() {
        return global.tryAcquire();
    }

    public void libertar() {
        global.release();
    }

    public boolean ocupar(UUID token) {
        return ocupar(emCursoPorToken, token, porToken);
    }

    public void libertar(UUID token) {
        libertar(emCursoPorToken, token);
    }

    public boolean ocuparCliente(String clienteId) {
        return ocupar(emCursoPorCliente, clienteId, porClienteSnit);
    }

    public void libertarCliente(String clienteId) {
        libertar(emCursoPorCliente, clienteId);
    }

    private <K> boolean ocupar(Map<K, Integer> emCursoPorChave, K chave, int maximo) {
        if (!global.tryAcquire()) {
            return false;
        }
        boolean[] ocupado = {false};
        emCursoPorChave.compute(chave, (k, emCurso) -> {
            int atual = emCurso == null ? 0 : emCurso;
            if (atual >= maximo) {
                return emCurso;
            }
            ocupado[0] = true;
            return atual + 1;
        });
        if (!ocupado[0]) {
            global.release();
        }
        return ocupado[0];
    }

    private <K> void libertar(Map<K, Integer> emCursoPorChave, K chave) {
        try {
            emCursoPorChave.computeIfPresent(chave, (k, emCurso) -> emCurso <= 1 ? null : emCurso - 1);
        } finally {
            global.release();
        }
    }
}
