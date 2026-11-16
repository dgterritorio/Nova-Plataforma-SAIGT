package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.OrigemRejeicao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicao;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItem;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoItemRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRejeicaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoValidacaoRejeicao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Regista as recusas do processo em `processo_rejeicao`, numeradas por processo.
 */
@Service
public class RejeicaoProcessoService {

    static final String SEPARADOR_MENSAGENS = "\n";
    static final String MOTIVO_INCM_EM_FALTA = "A INCM não indicou o motivo.";

    private final ProcessoRejeicaoRepository repository;
    private final ProcessoRejeicaoItemRepository itemRepository;

    public RejeicaoProcessoService(ProcessoRejeicaoRepository repository,
                                   ProcessoRejeicaoItemRepository itemRepository) {
        this.repository = repository;
        this.itemRepository = itemRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessoRejeicao registarRecusaDgt(Processo processo, Utilizador autor, String notasGerais,
                                              List<ProcessoValidacaoRejeicao> rascunho) {
        ProcessoRejeicao rejeicao = repository.save(ProcessoRejeicao.builder()
                .processo(processo)
                .sequencia(proximaSequencia(processo))
                .origem(OrigemRejeicao.DGT)
                .notasGerais(notasGerais)
                .criadoPor(autor)
                .build());
        itemRepository.saveAll(rascunho.stream()
                .map(linha -> new ProcessoRejeicaoItem(rejeicao, linha.getProcessoDocumento(),
                        linha.getProcessoInformacaoBase(), linha.getCategoria(), linha.getMotivo(), linha.getNotas()))
                .toList());
        return rejeicao;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessoRejeicao registarRecusaIncm(Processo processo, List<String> mensagens) {
        return repository.save(ProcessoRejeicao.builder()
                .processo(processo)
                .sequencia(proximaSequencia(processo))
                .origem(OrigemRejeicao.INCM)
                .motivoTexto(motivo(mensagens))
                .build());
    }

    private int proximaSequencia(Processo processo) {
        return repository.maiorSequencia(processo.getId()).orElse(0) + 1;
    }

    private static String motivo(List<String> mensagens) {
        String motivo = mensagens == null ? "" : mensagens.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(mensagem -> !mensagem.isEmpty())
                .collect(Collectors.joining(SEPARADOR_MENSAGENS));
        return motivo.isEmpty() ? MOTIVO_INCM_EM_FALTA : motivo;
    }
}
