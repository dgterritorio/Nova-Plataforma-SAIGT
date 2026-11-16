package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.core.BaseEntity;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Check;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Mutável em EM_SUBMISSAO, imutável a partir de ENVIADO_DGT (regra aplicada fora desta entidade). */
@Entity
@Table(name = "processo_documento")
@Check(constraints = "categoria IN ('PUBLICACAO', 'DEPOSITO') AND origem IN ('CARREGADO', 'GERADO', 'GEOCONFORM') "
        + "AND estado_validacao IN ('POR_VALIDAR', 'VALIDADO', 'REJEITADO', 'SUBSTITUIDO')")
@Getter
@NoArgsConstructor
public class ProcessoDocumento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    // Vazio = documento extra, sem item (secção «Outros»), de publicação ou de depósito.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_config_id")
    private DocumentoConfig documentoConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDocumento categoria;

    @Setter
    @Column(nullable = false, length = 10)
    private String extensao;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemDocumento origem = OrigemDocumento.CARREGADO;

    // `versionId` devolvido pelo storage em cada gravação.
    @Setter
    @Column(name = "versao_objeto", nullable = false, length = 100)
    private String versaoObjeto;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Setter
    @Column(name = "bloqueado_edicao", nullable = false)
    private boolean bloqueadoEdicao = false;

    // A do último desbloqueio: mantém-se quando o ficheiro volta a ficar bloqueado numa correção seguinte.
    @Setter
    @Column(name = "justificacao_desbloqueio", columnDefinition = "TEXT")
    private String justificacaoDesbloqueio;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacao", nullable = false, length = 20)
    private EstadoValidacao estadoValidacao = EstadoValidacao.POR_VALIDAR;

    @Setter
    @Column(name = "nome_ficheiro", nullable = false, length = 300)
    private String nomeFicheiro;

    @Setter
    @Column(name = "caminho_ficheiro", nullable = false, length = 500)
    private String caminhoFicheiro;

    // Integridade verificável independentemente do storage. Recalculado na substituição.
    @Setter
    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Setter
    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Utilizador criadoPor;

    // Itens de depósito obrigatório que o ficheiro também satisfaz. O lote evita uma ida à BD por ficheiro nas
    // listagens paginadas, onde um `join fetch` da coleção paginaria em memória.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "processo_documento_tag",
            joinColumns = @JoinColumn(name = "processo_documento_id"),
            inverseJoinColumns = @JoinColumn(name = "documento_config_id"))
    @BatchSize(size = 100)
    private Set<DocumentoConfig> tags = new HashSet<>();

    public void associarTags(Collection<DocumentoConfig> itens) {
        tags.addAll(itens);
    }

    public void substituirTags(Collection<DocumentoConfig> itens) {
        tags.clear();
        tags.addAll(itens);
    }

    /** Sem item, o ficheiro fica na secção «Outros», na caixa da sua categoria. */
    public void reclassificar(CategoriaDocumento novaCategoria, DocumentoConfig novoItem) {
        this.categoria = novaCategoria;
        this.documentoConfig = novoItem;
    }

    /** Os `documentoConfigId` das tags, por ordem crescente. */
    public List<Long> idsDasTags() {
        return tags.stream().map(DocumentoConfig::getId).sorted(Comparator.naturalOrder()).toList();
    }

    public ProcessoDocumento(Processo processo, DocumentoConfig documentoConfig, CategoriaDocumento categoria,
                              String extensao, String nomeFicheiro, String caminhoFicheiro, String hashSha256,
                              String versaoObjeto, long tamanhoBytes, Utilizador criadoPor) {
        this.processo = processo;
        this.documentoConfig = documentoConfig;
        this.categoria = categoria;
        this.extensao = extensao;
        this.nomeFicheiro = nomeFicheiro;
        this.caminhoFicheiro = caminhoFicheiro;
        this.hashSha256 = hashSha256;
        this.versaoObjeto = versaoObjeto;
        this.tamanhoBytes = tamanhoBytes;
        this.criadoPor = criadoPor;
    }

    public ProcessoDocumento(UUID id, Processo processo, DocumentoConfig documentoConfig, CategoriaDocumento categoria,
                             String extensao, String nomeFicheiro, String caminhoFicheiro, String hashSha256,
                             String versaoObjeto, long tamanhoBytes, Utilizador criadoPor) {
        this(processo, documentoConfig, categoria, extensao, nomeFicheiro, caminhoFicheiro, hashSha256, versaoObjeto,
                tamanhoBytes, criadoPor);
        atribuirId(id);
    }
}
