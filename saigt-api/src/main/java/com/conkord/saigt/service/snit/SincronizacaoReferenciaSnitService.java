package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Atualiza a réplica de uma tabela de referência do SNIT com o que o SNIT devolve: insere os códigos
 * novos, atualiza os alterados ou que voltaram e desativa os que deixaram de vir. Se a
 * leitura falhar, a réplica dessa lista fica como estava.
 */
@Service
public class SincronizacaoReferenciaSnitService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoReferenciaSnitService.class);
    private static final int TAMANHO_CODIGO = 50;
    private static final int TAMANHO_DESIGNACAO = 500;

    private final ClienteSnit clienteSnit;
    private final SnitReferenciaRepository repository;
    private final EntidadeRepository entidadeRepository;
    private final ObjectMapper objectMapper;

    public SincronizacaoReferenciaSnitService(ClienteSnit clienteSnit, SnitReferenciaRepository repository,
                                              EntidadeRepository entidadeRepository, ObjectMapper objectMapper) {
        this.clienteSnit = clienteSnit;
        this.repository = repository;
        this.entidadeRepository = entidadeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sincronizar(ListaReferenciaSnit lista) {
        List<JsonNode> registos = clienteSnit.obterTabelaReferencia(lista);
        Instant agora = Instant.now();
        Map<String, SnitReferencia> replica = repository.findByLista(lista).stream()
                .collect(Collectors.toMap(SnitReferencia::getCodigo, Function.identity()));
        Set<String> recebidos = new HashSet<>();
        int inseridos = 0;
        int atualizados = 0;
        int ignorados = 0;

        for (int posicao = 0; posicao < registos.size(); posicao++) {
            JsonNode registo = registos.get(posicao);
            String codigo = codigo(lista, registo);
            if (codigo == null) {
                log.warn("Registo do SNIT sem código válido, ignorado | lista={} posicao={}", lista, posicao);
                ignorados++;
                continue;
            }
            if (!recebidos.add(codigo)) {
                log.warn("Código repetido na resposta do SNIT, fica o primeiro | lista={} codigo={}", lista, codigo);
                ignorados++;
                continue;
            }
            String designacao = designacao(lista, registo);
            if (designacao == null) {
                log.warn("Registo do SNIT sem designação válida, ignorado | lista={} codigo={}", lista, codigo);
                ignorados++;
                continue;
            }
            SnitReferencia existente = replica.get(codigo);
            if (existente == null) {
                repository.save(new SnitReferencia(lista, codigo, designacao, registo.toString(), agora));
                inseridos++;
                continue;
            }
            if (!existente.isAtivo() || !existente.getDesignacao().equals(designacao)
                    || !mesmosDados(existente, registo)) {
                existente.setDesignacao(designacao);
                existente.setDadosOrigem(registo.toString());
                existente.setAtivo(true);
                atualizados++;
            }
            existente.setSincronizadoEm(agora);
        }

        int desativados = 0;
        for (SnitReferencia existente : replica.values()) {
            if (existente.isAtivo() && !recebidos.contains(existente.getCodigo())) {
                existente.setAtivo(false);
                desativados++;
            }
        }
        log.info("Réplica do SNIT sincronizada | lista={} recebidos={} inseridos={} atualizados={} desativados={} "
                + "ignorados={}", lista, registos.size(), inseridos, atualizados, desativados, ignorados);
    }

    @Transactional(readOnly = true)
    public void avisarDicoSemConcelho() {
        List<Entidade> entidades = entidadeRepository
                .findAllComDicoSemReferenciaAtiva(ListaReferenciaSnit.MUNICIPALITIES);
        for (Entidade entidade : entidades) {
            log.warn("Entidade com DICO sem concelho ativo na réplica do SNIT | entidade={} dico={}",
                    entidade.getCodigo(), entidade.getDico());
        }
    }

    private static String codigo(ListaReferenciaSnit lista, JsonNode registo) {
        JsonNode valor = registo.get(lista.chaveCodigo());
        return valor != null && (valor.isTextual() || valor.isIntegralNumber())
                ? limitado(valor.asText(), TAMANHO_CODIGO)
                : null;
    }

    private static String designacao(ListaReferenciaSnit lista, JsonNode registo) {
        JsonNode valor = registo.get(lista.chaveNome());
        return valor != null && valor.isTextual() ? limitado(valor.asText(), TAMANHO_DESIGNACAO) : null;
    }

    private static String limitado(String texto, int tamanhoMaximo) {
        String limpo = texto.strip();
        return limpo.isEmpty() || limpo.length() > tamanhoMaximo ? null : limpo;
    }

    private boolean mesmosDados(SnitReferencia existente, JsonNode registo) {
        try {
            return objectMapper.readTree(existente.getDadosOrigem()).equals(registo);
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
