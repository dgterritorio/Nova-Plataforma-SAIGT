package com.conkord.saigt.persistence.operacional;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** `PUBLICACAO,DEPOSITO` pela ordem do enum, a única forma que o CHECK da coluna aceita. */
@Converter
public class CaixasIncluidasConverter implements AttributeConverter<Set<CategoriaDocumento>, String> {

    @Override
    public String convertToDatabaseColumn(Set<CategoriaDocumento> caixas) {
        if (caixas == null) {
            return null;
        }
        return EnumSet.copyOf(caixas).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<CategoriaDocumento> convertToEntityAttribute(String valor) {
        if (valor == null) {
            return null;
        }
        return Arrays.stream(valor.split(",")).map(CategoriaDocumento::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CategoriaDocumento.class)));
    }
}
