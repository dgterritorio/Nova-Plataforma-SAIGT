package com.conkord.saigt.service.listavalores;

import com.conkord.saigt.dto.listavalores.CatalogoResponse;
import com.conkord.saigt.dto.referencia.NutIiSnitResponse;
import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.persistence.core.NomeDocumento;
import com.conkord.saigt.persistence.core.NomeDocumentoRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.core.TipoProcedimento;
import com.conkord.saigt.persistence.core.TipoProcedimentoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agrega `tipo_instrumento`, `tipo_procedimento`, `nome_documento` e as NUTS II da réplica do SNIT para o ecrã
 * "Listas de Valores". Só `MASTER`.
 */
@Service
public class CatalogoService {

    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final TipoProcedimentoRepository tipoProcedimentoRepository;
    private final NomeDocumentoRepository nomeDocumentoRepository;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;

    public CatalogoService(TipoInstrumentoRepository tipoInstrumentoRepository,
                            TipoProcedimentoRepository tipoProcedimentoRepository,
                            NomeDocumentoRepository nomeDocumentoRepository,
                            ReferenciaSnitConsultaService referenciaSnitConsultaService,
                            ContextoUtilizadorResolver contextoUtilizadorResolver) {
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.tipoProcedimentoRepository = tipoProcedimentoRepository;
        this.nomeDocumentoRepository = nomeDocumentoRepository;
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
    }

    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar() {
        exigirMaster();
        // "Tipos IGT" mostra a abreviatura (codigo: PDM, PP, PU...).
        List<String> tiposIgt = tipoInstrumentoRepository.findByAtivoTrueOrderByCodigoAsc().stream()
                .map(TipoInstrumento::getCodigo)
                .toList();
        List<String> tiposProcedimento = tipoProcedimentoRepository.findByAtivoTrueOrderByNomeAsc().stream()
                .map(TipoProcedimento::getNome)
                .toList();
        List<String> nomesDocumento = nomeDocumentoRepository.findByAtivoTrueOrderByOrdemAsc().stream()
                .map(NomeDocumento::getNome)
                .toList();
        List<String> nuts = referenciaSnitConsultaService.nutsII().stream()
                .map(NutIiSnitResponse::nome)
                .toList();
        return List.of(
                new CatalogoResponse("Tipos IGT", tiposIgt),
                new CatalogoResponse("Tipos de procedimento", tiposProcedimento),
                new CatalogoResponse("Nomes de documento", nomesDocumento),
                new CatalogoResponse("NUTs (região)", nuts));
    }

    private void exigirMaster() {
        if (contextoUtilizadorResolver.resolver().perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode consultar catálogos.");
        }
    }
}
