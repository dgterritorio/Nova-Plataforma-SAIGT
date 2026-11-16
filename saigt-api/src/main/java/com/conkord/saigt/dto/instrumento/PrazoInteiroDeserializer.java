package com.conkord.saigt.dto.instrumento;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** O Jackson trunca 1.5 para 1 num Integer; um prazo com decimais tem de falhar em vez de ser gravado alterado. */
public class PrazoInteiroDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return (Integer) contexto.handleWeirdNumberValue(Integer.class, parser.getNumberValue(), "Só se aceitam números inteiros");
        }
        return (Integer) contexto.handleUnexpectedToken(Integer.class, parser);
    }
}
