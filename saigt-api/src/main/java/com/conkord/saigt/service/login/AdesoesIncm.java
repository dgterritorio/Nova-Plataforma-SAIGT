package com.conkord.saigt.service.login;

import com.conkord.saigt.integracao.incm.AdesaoIncm;
import com.conkord.saigt.integracao.incm.UtilizadorIncm;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Passos do login INCM comuns ao endpoint antigo e aos grants do servidor de autorização. */
@Component
public class AdesoesIncm {

    public static final String MENSAGEM_ENTIDADE_NAO_CRIADA =
            "A entidade não está criada na SAIGT. Foi enviado um pedido de apoio à DGT.";
    public static final String ACAO_LOGIN = "LOGIN";
    public static final String ACAO_LOGIN_RECUSADO = "LOGIN_RECUSADO";
    public static final String TIPO_OCORRENCIA_UTILIZADOR = "UTILIZADOR";
    private static final Logger log = LoggerFactory.getLogger(AdesoesIncm.class);
    private static final String MOTIVO_SEM_CORRESPONDENCIA = "ENTIDADE_SEM_CORRESPONDENCIA";
    private static final String ORIGEM_INCM = "INCM";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final UtilizadorRepository utilizadorRepository;
    private final EntidadeRepository entidadeRepository;
    private final CorrespondenciaEntidadeService correspondenciaEntidadeService;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher publicador;

    public AdesoesIncm(UtilizadorRepository utilizadorRepository, EntidadeRepository entidadeRepository,
                       CorrespondenciaEntidadeService correspondenciaEntidadeService, AuditoriaService auditoriaService,
                       ApplicationEventPublisher publicador) {
        this.utilizadorRepository = utilizadorRepository;
        this.entidadeRepository = entidadeRepository;
        this.correspondenciaEntidadeService = correspondenciaEntidadeService;
        this.auditoriaService = auditoriaService;
        this.publicador = publicador;
    }

    /**
     * Localiza a conta INCM pelo email ou cria-a; nos logins seguintes atualiza nome e cargo. Uma conta `AD_DGT` com o
     * mesmo email é outra conta da mesma pessoa e não é tocada.
     */
    public Utilizador upsertUtilizador(UtilizadorIncm incm) {
        Utilizador utilizador = utilizadorRepository.findByEmailAndOrigemAutenticacao(incm.email(), ORIGEM_INCM)
                .map(existente -> {
                    existente.setNome(incm.nome());
                    existente.setCargo(incm.cargo());
                    return existente;
                })
                .orElseGet(() -> {
                    Utilizador novo = new Utilizador(incm.nome(), incm.email(), ORIGEM_INCM);
                    novo.setCargo(incm.cargo());
                    return novo;
                });
        return utilizadorRepository.save(utilizador);
    }

    /**
     * A entidade da carga que corresponde à adesão; nunca cria. Atualiza o que vem da INCM e guarda a adesão inteira,
     * exceto o NIPC, a descrição INCM e o DICO, que são os da carga ou do Master e o login não os muda.
     */
    public Optional<Entidade> atualizarEntidade(AdesaoIncm adesao) {
        return correspondenciaEntidadeService.corresponder(adesao.nif(), adesao.nomeEntidade())
                .map(entidade -> {
                    entidade.setTelefone(adesao.telefone());
                    entidade.setEmail(adesao.email());
                    entidade.setResponsavelNome(adesao.responsavelNome());
                    entidade.setResponsavelEmail(adesao.responsavelEmail());
                    entidade.setResponsavelCargo(adesao.responsavelCargo());
                    AdesaoIncm.Morada morada = adesao.morada();
                    entidade.setMoradaArteria(morada == null ? null : morada.arteria());
                    entidade.setMoradaNumero(morada == null ? null : morada.numero());
                    entidade.setMoradaPiso(morada == null ? null : morada.piso());
                    entidade.setMoradaCodigoPostal(morada == null ? null : morada.codigoPostal());
                    entidade.setMoradaDesignacaoPostal(morada == null ? null : morada.designacaoPostal());
                    entidade.setMoradaLocalidade(morada == null ? null : morada.localidade());
                    entidade.setIncmEnrollmentId(adesao.incmEnrollmentId());
                    entidade.setDrParteId(adesao.drParteId());
                    entidade.setDrParteLetra(adesao.drParteLetra());
                    entidade.setDrParteDescricao(adesao.drParteDescricao());
                    entidade.setAtosSerie1(json(adesao.atosAutorizadosSerie1()));
                    entidade.setAtosSerie2(json(adesao.atosAutorizadosSerie2()));
                    if (adesao.corpo() != null) {
                        entidade.setDadosIncm(adesao.corpo());
                    }
                    entidade.setAtualizadoEm(Instant.now());
                    return entidadeRepository.save(entidade);
                });
    }

    private static String json(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar os atos autorizados da INCM.", e);
        }
    }

    /** As entidades SAIGT das adesões que têm correspondência. */
    public List<Entidade> atualizarEntidades(List<AdesaoIncm> adesoes) {
        return adesoes.stream().map(this::atualizarEntidade).flatMap(Optional::stream).toList();
    }

    /** Auditoria `LOGIN_RECUSADO` com o utilizador e o motivo (sem dados pessoais). */
    public void auditarRecusa(Utilizador utilizador, String motivo, String ipOrigem) {
        auditoriaService.registar(TIPO_OCORRENCIA_UTILIZADOR, utilizador.getId().toString(), ACAO_LOGIN_RECUSADO,
                utilizador.getId(), ipOrigem, Map.of("motivo", motivo));
    }

    /** Audita a recusa e avisa os Master pelo evento; o log só leva o utilizador e o motivo. */
    public void registarRecusaSemCorrespondencia(Utilizador utilizador, String descricaoIncm, String nipc,
                                                 String ipOrigem) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("motivo", MOTIVO_SEM_CORRESPONDENCIA);
        dados.put("descricaoIncm", descricaoIncm);
        auditoriaService.registar(TIPO_OCORRENCIA_UTILIZADOR, utilizador.getId().toString(), ACAO_LOGIN_RECUSADO,
                utilizador.getId(), ipOrigem, dados);
        publicador.publishEvent(new LoginRecusadoEntidadeSemCorrespondencia(utilizador.getId(), descricaoIncm, nipc));
        log.info("Login INCM recusado | utilizadorId={} motivo={}", utilizador.getId(), MOTIVO_SEM_CORRESPONDENCIA);
    }
}
