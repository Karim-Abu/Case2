# Logistics Drools Engine — Problemfall 2

Spring-Boot-Service, der die automatische Speditionswahl via Drools Decision Table implementiert,
alle Entscheidungen in MySQL protokolliert und als Datenbasis für ein späteres AI-System dient.

---

## Architektur

```
┌─────────────────────────────────────────────────────────────────┐
│                        Accolaia AG                              │
│                                                                 │
│   Camunda 7 (BPMN-Prozess)                                      │
│     │                                                           │
│     ├─── External Task Worker (group2_droolsEngine) ───────────►│
│     │         ruft POST /deliveryRuleManager auf                │◄── dieses Repo
│     │         liest HTTP-Status + Response-Body zurück          │
│     │                                                           │
│     ├─── External Task Worker (group2_logDecision) ────────────►│
│     │         ruft POST /decisions/manual auf (nur bei          │
│     │         isManualDecision=true)                            │
│     │                                                           │
│     └─── External Task Worker (group2_requestAPI)               │
│               ruft externe Speditions-API auf                   │
└─────────────────────────────────────────────────────────────────┘
              │
              ▼
        MySQL decision_log
        (Drools + Human — getrennte Einträge, verknüpft via processInstanceId)
```

### Separation of Concerns

| Baustein                | Verantwortung                                                         |
| ----------------------- | --------------------------------------------------------------------- |
| **Drools (Excel)**      | Welche Versandart gilt für Land + Gewicht? Geschäftslogik, kein Code. |
| **LogisticsService**    | Drools initialisieren, KieSession pro Request, RuleNameListener       |
| **LogisticsController** | HTTP-Adapter: Drools-Ergebnis → HTTP-Statuscode, Entscheidung loggen  |
| **DecisionLogService**  | Protokollierung in MySQL. DROOLS- und HUMAN-Einträge getrennt.        |
| **Camunda Worker**      | Prozesssteuerung. Kennt weder Drools noch SQL direkt.                 |
| **MySQL decision_log**  | Audit-Trail + Trainingsdaten für zukünftiges ML-Modell                |

---

## HTTP-Konvention für den Camunda-Worker

Der `DroolsWorker` (Topic `group2_droolsEngine`) muss diese Statuscodes verarbeiten:

| HTTP  | Bedeutung                                                         | Worker-Aktion                                                                                   |
| ----- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `202` | AUTO — Drools hat eindeutige Versandart bestimmt                  | `complete(deliveryType, decisionStatus='AUTO', isManualDecision=false)`                         |
| `206` | MANUAL_REVIEW — Regel existiert, schreibt menschliche Prüfung vor | `complete(deliveryType='MANUAL_REVIEW', decisionStatus='MANUAL_REVIEW', isManualDecision=true)` |
| `400` | INVALID_INPUT — Ungültige Eingabe (weight ≤ 0, destination fehlt). Drools wird NICHT aufgerufen. | `complete(decisionStatus='INVALID_INPUT', isManualDecision=true)`                               |
| `500` | Technischer Fehler (Drools-Engine nicht erreichbar etc.)          | `handleFailure(errorMessage, retries=3, retryTimeout=15000)`                                    |

**Kein `handleBpmnError()` für fachliche Statuscodes.**
Alle drei Werte (AUTO, MANUAL_REVIEW, INVALID_INPUT) sind normale `complete()`-Abschlüsse.
Nur technische Fehler (HTTP 5xx, Netzwerk-Timeout) lösen `handleFailure()` mit Retries aus.

**Semantische Abgrenzung:**

| Status | Drools aufgerufen? | Bedeutung |
|--------|-------------------|-----------|
| `AUTO` | Ja | Drools hat eine eindeutige Versandart bestimmt |
| `MANUAL_REVIEW` | Ja | Eine Drools-Regel schreibt explizit menschliche Prüfung vor (z.B. RU Sanktionen, JP > 200 kg, unbekanntes Land via Fallback-Regel) |
| `INVALID_INPUT` | **Nein** | Eingabevalidierung fehlgeschlagen (weight ≤ 0, destination fehlt). Die Request erreicht Drools gar nicht. |

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

Tabelle `decision_log` — zwei Einträge pro manuellem Eingriff, verknüpft via `process_instance_id`:

| Spalte                | Typ          | Bedeutung                                         |
| --------------------- | ------------ | ------------------------------------------------- |
| `id`                  | BIGINT PK    | Auto-Increment                                    |
| `delivery_country`    | VARCHAR(10)  | ISO 3166 A-2, z.B. "AR"                           |
| `weight`              | DOUBLE       | Gewicht in kg                                     |
| `delivery_type`       | VARCHAR(30)  | z.B. "STANDARD_MAIL"                              |
| `decision_source`     | ENUM         | `DROOLS` oder `HUMAN`                             |
| `decision_status`     | ENUM         | `AUTO`, `MANUAL_REVIEW`, `INVALID_INPUT`, `FINAL` |
| `rule_name`           | VARCHAR(100) | Name der gefeuerten Drools-Regel (null bei HUMAN) |
| `rule_version`        | VARCHAR(20)  | Aus `app.rule-version` (null bei HUMAN)           |
| `manual_reason`       | VARCHAR(500) | Begründung manueller Entscheid (null bei DROOLS)  |
| `process_instance_id` | VARCHAR(100) | Camunda Prozessinstanz-ID                         |
| `timestamp`           | DATETIME     | Automatisch gesetzt via @PrePersist               |

**ML-Trainingsdaten-Logik:**

- Features: `delivery_country` + `weight`
- Label: `delivery_type` des `FINAL`-Eintrags
- Override-Signal: `decision_status = FINAL` mit passendem `MANUAL_REVIEW`-Eintrag

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
  "destination": "AR"
}
```

**Response 202 (AUTO):**

```json
{
  "weight": 55.0,
  "destination": "AR",
  "deliveryType": "STANDARD_MAIL",
  "ruleName": "Delivery AR 0kg < x <= 60kg"
}
```

**Response 206 (MANUAL_REVIEW):**

```json
{
  "weight": 100.0,
  "destination": "RU",
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

- Java 21
- Maven (Wrapper im Repo enthalten)

### Entwicklung (H2 In-Memory)

```bash
./mvnw.cmd spring-boot:run
# Profil h2 ist Standard (application.properties)
# H2-Console: http://localhost:8080/h2-console
```

### Produktion (MySQL)

Umgebungsvariablen setzen (nie Credentials im Code oder Repository):

```powershell
$env:DB_URL      = "jdbc:mysql://192.168.111.4:3306/db_group2?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "group2"
$env:DB_PASSWORD = "<Passwort aus sicherem Store>"
./mvnw.cmd spring-boot:run -Dspring.profiles.active=mysql
```

Tabelle `decision_log` wird automatisch via `ddl-auto=update` angelegt.

### Tests

```bash
./mvnw.cmd test
```

---

## Regelversion hochzählen

Bei Änderungen an der Excel-Entscheidungstabelle `Logistics.drl.xls`:

1. Excel anpassen
2. `app.rule-version` in `application.properties` hochzählen (z.B. `1.1`)
3. `logistic_rules.drl` synchron halten (Referenzdokument)
4. Tests ausführen

Die Regelversion wird in jedem `decision_log`-Eintrag gespeichert — so ist nachvollziehbar,
welche Regelversion eine Entscheidung getroffen hat.

---

## Camunda-Topics

| Topic                 | Worker             | Beschreibung                                                 |
| --------------------- | ------------------ | ------------------------------------------------------------ |
| `group2_droolsEngine` | DroolsWorker       | Ruft `/deliveryRuleManager` auf, schreibt Prozessvariablen   |
| `group2_logDecision`  | DecisionLogWorker  | Ruft `/decisions/manual` auf (nur bei isManualDecision=true) |
| `group2_requestAPI`   | SpeditionApiWorker | Ruft externe Speditions-API auf                              |

**Camunda-Zugangsdaten:** Nicht im Repository. Via Umgebungsvariable `CAMUNDA_USERNAME`/`CAMUNDA_PASSWORD`.
