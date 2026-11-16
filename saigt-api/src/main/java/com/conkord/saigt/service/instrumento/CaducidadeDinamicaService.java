package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.InformacaoBaseTextoAtoRequest;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.core.AuditoriaService;
import com.conkord.saigt.service.core.AuditoriaService.TransicaoEstado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Os processos da Dinâmica não mudam de estado e nenhuma ação é recusada por a Dinâmica caducar. */
@Service
public class CaducidadeDinamicaService {

    private static final Logger log = LoggerFactory.getLogger(CaducidadeDinamicaService.class);
    private static final String TIPO_OCORRENCIA = "DINAMICA";
    private static final String ACAO_CADUCIDADE = "CADUCIDADE";
    private static final Set<String> PROCEDIMENTOS_QUE_DEFINEM_CADUCIDADE = Set.of("DECISAO_INICIO");
    private static final String PROCEDIMENTO_QUE_RECALCULA_CADUCIDADE = "PRORROGACAO_ATO";
    private static final ZoneId ZONA_LISBOA = ZoneId.of("Europe/Lisbon");

    private final DinamicaRepository dinamicaRepository;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher publicador;
    private final ObjectMapper objectMapper;

    public CaducidadeDinamicaService(DinamicaRepository dinamicaRepository, AuditoriaService auditoriaService,
                                      ApplicationEventPublisher publicador, ObjectMapper objectMapper) {
        this.dinamicaRepository = dinamicaRepository;
        this.auditoriaService = auditoriaService;
        this.publicador = publicador;
        this.objectMapper = objectMapper;
    }

    /** Na publicação, com o dia de hoje em Lisboa para os limiares dos avisos. */
    public void definirNaPublicacao(Processo processo) {
        definirNaPublicacao(processo, LocalDate.now(ZONA_LISBOA));
    }

    /**
     * Publicada a Decisão de início, `data_caducidade` = data de publicação + prazo do Texto do ato (anos, depois
     * meses, depois dias), por cima de uma data anterior. Publicada uma Prorrogação do ato que visa essa Decisão de
     * início, a data é recalculada da mesma forma com a data e o prazo da Prorrogação, e vale a última. Grava na
     * Dinâmica gerida, na transação de quem chama, que a guarda a seguir. Nunca lança: a falta da data ou do prazo
     * não pode desfazer a publicação.
     */
    void definirNaPublicacao(Processo processo, LocalDate hoje) {
        String codigo = processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo();
        if (defineCaducidade(codigo)) {
            calcular(processo).ifPresent(processo.getDinamica()::setDataCaducidade);
        } else if (recalculaCaducidade(codigo)) {
            recalcularNaProrrogacao(processo, hoje);
        }
    }

    /**
     * Só a Prorrogação da Decisão de início da mesma Dinâmica (EF: «prorrogação para o processo de Decisão de
     * Início»), e só numa Dinâmica `EM_CURSO`: o que acontece a uma `CADUCADA` não está definido.
     */
    private void recalcularNaProrrogacao(Processo processo, LocalDate hoje) {
        Dinamica dinamica = processo.getDinamica();
        if (dinamica.getEstado() != EstadoDinamica.EM_CURSO) {
            log.info("Prorrogação do ato numa Dinâmica que não está em curso, data de caducidade inalterada — "
                    + "processoId={} dinamicaId={} estado={}", processo.getId(), dinamica.getId(), dinamica.getEstado());
            return;
        }
        Processo visado = processo.getProcessoReferido();
        if (visado != null && !eDecisaoDeInicioDa(visado, dinamica)) {
            log.info("Prorrogação do ato que não visa a Decisão de início, data de caducidade inalterada — "
                    + "processoId={} dinamicaId={}", processo.getId(), dinamica.getId());
            return;
        }
        Optional<LocalDate> nova = calcular(processo);
        if (nova.isEmpty()) {
            return;
        }
        if (visado == null) {
            log.warn("Prorrogação do ato sem ato visado, data de caducidade inalterada — processoId={} dinamicaId={}",
                    processo.getId(), dinamica.getId());
            return;
        }
        LocalDate anterior = dinamica.getDataCaducidade();
        dinamica.setDataCaducidade(nova.get());
        if (!nova.get().equals(anterior) && dinamica.getAlertasEnviados() != null) {
            dinamica.setAlertasEnviados(limiaresAindaAtingidos(dinamica.getAlertasEnviados(), nova.get(), hoje));
        }
        log.info("Data de caducidade recalculada pela Prorrogação do ato | processoId={} dinamicaId={} anterior={} nova={}",
                processo.getId(), dinamica.getId(), anterior, nova.get());
    }

    private static boolean eDecisaoDeInicioDa(Processo visado, Dinamica dinamica) {
        return defineCaducidade(visado.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo())
                && visado.getDinamica().getId().equals(dinamica.getId());
    }

    /**
     * Os avisos contam desde a data limite atual: um limiar que deixa de estar atingido sai, para o job avisar outra
     * vez antes da data nova. Um valor que não é um limiar conhecido fica.
     */
    static List<String> limiaresAindaAtingidos(List<String> registados, LocalDate dataCaducidade, LocalDate hoje) {
        return registados.stream()
                .filter(nome -> Arrays.stream(LimiarCaducidade.values())
                        .filter(limiar -> limiar.name().equals(nome))
                        .findFirst()
                        .map(limiar -> limiar.atingido(dataCaducidade, hoje))
                        .orElse(true))
                .toList();
    }

    private Optional<LocalDate> calcular(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        LocalDate dataPublicacao = processo.getDataPublicacaoDr();
        if (dataPublicacao == null) {
            log.warn("Publicação sem data no DR, data de caducidade por calcular — processoId={} dinamicaId={}",
                    processo.getId(), dinamica.getId());
            return Optional.empty();
        }
        Optional<Period> prazo = prazoValido(lerPrazoTextoAto(processo.getDadosFormulario()));
        if (prazo.isEmpty()) {
            log.warn("Publicação sem prazo do Texto do ato, data de caducidade por calcular — processoId={} dinamicaId={}",
                    processo.getId(), dinamica.getId());
            return Optional.empty();
        }
        return Optional.of(somarPrazo(dataPublicacao, prazo.get()));
    }

    static boolean defineCaducidade(String codigoTipoProcedimento) {
        return PROCEDIMENTOS_QUE_DEFINEM_CADUCIDADE.contains(codigoTipoProcedimento);
    }

    /** As Prorrogações das MP, das NP e da Suspensão são Dinâmicas próprias e não mexem nesta data. */
    static boolean recalculaCaducidade(String codigoTipoProcedimento) {
        return PROCEDIMENTO_QUE_RECALCULA_CADUCIDADE.equals(codigoTipoProcedimento);
    }

    /** Nulo conta como 0. Sem nenhum valor, ou com os três a 0, não há prazo: a Dinâmica caducaria logo. */
    static Optional<Period> prazoValido(InformacaoBaseTextoAtoRequest textoAto) {
        if (textoAto == null) {
            return Optional.empty();
        }
        Period prazo = Period.of(valor(textoAto.prazoAnos()), valor(textoAto.prazoMeses()), valor(textoAto.prazoDias()));
        return prazo.isZero() ? Optional.empty() : Optional.of(prazo);
    }

    /** Não é {@code data.plus(prazo)}: esse soma anos e meses de uma vez, e a ordem anos, meses, dias é a da regra. */
    static LocalDate somarPrazo(LocalDate data, Period prazo) {
        return data.plusYears(prazo.getYears()).plusMonths(prazo.getMonths()).plusDays(prazo.getDays());
    }

    private static int valor(Integer prazo) {
        return prazo == null ? 0 : prazo;
    }

    /** JSON ilegível ou sem Texto do ato vale sem prazo. */
    private InformacaoBaseTextoAtoRequest lerPrazoTextoAto(String dadosFormulario) {
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return null;
        }
        try {
            JsonNode textoAto = objectMapper.readTree(dadosFormulario).path("informacaoBase").path("textoAto");
            return textoAto.isObject() ? objectMapper.treeToValue(textoAto, InformacaoBaseTextoAtoRequest.class) : null;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Regista os limiares atingidos e ainda não registados e caduca a Dinâmica na data. Publica um só evento, com
     * o limiar mais avançado: se o job esteve parado, os limiares anteriores ficam registados sem avisos atrasados.
     * O evento é publicado dentro da transação, para os listeners `AFTER_COMMIT` o receberem depois do commit.
     *
     * @return o limiar do evento publicado; vazio se nada mudou ou se a Dinâmica mudou de estado entretanto
     */
    @Transactional
    public Optional<LimiarCaducidade> avaliar(UUID dinamicaId, LocalDate hoje) {
        Dinamica dinamica = dinamicaRepository.findById(dinamicaId).orElse(null);
        if (dinamica == null || dinamica.getEstado() != EstadoDinamica.EM_CURSO || dinamica.getDataCaducidade() == null) {
            return Optional.empty();
        }
        LocalDate dataCaducidade = dinamica.getDataCaducidade();
        List<String> registados = dinamica.getAlertasEnviados() == null ? List.of() : dinamica.getAlertasEnviados();
        List<LimiarCaducidade> novos = Arrays.stream(LimiarCaducidade.values())
                .filter(limiar -> limiar.atingido(dataCaducidade, hoje))
                .filter(limiar -> !registados.contains(limiar.name()))
                .toList();
        if (novos.isEmpty()) {
            return Optional.empty();
        }

        List<String> alertas = new ArrayList<>(registados);
        novos.forEach(limiar -> alertas.add(limiar.name()));
        if (dinamicaRepository.registarAlertas(dinamicaId, alertas) == 0) {
            return Optional.empty();
        }
        if (novos.contains(LimiarCaducidade.DATA)) {
            if (dinamicaRepository.marcarCaducada(dinamicaId) == 0) {
                return Optional.empty();
            }
            auditoriaService.registarSistema(TIPO_OCORRENCIA, dinamicaId.toString(), ACAO_CADUCIDADE,
                    TransicaoEstado.de(EstadoDinamica.EM_CURSO, EstadoDinamica.CADUCADA),
                    Map.of("dataCaducidade", dataCaducidade));
        }

        LimiarCaducidade maisAvancado = novos.get(novos.size() - 1);
        publicador.publishEvent(new AlertaCaducidadeDinamicaEvento(dinamicaId, maisAvancado, dataCaducidade));
        return Optional.of(maisAvancado);
    }
}
