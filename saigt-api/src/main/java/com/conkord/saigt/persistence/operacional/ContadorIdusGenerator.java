package com.conkord.saigt.persistence.operacional;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

import java.lang.reflect.Member;
import java.util.EnumSet;

/**
 * Incrementa o contador com {@link ContadoresIdus}, na ligação e na transação da própria sessão. As restrições
 * `UNIQUE` das colunas apanham qualquer repetição.
 */
public class ContadorIdusGenerator implements BeforeExecutionGenerator, AnnotationBasedGenerator<ContadorIdus> {

    private String nome;

    @Override
    public void initialize(ContadorIdus configuracao, Member membro, GeneratorCreationContext contexto) {
        this.nome = configuracao.nome();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    @Override
    public Object generate(SharedSessionContractImplementor sessao, Object entidade, Object valorAtual,
                           EventType evento) {
        String[] numero = new String[1];
        sessao.doWork(ligacao -> numero[0] = String.format("%05d", ContadoresIdus.proximo(ligacao, nome)));
        return numero[0];
    }
}
