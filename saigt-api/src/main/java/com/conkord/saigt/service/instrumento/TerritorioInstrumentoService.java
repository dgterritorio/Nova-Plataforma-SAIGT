package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TerritorioInstrumentoService {

    private static final Logger log = LoggerFactory.getLogger(TerritorioInstrumentoService.class);

    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ObjectMapper objectMapper;
    private final TerritorioLocalService territorioLocalService;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;

    public TerritorioInstrumentoService(InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                                        SnitReferenciaRepository snitReferenciaRepository, ObjectMapper objectMapper,
                                        TerritorioLocalService territorioLocalService,
                                        ReferenciaSnitConsultaService referenciaSnitConsultaService) {
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.objectMapper = objectMapper;
        this.territorioLocalService = territorioLocalService;
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
    }

    /**
     * Os nomes dos concelhos de cada Instrumento, pela ordem dos códigos DTCC, com o nome da réplica que o
     * {@link #territorio(UUID)} usa. Duas consultas para todos os Instrumentos: as listagens não pagam uma por linha.
     * Um Instrumento sem concelhos fica fora do mapa.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> nomesConcelhos(Collection<UUID> instrumentoIds) {
        if (instrumentoIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> codigos = instrumentoConcelhoRepository
                .findByIdInstrumentoIdInOrderByIdMunicipioCodigo(Set.copyOf(instrumentoIds)).stream()
                .collect(Collectors.groupingBy(concelho -> concelho.getId().getInstrumentoId(),
                        Collectors.mapping(InstrumentoConcelho::getMunicipioCodigo, Collectors.toList())));
        Map<String, String> nomes = referenciaSnitConsultaService.nomesConcelhos(
                codigos.values().stream().flatMap(List::stream).collect(Collectors.toSet()));
        return codigos.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entrada -> entrada.getValue().stream().map(nomes::get).toList()));
    }

    @Transactional(readOnly = true)
    public TerritorioInstrumentoResponse territorio(UUID instrumentoId) {
        List<String> codigosConcelho = instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(instrumentoId)
                .stream().map(InstrumentoConcelho::getMunicipioCodigo).toList();
        if (codigosConcelho.isEmpty()) {
            return TerritorioInstrumentoResponse.vazio();
        }

        Map<String, SnitReferencia> municipios = lerReplica(ListaReferenciaSnit.MUNICIPALITIES, codigosConcelho);
        List<ItemTerritorio> concelhos = new ArrayList<>();
        Set<String> codigosNutsII = new LinkedHashSet<>();
        for (String codigo : codigosConcelho) {
            SnitReferencia municipio = municipios.get(codigo);
            if (municipio == null) {
                log.warn("Concelho do Instrumento sem linha na réplica do SNIT | instrumentoId={} municipioCodigo={}",
                        instrumentoId, codigo);
                concelhos.add(new ItemTerritorio(codigo, territorioLocalService.nomeConcelho(codigo).orElse(null)));
                continue;
            }
            concelhos.add(new ItemTerritorio(codigo, municipio.getDesignacao()));
            String codigoNutII = codigoNutII(municipio);
            if (codigoNutII == null) {
                log.warn("Concelho da réplica do SNIT sem NUTS II | instrumentoId={} municipioCodigo={}", instrumentoId, codigo);
            } else {
                codigosNutsII.add(codigoNutII);
            }
        }

        Map<String, SnitReferencia> nutsII = lerReplica(ListaReferenciaSnit.NUT2, codigosNutsII);
        List<ItemTerritorio> itensNutsII = codigosNutsII.stream()
                .map(codigo -> {
                    SnitReferencia nutII = nutsII.get(codigo);
                    if (nutII == null) {
                        log.warn("NUTS II sem linha na réplica do SNIT | instrumentoId={} nut2Code={}", instrumentoId, codigo);
                    }
                    return new ItemTerritorio(codigo, nutII == null ? null : nutII.getDesignacao());
                })
                .toList();
        return new TerritorioInstrumentoResponse(List.copyOf(concelhos), itensNutsII);
    }

    /**
     * O território de uma Dinâmica: o do Instrumento de início ou, numa iniciada sem IGT, a união do dos Instrumentos
     * que abrange (vazio sem nenhum).
     */
    @Transactional(readOnly = true)
    public TerritorioInstrumentoResponse territorio(Dinamica dinamica) {
        if (dinamica.instrumentoDeInicio().isPresent()) {
            return territorio(dinamica.getInstrumento().getId());
        }
        Map<String, ItemTerritorio> concelhos = new TreeMap<>();
        Map<String, ItemTerritorio> nutsII = new TreeMap<>();
        dinamica.getInstrumentos().stream().map(Instrumento::getId).sorted().map(this::territorio).forEach(parte -> {
            parte.concelhos().forEach(item -> concelhos.putIfAbsent(item.codigo(), item));
            parte.nutsII().forEach(item -> nutsII.putIfAbsent(item.codigo(), item));
        });
        return new TerritorioInstrumentoResponse(List.copyOf(concelhos.values()), List.copyOf(nutsII.values()));
    }

    private Map<String, SnitReferencia> lerReplica(ListaReferenciaSnit lista, Collection<String> codigos) {
        if (codigos.isEmpty()) {
            return Map.of();
        }
        return snitReferenciaRepository.findByListaAndCodigoIn(lista, codigos).stream()
                .collect(Collectors.toMap(SnitReferencia::getCodigo, Function.identity()));
    }

    private String codigoNutII(SnitReferencia municipio) {
        try {
            String codigo = objectMapper.readTree(municipio.getDadosOrigem())
                    .path(ListaReferenciaSnit.CHAVE_NUT2_DO_MUNICIPIO).asText("").strip();
            return codigo.isEmpty() ? null : codigo;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
