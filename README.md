# Logistics Drools Engine

Dieses Repository enthält eine Spring Boot Applikation, die eine Rule Engine auf Basis von JBoss Drools implementiert. Das Hauptziel des Projekts ist die automatisierte Bestimmung von Versandarten (Delivery Types) basierend auf dem Gewicht und dem Bestimmungsland einer Lieferung.

---

## Projektübersicht

Die Anwendung nutzt Drools-Entscheidungstabellen, um Logikregeln von der Java-Programmierung zu trennen. Dies ermöglicht es, komplexe Versandregeln einfach zu verwalten und anzupassen.

### Kernkomponenten

* **LogisticsController**: Stellt einen REST-Endpunkt (`/deliveryRuleManager`) zur Verfügung, der Logistikdaten im JSON-Format entgegennimmt.
* **LogisticsService**: Verarbeitet die Logik, indem er die Drools-Session initiiert, die Fakten (Logistics-Objekte) einspeist und die Regeln ausführt.
* **Domain Modell**: Besteht aus Klassen wie `Logistics`, `DeliveryCountry` und `DeliveryType`, welche die Datenstruktur für die Regelprüfung definieren.
* **Regel-Definitionen**: Die Versandregeln sind in der Datei `logistic_rules.drl` definiert, welche ursprünglich aus einer Excel-Entscheidungstabelle generiert wurde.

## Versandregeln

Das System wendet aktuell folgende Logik an:

* **Argentinien (AR)**:
    * Bis 60kg: Standard Mail.
    * Über 60kg bis 500kg: Special Freight.
    * Über 500kg: Manuelle Überprüfung.
* **Japan (JP)**:
    * Bis 200kg: Air Freight.
* **Russland (RU)**: Immer manuelle Überprüfung.
* **Schweiz (CH) & Deutschland (DE)**: Standard Freight für alle Sendungen über 0kg.
* **Fallback**: Wenn keine Regel zutrifft oder das Land nicht definiert ist, wird die Sendung für eine manuelle Überprüfung markiert.

---

## Installation und Start

### Voraussetzungen
* Java 21
* Maven (Wrapper im Repository enthalten)

### Applikation starten
Nutzen Sie den Maven-Wrapper, um die Anwendung zu bauen und zu starten:

```bash
# Unter Linux/macOS
./mvnw spring-boot:run

# Unter Windows
./mvnw.cmd spring-boot:run
```
---

## API-Nutzung
### Beispiel-Anfrage

Sie können die Versandart über einen POST-Request an den Endpunkt /deliveryRuleManager abfragen:

URL: http://localhost:8080/deliveryRuleManager

### Body (JSON):
```JSON
{
"weight": 55.0,
"destination": "AR"
}
```

### Antwort:
Das System ergänzt das Feld deliveryType basierend auf den Regeln.

---

## Tests

Das Projekt enthält umfassende JUnit-Tests in DroolsTestApplicationTests.java, welche die verschiedenen Länder- und Gewichtskombinationen sowie Edge-Cases prüfen. Diese können wie folgt ausgeführt werden:
Bash

./mvnw test

---

