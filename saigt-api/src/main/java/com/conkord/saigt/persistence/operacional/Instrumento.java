package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.core.BaseEntity;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Não tem entidade: cada processo pertence à entidade que o criou. `numero` é a chave de negócio, lida de sequência dedicada e nunca reutilizada; a migração do legado
 * insere valores explícitos. `designacao` é o nome do IGT e pode repetir-se.
 */
@Entity
@Table(name = "instrumento")
@Getter
@NoArgsConstructor
public class Instrumento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_instrumento_id", nullable = false)
    private TipoInstrumento tipoInstrumento;

    // Lido de seq_instrumento_numero_sequencial pelo serviço que cria o Instrumento.
    @Column(nullable = false, updatable = false, length = 30)
    private String numero;

    @Column(nullable = false, length = 255)
    private String designacao;

    // N.º do IGT no SNIT, só nos IGT que já lá existem.
    @Setter
    @Column(name = "id_igt_snit", length = 50)
    private String idIgtSnit;

    // `TIPOLOGIA_SRUP` ativa; só nas servidões (categoria SRUP).
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipologia_srup_id")
    private ListaValores tipologiaSrup;

    // Informativo, sem função de autorização.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Utilizador responsavel;

    // Publicado e não revogado (TR-119); mantido por InstrumentoService quando uma Dinâmica termina.
    @Setter
    @Column(name = "em_vigor", nullable = false)
    private boolean emVigor = false;

    // NATIVO | ESPELHO_ABERTO | ESPELHO_FECHADO.
    @Setter
    @Column(name = "origem_dados", nullable = false, length = 20)
    private String origemDados = "NATIVO";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Utilizador criadoPor;

    // Servidão de origem ("mãe"), só nas servidões; fixada na criação. A categoria valida-a o serviço.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumento_origem_id", updatable = false)
    private Instrumento instrumentoOrigem;

    public Instrumento(TipoInstrumento tipoInstrumento, String numero, String designacao,
                        Utilizador responsavel, Utilizador criadoPor) {
        this(tipoInstrumento, numero, designacao, responsavel, criadoPor, null);
    }

    public Instrumento(TipoInstrumento tipoInstrumento, String numero, String designacao,
                        Utilizador responsavel, Utilizador criadoPor, Instrumento instrumentoOrigem) {
        this.tipoInstrumento = tipoInstrumento;
        this.numero = numero;
        this.designacao = designacao;
        this.responsavel = responsavel;
        this.criadoPor = criadoPor;
        this.instrumentoOrigem = instrumentoOrigem;
    }
}
