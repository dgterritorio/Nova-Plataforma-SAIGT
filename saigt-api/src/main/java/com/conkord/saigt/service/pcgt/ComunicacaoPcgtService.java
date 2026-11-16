package com.conkord.saigt.service.pcgt;

import com.conkord.saigt.exception.LinkPublicoNaoPermitidoException;
import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.pcgt.ClientePcgt;
import com.conkord.saigt.integracao.pcgt.PedidoCriacaoProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.ProcessoPcgt;
import com.conkord.saigt.integracao.pcgt.ResultadoPcgt;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.documento.LinkPublicoService;
import com.conkord.saigt.service.instrumento.NumeroDepositoService;
import com.conkord.saigt.service.notificacao.AvisoPedidoPcgtService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um processo PCGT corresponde a uma Dinâmica SAIGT, por isso o código é o n.º da Dinâmica. Não deve correr dentro de
 * uma transação: os dados leem-se numa transação curta, cada link público grava-se com commit próprio e as chamadas
 * correm fora delas.
 */
@Slf4j
@Service
public class ComunicacaoPcgtService {

    private static final String ORIGEM_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String TIPO_REN = "REN";
    private static final String CATEGORIA_SRUP = "SRUP";
    private static final String MDC_CORRELATION_ID = "apiTraceId";
    private static final int MAXIMO_MENSAGEM = 200;

    private final ProcessoRepository processoRepository;
    private final MontadorPedidoPcgt montador;
    private final NumeroDepositoService numeroDepositoService;
    private final LinkPublicoService linkPublicoService;
    private final ClientePcgt clientePcgt;
    private final AvisoPedidoPcgtService avisoPedidoPcgt;
    private final TransactionTemplate transacaoLeitura;

    public ComunicacaoPcgtService(ProcessoRepository processoRepository, MontadorPedidoPcgt montador,
                                  NumeroDepositoService numeroDepositoService, LinkPublicoService linkPublicoService,
                                  ClientePcgt clientePcgt, AvisoPedidoPcgtService avisoPedidoPcgt,
                                  PlatformTransactionManager transactionManager) {
        this.processoRepository = processoRepository;
        this.montador = montador;
        this.numeroDepositoService = numeroDepositoService;
        this.linkPublicoService = linkPublicoService;
        this.clientePcgt = clientePcgt;
        this.avisoPedidoPcgt = avisoPedidoPcgt;
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setReadOnly(true);
    }

    public void comunicarPublicacao(UUID processoId) {
        Objects.requireNonNull(processoId, "processoId");
        Optional<DadosPedidoPcgt> preparados = transacaoLeitura.execute(estado -> preparar(processoId));
        if (preparados == null || preparados.isEmpty()) {
            return;
        }
        DadosPedidoPcgt dados = preparados.get();

        Map<UUID, String> urls = new HashMap<>();
        for (DadosPedidoPcgt.Documento documento : dados.documentos()) {
            try {
                urls.put(documento.id(), linkPublicoService.obterOuCriar(documento.id(), DestinoLinkPublico.PCGT).url());
            } catch (LinkPublicoNaoPermitidoException e) {
                log.warn("Comunicação à PCGT por fazer, documento sem link público permitido — processoId={} "
                        + "numeroProcesso={} processoDocumentoId={}", processoId, dados.numeroProcesso(), documento.id());
                return;
            }
        }

        ReferenciaChamada referencia = new ReferenciaChamada(processoId, dados.idus(), null);
        ResultadoPcgt<ProcessoPcgt> atualizacao =
                clientePcgt.atualizar(montador.montarAtualizacao(dados, urls), referencia);
        if (atualizacao.sucesso()) {
            log.info("Processo PCGT atualizado — processoId={} numeroProcesso={} numeroDinamica={} httpStatus={}",
                    processoId, dados.numeroProcesso(), dados.codigoProcesso(), atualizacao.chamada().httpStatus());
            avisarPedidoEnviado(processoId);
            return;
        }
        if (!atualizacao.processoNaoExiste()) {
            avisarFalha("atualizar", dados, atualizacao);
            return;
        }
        Optional<PedidoCriacaoProcessoPcgt> criacao = montador.montarCriacao(dados, urls);
        if (criacao.isEmpty()) {
            return;
        }
        ResultadoPcgt<ProcessoPcgt> resultado = clientePcgt.criar(criacao.get(), referencia);
        if (resultado.sucesso()) {
            log.info("Processo PCGT criado — processoId={} numeroProcesso={} numeroDinamica={} httpStatus={}",
                    processoId, dados.numeroProcesso(), dados.codigoProcesso(), resultado.chamada().httpStatus());
            avisarPedidoEnviado(processoId);
            return;
        }
        avisarFalha("criar", dados, resultado);
    }

    private Optional<DadosPedidoPcgt> preparar(UUID processoId) {
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null || processo.getEstado() != EstadoProcesso.PUBLICADO
                || ORIGEM_REGISTO_EXTERNO.equals(processo.getOrigemDados())) {
            return Optional.empty();
        }
        TipoInstrumento tipo = processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento();
        // A REN nunca; as SRUP só no procedimento final.
        if (TIPO_REN.equals(tipo.getCodigo())) {
            log.info("REN não comunicada à PCGT — processoId={} numeroProcesso={}", processoId,
                    processo.getNumeroProcesso());
            return Optional.empty();
        }
        if (CATEGORIA_SRUP.equals(tipo.getCategoria()) && !numeroDepositoService.doProcedimentoFinal(processo)) {
            log.info("SRUP só comunicada à PCGT no procedimento final — processoId={} numeroProcesso={}", processoId,
                    processo.getNumeroProcesso());
            return Optional.empty();
        }
        return Optional.of(montador.preparar(processo));
    }

    /** Só depois da resposta de sucesso: o aviso diz que a PCGT já tem o pedido. */
    private void avisarPedidoEnviado(UUID processoId) {
        try {
            avisoPedidoPcgt.avisarPedidoEnviado(processoId);
        } catch (RuntimeException e) {
            log.error("Falha a criar o aviso de pedido enviado para a PCGT, a comunicação mantém-se — processoId={}",
                    processoId, e);
        }
    }

    private static void avisarFalha(String passo, DadosPedidoPcgt dados, ResultadoPcgt<?> resultado) {
        String mensagem = resultado.erro() == null ? null
                : truncar(LinkPublicoService.mascararTokens(resultado.erro().message()));
        log.warn("Comunicação à PCGT falhou, sem nova tentativa | sistema=PCGT passo={} processoId={} numeroProcesso={} "
                        + "numeroDinamica={} httpStatus={} falha={} code={} message={} correlationId={}", passo,
                dados.processoId(), dados.numeroProcesso(), dados.codigoProcesso(), resultado.chamada().httpStatus(),
                resultado.chamada().falhaTecnica(), resultado.erro() == null ? null : resultado.erro().code(), mensagem,
                MDC.get(MDC_CORRELATION_ID));
    }

    private static String truncar(String texto) {
        return texto == null || texto.length() <= MAXIMO_MENSAGEM ? texto : texto.substring(0, MAXIMO_MENSAGEM) + "…";
    }
}
