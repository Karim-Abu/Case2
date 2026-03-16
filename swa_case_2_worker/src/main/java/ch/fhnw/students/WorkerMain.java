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
 * Konfiguration über Umgebungsvariablen (mit Fallback-Defaults):
 *   - CAMUNDA_URL: URL der Camunda Engine
 *   - DROOLS_URL:  Basis-URL des Drools-Service (ohne Pfad)
 *   - API_URL:     Basis-URL der Speditions-API (ohne Pfad)
 */
public class WorkerMain {

    private static final Logger LOG = Logger.getLogger(WorkerMain.class.getName());

    /** Camunda Engine REST-URL. Konfigurierbar via Umgebungsvariable CAMUNDA_URL. */
    static final String CAMUNDA_URL = env("CAMUNDA_URL",
            "http://group2:A0fBMV7M7qGbPh@192.168.111.3:8080/engine-rest");

    /** Basis-URL des Drools-Service. Konfigurierbar via Umgebungsvariable DROOLS_URL. */
    static final String DROOLS_URL = env("DROOLS_URL", "http://localhost:8080");

    /** Basis-URL der Speditions-API. Konfigurierbar via Umgebungsvariable API_URL. */
    static final String API_URL = env("API_URL", "http://192.168.111.5:8080");

    public static void main(String[] args) {
        LOG.info("=== Camunda External Task Workers (Case 2, Group 2) ===");
        LOG.info("Camunda Engine: " + CAMUNDA_URL);
        LOG.info("Drools-Service: " + DROOLS_URL);
        LOG.info("Speditions-API: " + API_URL);

        ExternalTaskClient client = ExternalTaskClient
                .create()
                .baseUrl(CAMUNDA_URL)
                .asyncResponseTimeout(30000)
                .build();

        // Worker registrieren
        DroolsWorker.register(client);
        DecisionLogWorker.register(client);
        SpeditionApiWorker.register(client);

        LOG.info("Alle Worker gestartet. Warte auf Tasks...");
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
