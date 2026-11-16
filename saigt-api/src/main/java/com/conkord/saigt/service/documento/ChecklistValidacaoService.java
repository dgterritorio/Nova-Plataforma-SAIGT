package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.VerificacaoDocumentalResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ZipInvalidoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Checklist de validação DGT (BL-DOC-011): calculada a pedido sobre os ficheiros atuais do Processo, sem gravar nada.
 * É uma leitura — só Camadas 1 e 2. Um ZIP que não se consegue ler nunca faz falhar o pedido: as suas linhas ficam
 * para verificação manual.
 */
@Service
public class ChecklistValidacaoService {

    static final int DETALHE_MAXIMO = 500;
    static final String MSG_ATO_TEXTO = "Confirme que o texto do ato corresponde ao diploma.";
    static final String MSG_GEOTIFF_COM_SISTEMA = "GeoTIFF com sistema de referência EPSG:%d.";
    static final String MSG_TIF_COM_TFW = "TIF com TFW: confirme o sistema de referência, que o ficheiro não indica.";
    static final String MSG_SEM_GEORREFERENCIACAO =
            "Sem georreferenciação: falta o sistema de referência no TIFF ou o ficheiro %s.tfw.";
    static final String MSG_TIFF_ILEGIVEL = "Ficheiro TIFF ilegível.";
    static final String MSG_CONSISTENCIA = "Confirme a consistência entre a informação vetorial e as plantas matriciais.";
    static final String MSG_FOLHA = "Confirme que o n.º de folha do nome do ficheiro corresponde à planta.";
    static final String MSG_VERIFICACAO_MANUAL = "Não foi possível verificar automaticamente; verifique o ZIP à mão.";
    static final String MSG_FORMATO = "Formato %s não é %s para %s.";

    private static final Logger log = LoggerFactory.getLogger(ChecklistValidacaoService.class);
    private static final String PASTA_SUBSTITUIDOS = "/SUBSTITUIDOS/";
    private static final Set<String> EXTENSOES_TIF = Set.of("tif", "tiff");
    private static final String EXTENSAO_TFW = "tfw";

    private static final Comparator<VerificacaoDocumentalResponse> ORDEM = Comparator
            .comparing(VerificacaoDocumentalResponse::verificacao)
            .thenComparing(VerificacaoDocumentalResponse::nomeFicheiro,
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())));

    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final VerificadorZip verificadorZip;
    private final InspetorTiff inspetorTiff;

    public ChecklistValidacaoService(ProcessoRepository processoRepository,
                                     ProcessoDocumentoRepository processoDocumentoRepository,
                                     AutorizacaoService autorizacaoService,
                                     ContextoUtilizadorResolver contextoUtilizadorResolver,
                                     VerificadorZip verificadorZip,
                                     InspetorTiff inspetorTiff) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.verificadorZip = verificadorZip;
        this.inspetorTiff = inspetorTiff;
    }

    @Transactional(readOnly = true)
    public List<VerificacaoDocumentalResponse> verificar(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);

        List<ProcessoDocumento> atuais = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processoId)
                .stream().filter(ChecklistValidacaoService::atual).toList();

        List<VerificacaoDocumentalResponse> linhas = new ArrayList<>();
        linhas.add(doProcesso(VerificacaoChecklist.CORRESPONDENCIA_ATO_TEXTO, MSG_ATO_TEXTO));
        if (atuais.stream().anyMatch(documento -> tipoElemento(documento) == TipoElemento.PECA_GRAFICA)) {
            linhas.add(doProcesso(VerificacaoChecklist.CONSISTENCIA_VETORIAL_MATRICIAL, MSG_CONSISTENCIA));
        }
        for (ProcessoDocumento documento : atuais) {
            if (tipoElemento(documento) == TipoElemento.PECA_ESCRITA) {
                linhas.add(formatoQuadroAnexo(documento));
            }
            if (VerificadorZip.aplicaSe(documento.getNomeFicheiro())) {
                linhas.addAll(verificacoesDoZip(processoId, documento));
            }
        }
        linhas.sort(ORDEM);

        log.info("Checklist de validação calculada — processoId={} verificacoes={} falhas={}", processoId,
                linhas.size(), linhas.stream().filter(linha -> linha.resultado() == ResultadoVerificacao.FALHA).count());
        return linhas;
    }

    /** Os `SUBSTITUIDO` e as cópias em `SUBSTITUIDOS/` já só se consultam: não se validam. */
    private static boolean atual(ProcessoDocumento documento) {
        return documento.getEstadoValidacao() != EstadoValidacao.SUBSTITUIDO
                && !documento.getCaminhoFicheiro().contains(PASTA_SUBSTITUIDOS);
    }

    /** Nulo no balde «outros». */
    private static TipoElemento tipoElemento(ProcessoDocumento documento) {
        DocumentoConfig item = documento.getDocumentoConfig();
        return item == null ? null : item.getTipoElemento();
    }

    /** A regra é a da caixa onde o ficheiro está: editável em publicação, não editável em depósito. */
    private static VerificacaoDocumentalResponse formatoQuadroAnexo(ProcessoDocumento documento) {
        String extensao = CaminhoDocumento.extensao(documento.getNomeFicheiro());
        CategoriaDocumento categoria = documento.getCategoria();
        boolean aceite = TabelaExtensoesEf.extensoes(TipoElemento.PECA_ESCRITA, categoria)
                .map(extensoes -> extensoes.contains(extensao))
                .orElse(false);
        if (aceite) {
            return doFicheiro(VerificacaoChecklist.FORMATO_QUADRO_ANEXO, documento, ResultadoVerificacao.OK, null);
        }
        boolean publicacao = categoria == CategoriaDocumento.PUBLICACAO;
        return doFicheiro(VerificacaoChecklist.FORMATO_QUADRO_ANEXO, documento, ResultadoVerificacao.FALHA,
                MSG_FORMATO.formatted(extensao.isEmpty() ? "sem extensão" : extensao,
                        publicacao ? "editável" : "não editável", publicacao ? "publicação" : "depósito"));
    }

    /**
     * `CORRECAO_ZIP` lê só o diretório central do objeto gravado. Num ZIP de depósito de peças gráficas, as mesmas
     * entradas dão as linhas de cada TIF; se não se conseguirem listar, essas linhas ficam numa só, manual, do ZIP.
     */
    private List<VerificacaoDocumentalResponse> verificacoesDoZip(UUID processoId, ProcessoDocumento documento) {
        boolean comTifs = documento.getCategoria() == CategoriaDocumento.DEPOSITO
                && tipoElemento(documento) == TipoElemento.PECA_GRAFICA;
        List<VerificacaoDocumentalResponse> linhas = new ArrayList<>();
        List<String> entradas;
        try {
            entradas = verificadorZip.verificar(documento.getId(), documento.getCaminhoFicheiro(),
                    ProcessoDocumentoService.versaoParaLeitura(documento.getVersaoObjeto()), documento.getTamanhoBytes());
            linhas.add(doFicheiro(VerificacaoChecklist.CORRECAO_ZIP, documento, ResultadoVerificacao.OK, null));
        } catch (ZipInvalidoException e) {
            linhas.add(doFicheiro(VerificacaoChecklist.CORRECAO_ZIP, documento, ResultadoVerificacao.FALHA,
                    e.getCampos().get(0).erro()));
            entradas = comTifs ? entradasDeUmZipRecusado(processoId, documento) : null;
        } catch (RuntimeException e) {
            avisarFalhaTecnica(processoId, documento, e);
            linhas.add(doFicheiro(VerificacaoChecklist.CORRECAO_ZIP, documento, ResultadoVerificacao.MANUAL,
                    MSG_VERIFICACAO_MANUAL));
            entradas = null;
        }
        if (!comTifs) {
            return linhas;
        }
        if (entradas == null) {
            linhas.add(doFicheiro(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, documento,
                    ResultadoVerificacao.MANUAL, MSG_VERIFICACAO_MANUAL));
            linhas.add(doFicheiro(VerificacaoChecklist.FOLHA_NOME_TIF, documento, ResultadoVerificacao.MANUAL,
                    MSG_FOLHA));
            return linhas;
        }
        linhas.addAll(verificacoesDosTifs(processoId, documento, entradas));
        return linhas;
    }

    /** Nulo quando o ZIP é ilegível ou o armazenamento falha: as entradas não se conseguem listar. */
    private List<String> entradasDeUmZipRecusado(UUID processoId, ProcessoDocumento documento) {
        try {
            return verificadorZip.entradas(documento.getId(), documento.getCaminhoFicheiro(),
                    ProcessoDocumentoService.versaoParaLeitura(documento.getVersaoObjeto()), documento.getTamanhoBytes());
        } catch (ZipInvalidoException e) {
            return null;
        } catch (RuntimeException e) {
            avisarFalhaTecnica(processoId, documento, e);
            return null;
        }
    }

    private List<VerificacaoDocumentalResponse> verificacoesDosTifs(UUID processoId, ProcessoDocumento zip,
                                                                  List<String> entradas) {
        Set<String> basesComTfw = entradas.stream()
                .filter(entrada -> EXTENSAO_TFW.equals(CaminhoDocumento.extensao(entrada)))
                .map(entrada -> semExtensao(entrada).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<VerificacaoDocumentalResponse> linhas = new ArrayList<>();
        for (String entrada : entradas) {
            if (!EXTENSOES_TIF.contains(CaminhoDocumento.extensao(entrada))) {
                continue;
            }
            String base = semExtensao(entrada);
            linhas.add(georreferenciacao(processoId, zip, entrada, base,
                    basesComTfw.contains(base.toLowerCase(Locale.ROOT))));
            linhas.add(daEntrada(VerificacaoChecklist.FOLHA_NOME_TIF, zip, entrada, ResultadoVerificacao.MANUAL,
                    MSG_FOLHA));
        }
        return linhas;
    }

    /**
     * As mesmas leituras do carregamento: o GeoTIFF traz o sistema de referência; o TIF simples com TFW não o indica e
     * fica para confirmar.
     */
    private VerificacaoDocumentalResponse georreferenciacao(UUID processoId, ProcessoDocumento zip, String entrada,
                                                            String base, boolean comTfw) {
        CaracteristicasTiff tiff;
        try {
            tiff = inspetorTiff.inspecionar(zip.getCaminhoFicheiro(), entrada);
        } catch (TiffIlegivelException e) {
            return daEntrada(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, zip, entrada,
                    ResultadoVerificacao.FALHA, MSG_TIFF_ILEGIVEL);
        } catch (RuntimeException e) {
            log.warn("Checklist: TIFF não inspecionado — processoId={} processoDocumentoId={} motivo={}", processoId,
                    zip.getId(), e.getClass().getSimpleName());
            return daEntrada(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, zip, entrada,
                    ResultadoVerificacao.MANUAL, MSG_VERIFICACAO_MANUAL);
        }
        if (tiff.codigoEpsg() != null) {
            return daEntrada(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, zip, entrada, ResultadoVerificacao.OK,
                    MSG_GEOTIFF_COM_SISTEMA.formatted(tiff.codigoEpsg()));
        }
        return comTfw
                ? daEntrada(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, zip, entrada,
                ResultadoVerificacao.MANUAL, MSG_TIF_COM_TFW)
                : daEntrada(VerificacaoChecklist.GEORREFERENCIACAO_MATRICIAL, zip, entrada,
                ResultadoVerificacao.FALHA, MSG_SEM_GEORREFERENCIACAO.formatted(base));
    }

    /** Só ids e o tipo da exceção: a mensagem pode trazer a chave do objeto ou nomes de entradas. */
    private static void avisarFalhaTecnica(UUID processoId, ProcessoDocumento documento, RuntimeException e) {
        log.warn("Checklist: ZIP não verificado — processoId={} processoDocumentoId={} motivo={}", processoId,
                documento.getId(), e.getClass().getSimpleName());
    }

    private static String semExtensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? nome : nome.substring(0, ponto);
    }

    private static VerificacaoDocumentalResponse doProcesso(VerificacaoChecklist verificacao, String detalhe) {
        return new VerificacaoDocumentalResponse(verificacao, null, null, ResultadoVerificacao.MANUAL, detalhe);
    }

    private static VerificacaoDocumentalResponse doFicheiro(VerificacaoChecklist verificacao,
                                                            ProcessoDocumento documento,
                                                            ResultadoVerificacao resultado, String detalhe) {
        return daEntrada(verificacao, documento, documento.getNomeFicheiro(), resultado, detalhe);
    }

    private static VerificacaoDocumentalResponse daEntrada(VerificacaoChecklist verificacao,
                                                           ProcessoDocumento documento, String nomeFicheiro,
                                                           ResultadoVerificacao resultado, String detalhe) {
        return new VerificacaoDocumentalResponse(verificacao, documento.getId(), nomeFicheiro, resultado,
                limitar(detalhe));
    }

    static String limitar(String detalhe) {
        if (detalhe == null || detalhe.length() <= DETALHE_MAXIMO) {
            return detalhe;
        }
        return detalhe.substring(0, DETALHE_MAXIMO - 1) + "…";
    }
}
