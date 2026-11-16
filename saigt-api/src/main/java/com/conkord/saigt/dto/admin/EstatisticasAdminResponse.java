package com.conkord.saigt.dto.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * `emAnalise` conta `EM_VALIDACAO_DGT`; `publicadosComSucesso` conta `ENVIADO_INCM` + `PUBLICADO`;
 * `comPrazoExpirado` conta Dinâmicas `EM_CURSO` com `data_caducidade` já passada.
 */
public record EstatisticasAdminResponse(
        long totalProcessos,
        long emAnalise,
        long publicadosComSucesso,
        long comPrazoExpirado,
        List<SubmissaoMensal> submissoesPorMes,
        List<EstadoContagem> distribuicaoPorEstado,
        List<AtividadeRecente> atividadeRecente,
        List<PublicadosPorEntidade> publicadosPorEntidade
) {
    public record SubmissaoMensal(LocalDate mes, long total) {
    }

    public record EstadoContagem(String estado, long total) {
    }

    /**
     * Mesmos estados de sucesso de {@link #publicadosComSucesso}. `total` soma todos os tipos de
     * instrumento, não só as 4 colunas do mockup. `caducados`/`ultimaPublicacao` a zero/`null` sem linhas no grupo.
     */
    public record PublicadosPorEntidade(String entidade, long pdm, long pp, long pu, long prot, long total,
                                         long caducados, LocalDate ultimaPublicacao) {
    }

    /**
     * Colunas de `auditoria` para o feed, nunca `dadosAlteracao`/`ipOrigem`. `utilizadorNome` resolvido em lote:
     * `nome` do ator, ou `email` antes do primeiro login; nunca vazio (`utilizador_id` é NOT NULL com FK).
     */
    public record AtividadeRecente(String tipoOcorrencia, String idOcorrencia, String acao, UUID utilizadorId,
                                    String utilizadorNome, Instant criadoEm) {
    }
}
