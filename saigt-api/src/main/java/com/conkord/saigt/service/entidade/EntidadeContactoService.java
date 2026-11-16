package com.conkord.saigt.service.entidade;

import com.conkord.saigt.dto.entidade.AtualizarContactoRequest;
import com.conkord.saigt.dto.entidade.ContactoRequest;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
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
 * Contactos institucionais curados pela DGT; só {@code MASTER}. No máximo um contacto {@code principal} e
 * {@code ativo} por entidade: verificado aqui e garantido pelo índice único parcial, cuja violação (pedidos em
 * simultâneo) também dá 409. Trocar o principal faz-se em dois pedidos: desmarcar o atual e marcar o novo.
 */
@Service
public class EntidadeContactoService {

    private static final String TIPO_OCORRENCIA = "ENTIDADE_CONTACTO";
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@.]+\\.[^\\s@]+$");
    private static final Pattern TELEFONE_VALIDO = Pattern.compile("^\\d{9}$");
    private static final int MAXIMO_TEXTO = 200;
    private static final String CAMPO_EMAIL = "email";
    private static final String CAMPO_DESCRICAO = "descricao";
    private static final String CAMPO_TELEFONE = "telefone";
    private static final String CAMPO_PRINCIPAL = "principal";
    private static final String CAMPO_ATIVO = "ativo";
    private static final String CAMPO_CONTACTO_ID = "contactoId";
    private static final String MENSAGEM_INVALIDO = "Dados do contacto inválidos.";
    private static final String DUPLICADO = "Contacto duplicado ou principal já definido.";

    private final EntidadeContactoRepository repository;
    private final EntidadeRepository entidadeRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final AuditoriaService auditoriaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public EntidadeContactoService(EntidadeContactoRepository repository, EntidadeRepository entidadeRepository,
                                    UtilizadorRepository utilizadorRepository, AuditoriaService auditoriaService,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.repository = repository;
        this.entidadeRepository = entidadeRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.auditoriaService = auditoriaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    /** Ativos e inativos: o principal primeiro, depois os ativos, por email. */
    @Transactional(readOnly = true)
    public List<EntidadeContacto> listar(Long entidadeId) {
        exigirAdmin();
        if (!entidadeRepository.existsById(entidadeId)) {
            throw new RecursoNaoEncontradoException("Entidade não encontrada.");
        }
        return repository.findAllByEntidadeIdOrderByPrincipalDescAtivoDescEmailAsc(entidadeId);
    }

    /** Criado sempre ativo; {@code principal} omitido é {@code false}. */
    @Transactional
    public EntidadeContacto criarContacto(Long entidadeId, ContactoRequest request) {
        UUID atorId = exigirAdmin();
        List<CampoErro> erros = new ArrayList<>();
        String email = validarEmail(request.email(), erros);
        String descricao = validarDescricao(request.descricao(), erros);
        String telefone = validarTelefone(request.telefone(), erros);
        lancarSeHouverErros(erros);
        boolean principal = Boolean.TRUE.equals(request.principal());

        Entidade entidade = entidadeRepository.findById(entidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entidade não encontrada."));
        if (principal) {
            exigirSemOutroPrincipalAtivo(entidadeId, null);
        }
        Utilizador autor = utilizadorRepository.findById(atorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));

        EntidadeContacto contacto = new EntidadeContacto(entidade, email, autor);
        contacto.setDescricao(descricao);
        contacto.setTelefone(telefone);
        contacto.setPrincipal(principal);

        EntidadeContacto guardado = guardar(contacto);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_CONTACTO_ID, guardado.getId());
        dados.putAll(diferencas(dadosVazios(), dadosAuditaveis(guardado)));
        auditoriaService.registar(TIPO_OCORRENCIA, entidadeId.toString(), "CRIAR_CONTACTO", atorId, dados);
        return guardado;
    }

    /** 404 se o contacto não existir ou não pertencer a esta entidade. Sem alterações, não grava nem audita. */
    @Transactional
    public EntidadeContacto editarContacto(Long entidadeId, Long contactoId, AtualizarContactoRequest request) {
        UUID atorId = exigirAdmin();
        if (request.semCampos()) {
            throw new ValidacaoException("Indique pelo menos um campo a alterar.", List.of());
        }
        List<CampoErro> erros = new ArrayList<>();
        String email = request.email() == null ? null : validarEmail(request.email(), erros);
        String descricao = request.descricao() == null ? null : validarDescricao(request.descricao(), erros);
        String telefone = request.telefone() == null ? null : validarTelefone(request.telefone(), erros);
        lancarSeHouverErros(erros);

        EntidadeContacto contacto = repository.findById(contactoId)
                .filter(c -> c.getEntidade().getId().equals(entidadeId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contacto não encontrado."));

        // Antes de mexer na entidade gerida: a consulta faria flush da alteração e o índice recusava-a sem 409.
        boolean principalFinal = request.principal() != null ? request.principal() : contacto.isPrincipal();
        boolean ativoFinal = request.ativo() != null ? request.ativo() : contacto.isAtivo();
        if (principalFinal && ativoFinal) {
            exigirSemOutroPrincipalAtivo(entidadeId, contactoId);
        }

        Map<String, Object> antes = dadosAuditaveis(contacto);
        if (request.email() != null) {
            contacto.setEmail(email);
        }
        if (request.descricao() != null) {
            contacto.setDescricao(descricao);
        }
        if (request.telefone() != null) {
            contacto.setTelefone(telefone);
        }
        if (request.principal() != null) {
            contacto.setPrincipal(request.principal());
        }
        if (request.ativo() != null) {
            contacto.setAtivo(request.ativo());
        }

        Map<String, Object> alteracoes = diferencas(antes, dadosAuditaveis(contacto));
        if (alteracoes.isEmpty()) {
            return contacto;
        }
        EntidadeContacto guardado = guardar(contacto);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_CONTACTO_ID, contactoId);
        dados.putAll(alteracoes);
        auditoriaService.registar(TIPO_OCORRENCIA, entidadeId.toString(), "EDITAR_CONTACTO", atorId, dados);
        return guardado;
    }

    private void exigirSemOutroPrincipalAtivo(Long entidadeId, Long contactoId) {
        boolean outro = repository.findByEntidadeIdAndPrincipalTrueAndAtivoTrue(entidadeId)
                .filter(atual -> !atual.getId().equals(contactoId))
                .isPresent();
        if (outro) {
            throw new ReferenciaDuplicadaException(DUPLICADO);
        }
    }

    /** O único (entidade, email) e o índice do principal ativo dão a mesma resposta. */
    private EntidadeContacto guardar(EntidadeContacto contacto) {
        try {
            return repository.saveAndFlush(contacto);
        } catch (DataIntegrityViolationException e) {
            throw new ReferenciaDuplicadaException(DUPLICADO);
        }
    }

    private static String validarEmail(String email, List<CampoErro> erros) {
        if (email == null || email.isBlank()) {
            erros.add(new CampoErro(CAMPO_EMAIL, "Obrigatório."));
            return null;
        }
        if (email.length() > MAXIMO_TEXTO) {
            erros.add(new CampoErro(CAMPO_EMAIL, "Tem de ter no máximo " + MAXIMO_TEXTO + " caracteres."));
            return null;
        }
        if (!EMAIL_VALIDO.matcher(email).matches()) {
            erros.add(new CampoErro(CAMPO_EMAIL, "Formato de email inválido."));
            return null;
        }
        return email;
    }

    /** Vazio grava {@code null}. */
    private static String validarDescricao(String descricao, List<CampoErro> erros) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        if (descricao.length() > MAXIMO_TEXTO) {
            erros.add(new CampoErro(CAMPO_DESCRICAO, "Tem de ter no máximo " + MAXIMO_TEXTO + " caracteres."));
            return null;
        }
        return descricao;
    }

    /** Vazio grava {@code null}; senão exige exatos 9 dígitos. */
    private static String validarTelefone(String telefone, List<CampoErro> erros) {
        if (telefone == null || telefone.isEmpty()) {
            return null;
        }
        if (!TELEFONE_VALIDO.matcher(telefone).matches()) {
            erros.add(new CampoErro(CAMPO_TELEFONE, "Telefone deve ter exatamente 9 dígitos."));
            return null;
        }
        return telefone;
    }

    private static void lancarSeHouverErros(List<CampoErro> erros) {
        if (!erros.isEmpty()) {
            throw new ValidacaoException(MENSAGEM_INVALIDO, erros);
        }
    }

    private static Map<String, Object> dadosAuditaveis(EntidadeContacto contacto) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put(CAMPO_EMAIL, contacto.getEmail());
        dados.put(CAMPO_DESCRICAO, contacto.getDescricao());
        dados.put(CAMPO_TELEFONE, contacto.getTelefone());
        dados.put(CAMPO_PRINCIPAL, contacto.isPrincipal());
        dados.put(CAMPO_ATIVO, contacto.isAtivo());
        return dados;
    }

    private static Map<String, Object> dadosVazios() {
        Map<String, Object> dados = new LinkedHashMap<>();
        for (String campo : List.of(CAMPO_EMAIL, CAMPO_DESCRICAO, CAMPO_TELEFONE, CAMPO_PRINCIPAL, CAMPO_ATIVO)) {
            dados.put(campo, null);
        }
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

    private UUID exigirAdmin() {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode gerir contactos institucionais.");
        }
        return contexto.utilizadorId();
    }
}
