package com.conkord.saigt.service.admin;

import com.conkord.saigt.dto.admin.EstatisticasAdminResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.core.AuditoriaRepository;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Métricas agregadas de `processo`/`auditoria`; só `MASTER`. */
@Service
public class AdminEstatisticasService {

    private static final Set<EstadoProcesso> ESTADOS_SUCESSO = Set.of(EstadoProcesso.ENVIADO_INCM, EstadoProcesso.PUBLICADO);
    private static final ZoneId ZONE_LISBOA = ZoneId.of("Europe/Lisbon");

    private final ProcessoRepository processoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public AdminEstatisticasService(ProcessoRepository processoRepository, DinamicaRepository dinamicaRepository,
                                     AuditoriaRepository auditoriaRepository, UtilizadorRepository utilizadorRepository,
                                     ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.processoRepository = processoRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public EstatisticasAdminResponse obter() {
        exigirMaster();

        long totalProcessos = processoRepository.count();
        long emAnalise = processoRepository.countByEstado(EstadoProcesso.EM_VALIDACAO_DGT);
        long publicadosComSucesso = processoRepository.countByEstadoIn(ESTADOS_SUCESSO);
        long comPrazoExpirado = dinamicaRepository.countByEstadoAndDataCaducidadeBefore(EstadoDinamica.EM_CURSO, LocalDate.now(ZONE_LISBOA));

        List<EstatisticasAdminResponse.SubmissaoMensal> submissoesPorMes = processoRepository.contarSubmissoesPorMes()
                .stream()
                .map(projecao -> new EstatisticasAdminResponse.SubmissaoMensal(projecao.getMes(), projecao.getTotal()))
                .toList();

        List<EstatisticasAdminResponse.EstadoContagem> distribuicaoPorEstado = processoRepository.contarPorEstado()
                .stream()
                .map(projecao -> new EstatisticasAdminResponse.EstadoContagem(projecao.getEstado().name(), projecao.getTotal()))
                .toList();

        List<Auditoria> ultimasAtividades = auditoriaRepository.findTop10ByOrderByCriadoEmDescIdDesc();
        Map<UUID, String> nomesPorUtilizador = nomesPorUtilizadorEmLote(ultimasAtividades);
        List<EstatisticasAdminResponse.AtividadeRecente> atividadeRecente = ultimasAtividades.stream()
                .map(auditoria -> paraAtividadeRecente(auditoria, nomesPorUtilizador))
                .toList();

        List<EstatisticasAdminResponse.PublicadosPorEntidade> publicadosPorEntidade = publicadosPorEntidade();

        return new EstatisticasAdminResponse(totalProcessos, emAnalise, publicadosComSucesso, comPrazoExpirado,
                submissoesPorMes, distribuicaoPorEstado, atividadeRecente, publicadosPorEntidade);
    }

    /** 3 consultas agregadas; o pivot PDM/PP/PU/PROT é feito em memória, nunca uma consulta por entidade. */
    private List<EstatisticasAdminResponse.PublicadosPorEntidade> publicadosPorEntidade() {
        Map<String, Map<String, Long>> totalPorEntidadeETipo = new LinkedHashMap<>();
        for (ProcessoRepository.PublicadosPorEntidadeETipoProjecao linha : processoRepository.contarPublicadosPorEntidadeETipo(ESTADOS_SUCESSO)) {
            totalPorEntidadeETipo
                    .computeIfAbsent(linha.getEntidadeNome(), chave -> new LinkedHashMap<>())
                    .put(linha.getTipoInstrumentoCodigo(), linha.getTotal());
        }

        Map<String, Long> caducadosPorEntidade = processoRepository.contarCaducadosPorEntidade(EstadoProcesso.ANULADOS).stream()
                .collect(Collectors.toMap(ProcessoRepository.ContagemPorEntidadeProjecao::getEntidadeNome,
                        ProcessoRepository.ContagemPorEntidadeProjecao::getTotal));

        Map<String, LocalDate> ultimaPublicacaoPorEntidade = processoRepository.ultimaPublicacaoPorEntidade(ESTADOS_SUCESSO).stream()
                .filter(linha -> linha.getUltimaPublicacao() != null)
                .collect(Collectors.toMap(ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao::getEntidadeNome,
                        ProcessoRepository.UltimaPublicacaoPorEntidadeProjecao::getUltimaPublicacao));

        return totalPorEntidadeETipo.entrySet().stream()
                .map(entrada -> {
                    String entidade = entrada.getKey();
                    Map<String, Long> porTipo = entrada.getValue();
                    long pdm = porTipo.getOrDefault("PDM", 0L);
                    long pp = porTipo.getOrDefault("PP", 0L);
                    long pu = porTipo.getOrDefault("PU", 0L);
                    long prot = porTipo.getOrDefault("PROT", 0L);
                    long total = porTipo.values().stream().mapToLong(Long::longValue).sum();
                    return new EstatisticasAdminResponse.PublicadosPorEntidade(entidade, pdm, pp, pu, prot, total,
                            caducadosPorEntidade.getOrDefault(entidade, 0L), ultimaPublicacaoPorEntidade.get(entidade));
                })
                .sorted((a, b) -> Long.compare(b.total(), a.total()))
                .toList();
    }

    /** Uma só consulta para os autores de toda a atividade recente (evita N+1). */
    private Map<UUID, String> nomesPorUtilizadorEmLote(List<Auditoria> atividades) {
        if (atividades.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = atividades.stream().map(Auditoria::getUtilizadorId).collect(Collectors.toSet());
        return utilizadorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Utilizador::getId, utilizador -> utilizador.getNome() != null ? utilizador.getNome() : utilizador.getEmail()));
    }

    private static EstatisticasAdminResponse.AtividadeRecente paraAtividadeRecente(Auditoria auditoria, Map<UUID, String> nomesPorUtilizador) {
        return new EstatisticasAdminResponse.AtividadeRecente(auditoria.getTipoOcorrencia(), auditoria.getIdOcorrencia(),
                auditoria.getAcao(), auditoria.getUtilizadorId(), nomesPorUtilizador.get(auditoria.getUtilizadorId()), auditoria.getCriadoEm());
    }

    private void exigirMaster() {
        if (contextoUtilizadorResolver.resolver().perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode consultar o painel administrativo.");
        }
    }
}
