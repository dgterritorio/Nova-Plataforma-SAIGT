package com.conkord.saigt.service.snit;

import com.conkord.saigt.dto.referencia.ConcelhoSnitResponse;
import com.conkord.saigt.dto.referencia.NutIiSnitResponse;
import com.conkord.saigt.dto.referencia.ReferenciaSnitResponse;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lê da réplica `snit_referencia` as listas que os ecrãs escolhem; nunca chama o SNIT. */
@Service
public class ReferenciaSnitConsultaService {

    private static final Set<ListaReferenciaSnit> LISTAS_DISPONIVEIS =
            EnumSet.of(ListaReferenciaSnit.NUT2, ListaReferenciaSnit.MUNICIPALITIES);
    private static final int TAMANHO_MAXIMO_NUT2 = 50;
    // Em Java e não no ORDER BY: a collation da base não ordena os acentos à portuguesa.
    private static final Collator COLLATOR_PT = Collator.getInstance(Locale.forLanguageTag("pt-PT"));

    private final SnitReferenciaRepository snitReferenciaRepository;
    private final ObjectMapper objectMapper;

    public ReferenciaSnitConsultaService(SnitReferenciaRepository snitReferenciaRepository, ObjectMapper objectMapper) {
        this.snitReferenciaRepository = snitReferenciaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<? extends ReferenciaSnitResponse> listar(String lista, String nut2) {
        ListaReferenciaSnit listaPedida = lerLista(lista);
        if (listaPedida == ListaReferenciaSnit.NUT2) {
            return nutsII();
        }
        return concelhos(lerNut2(nut2));
    }

    /** As NUTS II da réplica com pelo menos um concelho ativo, pelo nome. */
    @Transactional(readOnly = true)
    public List<NutIiSnitResponse> nutsII() {
        // Em Java e não com o ->> do jsonb: corre também nos testes em H2.
        Set<String> comConcelho = snitReferenciaRepository.findByListaAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES).stream()
                .map(this::codigoNutII)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return snitReferenciaRepository.findByListaAndAtivoTrue(ListaReferenciaSnit.NUT2).stream()
                .filter(nutII -> comConcelho.contains(nutII.getCodigo()))
                .map(nutII -> new NutIiSnitResponse(nutII.getCodigo(), nutII.getDesignacao()))
                .sorted(Comparator.comparing(NutIiSnitResponse::nome, COLLATOR_PT)
                        .thenComparing(NutIiSnitResponse::codigo))
                .toList();
    }

    /** Os concelhos ativos da réplica, todos ou só os da NUT II `nut2`, pelo nome. */
    @Transactional(readOnly = true)
    public List<ConcelhoSnitResponse> concelhos(String nut2) {
        List<SnitReferencia> linhas = nut2 == null
                ? snitReferenciaRepository.findByListaAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES)
                : snitReferenciaRepository.findConcelhosAtivosDaNutII(nut2);
        return linhas.stream()
                .map(concelho -> new ConcelhoSnitResponse(concelho.getCodigo(), concelho.getDesignacao(),
                        codigoNutII(concelho)))
                .sorted(Comparator.comparing(ConcelhoSnitResponse::nome, COLLATOR_PT)
                        .thenComparing(ConcelhoSnitResponse::codigo))
                .toList();
    }

    private static ListaReferenciaSnit lerLista(String lista) {
        return LISTAS_DISPONIVEIS.stream()
                .filter(disponivel -> disponivel.name().equals(lista))
                .findFirst()
                .orElseThrow(() -> new ValidacaoException("Lista não disponível.",
                        List.of(new ValidacaoException.CampoErro("lista", "Valores aceites: NUT2, MUNICIPALITIES."))));
    }

    private static String lerNut2(String nut2) {
        if (nut2 == null || nut2.isBlank()) {
            return null;
        }
        String codigo = nut2.strip();
        if (codigo.length() > TAMANHO_MAXIMO_NUT2) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.",
                    List.of(new ValidacaoException.CampoErro("nut2", "Tem de ter no máximo 50 caracteres.")));
        }
        return codigo;
    }

    /**
     * `classId` do registo do tipo de Instrumento no `instrument-types` da réplica, para o registo da publicação no
     * SNIT. Vazio sem `snit_codigo`, sem registo ativo ou sem `classId` numérico.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> classIdDoTipo(TipoInstrumento tipoInstrumento) {
        String snitCodigo = tipoInstrumento.getSnitCodigo();
        if (snitCodigo == null || snitCodigo.isBlank()) {
            return Optional.empty();
        }
        return snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, snitCodigo)
                .filter(SnitReferencia::isAtivo)
                .flatMap(this::classId);
    }

    /**
     * O nome de cada DTCC na lista `MUNICIPALITIES` da réplica, numa só consulta. Todos os códigos pedidos ficam no
     * mapa: um código sem nome na réplica dá o próprio código. Um concelho desativado no SNIT continua a ter nome.
     */
    @Transactional(readOnly = true)
    public Map<String, String> nomesConcelhos(Collection<String> dtccs) {
        if (dtccs == null || dtccs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> nomes = new HashMap<>();
        snitReferenciaRepository.findByListaAndCodigoIn(ListaReferenciaSnit.MUNICIPALITIES, Set.copyOf(dtccs))
                .forEach(concelho -> nomes.put(concelho.getCodigo(), concelho.getDesignacao()));
        dtccs.forEach(dtcc -> nomes.computeIfAbsent(dtcc, Function.identity()));
        return nomes;
    }

    /** Os nomes dos concelhos pela ordem dos códigos (ver {@link #nomesConcelhos(Collection)}). */
    @Transactional(readOnly = true)
    public List<String> nomesConcelhosPorOrdem(List<String> dtccs) {
        Map<String, String> nomes = nomesConcelhos(dtccs);
        return dtccs == null ? List.of() : dtccs.stream().map(nomes::get).toList();
    }

    /** `nut2Code` do registo ativo do concelho em `municipalities` da réplica; vazio sem registo ou sem o campo. */
    @Transactional(readOnly = true)
    public Optional<String> nut2DoMunicipio(String dtcc) {
        if (dtcc == null || dtcc.isBlank()) {
            return Optional.empty();
        }
        return snitReferenciaRepository.findByListaAndCodigo(ListaReferenciaSnit.MUNICIPALITIES, dtcc)
                .filter(SnitReferencia::isAtivo)
                .map(this::codigoNutII);
    }

    private Optional<Integer> classId(SnitReferencia tipo) {
        try {
            JsonNode classe = objectMapper.readTree(tipo.getDadosOrigem())
                    .path(ListaReferenciaSnit.CHAVE_CLASSE_DO_TIPO_INSTRUMENTO);
            return classe.canConvertToInt() ? Optional.of(classe.asInt()) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String codigoNutII(SnitReferencia concelho) {
        try {
            String codigo = objectMapper.readTree(concelho.getDadosOrigem())
                    .path(ListaReferenciaSnit.CHAVE_NUT2_DO_MUNICIPIO).asText("").strip();
            return codigo.isEmpty() ? null : codigo;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
