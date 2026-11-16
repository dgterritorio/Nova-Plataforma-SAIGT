package com.conkord.saigt.service.configuracao;

import com.conkord.saigt.dto.configuracao.TipoDinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoDinamicaConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.ConfiguracaoEmUsoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.TipoDinamica;
import com.conkord.saigt.persistence.core.TipoDinamicaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import com.conkord.saigt.persistence.regras.CondicaoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Só `MASTER`, também nas leituras. Sem `DELETE`: desativa-se com `ativo=false`, o que só retira o tipo das
 * ofertas e não afeta processos em curso. O `codigo` (e a `categoria` do tipo de Instrumento) é chave: há
 * condições, formulários e configurações que o referenciam, por isso não muda enquanto estiver em uso.
 */
@Slf4j
@Service
public class ConfiguracaoTiposService {

    private static final String TIPO_OCORRENCIA = "CONFIGURACAO";
    private static final String RECURSO_TIPOS_INSTRUMENTO = "tipos-instrumento";
    private static final String RECURSO_TIPOS_DINAMICA = "tipos-dinamica";
    private static final String RECURSO_TIPOS_PROCEDIMENTO = "tipos-procedimento";

    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final TipoDinamicaRepository tipoDinamicaRepository;
    private final TipoProcedimentoRepository tipoProcedimentoRepository;
    private final InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final CondicaoRepository condicaoRepository;
    private final CatalogoFormularios catalogoFormularios;
    private final AuditoriaService auditoriaService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public ConfiguracaoTiposService(TipoInstrumentoRepository tipoInstrumentoRepository,
                                    TipoDinamicaRepository tipoDinamicaRepository,
                                    TipoProcedimentoRepository tipoProcedimentoRepository,
                                    InstrumentoDinamicaConfigRepository instrumentoDinamicaConfigRepository,
                                    InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                    InstrumentoRepository instrumentoRepository,
                                    CondicaoRepository condicaoRepository,
                                    CatalogoFormularios catalogoFormularios,
                                    AuditoriaService auditoriaService,
                                    AutorizacaoService autorizacaoService,
                                    ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.tipoDinamicaRepository = tipoDinamicaRepository;
        this.tipoProcedimentoRepository = tipoProcedimentoRepository;
        this.instrumentoDinamicaConfigRepository = instrumentoDinamicaConfigRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.condicaoRepository = condicaoRepository;
        this.catalogoFormularios = catalogoFormularios;
        this.auditoriaService = auditoriaService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public Page<TipoInstrumentoConfigResponse> listarTiposInstrumento(Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_TIPOS_INSTRUMENTO);
        Page<TipoInstrumento> tipos = ativo == null
                ? tipoInstrumentoRepository.findAll(pagina)
                : tipoInstrumentoRepository.findByAtivo(ativo, pagina);
        return tipos.map(TipoInstrumentoConfigResponse::de);
    }

    @Transactional
    public TipoInstrumentoConfigResponse criarTipoInstrumento(TipoInstrumentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_INSTRUMENTO);
        if (tipoInstrumentoRepository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        TipoInstrumento tipo = new TipoInstrumento(request.codigo(), request.nome(), request.categoria());
        tipo.setAtivo(request.ativo());
        tipo = tipoInstrumentoRepository.save(tipo);

        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("codigo", tipo.getCodigo());
        dados.put("nome", tipo.getNome());
        dados.put("categoria", tipo.getCategoria());
        dados.put("ativo", String.valueOf(tipo.isAtivo()));
        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_TIPOS_INSTRUMENTO, tipo.getId()), "CRIAR", atorId, dados);
        return TipoInstrumentoConfigResponse.de(tipo);
    }

    @Transactional
    public TipoInstrumentoConfigResponse atualizarTipoInstrumento(Long id, TipoInstrumentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_INSTRUMENTO);
        TipoInstrumento tipo = tipoInstrumentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Instrumento não encontrado."));

        Map<String, String> alterados = new LinkedHashMap<>();
        boolean codigoMuda = !tipo.getCodigo().equals(request.codigo());
        boolean categoriaMuda = !tipo.getCategoria().equals(request.categoria());
        if (codigoMuda) {
            exigirCodigoLivre(tipoInstrumentoRepository.existsByCodigoAndIdNot(request.codigo(), id));
            if (instrumentoDinamicaConfigRepository.existsByTipoInstrumento_Id(id)
                    || instrumentoRepository.existsByTipoInstrumento_Id(id)) {
                throw new ConfiguracaoEmUsoException();
            }
            tipo.setCodigo(request.codigo());
            alterados.put("codigo", request.codigo());
        }
        if (categoriaMuda) {
            if (instrumentoRepository.existsByTipoInstrumento_Id(id)) {
                throw new ConfiguracaoEmUsoException();
            }
            tipo.setCategoria(request.categoria());
            alterados.put("categoria", request.categoria());
        }
        aplicarNomeEAtivo(tipo.getNome(), tipo.isAtivo(), request.nome(), request.ativo(), alterados,
                tipo::setNome, tipo::setAtivo);

        tipoInstrumentoRepository.save(tipo);
        auditarAtualizacao(RECURSO_TIPOS_INSTRUMENTO, id, atorId, alterados);
        return TipoInstrumentoConfigResponse.de(tipo);
    }

    @Transactional(readOnly = true)
    public Page<TipoDinamicaConfigResponse> listarTiposDinamica(Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_TIPOS_DINAMICA);
        Page<TipoDinamica> tipos = ativo == null
                ? tipoDinamicaRepository.findAll(pagina)
                : tipoDinamicaRepository.findByAtivo(ativo, pagina);
        return tipos.map(TipoDinamicaConfigResponse::de);
    }

    @Transactional
    public TipoDinamicaConfigResponse criarTipoDinamica(TipoDinamicaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_DINAMICA);
        if (tipoDinamicaRepository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        TipoDinamica tipo = new TipoDinamica(request.codigo(), request.nome());
        tipo.setAtivo(request.ativo());
        tipo = tipoDinamicaRepository.save(tipo);

        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_TIPOS_DINAMICA, tipo.getId()), "CRIAR", atorId,
                dadosDeCriacao(tipo.getCodigo(), tipo.getNome(), tipo.isAtivo()));
        return TipoDinamicaConfigResponse.de(tipo);
    }

    @Transactional
    public TipoDinamicaConfigResponse atualizarTipoDinamica(Long id, TipoDinamicaConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_DINAMICA);
        TipoDinamica tipo = tipoDinamicaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Dinâmica não encontrado."));

        Map<String, String> alterados = new LinkedHashMap<>();
        if (!tipo.getCodigo().equals(request.codigo())) {
            exigirCodigoLivre(tipoDinamicaRepository.existsByCodigoAndIdNot(request.codigo(), id));
            if (instrumentoDinamicaConfigRepository.existsByTipoDinamica_Id(id)
                    || condicaoRepository.existeParametroCodigo(tipo.getCodigo())) {
                throw new ConfiguracaoEmUsoException();
            }
            tipo.setCodigo(request.codigo());
            alterados.put("codigo", request.codigo());
        }
        aplicarNomeEAtivo(tipo.getNome(), tipo.isAtivo(), request.nome(), request.ativo(), alterados,
                tipo::setNome, tipo::setAtivo);

        tipoDinamicaRepository.save(tipo);
        auditarAtualizacao(RECURSO_TIPOS_DINAMICA, id, atorId, alterados);
        return TipoDinamicaConfigResponse.de(tipo);
    }

    @Transactional(readOnly = true)
    public Page<TipoProcedimentoConfigResponse> listarTiposProcedimento(Boolean ativo, Pageable pagina) {
        exigirMaster(RECURSO_TIPOS_PROCEDIMENTO);
        Page<TipoProcedimento> tipos = ativo == null
                ? tipoProcedimentoRepository.findAll(pagina)
                : tipoProcedimentoRepository.findByAtivo(ativo, pagina);
        return tipos.map(this::respostaProcedimento);
    }

    @Transactional
    public TipoProcedimentoConfigResponse criarTipoProcedimento(TipoProcedimentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_PROCEDIMENTO);
        if (tipoProcedimentoRepository.existsByCodigo(request.codigo())) {
            throw codigoDuplicado();
        }
        TipoProcedimento tipo = new TipoProcedimento(request.codigo(), request.nome());
        tipo.setAtivo(request.ativo());
        tipo = tipoProcedimentoRepository.save(tipo);

        auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(RECURSO_TIPOS_PROCEDIMENTO, tipo.getId()), "CRIAR", atorId,
                dadosDeCriacao(tipo.getCodigo(), tipo.getNome(), tipo.isAtivo()));
        return respostaProcedimento(tipo);
    }

    @Transactional
    public TipoProcedimentoConfigResponse atualizarTipoProcedimento(Long id, TipoProcedimentoConfigRequest request) {
        UUID atorId = exigirMaster(RECURSO_TIPOS_PROCEDIMENTO);
        TipoProcedimento tipo = tipoProcedimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Procedimento não encontrado."));

        Map<String, String> alterados = new LinkedHashMap<>();
        if (!tipo.getCodigo().equals(request.codigo())) {
            exigirCodigoLivre(tipoProcedimentoRepository.existsByCodigoAndIdNot(request.codigo(), id));
            if (instrumentoProcedimentoConfigRepository.existsByTipoProcedimento_Id(id)
                    || condicaoRepository.existeParametroCodigo(tipo.getCodigo())) {
                throw new ConfiguracaoEmUsoException();
            }
            tipo.setCodigo(request.codigo());
            alterados.put("codigo", request.codigo());
        }
        aplicarNomeEAtivo(tipo.getNome(), tipo.isAtivo(), request.nome(), request.ativo(), alterados,
                tipo::setNome, tipo::setAtivo);

        tipoProcedimentoRepository.save(tipo);
        auditarAtualizacao(RECURSO_TIPOS_PROCEDIMENTO, id, atorId, alterados);
        return respostaProcedimento(tipo);
    }

    private TipoProcedimentoConfigResponse respostaProcedimento(TipoProcedimento tipo) {
        String formulario = catalogoFormularios.formularioDe(tipo).map(FormularioFixo::name).orElse(null);
        return TipoProcedimentoConfigResponse.de(tipo, formulario);
    }

    private static void aplicarNomeEAtivo(String nomeAtual, boolean ativoAtual, String nome, boolean ativo,
                                          Map<String, String> alterados,
                                          java.util.function.Consumer<String> definirNome,
                                          java.util.function.Consumer<Boolean> definirAtivo) {
        if (!nomeAtual.equals(nome)) {
            definirNome.accept(nome);
            alterados.put("nome", nome);
        }
        if (ativoAtual != ativo) {
            definirAtivo.accept(ativo);
            alterados.put("ativo", String.valueOf(ativo));
        }
    }

    private void auditarAtualizacao(String recurso, Long id, UUID atorId, Map<String, String> alterados) {
        if (!alterados.isEmpty()) {
            auditoriaService.registar(TIPO_OCORRENCIA, ocorrencia(recurso, id), "ATUALIZAR", atorId, alterados);
        }
    }

    private static Map<String, String> dadosDeCriacao(String codigo, String nome, boolean ativo) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("codigo", codigo);
        dados.put("nome", nome);
        dados.put("ativo", String.valueOf(ativo));
        return dados;
    }

    private static String ocorrencia(String recurso, Long id) {
        return recurso + ":" + id;
    }

    private static void exigirCodigoLivre(boolean jaExiste) {
        if (jaExiste) {
            throw codigoDuplicado();
        }
    }

    private static ValidacaoException codigoDuplicado() {
        return new ValidacaoException("Código duplicado.",
                List.of(new ValidacaoException.CampoErro("codigo", "Já existe um tipo com este código.")));
    }

    private UUID exigirMaster(String recurso) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        try {
            autorizacaoService.verificarPerfilMaster(contexto);
        } catch (AcaoNaoAutorizadaException e) {
            log.warn("Acesso recusado à configuração utilizadorId={} recurso={}", contexto.utilizadorId(), recurso);
            throw e;
        }
        return contexto.utilizadorId();
    }
}
