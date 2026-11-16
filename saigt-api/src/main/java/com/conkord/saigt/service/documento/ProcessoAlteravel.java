package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ClassificacaoInvalidaException;
import com.conkord.saigt.exception.DocumentoEstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.ExtensoesAceites;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Regras comuns às escritas documentais do Processo: quem pode escrever, em que estado e em que item. */
@Component
public class ProcessoAlteravel {

    private static final Logger log = LoggerFactory.getLogger(ProcessoAlteravel.class);
    private static final String MSG_ESTADO = "Os documentos só se carregam, substituem ou removem em EM_SUBMISSAO.";
    private static final String PARTE_TAGS = "tags";
    private static final String MSG_TAGS = "Só é possível associar o ficheiro a itens de depósito obrigatório.";
    private static final String MSG_TAGS_ORIGEM =
            "Só um ficheiro de depósito obrigatório pode ser associado a outros itens.";
    private static final String MSG_TAGS_EXTENSAO = "A extensão do ficheiro não é aceite num dos itens associados.";

    private final ProcessoRepository processoRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final AutorizacaoService autorizacaoService;

    public ProcessoAlteravel(ProcessoRepository processoRepository, DocumentoConfigRepository documentoConfigRepository,
                             AutorizacaoService autorizacaoService) {
        this.processoRepository = processoRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.autorizacaoService = autorizacaoService;
    }

    /** Camadas 2, 3 e autoria; a Camada 4 é só o estado, sem reavaliar sequenciamento nem regras (§14.3). */
    public Processo obter(UUID processoId, ContextoUtilizador contexto) {
        return verificar(processoRepository.findById(processoId), contexto);
    }

    public Processo obterComBloqueio(UUID processoId, ContextoUtilizador contexto) {
        return verificar(processoRepository.findByIdComBloqueio(processoId), contexto);
    }

    /** As mesmas camadas, com a mensagem própria do estado de quem chama. */
    public Processo obterComBloqueio(UUID processoId, ContextoUtilizador contexto, String mensagemEstado) {
        return verificar(processoRepository.findByIdComBloqueio(processoId), contexto, mensagemEstado);
    }

    /**
     * Carregar um documento novo: o autor em `EM_SUBMISSAO` ou a PCM no `VALIDADO_DGT` que lhe foi atribuído
     * (acrescenta; substituir e remover continuam só do autor).
     */
    public Processo obterParaAcrescentar(UUID processoId, ContextoUtilizador contexto) {
        return verificarAcrescento(processoRepository.findById(processoId), contexto);
    }

    public Processo obterParaAcrescentarComBloqueio(UUID processoId, ContextoUtilizador contexto) {
        return verificarAcrescento(processoRepository.findByIdComBloqueio(processoId), contexto);
    }

    private Processo verificarAcrescento(Optional<Processo> encontrado, ContextoUtilizador contexto) {
        Processo processo = encontrado.orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarPermissaoDeEditar(contexto, processo);
        EstadoProcesso exigido = autorizacaoService.correcaoPelaPcm(contexto, processo)
                ? EstadoProcesso.VALIDADO_DGT : EstadoProcesso.EM_SUBMISSAO;
        if (processo.getEstado() != exigido) {
            throw new DocumentoEstadoInvalidoException(MSG_ESTADO);
        }
        return processo;
    }

    private Processo verificar(Optional<Processo> encontrado, ContextoUtilizador contexto) {
        return verificar(encontrado, contexto, MSG_ESTADO);
    }

    private Processo verificar(Optional<Processo> encontrado, ContextoUtilizador contexto, String mensagemEstado) {
        Processo processo = encontrado.orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarPermissaoDeAgir(contexto, processo);
        autorizacaoService.verificarAutoria(contexto, processo);

        if (processo.getEstado() != EstadoProcesso.EM_SUBMISSAO) {
            throw new DocumentoEstadoInvalidoException(mensagemEstado);
        }
        return processo;
    }

    /** O item tem de ser do Procedimento deste Processo e estar ativo. */
    public DocumentoConfig item(Long documentoConfigId, Processo processo) {
        return documentoConfigRepository.findById(documentoConfigId)
                .filter(DocumentoConfig::isAtivo)
                .filter(config -> config.getInstrumentoProcedimentoConfig().getId()
                        .equals(processo.getInstrumentoProcedimentoConfig().getId()))
                .orElseThrow(() -> new ValidacaoException("Documento inválido.", List.of(new ValidacaoException.CampoErro(
                        "documentoConfigId", "Não existe, está inativo ou não pertence ao Procedimento deste Processo."))));
    }

    /**
     * BL-DOC-021 (EF v1.5, Recolha de ficheiros: «Ficheiros para depósito obrigatório podem ter mais do que um tag
     * associado»): repetições e o próprio item principal descartam-se; havendo tags, o item principal tem de ser de
     * depósito obrigatório e cada tag restante um item ativo de depósito obrigatório do Procedimento deste Processo
     * que aceite a extensão do ficheiro.
     */
    public List<DocumentoConfig> itensDasTags(List<String> tags, DocumentoConfig principal, String nomeFicheiro,
                                              Processo processo) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String tag : tags == null ? List.<String>of() : tags) {
            if (tag != null && !tag.isBlank()) {
                ids.add(idDaTag(tag));
            }
        }
        if (principal != null) {
            ids.remove(principal.getId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        if (!depositoObrigatorio(principal)) {
            log.warn("Tags recusadas, o ficheiro não é de depósito obrigatório — processoId={} tags={}",
                    processo.getId(), ids);
            throw new ClassificacaoInvalidaException(PARTE_TAGS, MSG_TAGS_ORIGEM);
        }
        Long procedimentoId = processo.getInstrumentoProcedimentoConfig().getId();
        List<DocumentoConfig> itens = documentoConfigRepository.findAllById(ids);
        boolean todosValidos = itens.size() == ids.size() && itens.stream().allMatch(item -> item.isAtivo()
                && depositoObrigatorio(item) && item.getInstrumentoProcedimentoConfig().getId().equals(procedimentoId));
        if (!todosValidos) {
            log.warn("Tags recusadas — processoId={} tags={}", processo.getId(), ids);
            throw new ClassificacaoInvalidaException(PARTE_TAGS, MSG_TAGS);
        }
        validarExtensaoNasTags(nomeFicheiro, itens);
        return itens;
    }

    /** Um ficheiro com tags conta como entregue em cada um desses itens, por isso tem de ser aceite em todos. */
    public void validarExtensaoNasTags(String nomeFicheiro, Collection<DocumentoConfig> itens) {
        String extensao = CaminhoDocumento.extensao(nomeFicheiro);
        for (DocumentoConfig item : itens) {
            if (!ExtensoesAceites.ler(item).contains(extensao)) {
                log.warn("Tag recusada pela extensão — documentoConfigId={} extensao={}", item.getId(), extensao);
                throw new ClassificacaoInvalidaException(PARTE_TAGS, MSG_TAGS_EXTENSAO);
            }
        }
    }

    private static boolean depositoObrigatorio(DocumentoConfig item) {
        return item != null && item.getCategoria() == CategoriaDocumento.DEPOSITO && item.isObrigatorio();
    }

    private static Long idDaTag(String tag) {
        try {
            return Long.valueOf(tag.trim());
        } catch (NumberFormatException e) {
            throw new ClassificacaoInvalidaException(PARTE_TAGS, MSG_TAGS);
        }
    }
}
