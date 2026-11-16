package com.conkord.saigt.service.idus;

import com.conkord.saigt.dto.snit.InfoEntradaVigorRequest;
import com.conkord.saigt.exception.EntradaVigorSnitException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusDinamica;
import com.conkord.saigt.persistence.operacional.IdusDinamicaRepository;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A chave é o n.º de depósito do processo da publicação final, com o n.º do processo como contravalidação. A
 * propagação corre depois do commit, em `PropagacaoIdusService`, para que uma falha não reverta a atualização local.
 */
@Slf4j
@Service
public class EntradaVigorService {

    private final ProcessoRepository processoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final IdusDinamicaRepository idusDinamicaRepository;
    private final IdusProcessoService idusProcessoService;
    private final Clock relogio;

    public EntradaVigorService(ProcessoRepository processoRepository, DinamicaRepository dinamicaRepository,
                               IdusDinamicaRepository idusDinamicaRepository, IdusProcessoService idusProcessoService,
                               Clock relogio) {
        this.processoRepository = processoRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.idusDinamicaRepository = idusDinamicaRepository;
        this.idusProcessoService = idusProcessoService;
        this.relogio = relogio;
    }

    @Transactional
    public EntradaEmVigorRegistada registar(InfoEntradaVigorRequest pedido) {
        String numeroDeposito = pedido.numeroDeposito().trim();
        Processo processo = processoRepository.findByNumeroDeposito(numeroDeposito)
                .orElseThrow(() -> EntradaVigorSnitException.processoDesconhecido(
                        "Não há processo com o n.º de depósito indicado."));
        if (pedido.saigtId() != null && !pedido.saigtId().equals(numero(processo.getNumeroProcesso()))) {
            throw EntradaVigorSnitException.validacao("O saigtId não corresponde ao processo do n.º de depósito.",
                    List.of(new ValidacaoException.CampoErro("saigtId", "Não corresponde ao numeroDeposito.")));
        }
        // Serializa duas comunicações da mesma Dinâmica: a segunda vê o IDUS da Dinâmica que a primeira gravou.
        Dinamica dinamica = dinamicaRepository.findByIdParaCriarProcesso(processo.getDinamica().getId())
                .orElseThrow(() -> EntradaVigorSnitException.processoDesconhecido("Dinâmica do processo não encontrada."));
        String idIgtSnit = Long.toString(pedido.idIGT());
        String ordemSnit = Integer.toString(pedido.ordemDinamica());

        Optional<IdusDinamica> existente = idusDinamicaRepository.findById(dinamica.getId());
        if (existente.isPresent()) {
            if (idIgtSnit.equals(existente.get().getIdIgtSnit()) && ordemSnit.equals(existente.get().getOrdemSnit())) {
                log.info("Entrada em vigor repetida com os mesmos dados, sem reprocessar — numeroDeposito={} dinamicaId={}",
                        numeroDeposito, dinamica.getId());
                return EntradaEmVigorRegistada.repetida(numeroDeposito, processo.getId());
            }
            throw EntradaVigorSnitException.conflito("A entrada em vigor desta dinâmica já foi recebida com outro "
                    + "idIGT ou outra ordemDinamica.");
        }

        List<IdusProcesso> atualizados;
        try {
            atualizados = idusProcessoService.registarEntradaEmVigor(dinamica.getId(), pedido.idIGT(),
                    pedido.ordemDinamica());
        } catch (DataIntegrityViolationException e) {
            // IDUS de processo repetido: outra Dinâmica do mesmo IGT já tem esta ordem. O rollback desfaz tudo.
            throw EntradaVigorSnitException.conflito("A ordemDinamica indicada já pertence a outra dinâmica deste IGT.");
        }
        List<UUID> processos = atualizados.stream().map(IdusProcesso::getProcessoId).toList();
        Long ssaigtId = numero(processo.getNumeroProcesso());
        Optional<IdusProcesso> doProcessoFinal = atualizados.stream()
                .filter(idus -> idus.getProcessoId().equals(processo.getId())).findFirst();
        if (doProcessoFinal.isEmpty()) {
            // Sem IDUS no processo final (tipos sem IDUS) não há com que compor o da Dinâmica.
            log.warn("IDUS da dinâmica por gerar, o processo da publicação final não tem IDUS — numeroDeposito={} "
                    + "processoId={}", numeroDeposito, processo.getId());
            return new EntradaEmVigorRegistada(numeroDeposito, true, processo.getId(), ssaigtId, null, processos);
        }
        String idusDinamica = ComposicaoIdus.comporDinamica(ComposicaoIdus.decompor(doProcessoFinal.get().getIdus()));
        if (idusDinamicaRepository.existsByIdus(idusDinamica)) {
            // Outra Dinâmica do mesmo IGT já tem esta ordem; o rollback desfaz a atualização dos processos.
            throw EntradaVigorSnitException.conflito("A ordemDinamica indicada já pertence a outra dinâmica deste IGT.");
        }
        idusDinamicaRepository.save(IdusDinamica.builder()
                .dinamica(dinamica)
                .idus(idusDinamica)
                .idIgtSnit(idIgtSnit)
                .ordemSnit(ordemSnit)
                .geradoEm(relogio.instant())
                .build());
        log.info("Entrada em vigor registada — numeroDeposito={} dinamicaId={} processos={} idusDinamica={} "
                        + "dataEntradaVigor={}", numeroDeposito, dinamica.getId(), atualizados.size(), idusDinamica,
                pedido.dataEntradaVigor());
        return new EntradaEmVigorRegistada(numeroDeposito, true, processo.getId(), ssaigtId, idusDinamica, processos);
    }

    private static Long numero(String numeroProcesso) {
        try {
            return numeroProcesso == null ? null : Long.valueOf(numeroProcesso);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
