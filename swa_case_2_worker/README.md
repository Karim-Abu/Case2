# Camunda External Task Workers — Case 2, Group 2

Drei External Task Workers für den Versandbeauftragungsprozess der Accolaia AG.
Die Workers holen Aufgaben per Long Polling vom Camunda-Server ab, verarbeiten sie
und melden das Ergebnis zurück.

> Für eine allgemeine Erklärung des Gesamtsystems: siehe [ANLEITUNG.md](../ANLEITUNG.md).
> Die Drools Engine (REST-Service) befindet sich im [übergeordneten Verzeichnis](../).

---

## Die drei Workers

| Worker | Camunda-Topic | Aufgabe |
|--------|---------------|---------|
| **DroolsWorker** | `group2_droolsEngine` | Sendet Zielland + Gewicht an die Drools Engine (`POST /deliveryRuleManager`). Wertet den HTTP-Statuscode aus und schreibt `decisionStatus`, `deliveryType` und `isManualDecision` als Prozessvariablen zurück. |
| **DecisionLogWorker** | `group2_logDecision` | Wird nur aktiv, wenn `isManualDecision=true`. Protokolliert die manuelle Entscheidung des Mitarbeiters in der Datenbank (`POST /decisions/manual`). |
| **SpeditionApiWorker** | `group2_requestAPI` | Sendet den fertigen Transportauftrag an die externe Speditions-API. Evaluiert die Antwort (Trackingnummer, Abholdatum) oder löst bei Ablehnung einen BPMN-Error aus. |

---

## Architektur

```
Camunda Engine (192.168.111.3:8080)
    │
    │  Long Polling (asyncResponseTimeout: 30s)
    ▼
WorkerMain.java  ← Startpunkt, registriert alle 3 Worker
    │
    ├── DroolsWorker         ──► Drools Service (localhost:8081)
    │                              POST /deliveryRuleManager
    │
    ├── DecisionLogWorker    ──► Drools Service (localhost:8081)
    │                              POST /decisions/manual
    │
    └── SpeditionApiWorker   ──► Speditions-API (192.168.111.5:8080)
                                   POST /v1/consignment/request
```

---

## Projektstruktur

```
swa_case_2_worker/
├── src/main/java/ch/fhnw/students/
│   ├── WorkerMain.java           ← Entry Point: registriert alle Worker
│   ├── DroolsWorker.java         ← Topic group2_droolsEngine
│   ├── DecisionLogWorker.java    ← Topic group2_logDecision
│   ├── SpeditionApiWorker.java   ← Topic group2_requestAPI
│   └── HttpHelper.java           ← POST-Request-Hilfsklasse (JSON, Timeout 30s)
├── pom.xml                       ← Maven-Konfiguration (Java 21, Camunda Client 1.3.1)
└── README.md                     ← Diese Datei
```

---

## Fehlerbehandlung

| Situation | Behandlung | Auswirkung im Prozess |
|-----------|------------|----------------------|
| **AUTO** (HTTP 202) | `complete()` mit `decisionStatus=AUTO` | Prozess fährt automatisch weiter |
| **MANUAL_REVIEW** (HTTP 206) | `complete()` mit `decisionStatus=MANUAL_REVIEW` | XOR-Gateway → User Task |
| **INVALID_INPUT** (HTTP 400) | `complete()` mit `decisionStatus=INVALID_INPUT` | XOR-Gateway → User Task |
| **Technischer Fehler** (HTTP 5xx, Timeout) | `handleFailure()` mit 3 Retries (je 15s) | Nach 3 Fehlversuchen: Camunda Incident |
| **Transport abgelehnt** (SpeditionApiWorker) | `handleBpmnError("TRANSPORT_REJECTED")` | Boundary Error Event → User Task |
| **Logging-Fehler** (DecisionLogWorker) | Task wird trotzdem abgeschlossen | Kein Prozessstopp |

Fachliche Statuscodes (AUTO, MANUAL_REVIEW, INVALID_INPUT) lösen **kein** `handleBpmnError()` aus.
Nur der SpeditionApiWorker nutzt `handleBpmnError()` bei Transportablehnung.

---

## Build und Start

### Voraussetzungen

- Java 21
- Netzwerkzugang zu:
  - Camunda Engine (`192.168.111.3:8080`)
  - Drools Service (`localhost:8081`)
  - Speditions-API (`192.168.111.5:8080`)

### Starten

```powershell
.\mvnw.cmd compile exec:java
```

Ausgabe bei erfolgreichem Start:
```
=== Camunda External Task Workers (Case 2, Group 2) ===
Worker registriert: group2_droolsEngine
Worker registriert: group2_logDecision
Worker registriert: group2_requestAPI
```

### Lokale Entwicklung (ohne Camunda-Zugang)

Für lokale Tests nur der Drools Engine: Den Drools Service im Hauptverzeichnis starten
und per `curl` oder Postman direkt `POST /deliveryRuleManager` aufrufen.
Die Workers selbst benötigen einen laufenden Camunda-Server.
