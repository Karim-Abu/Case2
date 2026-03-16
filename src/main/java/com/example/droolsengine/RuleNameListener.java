package com.example.droolsengine;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

/**
 * Drools AgendaEventListener that captures the name of the last fired rule
 * and stores it on the Logistics object for AI-tracing / audit purposes.
 */
public class RuleNameListener extends DefaultAgendaEventListener {

    private final Logistics logistics;

    public RuleNameListener(Logistics logistics) {
        this.logistics = logistics;
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        logistics.setRuleName(event.getMatch().getRule().getName());
    }
}
