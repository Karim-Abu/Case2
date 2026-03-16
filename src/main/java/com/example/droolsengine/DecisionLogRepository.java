package com.example.droolsengine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistenzschicht für Entscheidungsprotokolle.
 *
 * Alle Methoden sind read-only nach dem Speichern — kein delete(), kein save()
 * mit Mutation.
 * Der Service stellt sicher, dass keine bestehenden Einträge modifiziert
 * werden.
 */
public interface DecisionLogRepository extends JpaRepository<DecisionLog, Long> {

    /**
     * Alle Einträge einer Prozessinstanz — für Audit und Verknüpfung von DROOLS +
     * HUMAN.
     */
    List<DecisionLog> findByProcessInstanceId(String processInstanceId);

    /** Anzahl manueller Finalentscheidungen. KPI-Berechnung. */
    long countByDecisionStatus(DecisionStatus decisionStatus);
}
