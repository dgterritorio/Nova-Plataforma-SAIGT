package com.conkord.saigt.integracao.snit;

public enum ListaReferenciaSnit {
    INSTRUMENT_TYPES("/api/integrations/saigt/lookups/instrument-types", "id", "label"),
    PROCEDURES("/api/integrations/saigt/lookups/procedures", "id", "label"),
    PROCEDURE_TYPES("/api/integrations/saigt/lookups/procedure-types", "id", "label"),
    CLASSES("/api/integrations/saigt/lookups/classes", "id", "label"),
    NUT2("/api/integrations/saigt/lookups/nut2", "code", "label"),
    MUNICIPALITIES("/api/integrations/saigt/lookups/municipalities", "dtcc", "name");
    public static final String CHAVE_NUT2_DO_MUNICIPIO = "nut2Code";
    public static final String CHAVE_CLASSE_DO_TIPO_INSTRUMENTO = "classId";

    private final String caminho;
    private final String chaveCodigo;
    private final String chaveNome;

    ListaReferenciaSnit(String caminho, String chaveCodigo, String chaveNome) {
        this.caminho = caminho;
        this.chaveCodigo = chaveCodigo;
        this.chaveNome = chaveNome;
    }

    public String caminho() {
        return caminho;
    }

    public String chaveCodigo() {
        return chaveCodigo;
    }

    public String chaveNome() {
        return chaveNome;
    }

    public String operacao() {
        return "OBTER_REFERENCIA_" + name();
    }
}
