package com.conkord.saigt.controller.documento;

import com.conkord.saigt.service.documento.AlvoDescarga;
import com.conkord.saigt.service.documento.AlvoLinkPublico;
import com.conkord.saigt.service.documento.DescargaEmCurso;
import com.conkord.saigt.service.documento.DescargaLinkPublicoService;
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
 * Link público por onde a INCM e a PCGT descarregam um documento. Sem JWT nem sessão: o token é a autorização. O
 * RustFS nunca é acedido de fora; os bytes passam por aqui, em streaming.
 */
@RestController
public class LinkPublicoController {

    private final DescargaLinkPublicoService descargaService;

    public LinkPublicoController(DescargaLinkPublicoService descargaService) {
        this.descargaService = descargaService;
    }

    @RequestMapping(value = "/api/v1/publico/{token}/{nome}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void descarregar(@PathVariable String token, @PathVariable String nome, HttpServletRequest pedido,
                            HttpServletResponse resposta) throws IOException {
        AlvoLinkPublico alvo = descargaService.resolver(token, nome, pedido.getRemoteAddr());
        AlvoDescarga documento = alvo.documento();
        EnvioFicheiro envio = EnvioFicheiro.de(pedido, documento.nomeFicheiro(), documento.tamanhoBytes(),
                documento.hashSha256(), true);
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
            descargaService.terminar(descarga, alvo);
        }
    }
}
