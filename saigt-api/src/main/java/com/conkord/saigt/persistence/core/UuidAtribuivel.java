package com.conkord.saigt.persistence.core;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Id UUID gerado pelo Hibernate, a menos que a aplicação já o tenha atribuído antes do `persist`. */
@IdGeneratorType(UuidAtribuivelGenerator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface UuidAtribuivel {
}
