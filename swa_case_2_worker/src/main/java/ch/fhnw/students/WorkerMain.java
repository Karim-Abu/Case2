package ch.fhnw.students;

import org.camunda.bpm.client.ExternalTaskClient;

import java.util.logging.Logger;

/**
 * Einstiegspunkt für alle Camunda External Task Workers (Case 2).
 *
 * Registriert drei Worker auf einem einzigen Client:
 *   1. group2_droolsEngine  — Ruft Drools-Service auf, bestimmt Versandart
 *   2. group2_logDecision   — Protokolliert Entscheidungen in MySQL
 *   3. group2_requestAPI    — Ruft Speditions-API auf, verarbeitet Antwort
 *
 * Konfiguration:
 *   - CAMUNDA_URL: URL der Camunda Engine (Standard: http://group2:A0fBMV7M7qGbPh@192.168.111.3:8080/engine-rest)
 *   - DROOLS_URL:  URL des Drools-Service (Standard: http://localhost:8081)
 */
public class WorkerMain {

    private static final Logger LOG = Logger.getLogger(WorkerMain.class.getName());

    private static final String CAMUNDA_BASE_URL =
            "http://group2:A0fBMV7M7qGbPh@192.168.111.3:8080/engine-rest";

    public static void main(String[] args) {
        LOG.info("=== Camunda External Task Workers (Case 2, Group 2) ===");
        LOG.info("Camunda Engine: " + CAMUNDA_BASE_URL);

        ExternalTaskClient client = ExternalTaskClient
                .create()
                .baseUrl(CAMUNDA_BASE_URL)
                .asyncResponseTimeout(30000)
                .build();

        // Worker registrieren
        DroolsWorker.register(client);
        DecisionLogWorker.register(client);
        SpeditionApiWorker.register(client);

        LOG.info("Alle Worker gestartet. Warte auf Tasks...");
    }
}
