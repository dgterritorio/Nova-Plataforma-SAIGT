package com.conkord.saigt.controller.core;

import com.conkord.saigt.dto.core.ErrorResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.AnexoNotificacaoExtensaoNaoAceiteException;
import com.conkord.saigt.exception.AnexoNotificacaoTamanhoExcedidoException;
import com.conkord.saigt.exception.CanalGeoconformException;
import com.conkord.saigt.exception.ClassificacaoInvalidaException;
import com.conkord.saigt.exception.ConfigExtensaoNaoAceiteException;
import com.conkord.saigt.exception.ExtensaoNaoAceiteException;
import com.conkord.saigt.exception.DadosIncmEmFaltaException;
import com.conkord.saigt.exception.DestinoInvalidoException;
import com.conkord.saigt.exception.DestinoSoDepositoException;
import com.conkord.saigt.exception.DinamicaAssociadaInvalidaException;
import com.conkord.saigt.exception.DinamicaNaoElegivelException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.IncmIndisponivelException;
import com.conkord.saigt.exception.ItensPorReverException;
import com.conkord.saigt.exception.NotificacaoPrazoNaoAplicavelException;
import com.conkord.saigt.exception.NotificacaoTipoNaoReabrivelException;
import com.conkord.saigt.exception.ParteDrNaoAutorizadaException;
import com.conkord.saigt.exception.PcmAtorNaoAutorizadoException;
import com.conkord.saigt.exception.PcmEstadoInvalidoException;
import com.conkord.saigt.exception.PcmUtilizadorInvalidoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.SequenciamentoNaoCumpridoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.exception.TamanhoExcedidoException;
import com.conkord.saigt.exception.ProcessoEmCursoException;
import com.conkord.saigt.exception.TipoAtoNaoAutorizadoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ZipInvalidoException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveDevolver409ComEnvelopeQuandoEstadoInvalido() {
        EstadoInvalidoException ex = new EstadoInvalidoException("Procedimento(s) anterior(es) obrigatório(s) ainda não publicado(s).");

        ResponseEntity<ErrorResponse> resposta = handler.handleEstadoInvalido(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = resposta.getBody();
        assertThat(body).isNotNull();
        assertThat(body.codigo()).isEqualTo("ESTADO_INVALIDO");
        assertThat(body.mensagem()).isEqualTo("Procedimento(s) anterior(es) obrigatório(s) ainda não publicado(s).");
    }

    @Test
    void deveDevolver400ComOCodigoEAMensagemDoBlProc062NaDinamicaAssociadaInvalida() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(new DinamicaAssociadaInvalidaException());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = resposta.getBody();
        assertThat(body).isNotNull();
        assertThat(body.codigo()).isEqualTo("DINAMICA_ASSOCIADA_INVALIDA");
        assertThat(body.mensagem()).isEqualTo("A dinâmica indicada não está disponível para associação.");
        assertThat(body.campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("dinamicaAssociadaId");
    }

    @Test
    void deveDevolver400DestinoInvalidoNoCampoDestino() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(new DestinoInvalidoException());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = resposta.getBody();
        assertThat(body).isNotNull();
        assertThat(body.codigo()).isEqualTo("DESTINO_INVALIDO");
        assertThat(body.mensagem()).isEqualTo("Este processo não pode ser só para depósito.");
        assertThat(body.campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("destino");
    }

    @Test
    void deveDevolver409DestinoSoDeposito() {
        ResponseEntity<ErrorResponse> resposta = handler.handleEstadoInvalido(new DestinoSoDepositoException());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = resposta.getBody();
        assertThat(body).isNotNull();
        assertThat(body.codigo()).isEqualTo("DESTINO_SO_DEPOSITO");
        assertThat(body.mensagem()).isEqualTo("Este processo é só para depósito e não é enviado à INCM.");
    }

    @Test
    void deveDevolver400ComCamposPreenchidosAPartirDosFieldErrors() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", "email", "não pode estar em branco"));
        bindingResult.addError(new FieldError("loginRequest", "password", "não pode estar em branco"));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> resposta = handler.handleArgumentoInvalido(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = resposta.getBody();
        assertThat(body).isNotNull();
        assertThat(body.codigo()).isEqualTo("VALIDACAO");
        assertThat(body.campos()).extracting(ErrorResponse.CampoErro::path)
                .containsExactlyInAnyOrder("email", "password");
        assertThat(body.campos()).extracting(ErrorResponse.CampoErro::erro)
                .containsOnly("não pode estar em branco");
    }

    @Test
    void deveDevolver400ComOCampoQuandoOCorpoTemUmValorIlegivel() {
        JsonMappingException causa = JsonMappingException.wrapWithPath(
                new JsonMappingException(null, "data inválida"), new Object(), "dataAssinatura");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("ilegível", causa, new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> resposta = handler.handleCorpoIlegivel(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("VALIDACAO");
        assertThat(resposta.getBody().campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("dataAssinatura");
    }

    @Test
    void deveDevolver400SemCamposQuandoOJsonEMalFormado() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("mal formado", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> resposta = handler.handleCorpoIlegivel(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("VALIDACAO");
        assertThat(resposta.getBody().campos()).isNull();
    }

    @Test
    void deveDevolver400ComONomeDoParametroQuandoNaoSeConverte() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("2026-13-01",
                java.time.LocalDate.class, "dataDe", null, new IllegalArgumentException("data inválida"));

        ResponseEntity<ErrorResponse> resposta = handler.handleParametroInvalido(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("VALIDACAO");
        assertThat(resposta.getBody().campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("dataDe");
    }

    @Test
    void deveDevolver404ComCodigoNaoEncontrado() {
        ResponseEntity<ErrorResponse> resposta = handler.handleNaoEncontrado(new RecursoNaoEncontradoException("Processo não encontrado."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().codigo()).isEqualTo("NAO_ENCONTRADO");
        assertThat(resposta.getBody().timestamp()).isNotNull();
        assertThat(resposta.getBody().traceId()).isNotBlank();
    }

    @Test
    void deveDevolver403ComCodigoSemPermissao() {
        ResponseEntity<ErrorResponse> resposta = handler.handleNaoAutorizado(new AcaoNaoAutorizadaException("Sem perfil."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().codigo()).isEqualTo("SEM_PERMISSAO");
    }

    @Test
    void deveDevolverOsCodigosDoFluxoPcmNoEnvelopeDeErro() {
        ResponseEntity<ErrorResponse> ator = handler.handleNaoAutorizado(new PcmAtorNaoAutorizadoException("Só a DGT."));
        ResponseEntity<ErrorResponse> estado = handler.handleEstadoInvalido(new PcmEstadoInvalidoException(
                PcmEstadoInvalidoException.DOCUMENTOS_POR_VALIDAR, "Existem documentos por validar."));
        ResponseEntity<ErrorResponse> utilizador = handler.handleValidacao(new PcmUtilizadorInvalidoException("utilizadorId"));

        assertThat(ator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ator.getBody().codigo()).isEqualTo("PCM_ATOR_NAO_AUTORIZADO");
        assertThat(estado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(estado.getBody().codigo()).isEqualTo("PCM_DOCUMENTOS_POR_VALIDAR");
        assertThat(estado.getBody().mensagem()).isEqualTo("Existem documentos por validar.");
        assertThat(utilizador.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(utilizador.getBody().codigo()).isEqualTo("PCM_UTILIZADOR_INVALIDO");
        assertThat(utilizador.getBody().campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("utilizadorId");
    }

    @Test
    void deveDevolver500ComIntegracaoIndisponivelQuandoAIncmEstaIndisponivel() {
        ResponseEntity<ErrorResponse> resposta = handler.handleIncmIndisponivel(new IncmIndisponivelException("INCM em baixo."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody().codigo()).isEqualTo("INTEGRACAO_INDISPONIVEL");
    }

    @Test
    void deveDevolver500ComIntegracaoIndisponivelQuandoOStorageEstaIndisponivel() {
        ResponseEntity<ErrorResponse> resposta = handler.handleStorageIndisponivel(
                new StorageIndisponivelException("Storage em baixo.", new java.io.IOException("ligação recusada")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody().codigo()).isEqualTo("INTEGRACAO_INDISPONIVEL");
    }

    @Test
    void deveDevolver409ComOCodigoEspecificoDasExcecoesDeEstado() {
        List<EstadoInvalidoException> excecoes = List.of(
                new DinamicaNaoElegivelException("x"), new SequenciamentoNaoCumpridoException("x"),
                new ProcessoEmCursoException("x"), new ItensPorReverException());

        List<String> codigos = excecoes.stream()
                .map(ex -> {
                    ResponseEntity<ErrorResponse> resposta = handler.handleEstadoInvalido(ex);
                    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    return resposta.getBody().codigo();
                })
                .toList();

        assertThat(codigos).containsExactly("DINAMICA_NAO_ELEGIVEL", "SEQUENCIAMENTO_NAO_CUMPRIDO",
                "PROCESSO_EM_CURSO", "ITENS_POR_REVER");
    }

    @Test
    void deveDevolverOsCodigosDoPrazoNaoAplicavelEDoTipoNaoReabrivelDasNotificacoes() {
        ResponseEntity<ErrorResponse> prazo = handler.handleValidacao(new NotificacaoPrazoNaoAplicavelException());
        ResponseEntity<ErrorResponse> tipo = handler.handleEstadoInvalido(new NotificacaoTipoNaoReabrivelException());

        assertThat(prazo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(prazo.getBody().codigo()).isEqualTo("NOTIF_PRAZO_NAO_APLICAVEL");
        assertThat(prazo.getBody().mensagem()).isEqualTo("Prazo não aplicável a este tipo de notificação.");
        assertThat(tipo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tipo.getBody().codigo()).isEqualTo("NOTIF_TIPO_NAO_REABRIVEL");
        assertThat(tipo.getBody().mensagem()).isEqualTo("Só um pedido de apoio pode ser reaberto.");
    }

    @Test
    void deveDevolver409ComOCodigoDocCanalGeoconform() {
        ResponseEntity<ErrorResponse> resposta = handler.handleEstadoInvalido(new CanalGeoconformException("x"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().codigo()).isEqualTo("DOC_CANAL_GEOCONFORM");
        assertThat(resposta.getBody().mensagem()).isEqualTo("x");
    }

    @Test
    void deveDevolver403ComOCodigoEspecificoDasExcecoesDeCamada3c() {
        ResponseEntity<ErrorResponse> tipoAto = handler.handleNaoAutorizado(new TipoAtoNaoAutorizadoException("x"));
        ResponseEntity<ErrorResponse> parteDr = handler.handleNaoAutorizado(new ParteDrNaoAutorizadaException("x"));

        assertThat(tipoAto.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tipoAto.getBody().codigo()).isEqualTo("TIPO_ATO_NAO_AUTORIZADO");
        assertThat(parteDr.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parteDr.getBody().codigo()).isEqualTo("PARTE_DR_NAO_AUTORIZADA");
    }

    @Test
    void deveDevolver400ComCamposQuandoFaltamDadosIncm() {
        DadosIncmEmFaltaException ex = new DadosIncmEmFaltaException("Dados insuficientes.",
                List.of(new ValidacaoException.CampoErro("sumario", "Obrigatório na submissão.")));

        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("DADOS_INCM_EM_FALTA");
        assertThat(resposta.getBody().campos()).extracting(ErrorResponse.CampoErro::path).containsExactly("sumario");
    }

    @Test
    void deveDevolver400ComOsCodigosDosAnexosDeNotificacaoEOCampoFicheiro() {
        for (ValidacaoException excecao : List.of(new AnexoNotificacaoTamanhoExcedidoException(),
                new AnexoNotificacaoExtensaoNaoAceiteException())) {
            ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(excecao);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().codigo()).isEqualTo(excecao.getCodigo());
            assertThat(resposta.getBody().campos()).singleElement()
                    .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
        }
        assertThat(handler.handleValidacao(new AnexoNotificacaoTamanhoExcedidoException()).getBody().codigo())
                .isEqualTo("NOTIF_FICHEIRO_EXCEDE_LIMITE");
        assertThat(handler.handleValidacao(new AnexoNotificacaoExtensaoNaoAceiteException()).getBody().codigo())
                .isEqualTo("NOTIF_EXTENSAO_NAO_ACEITE");
    }

    @Test
    void deveDevolver400ComCodigoDocTamanhoExcedido() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(new TamanhoExcedidoException());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("DOC_TAMANHO_EXCEDIDO");
        assertThat(resposta.getBody().campos()).singleElement()
                .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
    }

    @Test
    void deveDevolver400ComCodigoDocExtensaoNaoAceite() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(
                new ExtensaoNaoAceiteException("Extensão não aceite para publicação."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("DOC_EXTENSAO_NAO_ACEITE");
        assertThat(resposta.getBody().mensagem()).isEqualTo("Extensão não aceite para publicação.");
        assertThat(resposta.getBody().traceId()).isNotBlank();
        assertThat(resposta.getBody().campos()).singleElement()
                .satisfies(campo -> assertThat(campo.path()).isEqualTo("ficheiro"));
    }

    @Test
    void deveDevolver400ComCodigoConfigExtensaoNaoAceite() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(new ConfigExtensaoNaoAceiteException());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("CONFIG_EXTENSAO_NAO_ACEITE");
        assertThat(resposta.getBody().traceId()).isNotBlank();
        assertThat(resposta.getBody().campos()).singleElement().satisfies(campo -> {
            assertThat(campo.path()).isEqualTo("extensoesAceites");
            assertThat(campo.erro()).isEqualTo("Extensão não aceite para este tipo de documento.");
        });
    }

    @Test
    void deveDevolver400ComCodigoDocClassificacaoInvalidaETraceId() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(new ClassificacaoInvalidaException("tags",
                "Só é possível associar o ficheiro a itens de depósito obrigatório."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("DOC_CLASSIFICACAO_INVALIDA");
        assertThat(resposta.getBody().traceId()).isNotBlank();
        assertThat(resposta.getBody().campos()).singleElement().satisfies(campo -> {
            assertThat(campo.path()).isEqualTo("tags");
            assertThat(campo.erro()).isEqualTo("Só é possível associar o ficheiro a itens de depósito obrigatório.");
        });
    }

    @Test
    void deveDevolver404NaoEncontradoQuandoOMetodoNaoESuportado() {
        ResponseEntity<ErrorResponse> resposta = handler.handleMetodoNaoSuportado(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().codigo()).isEqualTo("NAO_ENCONTRADO");
        assertThat(resposta.getBody().mensagem()).isEqualTo("Recurso não encontrado.");
    }

    @Test
    void deveDevolver400ComCodigoDocZipInvalidoETraceId() {
        ResponseEntity<ErrorResponse> resposta = handler.handleValidacao(
                new ZipInvalidoException("dados/, dados/plano.gpkg"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().codigo()).isEqualTo("DOC_ZIP_INVALIDO");
        assertThat(resposta.getBody().traceId()).isNotBlank();
        assertThat(resposta.getBody().campos()).singleElement().satisfies(campo -> {
            assertThat(campo.path()).isEqualTo("ficheiro");
            assertThat(campo.erro()).isEqualTo(
                    "O ZIP contém pastas ou ficheiros com nome inválido: dados/, dados/plano.gpkg.");
        });
    }
}
