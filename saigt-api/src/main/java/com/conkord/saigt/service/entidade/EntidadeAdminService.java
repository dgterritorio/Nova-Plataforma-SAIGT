package com.conkord.saigt.service.entidade;

import com.conkord.saigt.dto.entidade.AtualizarEntidadeRequest;
import com.conkord.saigt.dto.entidade.CriarEntidadeRequest;
import com.conkord.saigt.dto.entidade.EntidadeFiltro;
import com.conkord.saigt.dto.entidade.EntidadeResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gestão de entidades SAIGT pelo `MASTER`. O NIPC e a descrição INCM são os dados da correspondência com a adesão
 * INCM: só aqui se alteram, o login nunca. A mudança do tipo ou do `dico` vale no login seguinte e não revoga sessões.
 */
@Service
public class EntidadeAdminService {

    private static final String TIPO_OCORRENCIA = "ENTIDADE";
    private static final String CAMPO_CODIGO = "codigo";
    private static final String CAMPO_DESCRICAO_INCM = "descricaoIncm";
    private static final String CAMPO_DESCRICAO_SAIGT = "descricaoSaigt";
    private static final String CAMPO_TIPO_ENTIDADE = "tipoEntidade";
    private static final String CAMPO_NIPC = "nipc";
    private static final String CAMPO_DICO = "dico";
    private static final int MAXIMO_CODIGO = 20;
    private static final int MAXIMO_DESCRICAO = 200;
    private static final Pattern NOVE_DIGITOS = Pattern.compile("^\\d{9}$");
    private static final Pattern QUATRO_ALGARISMOS = Pattern.compile("^\\d{4}$");
    private static final String MENSAGEM_INVALIDO = "Dados da entidade inválidos.";
    private static final String CODIGO_DUPLICADO = "Código de entidade já existe.";
    private static final String COM_REGISTOS = "A entidade tem registos associados e não pode ser eliminada.";

    private final EntidadeRepository entidadeRepository;
    private final EntidadeContactoRepository entidadeContactoRepository;
    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ProcessoRepository processoRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public EntidadeAdminService(EntidadeRepository entidadeRepository,
                                 EntidadeContactoRepository entidadeContactoRepository,
                                 SnitReferenciaRepository snitReferenciaRepository,
                                 ProcessoRepository processoRepository,
                                 AuditoriaRepository auditoriaRepository,
                                 UtilizadorEntidadeRepository utilizadorEntidadeRepository,
                                 AuditoriaService auditoriaService,
                                 ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.entidadeRepository = entidadeRepository;
        this.entidadeContactoRepository = entidadeContactoRepository;
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.processoRepository = processoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.utilizadorEntidadeRepository = utilizadorEntidadeRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional
    public EntidadeResponse criar(CriarEntidadeRequest request) {
        UUID atorId = exigirAdmin();
        List<CampoErro> erros = new ArrayList<>();
        String codigo = validarObrigatorio(CAMPO_CODIGO, request.codigo(), MAXIMO_CODIGO, erros);
        String descricaoIncm = validarObrigatorio(CAMPO_DESCRICAO_INCM, request.descricaoIncm(), MAXIMO_DESCRICAO, erros);
        String descricaoSaigt = validarObrigatorio(CAMPO_DESCRICAO_SAIGT, request.descricaoSaigt(), MAXIMO_DESCRICAO,
                erros);
        TipoEntidade tipoEntidade = request.tipoEntidade() == null || request.tipoEntidade().isBlank()
                ? erroObrigatorio(CAMPO_TIPO_ENTIDADE, erros)
                : converterTipo(request.tipoEntidade(), erros);
        String nipc = validarNipc(vazioComoNulo(request.nipc()), erros);
        String dico = validarDico(vazioComoNulo(request.dico()), tipoEntidade, erros);
        lancarSeHouverErros(erros);

        if (entidadeRepository.existsByCodigo(codigo)) {
            throw new ReferenciaDuplicadaException(CODIGO_DUPLICADO);
        }

        Entidade entidade = new Entidade(codigo, tipoEntidade, nipc, descricaoIncm, descricaoSaigt);
        entidade.setDico(dico);
        Entidade guardada = entidadeRepository.saveAndFlush(entidade);

        auditoriaService.registar(TIPO_OCORRENCIA, guardada.getId().toString(), "CRIAR", atorId,
                dadosAuditaveis(guardada));
        return EntidadeResponse.de(guardada);
    }

    @Transactional
    public EntidadeResponse atualizar(Long id, AtualizarEntidadeRequest request) {
        UUID atorId = exigirAdmin();
        if (request.semCampos()) {
            throw new ValidacaoException("Indique pelo menos um campo a alterar.", List.of());
        }
        Entidade entidade = carregar(id);

        List<CampoErro> erros = new ArrayList<>();
        String codigo = request.codigo() == null ? null
                : validarObrigatorio(CAMPO_CODIGO, request.codigo(), MAXIMO_CODIGO, erros);
        String descricaoIncm = request.descricaoIncm() == null ? null
                : validarObrigatorio(CAMPO_DESCRICAO_INCM, request.descricaoIncm(), MAXIMO_DESCRICAO, erros);
        String descricaoSaigt = request.descricaoSaigt() == null ? null
                : validarObrigatorio(CAMPO_DESCRICAO_SAIGT, request.descricaoSaigt(), MAXIMO_DESCRICAO, erros);
        TipoEntidade tipoPedido = null;
        if (request.tipoEntidade() != null) {
            tipoPedido = request.tipoEntidade().isBlank()
                    ? erroObrigatorio(CAMPO_TIPO_ENTIDADE, erros)
                    : converterTipo(request.tipoEntidade(), erros);
        }
        String nipc = request.nipc() == null ? null : validarNipc(vazioComoNulo(request.nipc()), erros);
        TipoEntidade tipoFinal = tipoPedido != null ? tipoPedido : entidade.getTipoEntidade();
        String dico = request.dico() == null ? null : validarDico(vazioComoNulo(request.dico()), tipoFinal, erros);
        lancarSeHouverErros(erros);

        if (codigo != null && entidadeRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new ReferenciaDuplicadaException(CODIGO_DUPLICADO);
        }

        Map<String, Object> antes = dadosAuditaveis(entidade);
        if (codigo != null) {
            entidade.setCodigo(codigo);
        }
        if (descricaoIncm != null) {
            entidade.setDescricaoIncm(descricaoIncm);
        }
        if (descricaoSaigt != null) {
            entidade.setDescricaoSaigt(descricaoSaigt);
        }
        if (tipoPedido != null) {
            entidade.setTipoEntidade(tipoPedido);
        }
        if (request.nipc() != null) {
            entidade.setNipc(nipc);
        }
        if (request.dico() != null) {
            entidade.setDico(dico);
        }
        if (tipoFinal != TipoEntidade.CM) {
            entidade.setDico(null);
        }

        Map<String, Object> alteracoes = diferencas(antes, dadosAuditaveis(entidade));
        if (alteracoes.isEmpty()) {
            return EntidadeResponse.de(entidade, contactoPrincipal(id));
        }
        Entidade guardada = entidadeRepository.saveAndFlush(entidade);
        auditoriaService.registar(TIPO_OCORRENCIA, id.toString(), "ATUALIZAR", atorId, alteracoes);
        return EntidadeResponse.de(guardada, contactoPrincipal(id));
    }

    /**
     * A ordem das verificações fixa a causa da recusa. A FK fica como última defesa (ex.: notificações dirigidas à
     * entidade): a exceção desfaz também a auditoria da eliminação.
     */
    @Transactional
    public void eliminar(Long id) {
        UUID atorId = exigirAdmin();
        Entidade entidade = carregar(id);

        if (processoRepository.existsByEntidadeId(id)
                || entidadeContactoRepository.existsByEntidadeId(id)
                || auditoriaRepository.existsByEntidadeId(id)
                || utilizadorEntidadeRepository.existsByIdEntidadeId(id)) {
            throw new EstadoInvalidoException(COM_REGISTOS);
        }

        auditoriaService.registar(TIPO_OCORRENCIA, id.toString(), "ELIMINAR", atorId, dadosAuditaveis(entidade));
        try {
            entidadeRepository.deleteById(id);
            entidadeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EstadoInvalidoException(COM_REGISTOS);
        }
    }

    @Transactional(readOnly = true)
    public Page<EntidadeResponse> listar(EntidadeFiltro filtro, Pageable pageable) {
        exigirAdmin();
        Page<Entidade> pagina = entidadeRepository.findAll(construirEspecificacao(filtro), pageable);

        List<Long> idsEntidades = pagina.getContent().stream().map(Entidade::getId).toList();
        Map<Long, EntidadeContacto> contactosPrincipaisPorEntidade = entidadeContactoRepository
                .findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(idsEntidades).stream()
                .collect(Collectors.toMap(contacto -> contacto.getEntidade().getId(), contacto -> contacto));

        return pagina.map(entidade -> EntidadeResponse.de(entidade, contactosPrincipaisPorEntidade.get(entidade.getId())));
    }

    /** O filtro `tipoEntidade` da listagem: fora da lista → 400, depois do 403. */
    public TipoEntidade converterTipoFiltro(String tipoEntidade) {
        exigirAdmin();
        if (tipoEntidade == null || tipoEntidade.isBlank()) {
            return null;
        }
        List<CampoErro> erros = new ArrayList<>();
        TipoEntidade tipo = converterTipo(tipoEntidade, erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.", erros);
        }
        return tipo;
    }

    private UUID exigirAdmin() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode administrar entidades.");
        }
        return contexto.utilizadorId();
    }

    private Entidade carregar(Long id) {
        return entidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entidade não encontrada."));
    }

    private EntidadeContacto contactoPrincipal(Long id) {
        return entidadeContactoRepository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(id).orElse(null);
    }

    /** O código guarda-se sem espaços nas pontas; as descrições como recebidas (a INCM compara-as). */
    private static String validarObrigatorio(String campo, String valor, int maximo, List<CampoErro> erros) {
        if (valor == null || valor.isBlank()) {
            erros.add(new CampoErro(campo, "Obrigatório."));
            return null;
        }
        String aplicado = CAMPO_CODIGO.equals(campo) ? valor.strip() : valor;
        if (aplicado.length() > maximo) {
            erros.add(new CampoErro(campo, "Tem de ter no máximo " + maximo + " caracteres."));
            return null;
        }
        return aplicado;
    }

    private static TipoEntidade erroObrigatorio(String campo, List<CampoErro> erros) {
        erros.add(new CampoErro(campo, "Obrigatório."));
        return null;
    }

    private static TipoEntidade converterTipo(String valor, List<CampoErro> erros) {
        try {
            return TipoEntidade.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            erros.add(new CampoErro(CAMPO_TIPO_ENTIDADE, "Tipo de entidade inválido."));
            return null;
        }
    }

    private static String validarNipc(String nipc, List<CampoErro> erros) {
        if (nipc != null && !nipcValido(nipc)) {
            erros.add(new CampoErro(CAMPO_NIPC, "NIPC inválido."));
            return null;
        }
        return nipc;
    }

    /** Nove dígitos e dígito de controlo módulo 11. */
    static boolean nipcValido(String nipc) {
        if (!NOVE_DIGITOS.matcher(nipc).matches()) {
            return false;
        }
        int soma = 0;
        for (int i = 0; i < 8; i++) {
            soma += (nipc.charAt(i) - '0') * (9 - i);
        }
        int resto = soma % 11;
        int controlo = resto < 2 ? 0 : 11 - resto;
        return controlo == nipc.charAt(8) - '0';
    }

    /** Sem tipo válido não se decide o `dico`: o erro do tipo já vai na resposta. */
    private String validarDico(String dico, TipoEntidade tipoEntidade, List<CampoErro> erros) {
        if (dico == null || tipoEntidade == null) {
            return dico;
        }
        if (tipoEntidade != TipoEntidade.CM) {
            erros.add(new CampoErro(CAMPO_DICO, "O concelho só se indica nas câmaras municipais."));
            return null;
        }
        if (!QUATRO_ALGARISMOS.matcher(dico).matches()
                || !snitReferenciaRepository.existsByListaAndCodigoAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES, dico)) {
            erros.add(new CampoErro(CAMPO_DICO, "Concelho inexistente."));
            return null;
        }
        return dico;
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static void lancarSeHouverErros(List<CampoErro> erros) {
        if (!erros.isEmpty()) {
            throw new ValidacaoException(MENSAGEM_INVALIDO, erros);
        }
    }

    private static Map<String, Object> dadosAuditaveis(Entidade entidade) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_CODIGO, entidade.getCodigo());
        dados.put(CAMPO_NIPC, entidade.getNipc());
        dados.put(CAMPO_DICO, entidade.getDico());
        dados.put(CAMPO_DESCRICAO_INCM, entidade.getDescricaoIncm());
        dados.put(CAMPO_DESCRICAO_SAIGT, entidade.getDescricaoSaigt());
        dados.put(CAMPO_TIPO_ENTIDADE, entidade.getTipoEntidade() == null ? null : entidade.getTipoEntidade().name());
        return dados;
    }

    private static Map<String, Object> diferencas(Map<String, Object> antes, Map<String, Object> depois) {
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        antes.forEach((campo, valorAntes) -> {
            Object valorDepois = depois.get(campo);
            if (!Objects.equals(valorAntes, valorDepois)) {
                Map<String, Object> par = new LinkedHashMap<>();
                par.put("antes", valorAntes);
                par.put("depois", valorDepois);
                alteracoes.put(campo, par);
            }
        });
        return alteracoes;
    }

    private Specification<Entidade> construirEspecificacao(EntidadeFiltro filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (filtro.ativo() != null) {
                predicados.add(cb.equal(root.get("ativo"), filtro.ativo()));
            }
            Optional.ofNullable(filtro.nome()).filter(nome -> !nome.isBlank()).ifPresent(nome -> {
                String padrao = "%" + escaparLike(nome.strip().toLowerCase(Locale.ROOT)) + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get(CAMPO_DESCRICAO_SAIGT)), padrao, '\\'),
                        cb.like(cb.lower(root.get(CAMPO_DESCRICAO_INCM)), padrao, '\\'),
                        cb.like(cb.lower(root.get(CAMPO_CODIGO)), padrao, '\\')));
            });
            if (filtro.tipoEntidade() != null) {
                predicados.add(cb.equal(root.get(CAMPO_TIPO_ENTIDADE), filtro.tipoEntidade()));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
