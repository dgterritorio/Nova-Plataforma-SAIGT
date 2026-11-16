package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.RegrasTiff;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Regras TIFF de um item documental, antes de o gravar. Junta todos os erros num só 400. */
@Component
public class ValidadorRegrasTiff {

    public static final String CAMPO = "regrasTiff";
    public static final String MSG_SO_PECAS_GRAFICAS = "As regras TIFF só se aplicam a itens de peças gráficas.";
    public static final String MSG_SISTEMAS_REFERENCIA =
            "Cada sistema de referência tem de ter o formato EPSG:<código>, sem repetidos.";
    public static final String MSG_COMPRESSOES = "Indique pelo menos uma compressão aceite: NONE ou LZW.";
    public static final String MSG_BITS_POR_BANDA = "Os bits por banda têm de ser maiores que 0.";
    public static final String MSG_RESOLUCAO = "A resolução tem de ser maior que 0 dpi.";
    public static final String MSG_ELEMENTOS_LEGENDA =
            "Cada elemento da legenda tem de estar preenchido e ter no máximo 100 caracteres.";

    private static final Pattern EPSG = Pattern.compile("^EPSG:[0-9]{4,5}$");
    private static final int TAMANHO_MAXIMO_ELEMENTO_LEGENDA = 100;

    public void validar(TipoElemento tipoElemento, RegrasTiff regras) {
        List<CampoErro> erros = erros(tipoElemento, regras);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Regras TIFF inválidas.", erros);
        }
    }

    /** Os erros, um por campo, para juntar aos restantes erros do pedido. */
    public List<CampoErro> erros(TipoElemento tipoElemento, RegrasTiff regras) {
        if (regras == null) {
            return List.of();
        }
        if (tipoElemento != TipoElemento.PECA_GRAFICA) {
            return List.of(new CampoErro(CAMPO, MSG_SO_PECAS_GRAFICAS));
        }
        List<CampoErro> erros = new ArrayList<>();
        if (!sistemasReferenciaValidos(regras.sistemasReferencia())) {
            erros.add(new CampoErro(CAMPO + ".sistemasReferencia", MSG_SISTEMAS_REFERENCIA));
        }
        if (regras.compressoesAceites().isEmpty() || regras.compressoesAceites().stream().anyMatch(Objects::isNull)) {
            erros.add(new CampoErro(CAMPO + ".compressoesAceites", MSG_COMPRESSOES));
        }
        if (regras.bitsPorBanda() != null && regras.bitsPorBanda() <= 0) {
            erros.add(new CampoErro(CAMPO + ".bitsPorBanda", MSG_BITS_POR_BANDA));
        }
        if (regras.resolucaoDpi() != null && regras.resolucaoDpi() <= 0) {
            erros.add(new CampoErro(CAMPO + ".resolucaoDpi", MSG_RESOLUCAO));
        }
        if (!elementosLegendaValidos(regras.elementosLegenda())) {
            erros.add(new CampoErro(CAMPO + ".elementosLegenda", MSG_ELEMENTOS_LEGENDA));
        }
        return erros;
    }

    private static boolean sistemasReferenciaValidos(List<String> sistemas) {
        var vistos = new HashSet<String>();
        for (String sistema : sistemas) {
            if (sistema == null || !EPSG.matcher(sistema).matches() || !vistos.add(sistema)) {
                return false;
            }
        }
        return true;
    }

    private static boolean elementosLegendaValidos(List<String> elementos) {
        return elementos.stream().allMatch(elemento -> elemento != null && !elemento.isBlank()
                && elemento.length() <= TAMANHO_MAXIMO_ELEMENTO_LEGENDA);
    }
}
