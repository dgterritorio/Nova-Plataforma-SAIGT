package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;

import java.util.List;

/** Itens documentais do Procedimento com os ficheiros já carregados, sem forma de resposta HTTP. */
public record EstruturaDocumental(List<Separador> separadores, List<ProcessoDocumento> outros, List<Item> pendencias) {

    public record Separador(String nome, List<Item> publicacao, List<Item> deposito) {
    }

    /** `observacoes` nulas quando o item não as tem; com observações, um item obrigatório sem ficheiro fica completo (BL-DOC-003). */
    public record Item(DocumentoConfig config, List<ProcessoDocumento> ficheiros, String observacoes) {

        public Item(DocumentoConfig config, List<ProcessoDocumento> ficheiros) {
            this(config, ficheiros, null);
        }

        public boolean pendente() {
            return config.isObrigatorio() && ficheiros.isEmpty() && observacoes == null;
        }
    }
}
