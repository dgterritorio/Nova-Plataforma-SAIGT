package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.BaseEntity;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.SgifrRedeDefesa;
import com.conkord.saigt.persistence.core.TipoAto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Instância de um Procedimento; "processo" nunca designa o Instrumento.
 * Estados e transições em {@link EstadoProcesso}.
 * `versao` faz optimistic locking: escrita concorrente devolve HTTP 409.
 */
@Entity
@Table(name = "processo")
@Getter
@NoArgsConstructor
public class Processo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dinamica_id", nullable = false)
    private Dinamica dinamica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumento_procedimento_config_id", nullable = false)
    private InstrumentoProcedimentoConfig instrumentoProcedimentoConfig;

    // Dinâmica do IGT a publicar depois deste processo; não bloqueia o envio desse IGT à INCM.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dinamica_associada_id")
    private Dinamica dinamicaAssociada;

    // Só nos Procedimentos `ato_pontual`: o ato publicado (Decisão de início ou Discussão pública) que o ato visa.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_referido_id")
    private Processo processoReferido;

    // A entidade que criou o processo (a da sessão); decide a visibilidade e nunca muda.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entidade_id", nullable = false, updatable = false)
    private Entidade entidade;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoProcesso estado = EstadoProcesso.EM_SUBMISSAO;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinoProcesso destino = DestinoProcesso.PUBLICACAO_DEPOSITO;

    @ContadorIdus(nome = "NUMERO_PROCESSO")
    @Column(name = "numero_processo", nullable = false, unique = true, length = 20, updatable = false)
    private String numeroProcesso;

    // A Tentativa da Dinâmica em que o processo nasceu: recomeçar a Dinâmica não a muda.
    @Column(nullable = false, updatable = false)
    private int tentativa = 1;

    // Conta os processos do mesmo Procedimento na Tentativa; um novo só depois de o anterior ser anulado.
    @Setter
    @Column(nullable = false)
    private int ocorrencia = 1;

    // Informativo: os documentos submetem-se sempre de novo e nada segue para a PCGT.
    @Setter
    @Column(name = "aproveita_atos_anteriores")
    private Boolean aproveitaAtosAnteriores;

    @Version
    @Column(nullable = false)
    private long versao;

    // NATIVO | REGISTO_EXTERNO.
    @Setter
    @Column(name = "origem_dados", nullable = false, length = 20)
    private String origemDados = "NATIVO";

    // Só o autor edita a tentativa; o setter existe apenas para a reatribuição pelo MASTER.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Utilizador criadoPor;

    // Processo com a PCM: bloqueia o envio à INCM e dá à PCM acesso ao processo (TR-114, TR-177).
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcm_atribuido_a")
    private Utilizador pcmAtribuidoA;

    // Última devolução à DGT pela PCM na passagem atual por VALIDADO_DGT; a nova validação limpa-a (TR-114).
    @Setter
    @Column(name = "pcm_devolvido_em")
    private Instant pcmDevolvidoEm;

    // Tipo de ato da publicação, entre os aplicáveis à série do Instrumento e autorizados ao utilizador.
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_ato_id")
    private TipoAto tipoAto;

    // PECAS_ESCRITAS | PECAS_GRAFICAS | OUTROS.
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "elementos_afetados", columnDefinition = "jsonb")
    private List<String> elementosAfetados;

    // Destino do email dos eventos do processo.
    @Setter
    @Column(name = "contacto_tramitacao", length = 254)
    private String contactoTramitacao;

    // Texto livre do formulário inicial; a entidade só é obrigatória em `submeter`, nos Planos e Programas.
    @Setter
    @Column(name = "entidade_emitente", length = 500)
    private String entidadeEmitente;

    // Só nos Programas; nas outras categorias o formulário não a mostra e fica nula.
    @Setter
    @Column(name = "entidade_instrutora", length = 500)
    private String entidadeInstrutora;

    @Setter
    @Column(name = "contacto_publicacao", length = 500)
    private String contactoPublicacao;

    // Chaves do formulário inicial editáveis depois da primeira submissão; ver CamposFormularioInicial.
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campos_desbloqueados", columnDefinition = "jsonb")
    private List<String> camposDesbloqueados;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_formulario", columnDefinition = "jsonb")
    private String dadosFormulario;

    // Observações da secção «Outros», que não tem item documental.
    @Setter
    @Column(name = "observacoes_outros", columnDefinition = "text")
    private String observacoesOutros;

    @Setter
    @Column(name = "data_submissao")
    private Instant dataSubmissao;

    @Setter
    @Column(name = "numero_dr", length = 50)
    private String numeroDr;

    @Setter
    @Column(name = "data_publicacao_dr")
    private LocalDate dataPublicacaoDr;

    // ActURL da publicação, devolvido pela INCM.
    @Setter
    @Column(name = "link_dr", columnDefinition = "text")
    private String linkDr;

    // Só no procedimento final; atribuído na publicação e nunca reatribuído.
    @Setter
    @Column(name = "numero_deposito", length = 50, unique = true)
    private String numeroDeposito;

    // ActIdentifier devolvido pela INCM no SubmitNewAct; única chave do GetActDetails.
    @Setter
    @Column(name = "incm_act_identifier", length = 100)
    private String incmActIdentifier;

    // UserEnrollmentAgreementIdentifier da sessão que submeteu, congelado ao passar a ENVIADO_DGT.
    // Opcional no rascunho, obrigatório na submissão (a INCM exige-o em qualquer ato).
    @Setter
    @Column(name = "incm_enrollment_id", length = 100)
    private String incmEnrollmentId;

    // PartId da parte do DR da entidade emissora, congelado na mesma transição.
    // Obrigatório só na submissão de atos de 2.ª série.
    @Setter
    @Column(name = "dr_parte_id")
    private Long drParteId;

    // ActSummary — exigido pela INCM nas duas séries; obrigatório só na submissão.
    @Setter
    @Column(length = 2000)
    private String sumario;

    @Setter
    @Column(name = "data_assinatura")
    private LocalDate dataAssinatura;

    // LegalProvision — só 2.ª série.
    @Setter
    @Column(name = "norma_legal", length = 500)
    private String normaLegal;

    // TEXTO_INTEGRAL | EXTRATO — só 2.ª série.
    @Setter
    @Column(name = "tipo_informacao", length = 20)
    private String tipoInformacao;

    // PublishInSuplement — só 1.ª série.
    @Setter
    @Column(name = "publicar_suplemento")
    private Boolean publicarSuplemento;

    @Setter
    @Column(name = "data_suplemento")
    private LocalDate dataSuplemento;

    // Só nos Procedimentos sem série configurada (restantes servidões): a escolha do utilizador. Ler por serieDrEfetiva().
    @Setter
    @Column(name = "serie_dr", length = 10)
    private String serieDr;

    // Campos opcionais do Resumo Validados: gravam-se só no validar (API-PROC-012).
    @Setter
    @Column(name = "notas_incm", columnDefinition = "text")
    private String notasIncm;

    @Setter
    @Column(name = "notas_dgt", columnDefinition = "text")
    private String notasDgt;

    @Setter
    @Column(name = "reg_uso_solo")
    private Boolean regUsoSolo;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alteracao_peot_id")
    private ListaValores alteracaoPeot;

    // Redes de defesa do SGIFR do processo: cada rede é uma folha do mesmo ato.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "processo_rede_defesa",
            joinColumns = @JoinColumn(name = "processo_id"),
            inverseJoinColumns = @JoinColumn(name = "rede_defesa_id"))
    private Set<SgifrRedeDefesa> redesDefesa = new HashSet<>();

    public Processo(Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig, Utilizador criadoPor,
                    Entidade entidade) {
        this.dinamica = dinamica;
        if (dinamica != null) {
            this.tentativa = dinamica.getTentativa();
        }
        this.entidade = entidade;
        this.instrumentoProcedimentoConfig = instrumentoProcedimentoConfig;
        this.criadoPor = criadoPor;
    }

    /** A série do Procedimento quando configurada; senão a escolhida no processo, nula enquanto não há escolha. */
    public String serieDrEfetiva() {
        InstrumentoProcedimentoConfig config = getInstrumentoProcedimentoConfig();
        String doProcedimento = config == null ? null : config.getSerieDr();
        return doProcedimento != null ? doProcedimento : getSerieDr();
    }

    /** O Procedimento não fixa a série: escolhe-a o utilizador no processo. */
    public boolean escolheSerieDr() {
        InstrumentoProcedimentoConfig config = getInstrumentoProcedimentoConfig();
        return config != null && config.getSerieDr() == null;
    }

    public SituacaoPcm situacaoPcm() {
        if (getPcmAtribuidoA() != null) {
            return SituacaoPcm.COM_PCM;
        }
        return getPcmDevolvidoEm() != null ? SituacaoPcm.DEVOLVIDO : SituacaoPcm.SEM_PCM;
    }
}
