package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.ListaValores;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.Instant;

/**
 * Rascunho da rejeição de um item enquanto a DGT valida o Processo. Com o Processo em `EM_VALIDACAO_DGT`, um
 * item está `REJEITADO` se e só se tem linha aqui; quem grava a decisão mantém as duas coisas juntas.
 */
@Entity
@Table(name = "processo_validacao_rejeicao")
@Check(name = "ck_processo_validacao_rejeicao_alvo",
        constraints = "(processo_documento_id IS NULL) <> (processo_informacao_base_id IS NULL)")
@Check(name = "ck_processo_validacao_rejeicao_notas", constraints = "length(trim(notas)) > 0")
@Getter
@NoArgsConstructor
public class ProcessoValidacaoRejeicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false, updatable = false)
    private Processo processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_documento_id", unique = true, updatable = false)
    private ProcessoDocumento processoDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_informacao_base_id", unique = true, updatable = false)
    private ProcessoInformacaoBase processoInformacaoBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private ListaValores categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_id")
    private ListaValores motivo;

    @Column(nullable = false, columnDefinition = "text")
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atualizado_por", nullable = false)
    private Utilizador atualizadoPor;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public static ProcessoValidacaoRejeicao doDocumento(ProcessoDocumento documento) {
        ProcessoValidacaoRejeicao rejeicao = new ProcessoValidacaoRejeicao();
        rejeicao.processo = documento.getProcesso();
        rejeicao.processoDocumento = documento;
        return rejeicao;
    }

    public static ProcessoValidacaoRejeicao daInformacaoBase(ProcessoInformacaoBase elemento) {
        ProcessoValidacaoRejeicao rejeicao = new ProcessoValidacaoRejeicao();
        rejeicao.processo = elemento.getProcesso();
        rejeicao.processoInformacaoBase = elemento;
        return rejeicao;
    }

    public void registar(ListaValores categoria, ListaValores motivo, String notas, Utilizador utilizador,
                         Instant momento) {
        this.categoria = categoria;
        this.motivo = motivo;
        this.notas = notas;
        this.atualizadoPor = utilizador;
        this.atualizadoEm = momento;
    }
}
