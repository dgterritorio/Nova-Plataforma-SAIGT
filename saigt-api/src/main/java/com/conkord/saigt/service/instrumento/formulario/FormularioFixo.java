package com.conkord.saigt.service.instrumento.formulario;

public enum FormularioFixo {
    INFORMACAO_GERAL(false, true),
    /** Os campos e as validações do `INFORMACAO_GERAL`, mais a data da deliberação dentro da data limite da Dinâmica. */
    DISCUSSAO_PUBLICA(false, true),
    PUBLICACAO_ALTERACAO(false, true),
    /** A Informação Geral sem elementos afetados, mais as redes de defesa do programa e o texto do ato sempre integral. */
    SGIFR(false, false),
    /** Restantes servidões: o cabeçalho comum do `INFORMACAO_GERAL`, sem elementos afetados nem campos próprios. */
    SRUP(true, false),
    /** Ratificação pelo Governo: o cabeçalho comum, sem elementos afetados, mais a modalidade (Publicação ou Alteração). */
    RATIFICACAO(false, false),
    /**
     * Suspensão da iniciativa da CCDR (2.ª série, parte C): o cabeçalho comum, sem elementos afetados, com a entidade
     * emitente escolhida da lista das CCDR e a data de assinatura no próprio formulário.
     */
    SUSPENSAO_CCDR(false, false),
    /**
     * Publicação da Suspensão ou das Medidas Preventivas da iniciativa do Governo (1.ª série): o cabeçalho comum, sem
     * elementos afetados, com todos os campos obrigatórios, a «Publicação» (Diário ou Suplemento) e a data de
     * assinatura no próprio formulário.
     */
    SUSPENSAO_MP_GOVERNO(false, false),
    /**
     * Prorrogação da Suspensão ou das Medidas Preventivas do Governo: os campos do {@link #SUSPENSAO_MP_GOVERNO} sem a
     * «Publicação», que não se recebe e se grava como Diário.
     */
    PRORROGACAO_GOVERNO(false, false),
    /**
     * REN: o cabeçalho comum sem elementos afetados, mais o motivo do procedimento, o diploma corrigido, a região e o
     * município; admite processos só para depósito (EF v1.5).
     */
    REN(true, false);

    private final boolean admiteSoDeposito;
    private final boolean perguntaElementosAfetados;

    FormularioFixo(boolean admiteSoDeposito, boolean perguntaElementosAfetados) {
        this.admiteSoDeposito = admiteSoDeposito;
        this.perguntaElementosAfetados = perguntaElementosAfetados;
    }

    /**
     * Se o processo pode ser só para depósito (BL-PROC-064). A regra é do formulário, escolhido pelo tipo de
     * Procedimento, e nunca do tipo de Instrumento: só os formulários da REN e das restantes servidões a admitem.
     */
    public boolean admiteSoDeposito() {
        return admiteSoDeposito;
    }

    /**
     * Um formulário sem a pergunta dos elementos fundamentais afetados não os exige nem filtra por eles os itens
     * documentais, qualquer que seja a categoria do tipo de Instrumento.
     */
    public boolean perguntaElementosAfetados() {
        return perguntaElementosAfetados;
    }
}
