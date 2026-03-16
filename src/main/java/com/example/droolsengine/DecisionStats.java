package com.example.droolsengine;

/**
 * KPI-Antwort für GET /decisions/stats.
 *
 * overrideRate = finalHumanDecisions / (automaticDecisions +
 * manualReviewDecisions).
 * Bezugsgrösse sind nur Drools-Entscheidungen, nicht der Gesamtbestand.
 * Steigt die overrideRate → stimmen die Drools-Regeln nicht mehr mit der
 * Realität überein.
 * Diese Kennzahl ist der wichtigste Indikator für den Zeitpunkt, ein ML-Modell
 * einzuführen.
 */
public record DecisionStats(
        long totalDecisions,
        long automaticDecisions,
        long manualReviewDecisions,
        long finalHumanDecisions,
        double overrideRate) {
}
