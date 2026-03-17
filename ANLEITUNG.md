# Anleitung — Versandbeauftragungsprozess (Case 2, Group 2)

Diese Anleitung erklärt das gesamte Projekt so, dass man es auch ohne tiefes Programmierwissen versteht.
Sie beschreibt, was das System tut, wie die Teile zusammenspielen und wo man was findet.

---

## Was macht dieses System?

Die Firma **Accolaia AG** verschickt Waren in verschiedene Länder. Je nach Zielland und Gewicht
muss eine passende **Versandart** gewählt werden — z.B. Standardpost, Luftfracht oder Spezialfracht.

Dieses System **automatisiert** diese Entscheidung:

1. Ein Mitarbeiter gibt im Camunda-Prozess das Zielland und das Gewicht ein.
2. Das System prüft automatisch anhand von Regeln, welche Versandart passt.
3. Falls das System die Entscheidung nicht allein treffen kann, wird ein Mensch einbezogen.
4. Zum Schluss wird ein Transportauftrag an die Spedition gesendet.

Alle Entscheidungen werden in einer Datenbank gespeichert — sowohl die automatischen als auch die manuellen.
Diese Daten können später für ein KI-Modell verwendet werden.

---

## Übersicht der Komponenten

Das Projekt besteht aus **zwei Teilprojekten**, die zusammenarbeiten:

```
Case2 (dieses Repository)
│
├── /                          ← Drools Engine (Spring Boot Service)
│   ├── src/main/java/...      ← Java-Quellcode (Regeln, REST-API, Datenbank)
│   ├── src/main/resources/    ← Konfiguration + Regeltabelle (Excel)
│   └── src/test/java/...      ← Automatisierte Tests
│
├── swa_case_2_worker/         ← Camunda Workers (Java-Programm)
│   ├── src/main/java/...      ← Java-Quellcode (3 Worker)
│   └── pom.xml                ← Build-Konfiguration
│
├── README.md                  ← Technisches README des Drools-Service
├── ANLEITUNG.md               ← Diese Datei (Übersicht und Anleitung)
└── Versandbeauftragungsprozess_mit_drools (1).bpmn  ← Der BPMN-Prozess
```

### 1. Drools Engine (Hauptverzeichnis `/`)

Das ist ein **Webservice** (eine Art Server), der auf Anfragen wartet und folgendes tut:

- **Regeln auswerten:** Anhand einer Excel-Tabelle wird für ein Zielland + Gewicht automatisch die richtige Versandart bestimmt.
- **Entscheidungen speichern:** Jede Entscheidung wird in einer Datenbank protokolliert (wer hat entschieden, welche Regel, wann).
- **Statistiken bereitstellen:** Wie oft wurde automatisch entschieden vs. manuell?

### 2. Camunda Workers (Unterverzeichnis `swa_case_2_worker/`)

Das sind **drei kleine Programme**, die im Hintergrund laufen und Aufgaben vom Camunda-Prozess abholen:

| Worker                 | Was er tut                                                                                                                                                                                                                                       |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **DroolsWorker**       | Schickt Zielland + Gewicht an die Drools Engine, schreibt das Ergebnis (AUTO/MANUAL_REVIEW/INVALID_INPUT) als Prozessvariablen zurück. Bei MANUAL_REVIEW wird `manualDecisionReason` automatisch mit der auslösenden Regelbezeichnung vorbelegt. |
| **DecisionLogWorker**  | Schreibt nach der manuellen Entscheidung des Mitarbeiters den zweiten DB-Eintrag (HUMAN/FINAL). Der erste Eintrag (DROOLS/MANUAL_REVIEW) wurde bereits beim Drools-Aufruf automatisch angelegt.                                                  |
| **SpeditionApiWorker** | Sendet den fertigen Transportauftrag an die Speditions-API und verarbeitet die Antwort (Trackingnummer, Abholdatum). Bei Ablehnung wird ein BPMN-Error ausgelöst.                                                                                |

---

## Der Ablauf (Schritt für Schritt)

```
Mitarbeiter gibt Zielland + Gewicht ein
         │
         ▼
   ┌─────────────┐
   │ DroolsWorker │──► Drools Engine prüft Regeln
   └──────┬──────┘
          │
          ▼
    ┌───────────────────────┐
    │ Ergebnis der Prüfung: │
    ├───────────────────────┤
    │ AUTO                  │ → Versandart steht fest (z.B. STANDARD_MAIL)
    │ MANUAL_REVIEW         │ → Ein Mensch muss entscheiden (z.B. Sanktionsland)
    │ INVALID_INPUT         │ → Eingabe war ungültig (z.B. Gewicht = 0)
    └───────┬───────────────┘
            │
    ┌───────▼───────────────────┐
    │ Falls MANUAL_REVIEW oder  │
    │ INVALID_INPUT:            │
    │  → Mitarbeiter wählt      │
    │    Versandart manuell     │
    │  → DecisionLogWorker      │
    │    speichert Begründung   │
    └───────┬───────────────────┘
            │
            ▼
    ┌──────────────────┐
    │ SpeditionApiWorker│──► Speditions-API: Transportauftrag absenden
    └──────────────────┘
            │
            ▼
    Trackingnummer + Abholdatum erhalten
    Kunde wird informiert, Vorgang archiviert
```

---

## Die Versandregeln

Die Regeln stehen in einer **Excel-Datei** (`src/main/resources/rules/Logistics.drl.xls`).
Das bedeutet: Um Regeln zu ändern, muss man **keinen Code anfassen** — nur die Excel-Tabelle anpassen.

| Zielland         | Gewicht     | Versandart                          |
| ---------------- | ----------- | ----------------------------------- |
| Argentinien (AR) | bis 60 kg   | Standardpost (STANDARD_MAIL)        |
| Argentinien (AR) | 60–500 kg   | Spezialfracht (SPECIAL_FREIGHT)     |
| Argentinien (AR) | über 500 kg | Manuelle Prüfung nötig              |
| Japan (JP)       | bis 200 kg  | Luftfracht (AIR_FREIGHT)            |
| Japan (JP)       | über 200 kg | Manuelle Prüfung nötig              |
| Russland (RU)    | beliebig    | Manuelle Prüfung nötig (Sanktionen) |
| Schweiz (CH)     | beliebig    | Standardfracht (STANDARD_FREIGHT)   |
| Deutschland (DE) | beliebig    | Standardfracht (STANDARD_FREIGHT)   |
| Unbekanntes Land | beliebig    | Manuelle Prüfung nötig              |

Falls gar keine Regel greift (Sicherheitsnetz): → Manuelle Prüfung.

---

## Wo finde ich was?

### Drools Engine (Hauptverzeichnis)

| Datei / Ordner                                    | Beschreibung                                                                |
| ------------------------------------------------- | --------------------------------------------------------------------------- |
| `src/main/java/com/example/droolsengine/`         | Gesamter Java-Quellcode der Drools Engine                                   |
| `LogisticsController.java`                        | Empfängt Anfragen (REST-API) und gibt Antworten zurück (HTTP 202, 206, 400) |
| `LogisticsService.java`                           | Initialisiert die Drools-Regeln und wertet sie pro Anfrage aus              |
| `DecisionLogService.java`                         | Speichert Entscheidungen in der Datenbank                                   |
| `DecisionLogController.java`                      | REST-Endpunkte für Statistiken und manuelle Entscheidungen                  |
| `DecisionLog.java`                                | Datenbank-Modell: Was wird pro Entscheidung gespeichert                     |
| `DecisionStatus.java`                             | Die vier möglichen Status: AUTO, MANUAL_REVIEW, INVALID_INPUT, FINAL        |
| `Logistics.java`                                  | Datenobjekt: Zielland + Gewicht + Ergebnis (wird an Drools übergeben)       |
| `RuleNameListener.java`                           | Merkt sich, welche Regel gefeuert hat (für das Protokoll)                   |
| `src/main/resources/rules/Logistics.drl.xls`      | **Die Excel-Regeltabelle** — hier werden Versandregeln gepflegt             |
| `src/main/resources/rules/logistic_rules.drl`     | Referenzdokument der Regeln (wird nicht direkt verwendet)                   |
| `src/main/resources/application.properties`       | Grundkonfiguration (welches Datenbankprofil, Regelversion)                  |
| `src/main/resources/application-h2.properties`    | Konfiguration für lokale Entwicklung (In-Memory-Datenbank)                  |
| `src/main/resources/application-mysql.properties` | Konfiguration für Produktion (MySQL-Datenbank)                              |
| `src/test/java/com/example/droolstest/`           | Automatisierte Tests                                                        |
| `pom.xml`                                         | Build-Konfiguration und Abhängigkeiten (Maven)                              |

### Camunda Workers (`swa_case_2_worker/`)

| Datei                                                    | Beschreibung                                                 |
| -------------------------------------------------------- | ------------------------------------------------------------ |
| `src/main/java/ch/fhnw/students/WorkerMain.java`         | Startpunkt: Registriert alle drei Worker beim Camunda-Server |
| `src/main/java/ch/fhnw/students/DroolsWorker.java`       | Worker 1: Versandart bestimmen (ruft Drools Engine auf)      |
| `src/main/java/ch/fhnw/students/DecisionLogWorker.java`  | Worker 2: Manuelle Entscheidung in Datenbank protokollieren  |
| `src/main/java/ch/fhnw/students/SpeditionApiWorker.java` | Worker 3: Transportauftrag an Speditions-API senden          |
| `src/main/java/ch/fhnw/students/HttpHelper.java`         | Hilfscode für HTTP-Aufrufe                                   |
| `pom.xml`                                                | Build-Konfiguration des Worker-Projekts                      |

### Sonstige Dateien

| Datei                                             | Beschreibung                                                       |
| ------------------------------------------------- | ------------------------------------------------------------------ |
| `Versandbeauftragungsprozess_mit_drools (1).bpmn` | Der BPMN-Prozess als XML — kann in Camunda Modeler geöffnet werden |
| `README.md`                                       | Technisches README der Drools Engine (für Entwickler)              |
| `swa_case_2_worker/README.md`                     | Technisches README der Workers (für Entwickler)                    |
| `ANLEITUNG.md`                                    | Diese Datei                                                        |

---

## So startet man das System

### Voraussetzungen

- **Java 21** muss installiert sein ([Download](https://adoptium.net/))
- Maven muss nicht separat installiert werden — der Maven Wrapper (`mvnw.cmd`) ist im Repo enthalten

### Schritt 1: Drools Engine starten

Terminal öffnen im Hauptverzeichnis und ausführen:

```powershell
.\mvnw.cmd spring-boot:run
```

Der Service startet auf **Port 8080** mit MySQL (Standardprofil). Die MySQL-Zugangsdaten müssen als Umgebungsvariablen gesetzt sein (siehe unten).

Für lokale Tests **ohne** MySQL kann H2 temporär aktiviert werden:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring.profiles.active=h2"
```

Zum Testen, ob der Service läuft:

```
http://localhost:8080/ping
```

Erwartete Antwort: `{"ping": true}`

### Schritt 2: Workers starten

Zweites Terminal öffnen im Ordner `swa_case_2_worker/swa_case_2_worker/` und ausführen:

```powershell
..\..\swa_case_2_group_2_drools_engine\mvnw.cmd compile exec:java "-Dexec.mainClass=ch.fhnw.students.WorkerMain"
```

Die drei Worker verbinden sich mit Camunda und warten auf Aufgaben.

### Schritt 3: Tests ausführen

Im Hauptverzeichnis:

```powershell
.\mvnw.cmd test
```

Es gibt 24 automatisierte Tests, die alle Regeln und REST-Endpunkte prüfen.

---

## Wie die Datenbank funktioniert

Jede Entscheidung erzeugt mindestens einen Eintrag in der Tabelle `decision_log`:

| Feld                  | Bedeutung                                                      |
| --------------------- | -------------------------------------------------------------- |
| `delivery_country`    | Zielland (z.B. „AR" für Argentinien)                           |
| `weight`              | Gewicht in kg                                                  |
| `delivery_type`       | Versandart (z.B. „STANDARD_MAIL")                              |
| `decision_source`     | Wer hat entschieden? `DROOLS` (Computer) oder `HUMAN` (Mensch) |
| `decision_status`     | Status: AUTO, MANUAL_REVIEW, INVALID_INPUT oder FINAL          |
| `rule_name`           | Welche Regel hat gefeuert (nur bei DROOLS-Einträgen)           |
| `manual_reason`       | Begründung des Mitarbeiters (nur bei HUMAN-Einträgen)          |
| `selected_carrier`    | Vom Mitarbeiter gewählte Spedition (nur bei HUMAN-Einträgen)   |
| `process_instance_id` | Verknüpft DROOLS- und HUMAN-Eintrag derselben Prozessinstanz   |
| `timestamp`           | Zeitpunkt der Entscheidung (automatisch gesetzt)               |

### Warum zwei Einträge bei MANUAL_REVIEW?

Bei **manuellen Entscheidungen** erzeugt das System bewusst **zwei separate Einträge**,
verknüpft über die `process_instance_id`:

**Eintrag 1 — DROOLS / MANUAL_REVIEW** (wird beim Drools-Aufruf sofort angelegt):

| Feld              | Inhalt                                     |
| ----------------- | ------------------------------------------ |
| `decision_source` | `DROOLS`                                   |
| `decision_status` | `MANUAL_REVIEW`                            |
| `delivery_type`   | `MANUAL_REVIEW` (Systemempfehlung: unklar) |
| `rule_name`       | z.B. `Delivery JP > 200kg`                 |
| `manual_reason`   | leer                                       |

**Eintrag 2 — HUMAN / FINAL** (wird nach dem User Task angelegt):

| Feld               | Inhalt                                          |
| ------------------ | ----------------------------------------------- |
| `decision_source`  | `HUMAN`                                         |
| `decision_status`  | `FINAL`                                         |
| `delivery_type`    | z.B. `AIR_FREIGHT` (Entscheid des Mitarbeiters) |
| `manual_reason`    | Begründung des Mitarbeiters                     |
| `selected_carrier` | Gewählte Spedition                              |

**Warum diese Trennung sinnvoll ist:**

Würde man nur die menschliche Entscheidung speichern, verlöre man die wertvolle Systemsicht:
Welche Regel hat zu MANUAL_REVIEW geführt? Warum war das System unsicher?
Erst das **Paar aus Systemsicht und Menschensicht** macht die Daten aussagekräftig:

```
Systemsicht (DROOLS):     Land=JP, Gewicht=231 kg → Regel: JP > 200kg → MANUAL_REVIEW
Menschliche Finalsicht:   Versandart=AIR_FREIGHT, Begründung="Sondergenehmigung"
                                     ↓
Erkenntnis: JP 200–250 kg könnte zukünftig AIR_FREIGHT sein (Regelanpassung möglich)
```

Mit wachsender Datenmenge lässt sich ableiten, welche Regeln regelmässig übersteuert werden —
ein Signal, dass eine Regel angepasst oder durch ein KI-Modell ersetzt werden sollte.

**AUTO-Entscheidungen** erzeugen nur **einen** Eintrag (DROOLS/AUTO) —
kein Mensch greift ein, kein zweiter Eintrag nötig.

---

## Begriffe kurz erklärt

| Begriff                  | Erklärung                                                                                                                   |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| **Drools**               | Eine Regel-Engine von Red Hat. Man beschreibt Geschäftsregeln in einer Excel-Tabelle und Drools wertet sie automatisch aus. |
| **Camunda**              | Eine Workflow-Engine. Steuert den BPMN-Prozess und verteilt Aufgaben an Workers.                                            |
| **BPMN**                 | Business Process Model and Notation — eine standardisierte Darstellung von Geschäftsprozessen (wie ein Flussdiagramm).      |
| **External Task Worker** | Ein Programm, das sich bei Camunda anmeldet und Aufgaben abholt, verarbeitet und das Ergebnis zurückmeldet.                 |
| **Spring Boot**          | Ein Java-Framework für Webservices. Macht es einfach, REST-APIs und Datenbank-Zugriffe zu bauen.                            |
| **REST-API**             | Eine Schnittstelle, über die Programme miteinander über HTTP kommunizieren (wie ein Browser mit einer Website).             |
| **H2**                   | Eine kleine In-Memory-Datenbank für Entwicklung und Tests. Die Daten verschwinden beim Neustart.                            |
| **MySQL**                | Eine echte Datenbank für den Produktionsbetrieb. Die Daten bleiben gespeichert.                                             |
| **Maven**                | Ein Build-Tool für Java-Projekte. Lädt Abhängigkeiten herunter und kompiliert den Code.                                     |
| **XOR-Gateway**          | Eine Verzweigung im BPMN-Prozess — wie eine Weiche: Nur ein Weg wird genommen, je nach Bedingung.                           |
| **Decision Log**         | Die Protokolltabelle in der Datenbank, in der alle Entscheidungen festgehalten werden.                                      |

---

## Workers starten und Ausgaben lesen

### Workers starten

Die drei Worker werden gemeinsam mit einem einzigen Befehl gestartet.
Terminal öffnen im Ordner `swa_case_2_worker/swa_case_2_worker/` und ausführen:

```powershell
..\..\swa_case_2_group_2_drools_engine\mvnw.cmd compile exec:java "-Dexec.mainClass=ch.fhnw.students.WorkerMain"
```

Wenn die Worker erfolgreich gestartet sind, erscheinen folgende Zeilen:

```
INFORMATION: Drools-Worker registriert auf Topic: group2_droolsEngine
INFORMATION: Decision-Log-Worker registriert auf Topic: group2_logDecision
INFORMATION: Speditions-Worker registriert auf Topic: group2_requestAPI
INFORMATION: Alle Worker gestartet. Warte auf Tasks...
```

Ab diesem Moment warten die Worker auf Aufgaben vom Camunda-Server.

> **Wichtig:** Die Drools Engine (Schritt 1) muss bereits laufen, bevor die Workers gestartet werden.

### URLs anpassen (optional)

Standardmässig verwenden die Workers folgende Adressen:

| Variable      | Standardwert                                       | Bedeutung                    |
| ------------- | -------------------------------------------------- | ---------------------------- |
| `CAMUNDA_URL` | `http://group2:...@192.168.111.3:8080/engine-rest` | Camunda-Engine im Labor      |
| `DROOLS_URL`  | `http://localhost:8080`                            | Lokal laufende Drools Engine |
| `API_URL`     | `http://192.168.111.5:8080`                        | Speditions-API im Labor      |

Sollen andere Adressen verwendet werden, Umgebungsvariablen vor dem Start setzen:

```powershell
$env:DROOLS_URL = "http://localhost:9090"
$env:API_URL    = "http://192.168.111.5:8080"
```

### Ausgaben lesen

Jeder Worker gibt im Terminal Meldungen aus, die zeigen was er gerade tut:

**DroolsWorker — Drools Engine aufgerufen:**

```
INFORMATION: Drools-Worker: Task empfangen, id=abc123...
INFORMATION: Drools-Worker: AUTO → STANDARD_FREIGHT (Regel: Delivery CH any weight)
```

oder bei manuellem Review:

```
INFORMATION: Drools-Worker: MANUAL_REVIEW → JP / 231kg (Regel: Delivery JP > 200kg)
```

**DecisionLogWorker — Entscheidung protokolliert:**

```
INFORMATION: Log-Worker: Manuelle Entscheidung protokolliert — JP / AIR_FREIGHT
```

oder bei automatischer Entscheidung (kein Eintrag nötig):

```
INFORMATION: Log-Worker: AUTO-Entscheidung — bereits geloggt, überspringe.
```

**SpeditionApiWorker — Transportauftrag gesendet:**

```
INFORMATION: Speditions-Worker: Auftrag erfolgreich — Tracking: TRK-20240317-001
```

oder bei Ablehnung:

```
INFORMATION: Speditions-Worker: Fachliche Ablehnung — Zielland nicht bedienbar
```

**Fehlermeldungen (SCHWERWIEGEND):**

```
SCHWERWIEGEND: Drools-Worker: Technischer Fehler → ...
```

Bei technischen Fehlern (z.B. Netzwerkausfall) versucht der Worker es automatisch bis zu 3 Mal erneut. Erst danach erscheint ein Incident in Camunda Cockpit.

### Drools Engine mit MySQL starten (Produktion)

Für den Betrieb mit der echten MySQL-Datenbank im Labor:

```powershell
cd "...\swa_case_2_group_2_drools_engine"
$env:DB_URL      = "jdbc:mysql://192.168.111.4:3306/db_group2?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "group2"
$env:DB_PASSWORD = "..."
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Wenn die Verbindung klappt, erscheint:

```
HikariPool-1 - Added connection ... MySQL 8.4.x
```

Danach werden alle Entscheidungen dauerhaft in `db_group2.decision_log` gespeichert und können mit folgendem SQL abgefragt werden:

```sql
SELECT * FROM decision_log ORDER BY timestamp DESC;
```

---

## Technologie-Stack

| Komponente              | Technologie                  | Version |
| ----------------------- | ---------------------------- | ------- |
| Drools Engine           | Spring Boot                  | 4.0.3   |
| Regel-Engine            | Drools (Decision Table)      | 10.1.0  |
| Programmiersprache      | Java                         | 21      |
| Datenbank (Entwicklung) | H2 In-Memory                 | —       |
| Datenbank (Produktion)  | MySQL                        | —       |
| Workers                 | Camunda External Task Client | 1.3.1   |
| Build-Tool              | Maven (Wrapper)              | —       |
| Prozess-Engine          | Camunda 7                    | —       |
