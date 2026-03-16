package com.example.droolsengine;

/**
 * Status einer Versandentscheidung im Prozesskontext.
 *
 * Klare Abgrenzung der Statuswerte:
 *
 * AUTO — Drools hat eine eindeutige Versandart bestimmt.
 * Kein menschlicher Eingriff nötig.
 * Beispiel: CH 100kg → STANDARD_FREIGHT.
 *
 * MANUAL_REVIEW — Eine Drools-Regel wurde ausgewertet und schreibt
 * explizit menschliche Prüfung vor. Die Regel existiert und sagt
 * bewusst "Mensch muss entscheiden".
 * Beispiele:
 * - RU (jedes Gewicht) → Sanktionsprüfung nötig
 * - JP über 200kg → Sondergenehmigung nötig
 * - AR über 500kg → Kapazitätsprüfung nötig
 * - Unbekanntes Land (NOT_DEFINED) → System kann keine sichere Entscheidung
 * treffen, Drools-Fallback-Regel greift → menschliche Prüfung
 *
 * INVALID_INPUT — Eingabedaten sind ungültig. Drools wurde NICHT aufgerufen,
 * weil die Validierung vorher fehlschlug.
 * Beispiele: weight ≤ 0, destination fehlt im Request.
 * Dies ist ein Validierungsfehler, kein fachliches Drools-Ergebnis.
 *
 * FINAL — Finale menschliche Entscheidung nach einem MANUAL_REVIEW oder
 * INVALID_INPUT. Der Sachbearbeiter hat im User Task entschieden.
 * Verknüpft mit dem DROOLS-Eintrag derselben processInstanceId.
 *
 * BPMN-Routing (XOR-Gateway "DecisionStatus prüfen"):
 * - AUTO → direkt weiter zu "Daten validieren"
 * - MANUAL_REVIEW || INVALID_INPUT → User Task "Spedition manuell auswählen"
 *
 * Warum kein NO_RULE_MATCH als eigener Status?
 * Die Drools-Regeldatei enthält eine Fallback-Regel (salience -1000), die
 * IMMER greift wenn keine spezifische Regel trifft → Ergebnis: MANUAL_REVIEW.
 * Ein echtes "keine Regel hat gefeuert" kann daher nicht auftreten.
 * Was bisher als NO_RULE_MATCH bezeichnet wurde, war in Wahrheit INVALID_INPUT
 * (Drools wurde gar nicht aufgerufen).
 */
public enum DecisionStatus {
    AUTO,
    MANUAL_REVIEW,
    INVALID_INPUT,
    FINAL
}
