package com.example.droolsengine;

/**
 * Quelle einer Versandentscheidung.
 *
 * DROOLS = automatisch durch die Drools Rule Engine bestimmt.
 * HUMAN = manuell durch einen Sachbearbeiter im Camunda User Task gesetzt.
 *
 * Architekturziel: Wenn Drools später durch ein ML-Modell ersetzt wird,
 * wird ein neuer Wert ML hinzugefügt — der Prozess und das Schema bleiben
 * unverändert.
 */
public enum DecisionSource {
    DROOLS,
    HUMAN
}
