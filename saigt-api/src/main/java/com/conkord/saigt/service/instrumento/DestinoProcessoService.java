package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.exception.DestinoInvalidoException;
import com.conkord.saigt.exception.DestinoSoDepositoException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Regras do destino do processo (BL-PROC-064, BL-PROC-065): quem admite «só depósito», a mudança antes do primeiro
 * ficheiro e a recusa do envio à INCM.
 */
@Service
public class DestinoProcessoService {

    private static final Logger log = LoggerFactory.getLogger(DestinoProcessoService.class);

    static final String MSG_DESTINO_COM_FICHEIROS = "Remova os ficheiros carregados antes de mudar o destino.";

    private final CatalogoFormularios catalogoFormularios;
    private final ProcessoDocumentoRepository processoDocumentoRepository;

    public DestinoProcessoService(CatalogoFormularios catalogoFormularios,
                                  ProcessoDocumentoRepository processoDocumentoRepository) {
        this.catalogoFormularios = catalogoFormularios;
        this.processoDocumentoRepository = processoDocumentoRepository;
    }

    public boolean admiteSoDeposito(Processo processo) {
        return catalogoFormularios.admiteSoDeposito(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento());
    }

    /**
     * Antes de qualquer escrita no Processo. Nulo não muda o gravado. O destino fica fixo a partir do primeiro
     * ficheiro carregado, porque decide os itens onde os ficheiros entraram; repetir o gravado passa sempre.
     */
    public void verificarMudanca(Processo processo, DestinoProcesso pedido) {
        if (pedido == null) {
            return;
        }
        verificarAdmitido(processo, pedido);
        if (pedido != processo.getDestino() && processoDocumentoRepository.existsByProcesso_Id(processo.getId())) {
            log.warn("Mudança de destino recusada, processo com ficheiros — processoId={}", processo.getId());
            throw new EstadoInvalidoException(MSG_DESTINO_COM_FICHEIROS);
        }
    }

    /** Também na submissão, sobre o gravado: a configuração do Procedimento pode ter mudado depois da gravação. */
    public void verificarAdmitido(Processo processo, DestinoProcesso destino) {
        if (destino == DestinoProcesso.SO_DEPOSITO && !admiteSoDeposito(processo)) {
            log.warn("Destino só depósito recusado, formulário não o admite — processoId={}", processo.getId());
            throw new DestinoInvalidoException();
        }
    }

    /** Antes de qualquer chamada à INCM ou atribuição à PCM. */
    public void verificarEnvioIncm(Processo processo) {
        if (soDeposito(processo)) {
            throw new DestinoSoDepositoException();
        }
    }

    public static boolean soDeposito(Processo processo) {
        return processo.getDestino() == DestinoProcesso.SO_DEPOSITO;
    }

    /** `editavel` já diz autor, estado editável e formulário; falta a regra do formulário e não haver ficheiros. */
    public boolean destinoEditavel(Processo processo, boolean editavel) {
        return editavel && admiteSoDeposito(processo) && !processoDocumentoRepository.existsByProcesso_Id(processo.getId());
    }
}
