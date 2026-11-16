package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.AtualizarDadosFormularioRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseDeliberacaoRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseRequest;
import com.conkord.saigt.dto.instrumento.InformacaoBaseTextoAtoRequest;
import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.exception.CampoBloqueadoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Chaves do formulário inicial, as mesmas de `campos[].path` nos 400, e a regra de bloqueio depois da
 * primeira submissão. Os valores comparam-se normalizados: texto em branco = nulo, elementos afetados como conjunto.
 */
public final class CamposFormularioInicial {

    private static final String SUMARIO = "sumario";
    private static final String DATA_ASSINATURA = "dataAssinatura";
    private static final String NORMA_LEGAL = "normaLegal";
    static final String TIPO_INFORMACAO = "tipoInformacao";
    private static final String PUBLICAR_SUPLEMENTO = "publicarSuplemento";
    private static final String DATA_SUPLEMENTO = "dataSuplemento";
    private static final String CONTACTO_TRAMITACAO = "contactoTramitacao";
    private static final String ENTIDADE_EMITENTE = "entidadeEmitente";
    private static final String ENTIDADE_INSTRUTORA = "entidadeInstrutora";
    private static final String CONTACTO_PUBLICACAO = "contactoPublicacao";
    private static final String ELEMENTOS_AFETADOS = "elementosAfetados";
    private static final String FUNDAMENTACAO_RJIGT = "fundamentacaoRjigt";
    private static final String INTRODUZ_PLANTA_NOVA = "introduzPlantaNova";
    private static final String LOCALIZACAO = "localizacao";
    private static final String CONFORMA_HIERARQUIA_SUPERIOR = "conformaHierarquiaSuperior";
    private static final String MOTIVOS_ALTERACAO = "motivosAlteracao";
    private static final String DINAMICA_ASSOCIADA_ID = "dinamicaAssociadaId";
    /** Caminho do pedido (`dadosFormulario.simultaneoComPmot`); em `dados_formulario` a chave é `simultaneoComPmot`. */
    public static final String SIMULTANEO_COM_PMOT = "dadosFormulario.simultaneoComPmot";
    private static final String CHAVE_SIMULTANEO_COM_PMOT = "simultaneoComPmot";
    /** Só no formulário da Ratificação; o caminho do pedido e a chave em `dados_formulario` são iguais. */
    public static final String MODALIDADE_RATIFICACAO = "modalidadeRatificacao";
    /** O tipo de ato do cabeçalho, em `processo.tipo_ato_id`; o valor compara-se pelo id. */
    public static final String TIPO_ATO_ID = "tipoAtoId";
    /** A série escolhida no processo (`processo.serie_dr`), só nos Procedimentos sem série configurada. */
    public static final String SERIE_DR = "serieDr";
    private static final String DELIBERACAO = "informacaoBase.deliberacao.";
    private static final String TEXTO_ATO = "informacaoBase.textoAto.";

    public static final List<String> CHAVES = List.of(
            SUMARIO, DATA_ASSINATURA, NORMA_LEGAL, TIPO_INFORMACAO, PUBLICAR_SUPLEMENTO, DATA_SUPLEMENTO,
            CONTACTO_TRAMITACAO, ENTIDADE_EMITENTE, CONTACTO_PUBLICACAO, ELEMENTOS_AFETADOS, FUNDAMENTACAO_RJIGT,
            DELIBERACAO + "prazoAnos", DELIBERACAO + "prazoMeses", DELIBERACAO + "prazoDias",
            DELIBERACAO + "avaliacaoAmbiental", DELIBERACAO + "dataDeliberacao", DELIBERACAO + "textoPublicar",
            TEXTO_ATO + "prazoAnos", TEXTO_ATO + "prazoMeses", TEXTO_ATO + "prazoDias", TEXTO_ATO + "avaliacaoAmbiental",
            INTRODUZ_PLANTA_NOVA, LOCALIZACAO, CONFORMA_HIERARQUIA_SUPERIOR, MOTIVOS_ALTERACAO, ENTIDADE_INSTRUTORA,
            DINAMICA_ASSOCIADA_ID, SIMULTANEO_COM_PMOT, MODALIDADE_RATIFICACAO, TIPO_ATO_ID, SERIE_DR);

    /**
     * Chaves que só se alteram juntas e por isso se desbloqueiam e voltam a bloquear juntas: a gravação recusa
     * uma sem a outra (a Dinâmica associada só com a resposta SIM; os motivos só com a conformação; a data do
     * suplemento só com o suplemento, e não tem campo no ecrã) ou o ecrã mostra-as como um só campo (o prazo).
     */
    private static final List<Set<String>> GRUPOS = List.of(
            Set.of(DINAMICA_ASSOCIADA_ID, SIMULTANEO_COM_PMOT),
            Set.of(CONFORMA_HIERARQUIA_SUPERIOR, MOTIVOS_ALTERACAO),
            Set.of(PUBLICAR_SUPLEMENTO, DATA_SUPLEMENTO),
            Set.of(DELIBERACAO + "prazoAnos", DELIBERACAO + "prazoMeses", DELIBERACAO + "prazoDias"),
            Set.of(TEXTO_ATO + "prazoAnos", TEXTO_ATO + "prazoMeses", TEXTO_ATO + "prazoDias"));

    /** Valores normalizados, como se comparam; vão assim para a auditoria. */
    public record Alteracao(String campo, Object valorAntigo, Object valorNovo) {
    }

    private CamposFormularioInicial() {
    }

    /** A chave e as do seu grupo, pela ordem de {@link #CHAVES}; vazia quando a chave não é do formulário inicial. */
    public static List<String> grupo(String chave) {
        if (chave == null || !CHAVES.contains(chave)) {
            return List.of();
        }
        Set<String> juntas = GRUPOS.stream().filter(grupo -> grupo.contains(chave)).findFirst().orElse(Set.of(chave));
        return CHAVES.stream().filter(juntas::contains).toList();
    }

    /** Campos desbloqueados cujo valor pedido difere do gravado. Corre antes de qualquer escrita no processo. */
    public static List<Alteracao> alteracoesDesbloqueadas(Processo processo, Map<String, Object> pedidos,
                                                          ObjectMapper objectMapper) {
        List<String> desbloqueados = processo.getCamposDesbloqueados();
        if (processo.getDataSubmissao() == null || desbloqueados == null || desbloqueados.isEmpty()) {
            return List.of();
        }
        Map<String, Object> atuais = valoresAtuais(processo, objectMapper);
        List<Alteracao> alteracoes = new ArrayList<>();
        for (String chave : CHAVES) {
            if (desbloqueados.contains(chave) && pedidos.containsKey(chave)
                    && !Objects.equals(atuais.get(chave), pedidos.get(chave))) {
                alteracoes.add(new Alteracao(chave, atuais.get(chave), pedidos.get(chave)));
            }
        }
        return alteracoes;
    }

    /** Os desbloqueados sem as chaves alteradas e as do seu grupo: gravar um campo desbloqueado volta a bloqueá-lo. */
    public static List<String> semAsAlteradas(List<String> desbloqueados, List<Alteracao> alteracoes) {
        if (desbloqueados == null) {
            return null;
        }
        Set<String> aBloquear = new LinkedHashSet<>();
        alteracoes.forEach(alteracao -> aBloquear.addAll(grupo(alteracao.campo())));
        return desbloqueados.stream().filter(chave -> !aBloquear.contains(chave)).toList();
    }

    /** Vazio enquanto o processo nunca foi submetido; depois, todas as chaves menos as desbloqueadas. */
    public static List<String> camposBloqueados(Processo processo) {
        if (processo.getDataSubmissao() == null) {
            return List.of();
        }
        List<String> desbloqueados = processo.getCamposDesbloqueados() == null ? List.of() : processo.getCamposDesbloqueados();
        return CHAVES.stream().filter(chave -> !desbloqueados.contains(chave)).toList();
    }

    /** Só as chaves que o pedido traz entram na comparação: o `submeter` não leva todo o formulário. */
    public static void verificarAlteracoes(Processo processo, Map<String, Object> pedidos, ObjectMapper objectMapper) {
        List<String> bloqueados = camposBloqueados(processo);
        if (bloqueados.isEmpty()) {
            return;
        }
        Map<String, Object> atuais = valoresAtuais(processo, objectMapper);
        List<CampoErro> alterados = new ArrayList<>();
        for (String chave : bloqueados) {
            if (pedidos.containsKey(chave) && !Objects.equals(atuais.get(chave), pedidos.get(chave))) {
                alterados.add(new CampoErro(chave, CampoBloqueadoException.ERRO_CAMPO));
            }
        }
        if (!alterados.isEmpty()) {
            throw new CampoBloqueadoException(alterados);
        }
    }

    /**
     * A Prorrogação do Governo não tem «Publicação»: o valor pedido não se compara com o gravado (Diário, fixado pelo
     * servidor), para o campo bloqueado não recusar um pedido que não o traz.
     */
    public static void semPublicacao(FormularioFixo formulario, Map<String, Object> pedidos) {
        if (formulario == FormularioFixo.PRORROGACAO_GOVERNO) {
            pedidos.remove(PUBLICAR_SUPLEMENTO);
            pedidos.remove(DATA_SUPLEMENTO);
        }
    }

    public static Map<String, Object> pedidos(AtualizarDadosFormularioRequest request) {
        Map<String, Object> valores = new HashMap<>();
        valores.put(SUMARIO, normalizar(request.sumario()));
        valores.put(DATA_ASSINATURA, normalizar(request.dataAssinatura()));
        valores.put(NORMA_LEGAL, normalizar(request.normaLegal()));
        valores.put(TIPO_INFORMACAO, normalizar(request.tipoInformacao()));
        valores.put(PUBLICAR_SUPLEMENTO, normalizar(request.publicarSuplemento()));
        valores.put(DATA_SUPLEMENTO, normalizar(request.dataSuplemento()));
        valores.put(CONTACTO_TRAMITACAO, normalizar(request.contactoTramitacao()));
        valores.put(ENTIDADE_EMITENTE, normalizar(request.entidadeEmitente()));
        valores.put(ENTIDADE_INSTRUTORA, normalizar(request.entidadeInstrutora()));
        valores.put(CONTACTO_PUBLICACAO, normalizar(request.contactoPublicacao()));
        valores.put(ELEMENTOS_AFETADOS, normalizar(request.elementosAfetados() == null ? List.of() : request.elementosAfetados()));
        valores.put(FUNDAMENTACAO_RJIGT, normalizar(request.fundamentacaoRjigt() == null ? List.of() : request.fundamentacaoRjigt()));
        InformacaoBaseRequest informacaoBase = request.informacaoBase();
        InformacaoBaseDeliberacaoRequest deliberacao = informacaoBase == null ? null : informacaoBase.deliberacao();
        InformacaoBaseTextoAtoRequest textoAto = informacaoBase == null ? null : informacaoBase.textoAto();
        valores.put(DELIBERACAO + "prazoAnos", normalizar(deliberacao == null ? null : deliberacao.prazoAnos()));
        valores.put(DELIBERACAO + "prazoMeses", normalizar(deliberacao == null ? null : deliberacao.prazoMeses()));
        valores.put(DELIBERACAO + "prazoDias", normalizar(deliberacao == null ? null : deliberacao.prazoDias()));
        valores.put(DELIBERACAO + "avaliacaoAmbiental", normalizar(deliberacao == null ? null : deliberacao.avaliacaoAmbiental()));
        valores.put(DELIBERACAO + "dataDeliberacao", normalizar(deliberacao == null ? null : deliberacao.dataDeliberacao()));
        valores.put(DELIBERACAO + "textoPublicar", normalizar(deliberacao == null ? null : deliberacao.textoPublicar()));
        valores.put(TEXTO_ATO + "prazoAnos", normalizar(textoAto == null ? null : textoAto.prazoAnos()));
        valores.put(TEXTO_ATO + "prazoMeses", normalizar(textoAto == null ? null : textoAto.prazoMeses()));
        valores.put(TEXTO_ATO + "prazoDias", normalizar(textoAto == null ? null : textoAto.prazoDias()));
        valores.put(TEXTO_ATO + "avaliacaoAmbiental", normalizar(textoAto == null ? null : textoAto.avaliacaoAmbiental()));
        valores.put(INTRODUZ_PLANTA_NOVA, normalizar(request.introduzPlantaNova()));
        valores.put(LOCALIZACAO, normalizarLocalizacao(request.localizacao()));
        valores.put(CONFORMA_HIERARQUIA_SUPERIOR, normalizar(request.conformaHierarquiaSuperior()));
        valores.put(MOTIVOS_ALTERACAO, normalizarMotivos(request.motivosAlteracao()));
        valores.put(DINAMICA_ASSOCIADA_ID, normalizar(request.dinamicaAssociadaId()));
        valores.put(SIMULTANEO_COM_PMOT, normalizar(request.simultaneoComPmot()));
        valores.put(MODALIDADE_RATIFICACAO, normalizar(request.modalidadeRatificacao()));
        // Omitido não muda o gravado: não entra na comparação.
        if (request.tipoAtoId() != null) {
            valores.put(TIPO_ATO_ID, normalizar(request.tipoAtoId()));
        }
        if (request.serieDr() != null) {
            valores.put(SERIE_DR, normalizar(request.serieDr()));
        }
        return valores;
    }

    /** O `submeter` só regrava os campos INCM e, quando a traz, a modalidade da Ratificação (omitida = a gravada). */
    public static Map<String, Object> pedidos(SubmeterProcessoRequest request) {
        Map<String, Object> valores = new HashMap<>();
        valores.put(SUMARIO, normalizar(request.sumario()));
        valores.put(DATA_ASSINATURA, normalizar(request.dataAssinatura()));
        valores.put(NORMA_LEGAL, normalizar(request.normaLegal()));
        valores.put(TIPO_INFORMACAO, normalizar(request.tipoInformacao()));
        valores.put(PUBLICAR_SUPLEMENTO, normalizar(request.publicarSuplemento()));
        valores.put(DATA_SUPLEMENTO, normalizar(request.dataSuplemento()));
        if (request.modalidadeRatificacao() != null) {
            valores.put(MODALIDADE_RATIFICACAO, normalizar(request.modalidadeRatificacao()));
        }
        return valores;
    }

    static Map<String, Object> valoresAtuais(Processo processo, ObjectMapper objectMapper) {
        Map<String, Object> valores = new HashMap<>();
        valores.put(SUMARIO, normalizar(processo.getSumario()));
        valores.put(DATA_ASSINATURA, normalizar(processo.getDataAssinatura()));
        valores.put(NORMA_LEGAL, normalizar(processo.getNormaLegal()));
        valores.put(TIPO_INFORMACAO, normalizar(processo.getTipoInformacao()));
        valores.put(PUBLICAR_SUPLEMENTO, normalizar(processo.getPublicarSuplemento()));
        valores.put(DATA_SUPLEMENTO, normalizar(processo.getDataSuplemento()));
        valores.put(CONTACTO_TRAMITACAO, normalizar(processo.getContactoTramitacao()));
        valores.put(ENTIDADE_EMITENTE, normalizar(processo.getEntidadeEmitente()));
        valores.put(ENTIDADE_INSTRUTORA, normalizar(processo.getEntidadeInstrutora()));
        valores.put(CONTACTO_PUBLICACAO, normalizar(processo.getContactoPublicacao()));
        valores.put(ELEMENTOS_AFETADOS, normalizar(processo.getElementosAfetados() == null ? List.of() : processo.getElementosAfetados()));
        valores.put(FUNDAMENTACAO_RJIGT, normalizar(lerFundamentacaoRjigt(processo.getDadosFormulario(), objectMapper)));
        valores.put(INTRODUZ_PLANTA_NOVA, normalizar(lerIntroduzPlantaNova(processo.getDadosFormulario(), objectMapper)));
        valores.put(LOCALIZACAO, normalizarLocalizacao(lerLocalizacao(processo.getDadosFormulario(), objectMapper)));
        valores.put(CONFORMA_HIERARQUIA_SUPERIOR,
                normalizar(lerConformaHierarquiaSuperior(processo.getDadosFormulario(), objectMapper)));
        valores.put(MOTIVOS_ALTERACAO, normalizarMotivos(lerMotivosAlteracao(processo.getDadosFormulario(), objectMapper)));
        valores.put(DINAMICA_ASSOCIADA_ID,
                normalizar(processo.getDinamicaAssociada() == null ? null : processo.getDinamicaAssociada().getId()));
        valores.put(SIMULTANEO_COM_PMOT, normalizar(lerSimultaneoComPmot(processo.getDadosFormulario(), objectMapper)));
        valores.put(MODALIDADE_RATIFICACAO, normalizar(lerModalidadeRatificacao(processo.getDadosFormulario(), objectMapper)));
        valores.put(TIPO_ATO_ID, normalizar(processo.getTipoAto() == null ? null : processo.getTipoAto().getId()));
        valores.put(SERIE_DR, normalizar(processo.getSerieDr()));
        JsonNode informacaoBase = lerInformacaoBase(processo.getDadosFormulario(), objectMapper);
        for (String chave : CHAVES) {
            if (chave.startsWith("informacaoBase.")) {
                JsonNode no = informacaoBase;
                for (String passo : chave.substring("informacaoBase.".length()).split("\\.")) {
                    no = no.path(passo);
                }
                valores.put(chave, normalizar(no));
            }
        }
        return valores;
    }

    /** Códigos gravados em `dados_formulario`, pela ordem gravada; vazia quando não há nenhum. */
    public static List<String> lerFundamentacaoRjigt(String dadosFormulario, ObjectMapper objectMapper) {
        JsonNode codigos = lerChave(dadosFormulario, FUNDAMENTACAO_RJIGT, objectMapper);
        List<String> lidos = new ArrayList<>();
        if (codigos.isArray()) {
            codigos.forEach(codigo -> lidos.add(codigo.asText()));
        }
        return List.copyOf(lidos);
    }

    /** Nulo quando `dados_formulario` não traz a chave, ou o valor não é booleano. */
    public static Boolean lerIntroduzPlantaNova(String dadosFormulario, ObjectMapper objectMapper) {
        JsonNode resposta = lerChave(dadosFormulario, INTRODUZ_PLANTA_NOVA, objectMapper);
        return resposta.isBoolean() ? resposta.asBoolean() : null;
    }

    /** `SIM` ou `NAO`; nulo quando `dados_formulario` não traz a chave ou o valor não é texto. */
    public static String lerSimultaneoComPmot(String dadosFormulario, ObjectMapper objectMapper) {
        return textoOuNulo(lerChave(dadosFormulario, CHAVE_SIMULTANEO_COM_PMOT, objectMapper));
    }

    /** `PUBLICACAO` ou `ALTERACAO`; nula quando `dados_formulario` não traz a chave ou o valor não é texto. */
    public static String lerModalidadeRatificacao(String dadosFormulario, ObjectMapper objectMapper) {
        return textoOuNulo(lerChave(dadosFormulario, MODALIDADE_RATIFICACAO, objectMapper));
    }

    public static LocalizacaoRequest lerLocalizacao(String dadosFormulario, ObjectMapper objectMapper) {
        JsonNode localizacao = lerChave(dadosFormulario, LOCALIZACAO, objectMapper);
        if (!localizacao.isObject()) {
            return null;
        }
        return new LocalizacaoRequest(
                localizacao.path("aplicavelTodaArea").isBoolean() ? localizacao.get("aplicavelTodaArea").asBoolean() : null,
                textoOuNulo(localizacao.path("freguesia")), textoOuNulo(localizacao.path("toponimoCodigo")),
                textoOuNulo(localizacao.path("toponimoTexto")));
    }

    public static Boolean lerConformaHierarquiaSuperior(String dadosFormulario, ObjectMapper objectMapper) {
        JsonNode resposta = lerChave(dadosFormulario, CONFORMA_HIERARQUIA_SUPERIOR, objectMapper);
        return resposta.isBoolean() ? resposta.asBoolean() : null;
    }

    public static List<MotivoAlteracaoRequest> lerMotivosAlteracao(String dadosFormulario, ObjectMapper objectMapper) {
        JsonNode motivos = lerChave(dadosFormulario, MOTIVOS_ALTERACAO, objectMapper);
        List<MotivoAlteracaoRequest> lidos = new ArrayList<>();
        if (motivos.isArray()) {
            motivos.forEach(motivo -> lidos.add(
                    new MotivoAlteracaoRequest(textoOuNulo(motivo.path("tipo")), textoOuNulo(motivo.path("codigo")))));
        }
        return List.copyOf(lidos);
    }

    private static String textoOuNulo(JsonNode no) {
        return no.isTextual() ? no.asText() : null;
    }

    private static Object normalizarLocalizacao(LocalizacaoRequest localizacao) {
        if (localizacao == null) {
            return null;
        }
        String valor = String.join("|", Boolean.TRUE.equals(localizacao.aplicavelTodaArea()) ? "true" : "",
                Objects.toString(normalizar(localizacao.freguesia()), ""),
                Objects.toString(normalizar(localizacao.toponimoCodigo()), ""),
                Objects.toString(normalizar(localizacao.toponimoTexto()), ""));
        return "|||".equals(valor) ? null : valor;
    }

    private static Object normalizarMotivos(List<MotivoAlteracaoRequest> motivos) {
        return normalizar(motivos == null ? List.of()
                : motivos.stream().filter(Objects::nonNull).map(motivo -> motivo.tipo() + ":" + motivo.codigo()).toList());
    }

    private static JsonNode lerInformacaoBase(String dadosFormulario, ObjectMapper objectMapper) {
        return lerChave(dadosFormulario, "informacaoBase", objectMapper);
    }

    /** JSON ilegível vale vazio: qualquer valor pedido passa a diferir e a recusa é a escolha segura. */
    private static JsonNode lerChave(String dadosFormulario, String chave, ObjectMapper objectMapper) {
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(dadosFormulario).path(chave);
        } catch (JsonProcessingException e) {
            return objectMapper.missingNode();
        }
    }

    private static Object normalizar(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Collection<?> colecao) {
            Set<String> conjunto = new LinkedHashSet<>();
            colecao.forEach(elemento -> conjunto.add(String.valueOf(normalizar(elemento))));
            return conjunto;
        }
        if (valor instanceof JsonNode no) {
            return normalizarNo(no);
        }
        if (valor instanceof String texto) {
            return texto.isBlank() ? null : texto.strip();
        }
        return valor.toString();
    }

    private static Object normalizarNo(JsonNode no) {
        if (no.isMissingNode() || no.isNull()) {
            return null;
        }
        if (no.isArray() && no.size() == 3 && no.get(0).canConvertToInt()) {
            return LocalDate.of(no.get(0).asInt(), no.get(1).asInt(), no.get(2).asInt()).toString();
        }
        return normalizar(no.asText());
    }
}
