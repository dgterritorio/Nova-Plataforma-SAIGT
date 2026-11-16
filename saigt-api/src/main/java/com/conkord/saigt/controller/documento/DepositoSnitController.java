package com.conkord.saigt.controller.documento;

import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.service.documento.DepositoSnitManifestado;
import com.conkord.saigt.service.documento.DescargaDepositoSnitService;
import com.conkord.saigt.service.documento.DescargaEmCurso;
import com.conkord.saigt.service.documento.DescargaPastaEmCurso;
import com.conkord.saigt.service.documento.EntradaDepositoSnit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Pasta de depósito lida pelo SNIT. A cadeia de segurança só deixa passar o token do cliente do SNIT; o cliente é o
 * `sub` desse token. Sem token não há cliente a quem atribuir os limites e a auditoria, por isso o pedido é recusado.
 */
@RestController
public class DepositoSnitController {

    private static final String PREFIXO = "/api/v1/integracoes/snit/depositos/{numeroProcesso}";

    private final DescargaDepositoSnitService servico;

    public DepositoSnitController(DescargaDepositoSnitService servico) {
        this.servico = servico;
    }

    @RequestMapping(value = PREFIXO, method = {RequestMethod.GET, RequestMethod.HEAD})
    public void pasta(@PathVariable String numeroProcesso, @AuthenticationPrincipal Jwt token,
                      HttpServletRequest pedido, HttpServletResponse resposta) throws IOException {
        String cliente = cliente(token);
        DepositoSnitManifestado deposito = servico.localizarDeposito(numeroProcesso);
        if (HttpMethod.HEAD.matches(pedido.getMethod())) {
            escreverCabecalhosDaPasta(resposta, numeroProcesso);
            return;
        }

        DescargaPastaEmCurso descarga = servico.iniciarPasta(deposito, cliente, pedido.getRemoteAddr());
        try {
            escreverCabecalhosDaPasta(resposta, numeroProcesso);
            descarga.transferir(resposta.getOutputStream());
        } catch (ClientAbortException e) {
            // O cliente desistiu: a linha fica INTERROMPIDA e o ZIP, sem diretório central.
        } finally {
            servico.terminarPasta(descarga, deposito);
        }
    }

    @RequestMapping(value = PREFIXO + "/ficheiros/{id}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void ficheiro(@PathVariable String numeroProcesso, @PathVariable String id,
                         @AuthenticationPrincipal Jwt token, HttpServletRequest pedido, HttpServletResponse resposta)
            throws IOException {
        String cliente = cliente(token);
        DepositoSnitManifestado deposito = servico.localizarDeposito(numeroProcesso);
        EntradaDepositoSnit entrada = servico.localizarFicheiro(deposito, id, pedido.getRemoteAddr());
        EnvioFicheiro envio = EnvioFicheiro.de(pedido, entrada.nomeFicheiro(), entrada.tamanhoBytes(),
                entrada.hashSha256(), false);
        if (envio.recusarSeForaDoFicheiro(resposta)) {
            return;
        }
        if (HttpMethod.HEAD.matches(pedido.getMethod())) {
            envio.escreverCabecalhos(resposta);
            return;
        }

        DescargaEmCurso descarga = servico.iniciarFicheiro(deposito, entrada, cliente, pedido.getRemoteAddr(),
                envio.inicio(), envio.comprimento());
        try {
            envio.escreverCabecalhos(resposta);
            descarga.transferir(resposta.getOutputStream());
        } catch (ClientAbortException e) {
            // O cliente desistiu: a linha fica INTERROMPIDA e não há mais nada a enviar.
        } finally {
            servico.terminarFicheiro(descarga, deposito, entrada);
        }
    }

    private static String cliente(Jwt token) {
        if (token == null || token.getSubject() == null) {
            throw new AcaoNaoAutorizadaException("Ação não autorizada.");
        }
        return token.getSubject();
    }

    /** Sem `Content-Length` e sem `Range`: o ZIP é gerado ao longo do envio. */
    private static void escreverCabecalhosDaPasta(HttpServletResponse resposta, String numeroProcesso) {
        resposta.setStatus(HttpServletResponse.SC_OK);
        resposta.setHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
        resposta.setHeader(HttpHeaders.ACCEPT_RANGES, "none");
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("deposito-" + numeroProcesso + ".zip", StandardCharsets.UTF_8).build().toString());
        EnvioFicheiro.escreverCabecalhosDeSeguranca(resposta);
    }
}
