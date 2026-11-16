package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.SituacaoPcm;
import com.conkord.saigt.service.instrumento.GrupoProcesso;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validação dos filtros de {@code GET /api/v1/processos}, partilhada com a exportação para que as duas aceitem e
 * recusem exatamente o mesmo. Os valores chegam como texto para que um tipo errado seja um erro do filtro, com o
 * nome dele, e não uma falha de conversão do Spring.
 */
final class LeitorFiltroProcessos {

    static final Set<String> NOMES = Set.of("designacaoInstrumento", "numeroInstrumento", "estado",
            "tipoInstrumentoId", "entidadeId", "numeroProcesso", "ano", "grupo", "dataDe", "dataAte", "numeroDr",
            "incmActIdentifier", "concelho", "nutsII", "motivoAlteracao", "situacaoPcm");

    private static final Pattern PADRAO_NUMERO_PROCESSO = Pattern.compile("\\d{1,20}");
    private static final Pattern PADRAO_ANO = Pattern.compile("\\d{4}");
    private static final Pattern PADRAO_CODIGO_TERRITORIAL = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Pattern PADRAO_MOTIVO_ALTERACAO =
            Pattern.compile("(MOTIVO_ALTERACAO_LEI|MOTIVO_ALTERACAO_PROGRAMA|MOTIVO_ALTERACAO_PLANO):(\\S{1,20})");

    record Parametros(String designacaoInstrumento, String numeroInstrumento, List<String> estado,
                      String tipoInstrumentoId, String entidadeId, String numeroProcesso, String ano, String grupo,
                      String dataDe, String dataAte, String numeroDr, String incmActIdentifier, String concelho,
                      String nutsII, List<String> motivoAlteracao, List<String> situacaoPcm) {
    }

    /** `filtro` só é utilizável quando `erros` vem vazia. */
    record Leitura(ProcessoFiltro filtro, List<ValidacaoException.CampoErro> erros) {
    }

    private LeitorFiltroProcessos() {
    }

    static Leitura ler(Parametros p) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>(errosDeEstados(p.estado()));
        List<MotivoAlteracaoRequest> motivosAlteracao = lerMotivosAlteracao(p.motivoAlteracao(), erros);
        List<SituacaoPcm> situacoesPcm = lerSituacoesPcm(p.situacaoPcm(), erros);
        Long tipoInstrumentoId = lerLong("tipoInstrumentoId", p.tipoInstrumentoId(), erros);
        Long entidadeId = lerLong("entidadeId", p.entidadeId(), erros);
        GrupoProcesso grupo = lerGrupo(p.grupo(), erros);
        Integer ano = lerAno(p.ano(), erros);
        if (p.numeroProcesso() != null && !PADRAO_NUMERO_PROCESSO.matcher(p.numeroProcesso()).matches()) {
            erros.add(new ValidacaoException.CampoErro("numeroProcesso", "Tem de ter entre 1 e 20 dígitos."));
        }
        LocalDate dataDe = lerData("dataDe", p.dataDe(), erros);
        LocalDate dataAte = lerData("dataAte", p.dataAte(), erros);
        if (codigoTerritorialInvalido(p.concelho())) {
            erros.add(new ValidacaoException.CampoErro("concelho", "Código de concelho inválido."));
        }
        if (codigoTerritorialInvalido(p.nutsII())) {
            erros.add(new ValidacaoException.CampoErro("nutsII", "Código de NUTS II inválido."));
        }
        ProcessoFiltro filtro = new ProcessoFiltro(p.designacaoInstrumento(), p.numeroInstrumento(), p.estado(),
                tipoInstrumentoId, entidadeId, p.numeroProcesso(), ano, grupo, dataDe, dataAte, p.numeroDr(),
                p.incmActIdentifier(), p.concelho(), p.nutsII(), motivosAlteracao, situacoesPcm);
        return new Leitura(filtro, List.copyOf(erros));
    }

    private static List<ValidacaoException.CampoErro> errosDeEstados(List<String> estados) {
        if (estados == null) {
            return List.of();
        }
        return estados.stream()
                .filter(estado -> Arrays.stream(EstadoProcesso.values()).noneMatch(valor -> valor.name().equals(estado)))
                .map(estado -> new ValidacaoException.CampoErro("estado", "Estado desconhecido: " + estado + "."))
                .toList();
    }

    private static List<SituacaoPcm> lerSituacoesPcm(List<String> valores, List<ValidacaoException.CampoErro> erros) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        List<SituacaoPcm> situacoes = new ArrayList<>();
        for (String valor : valores) {
            SituacaoPcm situacao = Arrays.stream(SituacaoPcm.values()).filter(s -> s.name().equals(valor)).findFirst().orElse(null);
            if (situacao == null) {
                erros.add(new ValidacaoException.CampoErro("situacaoPcm", "Situação PCM desconhecida: use SEM_PCM, COM_PCM ou DEVOLVIDO."));
                return null;
            }
            situacoes.add(situacao);
        }
        return situacoes.stream().distinct().toList();
    }

    static GrupoProcesso lerGrupo(String grupo, List<ValidacaoException.CampoErro> erros) {
        if (grupo == null) {
            return null;
        }
        return Arrays.stream(GrupoProcesso.values()).filter(valor -> valor.name().equals(grupo)).findFirst()
                .orElseGet(() -> {
                    erros.add(new ValidacaoException.CampoErro("grupo", "Grupo desconhecido: " + grupo + "."));
                    return null;
                });
    }

    private static Integer lerAno(String ano, List<ValidacaoException.CampoErro> erros) {
        if (ano == null) {
            return null;
        }
        if (!PADRAO_ANO.matcher(ano).matches()) {
            erros.add(new ValidacaoException.CampoErro("ano", "Tem de ter 4 dígitos."));
            return null;
        }
        return Integer.valueOf(ano);
    }

    private static Long lerLong(String nome, String valor, List<ValidacaoException.CampoErro> erros) {
        if (valor == null) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            erros.add(new ValidacaoException.CampoErro(nome, "Valor inválido."));
            return null;
        }
    }

    private static LocalDate lerData(String nome, String valor, List<ValidacaoException.CampoErro> erros) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            erros.add(new ValidacaoException.CampoErro(nome, "Valor inválido."));
            return null;
        }
    }

    private static boolean codigoTerritorialInvalido(String codigo) {
        return codigo != null && !PADRAO_CODIGO_TERRITORIAL.matcher(codigo).matches();
    }

    /** Cada valor é `<TIPO>:<codigo>`, com um dos três tipos de motivo; `null` sem o parâmetro. */
    private static List<MotivoAlteracaoRequest> lerMotivosAlteracao(List<String> valores,
                                                                    List<ValidacaoException.CampoErro> erros) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        List<MotivoAlteracaoRequest> motivos = new ArrayList<>();
        for (String valor : valores) {
            Matcher partes = valor == null ? null : PADRAO_MOTIVO_ALTERACAO.matcher(valor);
            if (partes == null || !partes.matches()) {
                erros.add(new ValidacaoException.CampoErro("motivoAlteracao", "Use o formato <TIPO>:<código>, com o "
                        + "tipo MOTIVO_ALTERACAO_LEI, MOTIVO_ALTERACAO_PROGRAMA ou MOTIVO_ALTERACAO_PLANO."));
                return null;
            }
            motivos.add(new MotivoAlteracaoRequest(partes.group(1), partes.group(2)));
        }
        return motivos.stream().distinct().toList();
    }
}
