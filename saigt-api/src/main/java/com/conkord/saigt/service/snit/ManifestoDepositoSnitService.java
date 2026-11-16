package com.conkord.saigt.service.snit;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.core.ResultadoChamadaExterna;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.DepositoRecebidoSnit;
import com.conkord.saigt.integracao.snit.DepositoSnit;
import com.conkord.saigt.integracao.snit.FicheiroDepositoSnit;
import com.conkord.saigt.integracao.snit.FicheirosRecebidosSnit;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifesto;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinha;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinhaRepository;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comunicação do depósito ao SNIT (BL-DOC-028): o manifesto grava-se com commit antes do `POST /deposits`, porque o
 * SNIT pode descarregar a pasta antes de a SAIGT registar a resposta. Um depósito falhado deixa o seu manifesto; a
 * nova tentativa grava outro, e o SNIT recebe sempre o último.
 */
@Slf4j
@Service
@EnableConfigurationProperties(DepositoSnitProperties.class)
public class ManifestoDepositoSnitService {

    private static final String PASTA_SUBSTITUIDOS = "/SUBSTITUIDOS/";

    private final ProcessoDocumentoRepository documentoRepository;
    private final DepositoSnitManifestoRepository manifestoRepository;
    private final DepositoSnitManifestoLinhaRepository linhaRepository;
    private final ClienteSnit clienteSnit;
    private final DepositoSnitProperties propriedades;
    private final TransactionTemplate transacaoPropria;
    private final Clock relogio;

    public ManifestoDepositoSnitService(ProcessoDocumentoRepository documentoRepository,
                                        DepositoSnitManifestoRepository manifestoRepository,
                                        DepositoSnitManifestoLinhaRepository linhaRepository,
                                        ClienteSnit clienteSnit, DepositoSnitProperties propriedades,
                                        PlatformTransactionManager transactionManager, Clock relogio) {
        this.documentoRepository = documentoRepository;
        this.manifestoRepository = manifestoRepository;
        this.linhaRepository = linhaRepository;
        this.clienteSnit = clienteSnit;
        this.propriedades = propriedades;
        this.transacaoPropria = new TransactionTemplate(transactionManager);
        this.transacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.relogio = relogio;
    }

    /**
     * Fixa os documentos atuais das caixas configuradas. `zipsComunicadosUmAUm` são os ZIP de peças gráficas que vão
     * em `deposits/files`: ficam marcados no manifesto, para nunca entrarem no ZIP da pasta.
     */
    public DepositoSnitManifesto gravar(UUID processoId, Set<UUID> zipsComunicadosUmAUm) {
        Objects.requireNonNull(processoId, "processoId");
        Objects.requireNonNull(zipsComunicadosUmAUm, "zipsComunicadosUmAUm");
        return transacaoPropria.execute(estado -> gravarNaTransacao(processoId, zipsComunicadosUmAUm));
    }

    /**
     * Grava o manifesto e só depois chama o SNIT; os ficheiros só seguem com o depósito aceite. Não deve correr dentro
     * de uma transação, para não a manter aberta durante as chamadas externas.
     */
    public ComunicacaoDepositoSnit comunicarDeposito(UUID processoId, DepositoSnit deposito,
                                                     List<FicheiroDepositoSnit> ficheiros,
                                                     ReferenciaChamada referencia) {
        Objects.requireNonNull(deposito, "deposito");
        Objects.requireNonNull(ficheiros, "ficheiros");
        Set<UUID> zips = ficheiros.stream().map(f -> UUID.fromString(f.fileId())).collect(Collectors.toSet());
        DepositoSnitManifesto manifesto = gravar(processoId, zips);

        ResultadoChamadaExterna<DepositoRecebidoSnit> resultadoDeposito =
                clienteSnit.registarDeposito(deposito, referencia);
        if (resultadoDeposito.temFalhaTecnica() || ficheiros.isEmpty()) {
            return new ComunicacaoDepositoSnit(manifesto.getId(), resultadoDeposito, null);
        }
        ResultadoChamadaExterna<FicheirosRecebidosSnit> resultadoFicheiros =
                clienteSnit.registarFicheirosDeposito(deposito.ssaigtId(), ficheiros, referencia);
        return new ComunicacaoDepositoSnit(manifesto.getId(), resultadoDeposito, resultadoFicheiros);
    }

    private DepositoSnitManifesto gravarNaTransacao(UUID processoId, Set<UUID> zipsComunicadosUmAUm) {
        Set<CategoriaDocumento> caixas = propriedades.caixas();
        List<ProcessoDocumento> documentos = documentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId)
                .stream()
                .filter(documento -> caixas.contains(documento.getCategoria()))
                .filter(documento -> !documento.getCaminhoFicheiro().contains(PASTA_SUBSTITUIDOS))
                .toList();
        Set<UUID> incluidos = documentos.stream().map(ProcessoDocumento::getId).collect(Collectors.toSet());
        if (!incluidos.containsAll(zipsComunicadosUmAUm)) {
            throw new IllegalArgumentException("Há ZIP a comunicar um a um fora das caixas do manifesto.");
        }

        DepositoSnitManifesto manifesto = manifestoRepository.save(
                new DepositoSnitManifesto(processoId, caixas, Instant.now(relogio)));
        linhaRepository.saveAll(documentos.stream()
                .map(documento -> new DepositoSnitManifestoLinha(manifesto.getId(), documento,
                        zipsComunicadosUmAUm.contains(documento.getId())))
                .toList());
        log.info("Manifesto do depósito no SNIT gravado — processoId={} manifestoId={} ficheiros={}", processoId,
                manifesto.getId(), documentos.size());
        return manifesto;
    }
}
