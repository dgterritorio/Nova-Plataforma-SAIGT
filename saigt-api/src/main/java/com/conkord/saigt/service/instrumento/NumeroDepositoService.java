package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.core.MunicipioCodigoDeposito;
import com.conkord.saigt.persistence.core.MunicipioCodigoDepositoRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelho;
import com.conkord.saigt.persistence.operacional.InstrumentoConcelhoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.SequenciaNumeroDepositoRepository;
import com.conkord.saigt.service.core.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Atribui o n.º de depósito (`Região.DICO/SiglaIGT/CódDinâmica/Ano/Sequencial`) ao processo do procedimento final
 * quando passa a `PUBLICADO`, como a plataforma atual: uma vez, sequencial anual por série. Um dado em falta deixa o
 * processo publicado sem número e sem gastar sequencial.
 */
@Service
public class NumeroDepositoService {

    private static final Logger log = LoggerFactory.getLogger(NumeroDepositoService.class);
    private static final String TIPO_OCORRENCIA = "PROCESSO";
    private static final String ACAO_ATRIBUIR = "ATRIBUIR_NUMERO_DEPOSITO";
    private static final String ORIGEM_REGISTO_EXTERNO = "REGISTO_EXTERNO";
    private static final String DICO_NEUTRO = "00.00";
    private static final String REGIAO_NEUTRA = "00";
    private static final ZoneId ZONA_LISBOA = ZoneId.of("Europe/Lisbon");

    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final InstrumentoConcelhoRepository instrumentoConcelhoRepository;
    private final MunicipioCodigoDepositoRepository municipioCodigoDepositoRepository;
    private final SequenciaNumeroDepositoRepository sequenciaRepository;
    private final AuditoriaService auditoriaService;
    private final Clock relogio;

    public NumeroDepositoService(InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                 InstrumentoConcelhoRepository instrumentoConcelhoRepository,
                                 MunicipioCodigoDepositoRepository municipioCodigoDepositoRepository,
                                 SequenciaNumeroDepositoRepository sequenciaRepository,
                                 AuditoriaService auditoriaService, Clock relogio) {
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.instrumentoConcelhoRepository = instrumentoConcelhoRepository;
        this.municipioCodigoDepositoRepository = municipioCodigoDepositoRepository;
        this.sequenciaRepository = sequenciaRepository;
        this.auditoriaService = auditoriaService;
        this.relogio = relogio;
    }

    /** Corre na transação de quem passa o processo a `PUBLICADO`. */
    @Transactional
    public void atribuirNaPublicacao(Processo processo) {
        if (processo.getNumeroDeposito() != null || ORIGEM_REGISTO_EXTERNO.equals(processo.getOrigemDados())
                || !doProcedimentoFinal(processo)) {
            return;
        }
        Dinamica dinamica = processo.getDinamica();
        TipoInstrumento tipoInstrumento = dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento();
        String codigoDinamica = dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigoIdus();
        if (codigoDinamica == null) {
            avisarEmFalta(processo, "tipo_dinamica.codigo_idus");
            return;
        }
        Optional<String> regiaoDico = regiaoDico(dinamica, processo);
        if (regiaoDico.isEmpty()) {
            return;
        }
        int ano = relogio.instant().atZone(ZONA_LISBOA).getYear();
        int sequencial = sequenciaRepository.proximoSequencial(tipoInstrumento.getSerieNumeroDeposito(), ano);
        String numero = String.join("/", regiaoDico.get(), tipoInstrumento.getCodigo(), codigoDinamica,
                Integer.toString(ano), Integer.toString(sequencial));
        processo.setNumeroDeposito(numero);
        auditoriaService.registarSistema(TIPO_OCORRENCIA, processo.getId().toString(), ACAO_ATRIBUIR,
                Map.of("numeroDeposito", numero));
    }

    /** O processo é do Procedimento ativo e obrigatório de maior `ordem` da Dinâmica. */
    public boolean doProcedimentoFinal(Processo processo) {
        return instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(
                        processo.getDinamica().getInstrumentoDinamicaConfig())
                .map(InstrumentoProcedimentoConfig::getId)
                .filter(id -> Objects.equals(id, processo.getInstrumentoProcedimentoConfig().getId()))
                .isPresent();
    }

    /**
     * Um concelho: o seu código. Vários da mesma região: `RR.00.00` (DICO 0000). Regiões diferentes ou nenhum
     * concelho: `00.00.00`.
     */
    /** Pelos concelhos do Instrumento de início ou, numa Dinâmica iniciada sem IGT, pelos dos Instrumentos que abrange. */
    private Optional<String> regiaoDico(Dinamica dinamica, Processo processo) {
        List<UUID> instrumentoIds = dinamica.instrumentoDeInicio().map(instrumento -> List.of(instrumento.getId()))
                .orElseGet(() -> dinamica.getInstrumentos().stream().map(Instrumento::getId).toList());
        List<String> dtccs = instrumentoIds.stream()
                .flatMap(id -> instrumentoConcelhoRepository.findByIdInstrumentoIdOrderByIdMunicipioCodigo(id).stream())
                .map(InstrumentoConcelho::getMunicipioCodigo).distinct().sorted().toList();
        if (dtccs.isEmpty()) {
            return Optional.of(REGIAO_NEUTRA + "." + DICO_NEUTRO);
        }
        List<MunicipioCodigoDeposito> municipios = municipioCodigoDepositoRepository.findAllById(dtccs);
        if (municipios.size() != dtccs.size()) {
            avisarEmFalta(processo, "municipio_codigo_deposito de " + dtccs);
            return Optional.empty();
        }
        if (municipios.size() == 1) {
            return Optional.of(municipios.get(0).getCodigoMunicipio());
        }
        List<String> regioes = municipios.stream().map(MunicipioCodigoDeposito::getCodigoRegiao).distinct().toList();
        String regiao = regioes.size() == 1 ? regioes.get(0) : REGIAO_NEUTRA;
        return Optional.of(regiao + "." + DICO_NEUTRO);
    }

    private static void avisarEmFalta(Processo processo, String dado) {
        log.warn("N.º de depósito por atribuir, falta {} — processoId={}", dado, processo.getId());
    }
}
