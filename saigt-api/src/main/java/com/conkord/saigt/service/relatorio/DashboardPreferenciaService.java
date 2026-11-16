package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.dto.relatorio.PreferenciaKpiRequest;
import com.conkord.saigt.dto.relatorio.PreferenciaKpiResponse;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.relatorio.DashboardPreferencia;
import com.conkord.saigt.persistence.relatorio.DashboardPreferenciaRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Preferências do dashboard de KPIs do próprio chamador; só `MASTER`. */
@Service
public class DashboardPreferenciaService {

    private static final Logger log = LoggerFactory.getLogger(DashboardPreferenciaService.class);

    private static final String MENSAGEM_PAYLOAD_INVALIDO = "Payload inválido.";
    private static final String CAMPO_CORPO = "corpo";

    private final DashboardPreferenciaRepository dashboardPreferenciaRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final Validator validator;

    public DashboardPreferenciaService(DashboardPreferenciaRepository dashboardPreferenciaRepository,
                                       AutorizacaoService autorizacaoService,
                                       ContextoUtilizadorResolver contextoUtilizadorResolver, Validator validator) {
        this.dashboardPreferenciaRepository = dashboardPreferenciaRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<PreferenciaKpiResponse> obter() {
        return obterDe(utilizadorMaster());
    }

    /** O que um Master vê sem preferências gravadas. Provisória até o OI-364 a definir: é o único sítio a mudar. */
    public List<PreferenciaKpiResponse> configuracaoPorOmissao() {
        KpiCodigo[] catalogo = KpiCodigo.values();
        List<PreferenciaKpiResponse> omissao = new ArrayList<>(catalogo.length);
        for (int ordem = 0; ordem < catalogo.length; ordem++) {
            omissao.add(resposta(catalogo[ordem], true, ordem));
        }
        return omissao;
    }

    @Transactional
    public List<PreferenciaKpiResponse> substituir(List<PreferenciaKpiRequest> pedido) {
        UUID utilizadorId = utilizadorMaster();
        validar(pedido);

        dashboardPreferenciaRepository.apagarDoUtilizador(utilizadorId);
        Instant agora = Instant.now();
        dashboardPreferenciaRepository.saveAllAndFlush(pedido.stream()
                .map(item -> new DashboardPreferencia(utilizadorId, item.kpiCodigo(), item.visivel(),
                        item.ordem().shortValue(), agora))
                .toList());
        log.info("Preferências do dashboard gravadas | kpis={}", pedido.size());
        return obterDe(utilizadorId);
    }

    private UUID utilizadorMaster() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        autorizacaoService.verificarPerfilMaster(contexto);
        return contexto.utilizadorId();
    }

    private List<PreferenciaKpiResponse> obterDe(UUID utilizadorId) {
        List<PreferenciaKpiResponse> gravadas = new ArrayList<>();
        for (DashboardPreferencia preferencia : dashboardPreferenciaRepository.findByIdUtilizadorIdOrderByOrdemAsc(utilizadorId)) {
            // Preferência de um KPI que saiu do catálogo: ignorada.
            KpiCodigo.deCodigo(preferencia.getKpiCodigo())
                    .ifPresent(kpi -> gravadas.add(resposta(kpi, preferencia.isVisivel(), preferencia.getOrdem())));
        }
        if (gravadas.isEmpty()) {
            return configuracaoPorOmissao();
        }

        Map<String, PreferenciaKpiResponse> omissao = configuracaoPorOmissao().stream()
                .collect(Collectors.toMap(PreferenciaKpiResponse::kpiCodigo, Function.identity()));
        Set<String> comPreferencia = gravadas.stream().map(PreferenciaKpiResponse::kpiCodigo).collect(Collectors.toSet());
        int proxima = gravadas.get(gravadas.size() - 1).ordem() + 1;
        List<PreferenciaKpiResponse> resultado = new ArrayList<>(gravadas);
        for (KpiCodigo kpi : KpiCodigo.values()) {
            if (!comPreferencia.contains(kpi.name())) {
                resultado.add(resposta(kpi, omissao.get(kpi.name()).visivel(), proxima++));
            }
        }
        return resultado;
    }

    private void validar(List<PreferenciaKpiRequest> pedido) {
        if (pedido == null) {
            throw new ValidacaoException(MENSAGEM_PAYLOAD_INVALIDO, List.of(new CampoErro(CAMPO_CORPO, "Obrigatório.")));
        }
        List<CampoErro> erros = new ArrayList<>();
        if (pedido.size() > KpiCodigo.values().length) {
            erros.add(new CampoErro(CAMPO_CORPO, "Há mais KPIs do que os do catálogo."));
        }
        Set<KpiCodigo> kpisVistos = EnumSet.noneOf(KpiCodigo.class);
        Set<Integer> ordensVistas = new HashSet<>();
        for (int i = 0; i < pedido.size(); i++) {
            PreferenciaKpiRequest item = pedido.get(i);
            String prefixo = "[" + i + "]";
            if (item == null) {
                erros.add(new CampoErro(prefixo, "Obrigatório."));
                continue;
            }
            List<CampoErro> errosFormato = errosDeFormato(prefixo, item);
            erros.addAll(errosFormato);
            String campoCodigo = prefixo + ".kpiCodigo";
            if (errosFormato.stream().noneMatch(erro -> erro.path().equals(campoCodigo))) {
                Optional<KpiCodigo> kpi = KpiCodigo.deCodigo(item.kpiCodigo());
                if (kpi.isEmpty()) {
                    erros.add(new CampoErro(campoCodigo, "KPI desconhecido: " + item.kpiCodigo() + "."));
                } else if (!kpisVistos.add(kpi.get())) {
                    erros.add(new CampoErro(campoCodigo, "KPI repetido: " + item.kpiCodigo() + "."));
                }
            }
            if (item.ordem() != null && !ordensVistas.add(item.ordem())) {
                erros.add(new CampoErro(prefixo + ".ordem", "Posição repetida no dashboard."));
            }
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException(MENSAGEM_PAYLOAD_INVALIDO, erros);
        }
    }

    private List<CampoErro> errosDeFormato(String prefixo, PreferenciaKpiRequest item) {
        return validator.validate(item).stream()
                .sorted(Comparator.comparing((ConstraintViolation<PreferenciaKpiRequest> violacao) ->
                        violacao.getPropertyPath().toString()))
                .map(violacao -> new CampoErro(prefixo + "." + violacao.getPropertyPath(), violacao.getMessage()))
                .toList();
    }

    private static PreferenciaKpiResponse resposta(KpiCodigo kpi, boolean visivel, int ordem) {
        return new PreferenciaKpiResponse(kpi.name(), kpi.getDesignacao(), visivel, ordem);
    }
}
