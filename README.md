# Drools Engine — Versandbeauftragung (Case 2, Group 2)

Spring-Boot-Service, der die automatische Speditionswahl via Drools Decision Table (Excel) implementiert.
Protokolliert alle Entscheidungen in einer Datenbank und stellt KPI-Statistiken bereit.

> Für eine allgemeine, nicht-technische Erklärung des Gesamtsystems: siehe [ANLEITUNG.md](ANLEITUNG.md).
> Die Camunda Workers befinden sich im Unterverzeichnis [`swa_case_2_worker/`](swa_case_2_worker/).

---

## Architektur

```
Camunda 7 (BPMN-Prozess)
  │
  ├── DroolsWorker ──────► POST /deliveryRuleManager   ◄── dieser Service
  │                         → Regeln auswerten, Entscheidung loggen
  │
  ├── DecisionLogWorker ──► POST /decisions/manual
  │                         → Manuelle Entscheidung protokollieren
  │
  └── SpeditionApiWorker    (externer Speditions-Service, nicht Teil dieses Repos)
          │
          ▼
    MySQL decision_log
    (Drools- + Human-Einträge, verknüpft via processInstanceId)
```

### Aufgabenteilung

| Baustein                               | Verantwortung                                                          |
| -------------------------------------- | ---------------------------------------------------------------------- |
| **Drools Excel** (`Logistics.drl.xls`) | Geschäftsregeln: Welche Versandart gilt für Land + Gewicht? Kein Code. |
| **LogisticsService**                   | Drools initialisieren, KieSession pro Request, Regelnamen erfassen     |
| **LogisticsController**                | REST-Adapter: Drools-Ergebnis → HTTP 202/206/400, Entscheidung loggen  |
| **DecisionLogService**                 | Protokollierung in der Datenbank (DROOLS- und HUMAN-Einträge getrennt) |
| **DecisionLogController**              | REST-Endpunkte: KPI-Statistiken + manuelle Entscheidung speichern      |

## Projektstruktur

```
├── src/main/java/com/example/
│   ├── DroolsTestApplication.java          ← Spring Boot Entry Point
│   └── droolsengine/
│       ├── LogisticsController.java        ← REST: POST /deliveryRuleManager
│       ├── LogisticsService.java           ← Drools KieSession Management
│       ├── Logistics.java                  ← Request/Response-Objekt für Drools
│       ├── DecisionLogController.java      ← REST: GET /decisions/stats, POST /decisions/manual
│       ├── DecisionLogService.java         ← Entscheidungen in DB schreiben
│       ├── DecisionLog.java                ← JPA Entity (decision_log Tabelle)
│       ├── DecisionLogRepository.java      ← JPA Repository
│       ├── DecisionStatus.java             ← Enum: AUTO, MANUAL_REVIEW, INVALID_INPUT, FINAL
│       ├── DecisionSource.java             ← Enum: DROOLS, HUMAN
│       ├── DecisionStats.java              ← Record für KPI-Response
│       ├── DeliveryType.java               ← Enum: STANDARD_MAIL, AIR_FREIGHT, ...
│       ├── DeliveryCountry.java            ← Enum: AR, JP, RU, CH, DE, NOT_DEFINED
│       ├── ManualDecisionRequest.java      ← DTO für POST /decisions/manual
│       └── RuleNameListener.java           ← Drools AgendaEventListener
├── src/main/resources/
│   ├── application.properties              ← Grundkonfiguration (Profil, Regelversion)
│   ├── application-h2.properties           ← H2 In-Memory für Entwicklung
│   ├── application-mysql.properties        ← MySQL für Produktion
│   └── rules/
│       ├── Logistics.drl.xls               ← Excel-Regeltabelle (die Quelle)
│       └── logistic_rules.drl              ← Referenzdokument
├── src/test/java/com/example/droolstest/
│   ├── DroolsTestApplicationTests.java     ← 11 Unit-Tests für Regeln
│   └── LogisticsControllerTests.java       ← 13 Integrationstests für REST-API
├── swa_case_2_worker/                      ← Camunda External Task Workers
├── ANLEITUNG.md                            ← Nicht-technische Erklärung
└── pom.xml                                 ← Maven Build-Konfiguration
```

---

## HTTP-Konvention für den Camunda-Worker

Der `DroolsWorker` (Topic `group2_droolsEngine`) muss diese Statuscodes verarbeiten:

| HTTP  | Bedeutung                                                                                        | Worker-Aktion                                                                                   |
| ----- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------- |
| `202` | AUTO — Drools hat eindeutige Versandart bestimmt                                                       | `complete(deliveryType, decisionStatus='AUTO', isManualDecision=false, deliveryCountry=<normalisiert>)` |
| `206` | MANUAL_REVIEW — Regel existiert, schreibt menschliche Prüfung vor                                     | `complete(deliveryType='MANUAL_REVIEW', decisionStatus='MANUAL_REVIEW', isManualDecision=true, deliveryCountry=<user-input>)` |
| `400` | INVALID_INPUT — Ungültige Eingabe (weight ≤ 0, deliveryCountry fehlt). Drools wird NICHT aufgerufen.  | `complete(decisionStatus='INVALID_INPUT', isManualDecision=true)`                                      |
| `500` | Technischer Fehler (Drools-Engine nicht erreichbar etc.)                                         | `handleFailure(errorMessage, retries=3, retryTimeout=15000)`                                    |

**Kein `handleBpmnError()` für fachliche Statuscodes.**
Alle drei Werte (AUTO, MANUAL_REVIEW, INVALID_INPUT) sind normale `complete()`-Abschlüsse.
Nur technische Fehler (HTTP 5xx, Netzwerk-Timeout) lösen `handleFailure()` mit Retries aus.

**Semantische Abgrenzung:**

| Status          | Drools aufgerufen? | Bedeutung                                                                                                                          |
| --------------- | ------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `AUTO`          | Ja                 | Drools hat eine eindeutige Versandart bestimmt                                                                                     |
| `MANUAL_REVIEW` | Ja                 | Eine Drools-Regel schreibt explizit menschliche Prüfung vor (z.B. RU Sanktionen, JP > 200 kg, unbekanntes Land via Fallback-Regel) |
| `INVALID_INPUT` | **Nein**           | Eingabevalidierung fehlgeschlagen (weight ≤ 0, destination fehlt). Die Request erreicht Drools gar nicht.                          |

> **Wichtig:** Ein echter «No Rule Match» kann in der aktuellen Regelkonfiguration nicht auftreten,
> weil die Fallback-Regel (salience −1000) immer MANUAL_REVIEW setzt.

Das XOR-Gateway im BPMN routet anhand der Prozessvariable `decisionStatus`:

- `${decisionStatus == 'AUTO'}` → direkt zu „Daten validieren"
- `${decisionStatus == 'MANUAL_REVIEW' || decisionStatus == 'INVALID_INPUT'}` → User Task „Spedition manuell auswählen"

---

## Versandregeln (Logistics.drl.xls)

| Land                      | Gewicht              | Versandart       |
| ------------------------- | -------------------- | ---------------- |
| AR                        | 0–60 kg              | STANDARD_MAIL    |
| AR                        | 60–500 kg            | SPECIAL_FREIGHT  |
| AR                        | > 500 kg             | MANUAL_REVIEW    |
| JP                        | 0–200 kg             | AIR_FREIGHT      |
| JP                        | > 200 kg             | MANUAL_REVIEW    |
| RU                        | beliebig             | MANUAL_REVIEW    |
| CH                        | beliebig (> 0)       | STANDARD_FREIGHT |
| DE                        | beliebig (> 0)       | STANDARD_FREIGHT |
| NOT_DEFINED               | —                    | MANUAL_REVIEW    |
| Fallback (salience -1000) | deliveryType == null | MANUAL_REVIEW    |

Regeln werden ausschliesslich in `src/main/resources/rules/Logistics.drl.xls` gepflegt.
`logistic_rules.drl` ist nur ein Referenzdokument.

---

## Decision Log Schema

Tabelle `decision_log` — bei manuellen Entscheidungen zwei Einträge pro Prozessinstanz, verknüpft via `process_instance_id`:

| Spalte                | Typ          | Bedeutung                                                     |
| --------------------- | ------------ | ------------------------------------------------------------- |
| `id`                  | BIGINT PK    | Auto-Increment                                                |
| `delivery_country`    | VARCHAR(10)  | ISO 3166 A-2, z.B. "AR"                                       |
| `weight`              | DOUBLE       | Gewicht in kg                                                 |
| `delivery_type`       | VARCHAR(30)  | z.B. "STANDARD_MAIL"                                          |
| `decision_source`     | ENUM         | `DROOLS` oder `HUMAN`                                         |
| `decision_status`     | ENUM         | `AUTO`, `MANUAL_REVIEW`, `INVALID_INPUT`, `FINAL`             |
| `rule_name`           | VARCHAR(100) | Name der gefeuerten Drools-Regel (null bei HUMAN)             |
| `rule_version`        | VARCHAR(20)  | Aus `app.rule-version` (null bei HUMAN)                       |
| `manual_reason`       | VARCHAR(500) | Begründung des Mitarbeiters (null bei DROOLS)                 |
| `selected_carrier`    | VARCHAR(100) | Gewählte Spedition nach manuellem Entscheid (null bei DROOLS) |
| `process_instance_id` | VARCHAR(100) | Camunda Prozessinstanz-ID                                     |
| `timestamp`           | DATETIME     | Automatisch gesetzt via `@PrePersist`                         |

**Dual-Entry-Design (MANUAL_REVIEW):**

Bei manuellen Entscheidungen entstehen zwei Einträge für dieselbe `process_instance_id`:

| Eintrag                   | `decision_source` | `decision_status` | Inhalt                                       |
| ------------------------- | ----------------- | ----------------- | -------------------------------------------- |
| Sofort beim Drools-Aufruf | `DROOLS`          | `MANUAL_REVIEW`   | Welche Regel hat gefeuert, warum war unklar? |
| Nach dem User Task        | `HUMAN`           | `FINAL`           | Was hat der Mensch gewählt + Begründung      |

**ML-Trainingsdaten-Logik:**

Erst das Paar aus Systemsicht und Menschensicht macht die Daten aussagekräftig:

- Features: `delivery_country` + `weight` (aus dem DROOLS-Eintrag)
- Label: `delivery_type` des zugehörigen `FINAL`-Eintrags
- Override-Signal: Welche Regeln werden regelmässig übersteuert → Kandidat für Regelanpassung

---

## REST-Endpunkte

| Methode | Pfad                   | Beschreibung                                            |
| ------- | ---------------------- | ------------------------------------------------------- |
| `GET`   | `/ping`                | Health-Check → `{"ping": true}`                         |
| `POST`  | `/deliveryRuleManager` | Drools-Auswertung → 202/206/400/500                     |
| `GET`   | `/decisions/stats`     | KPI: Override-Rate und Entscheidungsverteilung          |
| `POST`  | `/decisions/manual`    | Manuelle Entscheidung vom Camunda-Worker protokollieren |

### POST /deliveryRuleManager

**Request-Header (optional):**

```
X-Process-Instance-Id: <Camunda-Prozessinstanz-ID>
```

**Request-Body:**

```json
{
  "weight": 55.0,
  "deliveryCountry": "AR"
}
```

**Response 202 (AUTO):**

```json
{
  "weight": 55.0,
  "deliveryCountry": "AR",
  "deliveryType": "STANDARD_MAIL",
  "ruleName": "Delivery AR 0kg < x <= 60kg"
}
```

**Response 206 (MANUAL_REVIEW):**

```json
{
  "weight": 100.0,
  "deliveryCountry": "RU",
  "deliveryType": "MANUAL_REVIEW",
  "ruleName": "Delivery RU any weight"
}
```

### POST /decisions/manual

Aufgerufen vom Camunda `group2_logDecision`-Worker nach manueller Entscheidung.

```json
{
  "deliveryCountry": "RU",
  "weight": 100.0,
  "deliveryType": "SPECIAL_FREIGHT",
  "manualReason": "Sondergenehmigung erteilt \u2014 Ausnahmeregelung 2026-03",
  "processInstanceId": "abc-123-xyz"
}
```

Response: `201 Created`

### GET /decisions/stats

```json
{
  "totalDecisions": 47,
  "automaticDecisions": 41,
  "manualReviewDecisions": 5,
  "finalHumanDecisions": 5,
  "overrideRate": 0.106
}
```

---

## Installation und Start

### Voraussetzungen

- Java 21 ([Download](https://adoptium.net/))
- Maven Wrapper ist im Repo enthalten — kein separates Maven nötig

### Standard (MySQL — aktives Profil)

Umgebungsvariablen setzen (Credentials nie im Code oder Repository):

```powershell
$env:DB_URL      = "jdbc:mysql://192.168.111.4:3306/db_group2?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "group2"
$env:DB_PASSWORD = "<Passwort aus sicherem Store>"
.\mvnw.cmd spring-boot:run
```

Standardprofil ist `mysql` (konfiguriert in `application.properties`).
Tabelle `decision_log` wird automatisch via `ddl-auto=update` angelegt.
Nach dem Start erreichbar unter `http://localhost:8080`.

Health-Check:

```
GET http://localhost:8080/ping → {"ping": true}
```

### Lokale Entwicklung (H2 In-Memory, optional)

Nur für lokale Tests ohne MySQL-Zugang:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring.profiles.active=h2"
```

H2-Konsole: `http://localhost:8080/h2-console` — Daten verschwinden beim Neustart.

### Tests ausführen

```powershell
.\mvnw.cmd test "-Dspring.profiles.active=h2"
```

24 Tests (11 Regel-Unit-Tests + 13 REST-Integrationstests). Alle Tests nutzen eine H2-In-Memory-Datenbank.

---

## Camunda-Topics

| Topic                 | Worker             | Beschreibung                                                        |
| --------------------- | ------------------ | ------------------------------------------------------------------- |
| `group2_droolsEngine` | DroolsWorker       | Ruft `POST /deliveryRuleManager` auf, schreibt Prozessvariablen     |
| `group2_logDecision`  | DecisionLogWorker  | Ruft `POST /decisions/manual` auf (nur bei `isManualDecision=true`) |
| `group2_requestAPI`   | SpeditionApiWorker | Ruft externe Speditions-API auf                                     |

**Camunda-Zugangsdaten** werden nicht im Repository gespeichert.
