package com.conkord.saigt.service.listavalores;

import com.conkord.saigt.dto.instrumento.OpcaoListaResponse;
import com.conkord.saigt.dto.listavalores.AtualizarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.CriarListaValoresRequest;
import com.conkord.saigt.dto.listavalores.ListaValoresResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Só `MASTER` escreve; `TECNICO` lê, porque escolhe daqui os motivos ao rejeitar. Sem `DELETE` físico:
 * desativa com `ativo=false`.
 * `tipo`/`codigo` são imutáveis (a entidade não tem setters) porque há lógica que os referencia
 * (ex. {@code MOTIVO_REJEICAO_DGT}). O `tipo` tem de ser um de {@link TipoListaValores}, mesmo que ainda não
 * tenha valores.
 */
@Service
public class ListaValoresService {

    private static final Set<PerfilInterno> PERFIS_LEITURA = Set.of(PerfilInterno.MASTER, PerfilInterno.TECNICO);
    private static final String TIPO_OCORRENCIA = "LISTA_VALORES";
    // Tipo dependente → tipo do valor de que depende.
    private static final Map<String, String> HIERARQUIA = Map.of(
            TipoListaValores.CLASSE_SRUP.name(), TipoListaValores.DOMINIO_SRUP.name(),
            TipoListaValores.TIPOLOGIA_SRUP.name(), TipoListaValores.CLASSE_SRUP.name(),
            TipoListaValores.CIM_AM.name(), TipoListaValores.REGIAO.name());
    private static final Set<TipoListaValores> TIPOS_FORMULARIO = Set.of(TipoListaValores.FREGUESIA,
            TipoListaValores.TOPONIMO, TipoListaValores.MOTIVO_ALTERACAO_LEI, TipoListaValores.MOTIVO_ALTERACAO_PROGRAMA,
            TipoListaValores.MOTIVO_ALTERACAO_PLANO);

    private final ListaValoresRepository listaValoresRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public ListaValoresService(ListaValoresRepository listaValoresRepository, AuditoriaService auditoriaService,
                                ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.listaValoresRepository = listaValoresRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public List<ListaValoresResponse> listar(String tipo) {
        exigirLeitura();
        List<ListaValores> itens = tipo != null
                ? listaValoresRepository.findByTipoOrderByOrdemAsc(tipo)
                : listaValoresRepository.findAllByOrderByTipoAscOrdemAsc();
        return itens.stream().map(ListaValoresResponse::de).toList();
    }

    @Transactional
    public ListaValoresResponse criar(CriarListaValoresRequest request) {
        UUID atorId = exigirAdmin();
        if (!tipoConhecido(request.tipo())) {
            throw new ValidacaoException("Tipo inexistente.", List.of(new ValidacaoException.CampoErro("tipo",
                    "Não é um dos tipos de listas de valores.")));
        }
        if (listaValoresRepository.existsByTipoAndCodigo(request.tipo(), request.codigo())) {
            throw new ReferenciaDuplicadaException("Já existe um item com este código para este tipo.");
        }

        ListaValores pai = validarPai(request);
        ListaValores item = new ListaValores(request.tipo(), request.codigo(), request.descricao(), request.ordem(), pai);
        item = listaValoresRepository.save(item);
        Map<String, Object> dados = new HashMap<>(Map.of("tipo", request.tipo(), "codigo", request.codigo(),
                "descricao", request.descricao()));
        if (pai != null) {
            dados.put("paiId", pai.getId());
        }
        auditoriaService.registar(TIPO_OCORRENCIA, item.getId().toString(), "CRIAR", atorId, dados);
        return ListaValoresResponse.de(item);
    }

    @Transactional
    public ListaValoresResponse atualizar(Long id, AtualizarListaValoresRequest request) {
        UUID atorId = exigirAdmin();
        ListaValores item = listaValoresRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));

        Map<String, String> camposAlterados = new HashMap<>();
        if (request.descricao() != null) {
            item.setDescricao(request.descricao());
            camposAlterados.put("descricao", request.descricao());
        }
        if (request.ordem() != null) {
            item.setOrdem(request.ordem());
            camposAlterados.put("ordem", String.valueOf(request.ordem()));
        }
        if (request.ativo() != null) {
            validarAtivacao(item, request.ativo());
            item.setAtivo(request.ativo());
            camposAlterados.put("ativo", String.valueOf(request.ativo()));
        }

        listaValoresRepository.save(item);
        if (!camposAlterados.isEmpty()) {
            auditoriaService.registar(TIPO_OCORRENCIA, item.getId().toString(), "ATUALIZAR", atorId, camposAlterados);
        }
        return ListaValoresResponse.de(item);
    }

    @Transactional(readOnly = true)
    public List<OpcaoListaResponse> opcoesFormulario(String tipo) {
        TipoListaValores tipoFormulario = Arrays.stream(TipoListaValores.values())
                .filter(conhecido -> conhecido.name().equals(tipo) && TIPOS_FORMULARIO.contains(conhecido))
                .findFirst()
                .orElseThrow(() -> new ValidacaoException("Tipo de lista não disponível para formulários.",
                        List.of(new ValidacaoException.CampoErro("tipo", "Tipo de lista não disponível para formulários."))));
        return opcoesAtivas(tipoFormulario);
    }

    @Transactional(readOnly = true)
    public List<OpcaoListaResponse> opcoesAtivas(TipoListaValores tipo) {
        return listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(tipo.name()).stream()
                .map(OpcaoListaResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean temValoresAtivos(TipoListaValores tipo) {
        return listaValoresRepository.existsByTipoAndAtivoTrue(tipo.name());
    }

    @Transactional(readOnly = true)
    public ListaValores obterAtivo(TipoListaValores tipo, Long id, String campo) {
        return Optional.ofNullable(id)
                .flatMap(listaValoresRepository::findById)
                .filter(valor -> tipo.name().equals(valor.getTipo()) && valor.isAtivo())
                .orElseThrow(() -> new ValidacaoException("Valor de lista inválido.",
                        List.of(new ValidacaoException.CampoErro(campo,
                                "Não existe ou não é um valor ativo de " + tipo.name() + "."))));
    }

    private ListaValores validarPai(CriarListaValoresRequest request) {
        String tipoDoPai = HIERARQUIA.get(request.tipo());
        if (tipoDoPai == null) {
            if (request.paiId() != null) {
                throw erroPai("Este tipo de lista não tem valores dependentes.");
            }
            return null;
        }
        if (request.paiId() == null) {
            throw erroPai("Indique o valor de que este depende.");
        }
        return listaValoresRepository.findById(request.paiId())
                .filter(pai -> tipoDoPai.equals(pai.getTipo()) && pai.isAtivo())
                .orElseThrow(() -> erroPai("O valor indicado não é do nível acima."));
    }

    private void validarAtivacao(ListaValores item, boolean ativo) {
        if (!ativo && item.isAtivo() && listaValoresRepository.existsByPaiAndAtivoTrue(item)) {
            throw new EstadoInvalidoException("Desative primeiro os valores que dependem deste.");
        }
        if (ativo && !item.isAtivo() && item.getPai() != null && !item.getPai().isAtivo()) {
            throw new EstadoInvalidoException("Ative primeiro o valor de que este depende.");
        }
    }

    private static ValidacaoException erroPai(String mensagem) {
        return new ValidacaoException("Payload inválido.", List.of(new ValidacaoException.CampoErro("paiId", mensagem)));
    }

    private static boolean tipoConhecido(String tipo) {
        return Arrays.stream(TipoListaValores.values()).anyMatch(conhecido -> conhecido.name().equals(tipo));
    }

    private UUID exigirAdmin() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode gerir listas de valores.");
        }
        return contexto.utilizadorId();
    }

    private void exigirLeitura() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        PerfilInterno perfilInterno = contexto.perfilInterno();
        if (perfilInterno == null || !PERFIS_LEITURA.contains(perfilInterno)) {
            throw new AcaoNaoAutorizadaException("Só MASTER/TECNICO pode consultar listas de valores.");
        }
    }
}
