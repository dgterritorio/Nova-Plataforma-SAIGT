package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.dto.utilizador.AlterarPerfilInternoRequest;
import com.conkord.saigt.dto.utilizador.EntidadeVinculoResponse;
import com.conkord.saigt.dto.utilizador.RevogarSessoesResponse;
import com.conkord.saigt.dto.utilizador.UtilizadorFiltro;
import com.conkord.saigt.dto.utilizador.UtilizadorResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidade;
import com.conkord.saigt.persistence.operacional.UtilizadorEntidadeRepository;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.sessao.SessaoService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Só `MASTER`, também na leitura. */
@Service
public class UtilizadorAdminService {

    private static final String TIPO_OCORRENCIA = "UTILIZADOR";
    private static final String ORIGEM_AD_DGT = "AD_DGT";
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");
    private static final String CAMPO_PERFIL_INTERNO = "perfilInterno";
    private static final String MENSAGEM_PERFIL_INTERNO_VALIDO = "Tem de ser MASTER, TECNICO ou VISITANTE.";
    private static final String MENSAGEM_SEM_ADMINISTRADOR = "Operação deixaria a plataforma sem administrador.";
    private static final String MENSAGEM_INDIQUE_ENTIDADE = "Indique a entidade.";

    private final UtilizadorRepository utilizadorRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final UtilizadorEntidadeRepository utilizadorEntidadeRepository;
    private final AuditoriaService auditoriaService;
    private final SessaoService sessaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public UtilizadorAdminService(UtilizadorRepository utilizadorRepository, AuditoriaRepository auditoriaRepository,
                                   UtilizadorEntidadeRepository utilizadorEntidadeRepository, AuditoriaService auditoriaService,
                                   SessaoService sessaoService, ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.utilizadorRepository = utilizadorRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.utilizadorEntidadeRepository = utilizadorEntidadeRepository;
        this.auditoriaService = auditoriaService;
        this.sessaoService = sessaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public Page<UtilizadorResponse> listar(UtilizadorFiltro filtro, Pageable pageable) {
        exigirAdmin();
        if (filtro.atuouDesde() != null && filtro.entidadeId() == null) {
            throw new ValidacaoException(MENSAGEM_INDIQUE_ENTIDADE,
                    List.of(new ValidacaoException.CampoErro("entidadeId", MENSAGEM_INDIQUE_ENTIDADE)));
        }
        PerfilInterno perfilInterno = filtro.perfilInterno() == null
                ? null : validarPerfilInterno(filtro.perfilInterno(), "Filtro inválido.");
        Specification<Utilizador> especificacao = construirEspecificacao(filtro, perfilInterno);
        if (filtro.entidadeId() != null) {
            especificacao = especificacao.and(idEm(idsDaEntidade(filtro)));
        }
        Page<Utilizador> pagina = utilizadorRepository.findAll(especificacao, pageable);

        Map<UUID, List<EntidadeVinculoResponse>> entidadesPorUtilizador = entidadesEmLote(pagina.getContent());
        Map<UUID, Instant> ultimoLoginPorUtilizador = ultimoLoginEmLote(pagina.getContent());
        Map<UUID, Instant> ultimaAtuacaoPorUtilizador = filtro.entidadeId() == null
                ? Map.of() : ultimaAtuacaoNaEntidadeEmLote(filtro.entidadeId(), pagina.getContent());
        return pagina.map(utilizador -> UtilizadorResponse.deCompleto(utilizador,
                entidadesPorUtilizador.getOrDefault(utilizador.getId(), List.of()),
                ultimoLoginPorUtilizador.get(utilizador.getId()),
                ultimaAtuacaoPorUtilizador.get(utilizador.getId())));
    }

    /**
     * Sem data, quem tem vínculo à entidade (`utilizador_entidade`); com data, o roster de quem atuou por ela
     * desde essa data (`auditoria`), tenha ou não vínculo hoje. Fora da {@link Specification} para consultar
     * uma só vez, independentemente do provider JPA.
     */
    private List<UUID> idsDaEntidade(UtilizadorFiltro filtro) {
        if (filtro.atuouDesde() == null) {
            return utilizadorEntidadeRepository.findUtilizadorIdsByEntidadeId(filtro.entidadeId());
        }
        Instant desde = filtro.atuouDesde().atStartOfDay(ZONE_LISBOA).toInstant();
        return auditoriaRepository.findUtilizadorIdsQueAtuaramNaEntidadeDesde(filtro.entidadeId(), desde);
    }

    /** Uma só consulta para a página. */
    private Map<UUID, Instant> ultimaAtuacaoNaEntidadeEmLote(Long entidadeId, List<Utilizador> utilizadores) {
        List<UUID> ids = utilizadores.stream().map(Utilizador::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return auditoriaRepository.findUltimaAtuacaoNaEntidadeEmLote(entidadeId, ids).stream()
                .collect(Collectors.toMap(AuditoriaRepository.UltimaAtuacaoProjecao::getUtilizadorId,
                        AuditoriaRepository.UltimaAtuacaoProjecao::getUltimaAtuacaoEm));
    }

    /** Uma só consulta para a página. A fonte é `utilizador_entidade`, não a `auditoria`. */
    private Map<UUID, List<EntidadeVinculoResponse>> entidadesEmLote(List<Utilizador> utilizadores) {
        List<UUID> ids = utilizadores.stream().map(Utilizador::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Utilizador> porId = utilizadores.stream().collect(Collectors.toMap(Utilizador::getId, u -> u));
        Map<UUID, List<EntidadeVinculoResponse>> resultado = new HashMap<>();
        for (UtilizadorEntidade vinculo : utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(ids)) {
            UUID utilizadorId = vinculo.getId().getUtilizadorId();
            resultado.computeIfAbsent(utilizadorId, chave -> new ArrayList<>())
                    .add(paraVinculo(vinculo, porId.get(utilizadorId)));
        }
        return resultado;
    }

    /** `responsavel`: o email do responsável do termo de adesão é o desta pessoa (case-insensitive). */
    private EntidadeVinculoResponse paraVinculo(UtilizadorEntidade vinculo, Utilizador utilizador) {
        String emailResponsavel = vinculo.getEntidade().getResponsavelEmail();
        return new EntidadeVinculoResponse(vinculo.getEntidade().getId(), vinculo.getEntidade().getDescricaoIncm(),
                emailResponsavel != null && emailResponsavel.equalsIgnoreCase(utilizador.getEmail()));
    }

    /** Uma só consulta para a página. */
    private Map<UUID, Instant> ultimoLoginEmLote(List<Utilizador> utilizadores) {
        List<UUID> ids = utilizadores.stream().map(Utilizador::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return auditoriaRepository.findUltimoLoginEmLotePorUtilizador(ids).stream()
                .collect(Collectors.toMap(AuditoriaRepository.UltimoLoginProjecao::getUtilizadorId,
                        AuditoriaRepository.UltimoLoginProjecao::getUltimoLoginEm));
    }

    @Transactional(readOnly = true)
    public UtilizadorResponse obter(UUID id) {
        exigirAdmin();
        Utilizador utilizador = procurar(id);

        List<EntidadeVinculoResponse> entidades = utilizadorEntidadeRepository.findComEntidadeByUtilizadorIdIn(List.of(id))
                .stream()
                .map(vinculo -> paraVinculo(vinculo, utilizador))
                .toList();
        Instant ultimoLoginEm = auditoriaRepository.findUltimoLoginEmLotePorUtilizador(List.of(id)).stream()
                .findFirst().map(AuditoriaRepository.UltimoLoginProjecao::getUltimoLoginEm).orElse(null);
        return UtilizadorResponse.deCompleto(utilizador, entidades, ultimoLoginEm, null);
    }

    @Transactional
    public UtilizadorResponse alterarPerfilInterno(UUID id, AlterarPerfilInternoRequest request) {
        UUID atorId = exigirAdmin();
        Utilizador utilizador = procurar(id);

        if (!ORIGEM_AD_DGT.equals(utilizador.getOrigemAutenticacao())) {
            throw new ValidacaoException("Utilizador não é AD_DGT — não tem perfil interno.",
                    List.of(new ValidacaoException.CampoErro(CAMPO_PERFIL_INTERNO, "Utilizador de origem INCM.")));
        }
        PerfilInterno perfilNovo = validarPerfilInterno(request.perfilInterno(), "Perfil interno inválido.");
        if (deixariaZeroMasters(utilizador, perfilNovo)) {
            throw new EstadoInvalidoException(MENSAGEM_SEM_ADMINISTRADOR);
        }

        PerfilInterno perfilAntigo = utilizador.getPerfilInterno();
        utilizador.setPerfilInterno(perfilNovo);
        utilizadorRepository.save(utilizador);
        sessaoService.revogarSessoesAtivas(id);

        auditoriaService.registar(TIPO_OCORRENCIA, id.toString(), "ALTERAR_PERFIL_INTERNO", atorId,
                Map.of("perfilInternoAntes", String.valueOf(perfilAntigo), "perfilInternoDepois", perfilNovo.name()));
        return UtilizadorResponse.de(utilizador);
    }

    /** Logout forçado: o perfil não muda, por isso não se aplica a regra do último administrador. */
    @Transactional
    public RevogarSessoesResponse revogarSessoes(UUID id) {
        UUID atorId = exigirAdmin();
        procurar(id);

        int sessoesRevogadas = sessaoService.revogarSessoesAtivas(id);

        auditoriaService.registar(TIPO_OCORRENCIA, id.toString(), "REVOGAR_SESSOES", atorId,
                Map.of("sessoesRevogadas", sessoesRevogadas));
        return new RevogarSessoesResponse(sessoesRevogadas);
    }

    private Utilizador procurar(UUID id) {
        return utilizadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));
    }

    private PerfilInterno validarPerfilInterno(String perfilInterno, String mensagem) {
        return PerfilInterno.deTexto(perfilInterno).orElseThrow(() -> new ValidacaoException(mensagem, List.of(
                new ValidacaoException.CampoErro(CAMPO_PERFIL_INTERNO, MENSAGEM_PERFIL_INTERNO_VALIDO))));
    }

    /** A auto-despromoção é permitida enquanto houver outro `MASTER`. */
    private boolean deixariaZeroMasters(Utilizador utilizador, PerfilInterno perfilInternoNovo) {
        if (utilizador.getPerfilInterno() != PerfilInterno.MASTER || perfilInternoNovo == PerfilInterno.MASTER) {
            return false;
        }
        return utilizadorRepository.countByPerfilInterno(PerfilInterno.MASTER) <= 1;
    }

    private UUID exigirAdmin() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode administrar utilizadores.");
        }
        return contexto.utilizadorId();
    }

    private Specification<Utilizador> construirEspecificacao(UtilizadorFiltro filtro, PerfilInterno perfilInterno) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (filtro.origemAutenticacao() != null) {
                predicados.add(cb.equal(root.get("origemAutenticacao"), filtro.origemAutenticacao()));
            }
            if (perfilInterno != null) {
                predicados.add(cb.equal(root.get(CAMPO_PERFIL_INTERNO), perfilInterno));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private static Specification<Utilizador> idEm(List<UUID> ids) {
        return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }
}
