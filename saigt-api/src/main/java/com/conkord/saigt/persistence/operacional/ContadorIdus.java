package com.conkord.saigt.persistence.operacional;

import org.hibernate.annotations.ValueGenerationType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Atribui ao campo, na inserção, o próximo valor do contador `nome` de `idus_contador`, com 5 dígitos. */
@ValueGenerationType(generatedBy = ContadorIdusGenerator.class)
@Retention(RUNTIME)
@Target(FIELD)
public @interface ContadorIdus {

    String nome();
}
