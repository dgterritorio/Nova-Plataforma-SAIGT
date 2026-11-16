package com.conkord.saigt.service.idus;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.CorrespondenciaIdusGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoCorrecaoIdentificadoresGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoPublicacaoGeoconform;
import com.conkord.saigt.persistence.operacional.EventoIdus;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistorico;
import com.conkord.saigt.persistence.operacional.IdusProcessoHistoricoRepository;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Só em PDM (primeira fase) e só nas Dinâmicas com GeoConform, as únicas com processo criado lá: primeiro o ato de publicação do processo final (I-10), depois a correção em lote dos IDUS
 * da tentativa (I-03). O GeoConform só conhece os IDUS anteriores à entrada em vigor até a I-03 os corrigir, por isso a
 * publicação vai com o IDUS que o processo final tinha antes. Vale também na Dinâmica sem informação vetorial, que tem
 * no GeoConform o processo leve da ficha. Uma falha fica num `ERROR`, sem nova tentativa e sem reverter nada.
 */
@Slf4j
@Service
public class PropagacaoIdusGeoconformService {

    static final String PDM = "PDM";

    private final ProcessoRepository processoRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final IdusProcessoHistoricoRepository historicoRepository;
    private final ClienteGeoconform clienteGeoconform;
    private final TransactionTemplate transacaoLeitura;

    public PropagacaoIdusGeoconformService(ProcessoRepository processoRepository,
                                           IdusProcessoRepository idusProcessoRepository,
                                           IdusProcessoHistoricoRepository historicoRepository,
                                           ClienteGeoconform clienteGeoconform,
                                           PlatformTransactionManager transactionManager) {
        this.processoRepository = processoRepository;
        this.idusProcessoRepository = idusProcessoRepository;
        this.historicoRepository = historicoRepository;
        this.clienteGeoconform = clienteGeoconform;
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transacaoLeitura.setReadOnly(true);
    }

    /** `publicacao` é nulo quando falta algum dos quatro campos obrigatórios, listados em `emFalta`. */
    record DadosGeoconform(String idusPublicacao, PedidoPublicacaoGeoconform publicacao, List<String> emFalta,
                           List<CorrespondenciaIdusGeoconform> correspondencias) {
    }

    private record Mudanca(String idusAnterior, String idusNovo) {
    }

    public void propagar(EntradaEmVigorRegistada registo) {
        Objects.requireNonNull(registo, "registo");
        Optional<DadosGeoconform> preparados = transacaoLeitura.execute(estado -> preparar(registo));
        if (preparados == null || preparados.isEmpty()) {
            return;
        }
        registarPublicacao(registo, preparados.get());
        corrigirIdentificadores(registo, preparados.get());
    }

    private Optional<DadosGeoconform> preparar(EntradaEmVigorRegistada registo) {
        Processo processoFinal = processoRepository.findById(registo.processoFinalId()).orElse(null);
        if (processoFinal == null) {
            log.error("Propagação do IDUS ao GeoConform não feita, processo da publicação final não encontrado "
                    + "| sistema=GEOCONFORM numeroDeposito={} processoId={}", registo.numeroDeposito(),
                    registo.processoFinalId());
            return Optional.empty();
        }
        if (!PDM.equals(processoFinal.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCodigo())
                || !Boolean.TRUE.equals(processoFinal.getDinamica().getComGeoconform())) {
            return Optional.empty();
        }
        Map<UUID, String> numeros = new HashMap<>();
        processoRepository.findAllById(registo.processos())
                .forEach(processo -> numeros.put(processo.getId(), processo.getNumeroProcesso()));
        numeros.put(processoFinal.getId(), processoFinal.getNumeroProcesso());
        Map<UUID, Mudanca> mudancas = mudancasNaEntradaEmVigor(registo.processos());

        List<CorrespondenciaIdusGeoconform> correspondencias = new ArrayList<>();
        for (UUID processoId : registo.processos()) {
            Mudanca mudanca = mudancas.get(processoId);
            if (mudanca != null && !mudanca.idusAnterior().equals(mudanca.idusNovo())) {
                correspondencias.add(new CorrespondenciaIdusGeoconform(mudanca.idusAnterior(), mudanca.idusNovo(),
                        numeros.get(processoId)));
            }
        }
        Mudanca doFinal = mudancas.get(processoFinal.getId());
        String idusPublicacao = doFinal != null ? doFinal.idusAnterior()
                : idusProcessoRepository.findById(processoFinal.getId()).map(IdusProcesso::getIdus)
                        .filter(idus -> !idus.isBlank()).orElse(null);

        List<String> emFalta = new ArrayList<>();
        if (vazio(processoFinal.getNumeroProcesso())) {
            emFalta.add("numprocSaigt");
        }
        if (vazio(processoFinal.getNumeroDr())) {
            emFalta.add("numeroAtoIncm");
        }
        if (vazio(processoFinal.getLinkDr())) {
            emFalta.add("linkPublicacaoIncm");
        }
        if (processoFinal.getDataPublicacaoDr() == null) {
            emFalta.add("dataPublicacao");
        }
        PedidoPublicacaoGeoconform publicacao = emFalta.isEmpty()
                ? new PedidoPublicacaoGeoconform(processoFinal.getNumeroProcesso(), processoFinal.getNumeroDr(),
                        processoFinal.getLinkDr(), processoFinal.getDataPublicacaoDr())
                : null;
        return Optional.of(new DadosGeoconform(idusPublicacao, publicacao, List.copyOf(emFalta),
                List.copyOf(correspondencias)));
    }

    /** IDUS de cada processo antes e depois da entrada em vigor, que pode ter mudado o ID_IGT e a Ordem_Dinâmica. */
    private Map<UUID, Mudanca> mudancasNaEntradaEmVigor(List<UUID> processos) {
        Map<UUID, Mudanca> mudancas = new LinkedHashMap<>();
        if (processos.isEmpty()) {
            return mudancas;
        }
        for (IdusProcessoHistorico linha : historicoRepository.findByProcessoIdInAndEventoOrderByIdAsc(processos,
                EventoIdus.ENTRADA_EM_VIGOR)) {
            if (linha.getIdusAnterior() == null) {
                continue;
            }
            mudancas.merge(linha.getProcessoId(), new Mudanca(linha.getIdusAnterior(), linha.getIdusNovo()),
                    (primeira, seguinte) -> new Mudanca(primeira.idusAnterior(), seguinte.idusNovo()));
        }
        return mudancas;
    }

    private void registarPublicacao(EntradaEmVigorRegistada registo, DadosGeoconform dados) {
        if (dados.idusPublicacao() == null) {
            log.error("Publicação não registada no GeoConform, o processo da publicação final não tem IDUS "
                    + "| sistema=GEOCONFORM numeroDeposito={} processoId={}", registo.numeroDeposito(),
                    registo.processoFinalId());
            return;
        }
        if (dados.publicacao() == null) {
            log.error("Publicação não registada no GeoConform, faltam dados da publicação | sistema=GEOCONFORM "
                    + "numeroDeposito={} processoId={} emFalta={}", registo.numeroDeposito(), registo.processoFinalId(),
                    dados.emFalta());
            return;
        }
        try {
            ResultadoChamadaExterna<Void> resultado = clienteGeoconform.registarPublicacao(dados.idusPublicacao(),
                    dados.publicacao(), new ReferenciaChamada(registo.processoFinalId(), dados.idusPublicacao(), null));
            if (!resultado.temFalhaTecnica()) {
                log.info("Publicação registada no GeoConform — numeroDeposito={} processoId={} idus={} httpStatus={}",
                        registo.numeroDeposito(), registo.processoFinalId(), dados.idusPublicacao(),
                        resultado.httpStatus());
                return;
            }
            log.error("Registo da publicação no GeoConform falhou, sem nova tentativa | sistema=GEOCONFORM "
                            + "numeroDeposito={} processoId={} idus={} httpStatus={} falha={}", registo.numeroDeposito(),
                    registo.processoFinalId(), dados.idusPublicacao(), resultado.httpStatus(), resultado.falhaTecnica());
        } catch (RuntimeException e) {
            log.error("Registo da publicação no GeoConform falhou, sem nova tentativa | sistema=GEOCONFORM "
                    + "numeroDeposito={} processoId={} idus={} erro={}", registo.numeroDeposito(),
                    registo.processoFinalId(), dados.idusPublicacao(), e.getClass().getSimpleName());
        }
    }

    private void corrigirIdentificadores(EntradaEmVigorRegistada registo, DadosGeoconform dados) {
        if (registo.idusDinamica() == null) {
            log.warn("IDUS da tentativa não corrigidos no GeoConform, o IDUS da dinâmica não foi gerado "
                    + "— numeroDeposito={} processoId={}", registo.numeroDeposito(), registo.processoFinalId());
            return;
        }
        if (dados.correspondencias().isEmpty()) {
            log.info("Nenhum IDUS provisório a corrigir no GeoConform — numeroDeposito={} idusDinamica={}",
                    registo.numeroDeposito(), registo.idusDinamica());
            return;
        }
        PedidoCorrecaoIdentificadoresGeoconform pedido = PedidoCorrecaoIdentificadoresGeoconform.publicacaoFinal(
                registo.idusDinamica(), dados.correspondencias());
        try {
            ResultadoChamadaExterna<Void> resultado = clienteGeoconform.corrigirIdentificadores(pedido,
                    new ReferenciaChamada(registo.processoFinalId(), registo.idusDinamica(), null));
            if (!resultado.temFalhaTecnica()) {
                log.info("IDUS da tentativa corrigidos no GeoConform — numeroDeposito={} idusDinamica={} "
                                + "correspondencias={} httpStatus={}", registo.numeroDeposito(), registo.idusDinamica(),
                        dados.correspondencias().size(), resultado.httpStatus());
                return;
            }
            log.error("Correção dos IDUS da tentativa no GeoConform falhou, sem nova tentativa | sistema=GEOCONFORM "
                            + "numeroDeposito={} idusDinamica={} correspondencias={} httpStatus={} falha={}",
                    registo.numeroDeposito(), registo.idusDinamica(), dados.correspondencias().size(),
                    resultado.httpStatus(), resultado.falhaTecnica());
        } catch (RuntimeException e) {
            log.error("Correção dos IDUS da tentativa no GeoConform falhou, sem nova tentativa | sistema=GEOCONFORM "
                    + "numeroDeposito={} idusDinamica={} erro={}", registo.numeroDeposito(), registo.idusDinamica(),
                    e.getClass().getSimpleName());
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
