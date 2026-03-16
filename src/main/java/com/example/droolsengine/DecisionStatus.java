package com.example.droolsengine;

/**
 * Status einer Versandentscheidung im Prozesskontext.
 *
 * AUTO = Drools hat eine eindeutige Versandart bestimmt. Kein menschlicher
 * Eingriff nötig.
 * MANUAL_REVIEW = Eine Drools-Regel existiert, schreibt aber explizit
 * menschliche Prüfung vor
 * (z.B. Russland wegen Sanktionen, JP über 200kg). Das ist KEINE fehlende
 * Regel.
 * NO_RULE_MATCH = Keine passende Regel / ungültige Eingabe. Der Fallback hat
 * gefeuert
 * oder die Input-Validierung (weight ≤ 0, destination fehlt) schlug fehl.
 * FINAL = Finale menschliche Entscheidung nach MANUAL_REVIEW oder
 * NO_RULE_MATCH.
 * Verknüpft mit dem DROOLS-Eintrag derselben processInstanceId.
 *
 * BPMN-Routing (XOR-Gateway "DecisionStatus prüfen"):
 * - AUTO → direkt weiter zu "Daten validieren"
 * - MANUAL_REVIEW || NO_RULE_MATCH → User Task "Spedition manuell auswählen"
 *
 * Alle drei Statuswerte (AUTO, MANUAL_REVIEW, NO_RULE_MATCH) sind fachliche
 * Ergebnisse.
 * Der Worker ruft für ALLE complete() auf — kein handleBpmnError().
 * Nur technische Fehler (HTTP 5xx, Netzwerk) lösen handleFailure() aus.
 */
public enum DecisionStatus {
    AUTO,
    MANUAL_REVIEW,
    NO_RULE_MATCH,
    FINAL
}
