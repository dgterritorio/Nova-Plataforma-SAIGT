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


@Entity
@Table(name = "processo_rejeicao_item")
@Check(name = "ck_processo_rejeicao_item_alvo",
        constraints = "(processo_documento_id IS NULL) <> (processo_informacao_base_id IS NULL)")
@Check(name = "ck_processo_rejeicao_item_notas", constraints = "length(trim(notas)) > 0")
@Getter
@NoArgsConstructor
public class ProcessoRejeicaoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rejeicao_id", nullable = false, updatable = false)
    private ProcessoRejeicao rejeicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_documento_id", updatable = false)
    private ProcessoDocumento processoDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_informacao_base_id", updatable = false)
    private ProcessoInformacaoBase processoInformacaoBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", updatable = false)
    private ListaValores categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_id", updatable = false)
    private ListaValores motivo;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String notas;

    public ProcessoRejeicaoItem(ProcessoRejeicao rejeicao, ProcessoDocumento processoDocumento,
                                ProcessoInformacaoBase processoInformacaoBase, ListaValores categoria,
                                ListaValores motivo, String notas) {
        this.rejeicao = rejeicao;
        this.processoDocumento = processoDocumento;
        this.processoInformacaoBase = processoInformacaoBase;
        this.categoria = categoria;
        this.motivo = motivo;
        this.notas = notas;
    }
}
