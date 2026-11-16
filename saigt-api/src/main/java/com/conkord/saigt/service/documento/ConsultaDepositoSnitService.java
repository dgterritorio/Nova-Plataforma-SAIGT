package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.operacional.DepositoSnitManifesto;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinha;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoLinhaRepository;
import com.conkord.saigt.persistence.operacional.DepositoSnitManifestoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leitura do manifesto do último depósito comunicado (DB-DOC-008, DB-DOC-009), em transações curtas: o envio nunca
 * mantém uma aberta. O manifesto é imutável, por isso ler o cabeçalho e as linhas em separado é consistente.
 */
@Service
public class ConsultaDepositoSnitService {

    private static final String RAIZ = "processos/";
    private static final String PASTA_SUBSTITUIDOS = "/SUBSTITUIDOS/";

    private final ProcessoRepository processoRepository;
    private final DepositoSnitManifestoRepository manifestoRepository;
    private final DepositoSnitManifestoLinhaRepository linhaRepository;

    public ConsultaDepositoSnitService(ProcessoRepository processoRepository,
                                       DepositoSnitManifestoRepository manifestoRepository,
                                       DepositoSnitManifestoLinhaRepository linhaRepository) {
        this.processoRepository = processoRepository;
        this.manifestoRepository = manifestoRepository;
        this.linhaRepository = linhaRepository;
    }

    /** Vazio se o processo não existe ou nunca teve um depósito comunicado. */
    @Transactional(readOnly = true)
    public Optional<DepositoSnitManifestado> ultimoDeposito(String numeroProcesso) {
        return processoRepository.findIdByNumeroProcesso(numeroProcesso)
                .flatMap(processoId -> manifestoRepository.findFirstByProcessoIdOrderByCriadoEmDesc(processoId))
                .map(ConsultaDepositoSnitService::manifestado);
    }

    /**
     * Os ficheiros da pasta, por ordem do caminho: só os das caixas do manifesto, nunca `SUBSTITUIDOS/` nem os ZIP
     * comunicados um a um.
     */
    @Transactional(readOnly = true)
    public List<EntradaDepositoSnit> entradasDaPasta(DepositoSnitManifestado deposito) {
        return linhaRepository.findByIdManifestoId(deposito.manifestoId()).stream()
                .filter(linha -> !linha.isComunicadoUmAUm())
                .filter(linha -> !linha.getChaveObjeto().contains(PASTA_SUBSTITUIDOS))
                .map(ConsultaDepositoSnitService::entrada)
                .filter(entrada -> deposito.caixas().contains(caixaDe(entrada.caminhoNoZip())))
                .sorted(Comparator.comparing(EntradaDepositoSnit::caminhoNoZip))
                .toList();
    }

    /** Só um ZIP de peça gráfica comunicado um a um serve por id; qualquer outro, incluindo os da pasta, é vazio. */
    @Transactional(readOnly = true)
    public Optional<EntradaDepositoSnit> ficheiroUmAUm(UUID manifestoId, UUID documentoId) {
        return linhaRepository.findById(new DepositoSnitManifestoLinha.Id(manifestoId, documentoId))
                .filter(DepositoSnitManifestoLinha::isComunicadoUmAUm)
                .map(ConsultaDepositoSnitService::entrada);
    }

    private static DepositoSnitManifestado manifestado(DepositoSnitManifesto manifesto) {
        Set<String> caixas = manifesto.getCaixasIncluidas().stream().map(Enum::name).collect(Collectors.toSet());
        return new DepositoSnitManifestado(manifesto.getId(), manifesto.getProcessoId(), manifesto.getCriadoEm(),
                caixas);
    }

    /** A chave é `processos/{numero}/{CAIXA}/[GEOCONFORM/]{id}-{nome}`; o ZIP leva o que vem depois do número. */
    private static EntradaDepositoSnit entrada(DepositoSnitManifestoLinha linha) {
        String chave = linha.getChaveObjeto();
        int fimNumero = chave.startsWith(RAIZ) ? chave.indexOf('/', RAIZ.length()) : -1;
        if (fimNumero < 0 || fimNumero == chave.length() - 1) {
            throw new IllegalStateException("Chave de objeto fora do formato esperado no manifesto "
                    + linha.getId().getManifestoId() + ".");
        }
        return new EntradaDepositoSnit(linha.getId().getProcessoDocumentoId(), chave.substring(fimNumero + 1),
                chave.substring(chave.lastIndexOf('/') + 1), chave, linha.getVersaoObjeto(), linha.getTamanhoBytes(),
                linha.getHashSha256());
    }

    private static String caixaDe(String caminhoNoZip) {
        return caminhoNoZip.substring(0, Math.max(caminhoNoZip.indexOf('/'), 0));
    }
}
