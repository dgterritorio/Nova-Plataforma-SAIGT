package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Planta de um Processo, consultada pelo SNIT e completada no BackOffice. A SAIGT é a dona desta informação. */
@Entity
@Table(name = "planta")
@Getter
@NoArgsConstructor
public class Planta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    /** O ficheiro de onde vem a planta: o ZIP ou o TIFF carregado; um ZIP das Plantas tem uma linha por folha. */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_documento_id", nullable = false)
    private ProcessoDocumento processoDocumento;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metadados_motu_id")
    private MetadadosMotu metadadosMotu;

    /** A ordem da linha do TXT da página das Plantas a que a folha foi associada; nulo fora dessa página. */
    @Setter
    @Column(name = "linha_txt")
    private Integer linhaTxt;

    @Setter
    @Column(name = "nome_regulamento", length = 500)
    private String nomeRegulamento;

    @Setter
    @Column(name = "numero_folha", length = 20)
    private String numeroFolha;

    @Setter
    @Column(name = "nome_tiff", length = 300)
    private String nomeTiff;

    @Setter
    @Column(name = "nome_zip", length = 300)
    private String nomeZip;

    @Setter
    @Column(name = "link_jpeg", columnDefinition = "TEXT")
    private String linkJpeg;

    @Setter
    @Column(name = "em_vigor", nullable = false)
    private boolean emVigor = false;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por")
    private Utilizador atualizadoPor;

    public Planta(Processo processo, ProcessoDocumento processoDocumento) {
        this.processo = processo;
        this.processoDocumento = processoDocumento;
    }
}
