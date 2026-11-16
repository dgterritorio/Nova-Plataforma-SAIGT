package com.conkord.saigt.service.relatorio;

import com.conkord.saigt.configuration.ExportacaoProperties;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.dto.instrumento.ProcessoListagemResponse;
import com.conkord.saigt.service.instrumento.ProcessoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exportação síncrona da listagem de processos: as linhas, a visibilidade e a ordem são as de
 * {@link ProcessoConsultaService#listar}, sem paginação; as colunas são as do ecrã.
 */
@Service
public class ExportacaoProcessosService {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoProcessosService.class);

    static final String TITULO = "Processos";
    static final List<String> CABECALHO =
            List.of("Referência", "Tipo IGT", "Designação", "Dinâmica", "Procedimento", "Concelho", "Estado", "Utilizador");
    private static final String SEPARADOR_CONCELHOS = ", ";

    private final ProcessoConsultaService processoConsultaService;
    private final ExportacaoProperties properties;
    private final Map<FormatoExportacao, GeradorExportacao> geradores;

    public ExportacaoProcessosService(ProcessoConsultaService processoConsultaService, ExportacaoProperties properties,
                                      List<GeradorExportacao> geradores) {
        this.processoConsultaService = processoConsultaService;
        this.properties = properties;
        this.geradores = geradores.stream().collect(Collectors.toMap(GeradorExportacao::formato, Function.identity(),
                (a, b) -> a, () -> new EnumMap<>(FormatoExportacao.class)));
        if (!this.geradores.keySet().containsAll(Arrays.asList(FormatoExportacao.values()))) {
            throw new IllegalStateException("Falta um gerador para algum formato de exportação.");
        }
    }

    public record Ficheiro(byte[] conteudo, FormatoExportacao formato) {
        public String nome() {
            return "processos." + formato.extensao();
        }
    }

    public Ficheiro exportar(ProcessoFiltro filtro, Sort ordenacao, FormatoExportacao formato) {
        long inicio = System.nanoTime();
        List<ProcessoListagemResponse> processos =
                processoConsultaService.listarParaExportacao(filtro, ordenacao, properties.limiteLinhas());
        List<List<String>> linhas = processos.stream().map(ExportacaoProcessosService::linha).toList();
        byte[] conteudo = geradores.get(formato).gerar(TITULO, CABECALHO, linhas);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
        log.info("Exportação de processos gerada | formato={} linhas={} duracaoMs={}", formato, linhas.size(), duracaoMs);
        return new Ficheiro(conteudo, formato);
    }

    private static List<String> linha(ProcessoListagemResponse processo) {
        return Arrays.asList(processo.numeroProcesso(), processo.tipoInstrumento(), processo.designacaoInstrumento(),
                processo.nomeDinamica(), processo.nomeProcedimento(), processo.concelhos().stream().map(NomesConcelho::formatar).collect(Collectors.joining(SEPARADOR_CONCELHOS)),
                RotulosEstadoProcesso.rotulo(processo.estado()),
                processo.autorNome());
    }
}
