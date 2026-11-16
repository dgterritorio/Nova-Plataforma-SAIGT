package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.DescargaProperties;
import com.conkord.saigt.exception.DescargaIndisponivelException;
import com.conkord.saigt.exception.LimiteDescargasClienteException;
import com.conkord.saigt.exception.ProcessoDesconhecidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.operacional.ResultadoDescarga;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pasta de depósito e ZIP de peças gráficas lidos pelo SNIT (BL-DOC-028, BL-DOC-029). O conteúdo é sempre o do
 * manifesto, lido pelo `versionId`. Processo inexistente, sem depósito e id fora do manifesto dão o mesmo 404; os
 * pedidos de um processo com depósito ficam auditados mesmo quando recusados.
 */
@Service
public class DescargaDepositoSnitService {

    private static final Logger log = LoggerFactory.getLogger(DescargaDepositoSnitService.class);
    private static final String METRICA = "documento.descarga.snit.total";

    private final ConsultaDepositoSnitService consulta;
    private final AuditoriaDescargaService auditoria;
    private final ArmazenamentoDocumentos armazenamento;
    private final LimiteDescargas limite;
    private final MeterRegistry metricas;
    private final Duration repetirApos;

    public DescargaDepositoSnitService(ConsultaDepositoSnitService consulta, AuditoriaDescargaService auditoria,
                                       ArmazenamentoDocumentos armazenamento, LimiteDescargas limite,
                                       MeterRegistry metricas, DescargaProperties propriedades) {
        this.consulta = consulta;
        this.auditoria = auditoria;
        this.armazenamento = armazenamento;
        this.limite = limite;
        this.metricas = metricas;
        this.repetirApos = propriedades.retryAfter();
    }

    public DepositoSnitManifestado localizarDeposito(String numeroProcesso) {
        return consulta.ultimoDeposito(numeroProcesso).orElseThrow(ProcessoDesconhecidoException::new);
    }

    /** O ZIP de peça gráfica comunicado um a um; qualquer outro id fica `RECUSADA` e dá o 404 de sempre. */
    public EntradaDepositoSnit localizarFicheiro(DepositoSnitManifestado deposito, String id, String ip) {
        Optional<EntradaDepositoSnit> entrada = lerId(id)
                .flatMap(documentoId -> consulta.ficheiroUmAUm(deposito.manifestoId(), documentoId));
        if (entrada.isEmpty()) {
            auditoria.registarSnit(deposito.processoId(), null, deposito.manifestoId(), ip, ResultadoDescarga.RECUSADA);
            contar(ResultadoDescarga.RECUSADA);
            throw new ProcessoDesconhecidoException();
        }
        return entrada.get();
    }

    /** Aceita a pasta ou recusa-a (429, 503), deixando a linha `RECUSADA`. */
    public DescargaPastaEmCurso iniciarPasta(DepositoSnitManifestado deposito, String clienteId, String ip) {
        List<EntradaDepositoSnit> entradas = consulta.entradasDaPasta(deposito);
        verificarQueCabe(deposito, entradas);
        ocuparLugar(deposito, null, clienteId, ip);
        Long descargaId = null;
        try {
            descargaId = auditoria.registarSnit(deposito.processoId(), null, deposito.manifestoId(), ip,
                    ResultadoDescarga.INICIADA);
            InputStream primeiro = entradas.isEmpty() ? null : abrir(entradas.get(0), 0, entradas.get(0).tamanhoBytes());
            return new DescargaPastaEmCurso(entradas, primeiro, armazenamento, deposito.criadoEm(), descargaId,
                    auditoria, () -> limite.libertarCliente(clienteId));
        } catch (RuntimeException e) {
            recusar(descargaId, clienteId);
            throw e;
        }
    }

    public void terminarPasta(DescargaPastaEmCurso descarga, DepositoSnitManifestado deposito) {
        ResultadoDescarga resultado = descarga.fechar();
        contar(resultado);
        log.info("Descarga da pasta de depósito pelo SNIT | manifestoId={} resultado={} bytes={}",
                deposito.manifestoId(), resultado, descarga.bytesEnviados());
    }

    /** Aceita a descarga do ZIP ou recusa-a (429, 503, 404 do objeto), deixando a linha `RECUSADA`. */
    public DescargaEmCurso iniciarFicheiro(DepositoSnitManifestado deposito, EntradaDepositoSnit entrada,
                                           String clienteId, String ip, long inicio, long comprimento) {
        ocuparLugar(deposito, entrada.documentoId(), clienteId, ip);
        Long descargaId = null;
        try {
            descargaId = auditoria.registarSnit(deposito.processoId(), entrada.documentoId(), deposito.manifestoId(),
                    ip, ResultadoDescarga.INICIADA);
            InputStream conteudo = abrir(entrada, inicio, comprimento);
            return new DescargaEmCurso(conteudo, descargaId, auditoria, () -> limite.libertarCliente(clienteId));
        } catch (RuntimeException e) {
            recusar(descargaId, clienteId);
            throw e;
        }
    }

    public void terminarFicheiro(DescargaEmCurso descarga, DepositoSnitManifestado deposito,
                                 EntradaDepositoSnit entrada) {
        ResultadoDescarga resultado = descarga.fechar();
        contar(resultado);
        log.info("Descarga de ficheiro do depósito pelo SNIT | manifestoId={} documentoId={} resultado={} bytes={}",
                deposito.manifestoId(), entrada.documentoId(), resultado, descarga.bytesEnviados());
    }

    private void ocuparLugar(DepositoSnitManifestado deposito, UUID documentoId, String clienteId, String ip) {
        if (!limite.ocuparCliente(clienteId)) {
            auditoria.registarSnit(deposito.processoId(), documentoId, deposito.manifestoId(), ip,
                    ResultadoDescarga.RECUSADA);
            contar(ResultadoDescarga.RECUSADA);
            throw new LimiteDescargasClienteException();
        }
    }

    /** Objeto em falta é o 404 de sempre; qualquer outra falha antes de haver bytes é o 503 com `Retry-After`. */
    private InputStream abrir(EntradaDepositoSnit entrada, long inicio, long comprimento) {
        try {
            return armazenamento.abrir(entrada.chave(), entrada.versao(), inicio, comprimento);
        } catch (RecursoNaoEncontradoException e) {
            log.error("Objeto do manifesto em falta no storage | documentoId={}", entrada.documentoId());
            throw new ProcessoDesconhecidoException();
        } catch (StorageIndisponivelException e) {
            log.warn("Storage indisponível ao iniciar a descarga do SNIT | documentoId={}", entrada.documentoId());
            throw new DescargaIndisponivelException(repetirApos, e);
        }
    }

    /** Sem ZIP64, um manifesto que não cabe falha antes de gastar um lugar ou de enviar um byte. */
    private static void verificarQueCabe(DepositoSnitManifestado deposito, List<EntradaDepositoSnit> entradas) {
        long bytesDosNomes = entradas.stream()
                .mapToLong(entrada -> entrada.caminhoNoZip().getBytes(StandardCharsets.UTF_8).length).sum();
        List<Long> tamanhos = entradas.stream().map(EntradaDepositoSnit::tamanhoBytes).toList();
        if (!EscritorZipSemCompressao.cabe(tamanhos, bytesDosNomes)) {
            throw new IllegalStateException("A pasta do manifesto " + deposito.manifestoId()
                    + " não cabe num ZIP sem ZIP64.");
        }
    }

    private void recusar(Long descargaId, String clienteId) {
        try {
            if (descargaId != null) {
                auditoria.concluir(descargaId, ResultadoDescarga.RECUSADA, 0);
                contar(ResultadoDescarga.RECUSADA);
            }
        } finally {
            limite.libertarCliente(clienteId);
        }
    }

    private static Optional<UUID> lerId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void contar(ResultadoDescarga resultado) {
        metricas.counter(METRICA, "resultado", resultado.name().toLowerCase()).increment();
    }
}
