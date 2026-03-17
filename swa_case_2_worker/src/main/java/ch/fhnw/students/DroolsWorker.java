package ch.fhnw.students;

import org.camunda.bpm.client.ExternalTaskClient;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * External Task Worker für Topic: group2_droolsEngine
 *
 * Ruft den Drools-Service auf, um die Versandart automatisch zu bestimmen.
 *
 * BPMN-Konvention (XOR-Gateway "DecisionStatus prüfen"):
 * - HTTP 202 → complete(decisionStatus='AUTO') → Normalweg
 * - HTTP 206 → complete(decisionStatus='MANUAL_REVIEW') → User Task
 * - HTTP 400 → complete(decisionStatus='INVALID_INPUT') → User Task
 * - HTTP 5xx / Exception → handleFailure() mit Retries → Incident
 *
 * Wichtig: MANUAL_REVIEW ≠ INVALID_INPUT
 * - MANUAL_REVIEW: Drools wurde aufgerufen, Regel schreibt menschliche Prüfung
 * vor (z.B. RU, JP>200kg, unbekanntes Land).
 * - INVALID_INPUT: Validierung fehlgeschlagen, Drools wurde NIE aufgerufen
 * (z.B. weight ≤ 0, destination fehlt).
 *
 * Kein handleBpmnError() — alle fachlichen Statuscodes werden als complete()
 * an das XOR-Gateway weitergegeben. Nur technische Fehler → handleFailure().
 */
public class DroolsWorker {

    private static final Logger LOG = Logger.getLogger(DroolsWorker.class.getName());

    private static final String TOPIC = "group2_droolsEngine";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_TIMEOUT_MS = 15000L;

    public static void register(ExternalTaskClient client) {

        client.subscribe(TOPIC).lockDuration(30000).handler((externalTask, externalTaskService) -> {

            LOG.info("Drools-Worker: Task empfangen, id=" + externalTask.getId());

            try {
                // 1. Prozessvariablen lesen
                // deliveryCountry (User Task "Angaben zum Kunden") mit Fallback auf
                // destination (User Task "Spedition manuell auswählen")
                String country = (String) externalTask.getVariable("deliveryCountry");
                if (country == null || country.isBlank()) {
                    country = (String) externalTask.getVariable("destination");
                }
                // weight: Camunda-Form kann Long oder Integer liefern → Number-Cast
                Number weightNum = (Number) externalTask.getVariable("weight");
                String processInstanceId = externalTask.getProcessInstanceId();

                // 2. Inputvalidierung — bei fehlenden Daten direkt INVALID_INPUT
                // Drools wird nicht aufgerufen. Das ist kein fachliches Regelresultat,
                // sondern ein Validierungsfehler.
                // (kein bpmnError — XOR-Gateway routet zum User Task)
                if (country == null || country.isBlank()) {
                    LOG.info("Drools-Worker: Zielland fehlt — INVALID_INPUT");
                    completeWithStatus(externalTaskService, externalTask,
                            "INVALID_INPUT", "MANUAL_REVIEW", true);
                    return;
                }
                if (weightNum == null || weightNum.longValue() <= 0) {
                    LOG.info("Drools-Worker: Gewicht ungültig — INVALID_INPUT");
                    completeWithStatus(externalTaskService, externalTask,
                            "INVALID_INPUT", "MANUAL_REVIEW", true);
                    return;
                }

                long weight = weightNum.longValue();

                // 3. Drools-Service aufrufen
                JSONObject requestBody = new JSONObject();
                requestBody.put("weight", weight);
                requestBody.put("destination", country);

                String url = WorkerMain.DROOLS_URL + "/deliveryRuleManager";

                // processInstanceId via Header (Controller erwartet Header)
                Map<String, String> headers = new HashMap<>();
                if (processInstanceId != null && !processInstanceId.isBlank()) {
                    headers.put("X-Process-Instance-Id", processInstanceId);
                }

                JSONObject response = HttpHelper.post(url, requestBody, headers);
                int statusCode = response.getInt("statusCode");

                // 4. Ergebnis auswerten — ALLE fachlichen Statuscodes → complete()
                if (statusCode == 202) {
                    // AUTO — Drools hat eindeutig entschieden
                    HashMap<String, Object> variables = new HashMap<>();
                    variables.put("deliveryType", response.getString("deliveryType"));
                    variables.put("ruleName", response.optString("ruleName", "UNKNOWN"));
                    variables.put("decisionStatus", "AUTO");
                    variables.put("isManualDecision", false);

                    LOG.info("Drools-Worker: AUTO — "
                            + response.getString("deliveryType")
                            + " (Regel: " + response.optString("ruleName") + ")");

                    externalTaskService.complete(externalTask, variables);

                } else if (statusCode == 206) {
                    // MANUAL_REVIEW — Drools-Regel schreibt menschliche Prüfung vor
                    String ruleName = response.optString("ruleName", "UNKNOWN");
                    HashMap<String, Object> variables = new HashMap<>();
                    variables.put("deliveryType", response.optString("deliveryType", "MANUAL_REVIEW"));
                    variables.put("ruleName", ruleName);
                    variables.put("decisionStatus", "MANUAL_REVIEW");
                    variables.put("isManualDecision", true);
                    variables.put("manualDecisionReason", "Drools-Regel: " + ruleName);

                    LOG.info("Drools-Worker: MANUAL_REVIEW — "
                            + country + " / " + weight + "kg"
                            + " (Regel: " + ruleName + ")");

                    externalTaskService.complete(externalTask, variables);

                } else if (statusCode == 400) {
                    // INVALID_INPUT — Validierungsfehler, Drools nicht aufgerufen
                    LOG.info("Drools-Worker: INVALID_INPUT — "
                            + response.optString("reason", "ungültige Daten"));
                    completeWithStatus(externalTaskService, externalTask,
                            "INVALID_INPUT", "MANUAL_REVIEW", true);

                } else {
                    // 5xx oder unbekannt → technischer Fehler → Retry
                    LOG.warning("Drools-Worker: Technischer Fehler, HTTP " + statusCode);
                    externalTaskService.handleFailure(externalTask,
                            "Drools-Service: HTTP " + statusCode,
                            null, MAX_RETRIES, RETRY_TIMEOUT_MS);
                }

            } catch (Exception e) {
                // Technischer Fehler — Retry mit Backoff
                LOG.severe("Drools-Worker: Technischer Fehler — " + e.getMessage());
                externalTaskService.handleFailure(externalTask,
                        "Drools-Service nicht erreichbar: " + e.getMessage(),
                        e.toString(), MAX_RETRIES, RETRY_TIMEOUT_MS);
            }

        }).open();

        LOG.info("Drools-Worker registriert auf Topic: " + TOPIC);
    }

    /**
     * Hilfsmethode: Schliesst den Task mit fachlichem Status ab.
     * Wird für MANUAL_REVIEW und INVALID_INPUT verwendet —
     * beide routen via XOR-Gateway zum User Task.
     */
    private static void completeWithStatus(
            org.camunda.bpm.client.task.ExternalTaskService service,
            org.camunda.bpm.client.task.ExternalTask task,
            String decisionStatus, String deliveryType, boolean isManual) {
        HashMap<String, Object> variables = new HashMap<>();
        variables.put("decisionStatus", decisionStatus);
        variables.put("deliveryType", deliveryType);
        variables.put("isManualDecision", isManual);
        variables.put("manualDecisionReason", "Ungültige Eingabedaten – manuelle Klärung erforderlich");
        service.complete(task, variables);
    }
}
