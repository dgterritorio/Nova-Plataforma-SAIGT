package com.conkord.saigt.controller.documento;

import com.conkord.saigt.service.documento.AlvoDescarga;
import com.conkord.saigt.service.documento.DescargaDocumentoService;
import com.conkord.saigt.service.documento.DescargaEmCurso;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Sem JWT: o ticket é a autorização. O envio é em streaming, sem transação aberta e sem guardar o ficheiro em
 * memória ou disco.
 */
@RestController
public class DescargaController {

    private final DescargaDocumentoService descargaService;

    public DescargaController(DescargaDocumentoService descargaService) {
        this.descargaService = descargaService;
    }

    @RequestMapping(value = "/api/v1/descargas/{ticket}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void descarregar(@PathVariable String ticket, HttpServletRequest pedido, HttpServletResponse resposta)
            throws IOException {
        AlvoDescarga alvo = descargaService.resolver(ticket);
        EnvioFicheiro envio = EnvioFicheiro.de(pedido, alvo.nomeFicheiro(), alvo.tamanhoBytes(), alvo.etag(),
                false);
        if (envio.recusarSeForaDoFicheiro(resposta)) {
            return;
        }
        if (HttpMethod.HEAD.matches(pedido.getMethod())) {
            envio.escreverCabecalhos(resposta);
            return;
        }

        DescargaEmCurso descarga = descargaService.iniciar(alvo, pedido.getRemoteAddr(), envio.inicio(),
                envio.comprimento());
        try {
            envio.escreverCabecalhos(resposta);
            descarga.transferir(resposta.getOutputStream());
        } catch (ClientAbortException e) {
            // O cliente desistiu: a linha fica INTERROMPIDA e não há mais nada a enviar.
        } finally {
            descargaService.terminar(descarga, alvo.documentoId());
        }
    }
}
