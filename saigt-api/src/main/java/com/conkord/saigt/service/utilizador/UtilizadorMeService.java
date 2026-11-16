package com.conkord.saigt.service.utilizador;

import com.conkord.saigt.dto.utilizador.AtualizarPerfilRequest;
import com.conkord.saigt.dto.utilizador.UtilizadorMeResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * O utilizador é sempre o do JWT, nunca um id do pedido. Os INCM só consultam (os dados vêm da INCM em cada login);
 * os AD_DGT editam só o email, o telefone e a morada, nunca o nome nem o cargo.
 */
@Service
public class UtilizadorMeService {

    private static final String TIPO_OCORRENCIA = "UTILIZADOR";
    private static final String ORIGEM_AD_DGT = "AD_DGT";
    private static final String CAMPO_EMAIL = "email";
    private static final String CAMPO_TELEFONE = "telefone";
    private static final String CAMPO_MORADA = "morada";
    private static final int MAXIMO_EMAIL = 200;
    private static final int MAXIMO_TELEFONE = 30;
    private static final int MAXIMO_MORADA = 300;
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@.]+\\.[^\\s@]+$");
    // Algarismos e os separadores usuais (+351 912 345 678, (21) 000-0000); pelo menos um algarismo.
    private static final Pattern TELEFONE_VALIDO = Pattern.compile("^(?=.*\\d)[\\d +().-]+$");
    static final String SO_EMAIL_TELEFONE_MORADA = "Só pode alterar o email, o telefone e a morada.";
    static final String GERIDO_PELA_INCM = "Os seus dados são geridos pela INCM.";
    static final String EMAIL_DE_OUTRA_CONTA = "Este email já está associado a outra conta da DGT.";

    private final UtilizadorRepository utilizadorRepository;
    private final EntidadeRepository entidadeRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public UtilizadorMeService(UtilizadorRepository utilizadorRepository, EntidadeRepository entidadeRepository,
                               AuditoriaService auditoriaService, ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.utilizadorRepository = utilizadorRepository;
        this.entidadeRepository = entidadeRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public UtilizadorMeResponse obterMe() {
        Utilizador utilizador = utilizadorDaSessao();
        return UtilizadorMeResponse.de(utilizador, ORIGEM_AD_DGT.equals(utilizador.getOrigemAutenticacao()) ? null
                : entidadeDaSessao());
    }

    @Transactional
    public UtilizadorMeResponse atualizar(AtualizarPerfilRequest request) {
        Utilizador utilizador = utilizadorDaSessao();
        if (!ORIGEM_AD_DGT.equals(utilizador.getOrigemAutenticacao())) {
            throw new AcaoNaoAutorizadaException(GERIDO_PELA_INCM);
        }
        if (!request.getCamposNaoEditaveis().isEmpty()) {
            throw new ValidacaoException(SO_EMAIL_TELEFONE_MORADA, request.getCamposNaoEditaveis().stream()
                    .map(campo -> new CampoErro(campo, SO_EMAIL_TELEFONE_MORADA)).toList());
        }

        List<CampoErro> erros = new ArrayList<>();
        String email = validarEmail(request.getEmail(), erros);
        String telefone = validarTexto(request.getTelefone(), CAMPO_TELEFONE, MAXIMO_TELEFONE, erros);
        if (telefone != null && !TELEFONE_VALIDO.matcher(telefone).matches()) {
            erros.add(new CampoErro(CAMPO_TELEFONE, "Use só algarismos, espaços e os sinais + ( ) - ."));
        }
        String morada = validarTexto(request.getMorada(), CAMPO_MORADA, MAXIMO_MORADA, erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Dados do perfil inválidos.", erros);
        }

        Map<String, Object> antes = dadosEditaveis(utilizador);
        if (request.getEmail() != null) {
            exigirEmailLivre(email, utilizador.getId());
            utilizador.setEmail(email);
        }
        if (request.getTelefone() != null) {
            utilizador.setTelefone(telefone);
        }
        if (request.getMorada() != null) {
            utilizador.setMorada(morada);
        }

        Map<String, Object> alteracoes = diferencas(antes, dadosEditaveis(utilizador));
        if (!alteracoes.isEmpty()) {
            guardar(utilizador);
            auditoriaService.registar(TIPO_OCORRENCIA, utilizador.getId().toString(), "EDITAR_PERFIL",
                    utilizador.getId(), alteracoes);
        }
        return UtilizadorMeResponse.de(utilizador, null);
    }

    private UtilizadorMeResponse.EntidadeSessao entidadeDaSessao() {
        return contextoUtilizadorResolver.entidadeDoToken()
                .map(token -> UtilizadorMeResponse.EntidadeSessao.de(token.codigo(), token.nome(), token.tipoEntidade(),
                        token.codigo() == null ? null : entidadeRepository.findByCodigo(token.codigo()).orElse(null)))
                .orElse(null);
    }

    /** O email só é único dentro da origem; a comparação ignora maiúsculas, como o domínio de email. */
    private void exigirEmailLivre(String email, UUID utilizadorId) {
        if (email != null && utilizadorRepository.existsByEmailIgnoreCaseAndOrigemAutenticacaoAndIdNot(email,
                ORIGEM_AD_DGT, utilizadorId)) {
            throw ReferenciaDuplicadaException.noCampo(CAMPO_EMAIL, EMAIL_DE_OUTRA_CONTA);
        }
    }

    /** Dois pedidos em simultâneo com o mesmo email: o índice `uq_utilizador_email_origem` dá a mesma resposta. */
    private void guardar(Utilizador utilizador) {
        try {
            utilizadorRepository.saveAndFlush(utilizador);
        } catch (DataIntegrityViolationException e) {
            throw ReferenciaDuplicadaException.noCampo(CAMPO_EMAIL, EMAIL_DE_OUTRA_CONTA);
        }
    }

    private static String validarEmail(String valor, List<CampoErro> erros) {
        String email = validarTexto(valor, CAMPO_EMAIL, MAXIMO_EMAIL, erros);
        if (email != null && !EMAIL_VALIDO.matcher(email).matches()) {
            erros.add(new CampoErro(CAMPO_EMAIL, "Formato de email inválido."));
            return null;
        }
        return email;
    }

    /** Sem espaços nas pontas; vazio dá {@code null} (limpa o campo). */
    private static String validarTexto(String valor, String campo, int maximo, List<CampoErro> erros) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.strip();
        if (texto.length() > maximo) {
            erros.add(new CampoErro(campo, "Tem de ter no máximo " + maximo + " caracteres."));
            return null;
        }
        return texto;
    }

    private static Map<String, Object> dadosEditaveis(Utilizador utilizador) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_EMAIL, utilizador.getEmail());
        dados.put(CAMPO_TELEFONE, utilizador.getTelefone());
        dados.put(CAMPO_MORADA, utilizador.getMorada());
        return dados;
    }

    private static Map<String, Object> diferencas(Map<String, Object> antes, Map<String, Object> depois) {
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        antes.forEach((campo, valorAntes) -> {
            Object valorDepois = depois.get(campo);
            if (!Objects.equals(valorAntes, valorDepois)) {
                Map<String, Object> par = new LinkedHashMap<>();
                par.put("antes", valorAntes);
                par.put("depois", valorDepois);
                alteracoes.put(campo, par);
            }
        });
        return alteracoes;
    }

    private Utilizador utilizadorDaSessao() {
        UUID utilizadorId = contextoUtilizadorResolver.resolver().utilizadorId();
        return utilizadorRepository.findById(utilizadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));
    }
}
