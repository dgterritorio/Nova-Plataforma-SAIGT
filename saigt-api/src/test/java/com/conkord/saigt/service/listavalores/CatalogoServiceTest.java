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
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private TipoInstrumentoRepository tipoInstrumentoRepository;
    @Mock
    private TipoProcedimentoRepository tipoProcedimentoRepository;
    @Mock
    private NomeDocumentoRepository nomeDocumentoRepository;
    @Mock
    private ReferenciaSnitConsultaService referenciaSnitConsultaService;
    @Mock
    private ContextoUtilizadorResolver contextoUtilizadorResolver;

    @InjectMocks
    private CatalogoService service;

    private static ContextoUtilizador contextoComPerfil(PerfilInterno perfilInterno) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfilInterno, null, null, null, null, null, null, null, null);
    }

    @Test
    void deveDevolverOsQuatroCatalogosNaOrdemCerta() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.MASTER));
        when(tipoInstrumentoRepository.findByAtivoTrueOrderByCodigoAsc())
                .thenReturn(List.of(new TipoInstrumento("PDM", "Plano Diretor Municipal", "PLANO")));
        when(tipoProcedimentoRepository.findByAtivoTrueOrderByNomeAsc())
                .thenReturn(List.of(new TipoProcedimento("ELABORACAO", "Elaboração")));
        when(nomeDocumentoRepository.findByAtivoTrueOrderByOrdemAsc())
                .thenReturn(List.of(new NomeDocumento("Texto DR", 1)));
        when(referenciaSnitConsultaService.nutsII())
                .thenReturn(List.of(new NutIiSnitResponse("NORTE", "NORTE")));

        List<CatalogoResponse> catalogos = service.listar();

        assertThat(catalogos).containsExactly(
                new CatalogoResponse("Tipos IGT", List.of("PDM")),
                new CatalogoResponse("Tipos de procedimento", List.of("Elaboração")),
                new CatalogoResponse("Nomes de documento", List.of("Texto DR")),
                new CatalogoResponse("NUTs (região)", List.of("NORTE")));
    }

    @Test
    void deveRejeitarTecnico() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(PerfilInterno.TECNICO));

        assertThatThrownBy(() -> service.listar()).isInstanceOf(AcaoNaoAutorizadaException.class);

        verifyNoInteractions(tipoInstrumentoRepository);
    }

    @Test
    void deveRejeitarQuandoChamadorNaoTemPerfilDeLeitura() {
        when(contextoUtilizadorResolver.resolver()).thenReturn(contextoComPerfil(null));

        assertThatThrownBy(() -> service.listar()).isInstanceOf(AcaoNaoAutorizadaException.class);
    }
}
