package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.AtualizarPlantaRequest;
import com.conkord.saigt.dto.documento.PlantaFiltro;
import com.conkord.saigt.dto.documento.PlantaResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.PlantaNaoEncontradaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Plantas SAIGT/SNIT do BackOffice: só `MASTER` e `TECNICO`. O que aqui se grava é o que o SNIT lê. */
@Service
public class PlantaConsultaService {

    static final String TIPO_OCORRENCIA = "PLANTA";
    static final String ACAO_ATUALIZAR = "ATUALIZAR_PLANTA";

    private static final Set<PerfilInterno> PERFIS_AUTORIZADOS = Set.of(PerfilInterno.MASTER, PerfilInterno.TECNICO);
    private static final int MAX_NOME_REGULAMENTO = 500;
    private static final int MAX_NUMERO_FOLHA = 20;
    private static final int MAX_NOME_FICHEIRO = 300;

    private final PlantaRepository plantaRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public PlantaConsultaService(PlantaRepository plantaRepository, IdusProcessoRepository idusProcessoRepository,
                                 UtilizadorRepository utilizadorRepository, AuditoriaService auditoriaService,
                                 ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.plantaRepository = plantaRepository;
        this.idusProcessoRepository = idusProcessoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public Page<PlantaResponse> listar(PlantaFiltro filtro, int page, int size) {
        autorizar();
        Page<Planta> plantas = plantaRepository.findAll(comFiltro(filtro), PageRequest.of(page, size));
        Map<UUID, String> idusPorProcesso = idusDosProcessos(plantas.getContent());
        return plantas.map(planta -> PlantaResponse.de(planta, idusPorProcesso.get(planta.getProcesso().getId())));
    }

    @Transactional
    public PlantaResponse atualizar(UUID plantaId, AtualizarPlantaRequest pedido) {
        ContextoUtilizador contexto = autorizar();
        validar(pedido);
        Planta planta = plantaRepository.findById(plantaId).orElseThrow(PlantaNaoEncontradaException::new);

        Map<String, Object> antes = new LinkedHashMap<>();
        Map<String, Object> depois = new LinkedHashMap<>();
        aplicar("nomeRegulamento", planta.getNomeRegulamento(), pedido.nomeRegulamento(), planta::setNomeRegulamento, antes, depois);
        aplicar("numeroFolha", planta.getNumeroFolha(), pedido.numeroFolha(), planta::setNumeroFolha, antes, depois);
        aplicar("nomeTiff", planta.getNomeTiff(), pedido.nomeTiff(), planta::setNomeTiff, antes, depois);
        aplicar("nomeZip", planta.getNomeZip(), pedido.nomeZip(), planta::setNomeZip, antes, depois);
        if (pedido.emVigor() != null && pedido.emVigor() != planta.isEmVigor()) {
            antes.put("emVigor", planta.isEmVigor());
            depois.put("emVigor", pedido.emVigor());
            planta.setEmVigor(pedido.emVigor());
        }
        planta.setAtualizadoPor(utilizadorRepository.getReferenceById(contexto.utilizadorId()));
        planta = plantaRepository.saveAndFlush(planta);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("antes", antes);
        dados.put("depois", depois);
        auditoriaService.registar(TIPO_OCORRENCIA, planta.getId().toString(), ACAO_ATUALIZAR, contexto.utilizadorId(), dados);

        String idus = idusProcessoRepository.findById(planta.getProcesso().getId()).map(IdusProcesso::getIdus).orElse(null);
        return PlantaResponse.de(planta, idus);
    }

    private ContextoUtilizador autorizar() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() == null || !PERFIS_AUTORIZADOS.contains(contexto.perfilInterno())) {
            throw new AcaoNaoAutorizadaException("Só os perfis Master e Técnico gerem as plantas.");
        }
        return contexto;
    }

    private static void validar(AtualizarPlantaRequest pedido) {
        if (pedido == null || pedido.vazio()) {
            throw new ValidacaoException("Indique pelo menos um campo a alterar.", List.of());
        }
        List<CampoErro> erros = new ArrayList<>();
        validarComprimento("nomeRegulamento", pedido.nomeRegulamento(), MAX_NOME_REGULAMENTO, erros);
        validarComprimento("numeroFolha", pedido.numeroFolha(), MAX_NUMERO_FOLHA, erros);
        validarComprimento("nomeTiff", pedido.nomeTiff(), MAX_NOME_FICHEIRO, erros);
        validarComprimento("nomeZip", pedido.nomeZip(), MAX_NOME_FICHEIRO, erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Payload inválido.", erros);
        }
    }

    private static void validarComprimento(String campo, String valor, int maximo, List<CampoErro> erros) {
        String aparado = aparar(valor);
        if (aparado != null && aparado.length() > maximo) {
            erros.add(new CampoErro(campo, "Máximo de " + maximo + " caracteres."));
        }
    }

    private static void aplicar(String campo, String atual, String recebido, Consumer<String> definir,
                                Map<String, Object> antes, Map<String, Object> depois) {
        if (recebido == null) {
            return;
        }
        String novo = aparar(recebido);
        if (!Objects.equals(atual, novo)) {
            antes.put(campo, atual);
            depois.put(campo, novo);
            definir.accept(novo);
        }
    }

    /** Nulo fica nulo (campo ausente); vazio depois de aparar passa a nulo. */
    private static String aparar(String valor) {
        if (valor == null) {
            return null;
        }
        String aparado = valor.strip();
        return aparado.isEmpty() ? null : aparado;
    }

    /**
     * Ordem: processo descendente, depois TIFF e ZIP; a linha do TXT e o id desempatam, para a paginação ser estável
     * com várias folhas do mesmo ZIP.
     */
    private static Specification<Planta> comFiltro(PlantaFiltro filtro) {
        return (raiz, consulta, cb) -> {
            Path<Processo> processo = raiz.get("processo");
            if (!ehContagem(consulta.getResultType())) {
                raiz.fetch("processo");
                raiz.fetch("atualizadoPor", JoinType.LEFT);
                consulta.orderBy(cb.desc(processo.get("numeroProcesso")), cb.asc(raiz.get("nomeTiff")),
                        cb.asc(raiz.get("nomeZip")), cb.asc(raiz.get("linhaTxt")), cb.asc(raiz.get("id")));
            }
            List<Predicate> condicoes = new ArrayList<>();
            if (temTexto(filtro.numeroProcesso())) {
                condicoes.add(cb.equal(processo.get("numeroProcesso"), filtro.numeroProcesso().strip()));
            }
            if (temTexto(filtro.numeroDeposito())) {
                condicoes.add(cb.equal(processo.get("numeroDeposito"), filtro.numeroDeposito().strip()));
            }
            if (filtro.emVigor() != null) {
                condicoes.add(cb.equal(raiz.get("emVigor"), filtro.emVigor()));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    private static boolean ehContagem(Class<?> tipoResultado) {
        return Long.class.equals(tipoResultado) || long.class.equals(tipoResultado);
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private Map<UUID, String> idusDosProcessos(List<Planta> plantas) {
        Set<UUID> processos = plantas.stream().map(planta -> planta.getProcesso().getId()).collect(Collectors.toSet());
        if (processos.isEmpty()) {
            return Map.of();
        }
        return idusProcessoRepository.findAllById(processos).stream()
                .collect(Collectors.toMap(IdusProcesso::getProcessoId, IdusProcesso::getIdus));
    }
}
