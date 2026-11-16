package com.conkord.saigt.persistence.configuracao;

import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documento_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_documento_config_codigo",
                columnNames = {"instrumento_procedimento_config_id", "codigo"}))
@Check(constraints = "categoria IN ('PUBLICACAO', 'DEPOSITO') "
        + "AND tipo_elemento IN ('PECA_ESCRITA', 'PECA_GRAFICA', 'OUTROS') "
        + "AND formato IN ('EDITAVEL', 'NAO_EDITAVEL') "
        + "AND (elemento_informacao_base IS NULL OR elemento_informacao_base IN ('DELIBERACAO', 'TEXTO_ATO')) "
        + "AND (tamanho_maximo_ficheiro IS NULL OR tamanho_maximo_ficheiro > 0) "
        + "AND (tamanho_maximo_total IS NULL OR tamanho_maximo_total > 0) "
        + "AND (numero_maximo_ficheiros IS NULL OR numero_maximo_ficheiros > 0) "
        + "AND (regras_tiff IS NULL OR tipo_elemento = 'PECA_GRAFICA') "
        + "AND regra_nomenclatura IN ('GERAL', 'SGIFR')")
@Getter
@NoArgsConstructor
public class DocumentoConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_procedimento_config_id", nullable = false)
    private InstrumentoProcedimentoConfig instrumentoProcedimentoConfig;

    // Identifica o item dentro do Procedimento.
    @Column(nullable = false, length = 50)
    private String codigo;

    @Setter
    @Column(nullable = false, length = 100)
    private String nome;

    @Setter
    @Column(nullable = false, length = 100)
    private String separador;

    @Setter
    @Column(length = 300)
    private String descricao;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDocumento categoria;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_elemento", nullable = false, length = 20)
    private TipoElemento tipoElemento;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormatoDocumento formato;

    @Setter
    @Column(nullable = false)
    private boolean obrigatorio = false;

    // Ex: ["pdf","docx"]. Em PUBLICACAO, limitado ao que a INCM aceita em FileURLList.
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extensoes_aceites", nullable = false, columnDefinition = "jsonb")
    private String extensoesAceites;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "elemento_informacao_base", length = 30)
    private ElementoInformacaoBase elementoInformacaoBase;

    @Setter
    @Column(name = "link_publico_permitido", nullable = false)
    private boolean linkPublicoPermitido = false;

    // PLANTAS: item da página das Plantas (sem GeoConform), fora da lista de documentos.
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaginaDocumento pagina = PaginaDocumento.DOCUMENTOS;

    @Setter
    @Column(name = "tamanho_maximo_ficheiro")
    private Long tamanhoMaximoFicheiro;

    @Setter
    @Column(name = "tamanho_maximo_total")
    private Long tamanhoMaximoTotal;

    @Setter
    @Column(name = "numero_maximo_ficheiros")
    private Integer numeroMaximoFicheiros;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regras_tiff", columnDefinition = "jsonb")
    private RegrasTiff regrasTiff;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "regra_nomenclatura", nullable = false, length = 10)
    private RegraNomenclatura regraNomenclatura = RegraNomenclatura.GERAL;

    // Exigido quaisquer que sejam os elementos afetados: documentos instrutórios que não são peças do plano.
    @Setter
    @Column(name = "independente_elementos_afetados", nullable = false)
    private boolean independenteElementosAfetados = false;

    @Setter
    @Column(nullable = false)
    private int ordem;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    public DocumentoConfig(InstrumentoProcedimentoConfig instrumentoProcedimentoConfig, String codigo, String nome,
                            String separador, CategoriaDocumento categoria, TipoElemento tipoElemento,
                            FormatoDocumento formato, String extensoesAceites, int ordem) {
        this.instrumentoProcedimentoConfig = instrumentoProcedimentoConfig;
        this.codigo = codigo;
        this.nome = nome;
        this.separador = separador;
        this.categoria = categoria;
        this.tipoElemento = tipoElemento;
        this.formato = formato;
        this.extensoesAceites = extensoesAceites;
        this.ordem = ordem;
    }

    /** As regras gravadas ou, num item de peças gráficas com a coluna vazia (seed posterior), as iniciais gerais. */
    public Optional<RegrasTiff> regrasTiffEfetivas() {
        if (tipoElemento != TipoElemento.PECA_GRAFICA) {
            return Optional.empty();
        }
        return Optional.of(regrasTiff != null ? regrasTiff : RegrasTiff.inicialGeral());
    }
}
