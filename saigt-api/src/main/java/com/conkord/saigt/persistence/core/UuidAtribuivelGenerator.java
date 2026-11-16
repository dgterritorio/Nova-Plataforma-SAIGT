package com.conkord.saigt.persistence.core;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.util.EnumSet;
import java.util.UUID;

public class UuidAtribuivelGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor sessao, Object entidade, Object valorAtual, EventType evento) {
        if (entidade instanceof BaseEntity base && base.getId() != null) {
            return base.getId();
        }
        return UUID.randomUUID();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }
}
