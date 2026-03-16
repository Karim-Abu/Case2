package ch.fhnw.students;

import org.camunda.bpm.client.ExternalTaskClient;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * External Task Worker für Topic: group2_requestAPI
 *
 * Verbesserter Speditions-Worker (gegenüber Case 1):
 *
 * 1. Abstraktionsschicht: HTTP-Statuscodes werden auf fachliche Meldungen
 * gemappt.
 * Rohe Fehlermeldungen erreichen nie den Prozess.
 *
 * 2. Plausibilitätsprüfung: Alle API-Antwortfelder werden validiert,
 * bevor sie als Prozessvariablen gesetzt werden.
 *
 * 3. Fehlerbehandlung (explizite Trennung fachlich / technisch):
 * - HTTP 202 → complete() mit statusCode=202 → BPMN-Gateway "ja"-Pfad
 * - HTTP 4xx (nicht 429) → complete() mit statusCode → BPMN-Gateway "nein"-Pfad
 *   → "Spedition telefonisch kontaktieren"
 * - HTTP 429/5xx / Exception → handleFailure() mit Retries → Incident
 *
 * BPMN-Gateway "Lieferung in Ordnung?" prüft: ${statusCode==202} / ${statusCode!=202}
 * Deshalb KEIN handleBpmnError() — es gibt kein Error Boundary Event auf diesem Task.
 *
 * 4. Idempotenz-Hinweis: Bei Retries könnte ein doppelter Auftrag entstehen.
 * In einer Produktionsumgebung sollte vor dem POST ein GET/DB-Check erfolgen.
 */
public class SpeditionApiWorker {

    private static final Logger LOG = Logger.getLogger(SpeditionApiWorker.class.getName());

    private static final String TOPIC = "group2_requestAPI";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_TIMEOUT_MS = 15000L;

    public static void register(ExternalTaskClient client) {

        client.subscribe(TOPIC).lockDuration(60000).handler((externalTask, externalTaskService) -> {

            LOG.info("Speditions-Worker: Task empfangen, id=" + externalTask.getId());

            try {
                // 1. Prozessvariablen lesen und validieren
                String street = getStringVar(externalTask, "deliveryAddressStreet");
                // houseNumber: Camunda-Form kann Long oder Integer liefern → Number-Cast
                Number houseNumberNum = (Number) externalTask.getVariable("deliveryAddressHouseNumber");
                String postalCode = getStringVar(externalTask, "deliveryPostalCode");
                String city = getStringVar(externalTask, "deliveryCity");
                String country = getStringVar(externalTask, "deliveryCountry");
                String phone = getStringVar(externalTask, "customerPhone");
                // weight: ebenfalls Number-Cast
                Number weightNum = (Number) externalTask.getVariable("weight");
                String customerId = getStringVar(externalTask, "customerId");
                String deliveryType = getStringVar(externalTask, "deliveryType");
                String selectedCarrier = getStringVar(externalTask, "selectedCarrier");

                // Pflichtfelder prüfen — bei fehlenden Daten complete() mit statusCode
                // (NICHT handleBpmnError! Kein Error Boundary Event im BPMN)
                if (city == null || street == null || weightNum == null || customerId == null) {
                    LOG.warning("Speditions-Worker: Pflichtfelder fehlen");
                    HashMap<String, Object> errorVars = new HashMap<>();
                    errorVars.put("statusCode", 400);
                    errorVars.put("transportError",
                            "Versanddaten unvollständig — bitte telefonisch beauftragen");
                    externalTaskService.complete(externalTask, errorVars);
                    return;
                }

                long weight = weightNum.longValue();

                // 2. Adresse zusammenbauen
                String destination = city + ", " + street
                        + (houseNumberNum != null ? " " + houseNumberNum.longValue() : "");

                // 3. API-Request bauen — inkl. deliveryType und selectedCarrier
                JSONObject requestBody = new JSONObject();
                requestBody.put("destination", destination);
                requestBody.put("customerReference", customerId);
                requestBody.put("recepientPhone", phone != null ? phone : "");
                requestBody.put("weight", weight);
                if (deliveryType != null && !deliveryType.isBlank()) {
                    requestBody.put("deliveryType", deliveryType);
                }
                if (selectedCarrier != null && !selectedCarrier.isBlank()) {
                    requestBody.put("selectedCarrier", selectedCarrier);
                }

                LOG.info("Speditions-Worker: Sende API-Request — " + destination + ", " + weight + "kg");

                // 4. API aufrufen
                String apiUrl = WorkerMain.API_URL + "/v1/consignment/request";
                JSONObject response = HttpHelper.post(apiUrl, requestBody);
                int statusCode = response.getInt("statusCode");

                // 5. Ergebnis auswerten — explizite Trennung fachlich / technisch
                if (statusCode == 202) {
                    // Erfolg — Antwort validieren und Variablen setzen
                    HashMap<String, Object> result = extractAndValidateResponse(response);
                    if (result == null) {
                        // Plausibilitätsprüfung fehlgeschlagen — fachlich, nicht technisch
                        result = new HashMap<>();
                        result.put("statusCode", 422);
                        result.put("transportError",
                                "Speditionsantwort unvollständig — bitte telefonisch klären");
                        externalTaskService.complete(externalTask, result);
                        return;
                    }

                    result.put("statusCode", statusCode);
                    LOG.info("Speditions-Worker: Auftrag erfolgreich — Tracking: "
                            + result.get("trackingNumber"));

                    externalTaskService.complete(externalTask, result);

                } else if (isTechnicalError(statusCode)) {
                    // Technischer / transienter Fehler — Retry mit Backoff
                    String reason = mapStatusToBusinessMessage(statusCode);
                    LOG.warning("Speditions-Worker: Technischer Fehler (" + statusCode + ") — Retry");
                    externalTaskService.handleFailure(externalTask,
                            "Speditions-API: " + reason,
                            "HTTP " + statusCode,
                            MAX_RETRIES, RETRY_TIMEOUT_MS);

                } else {
                    // Fachliche Ablehnung (4xx ausser 429) — complete() mit statusCode
                    // BPMN-Gateway routet über ${statusCode!=202} zum "telefonisch"-Pfad
                    String reason = mapStatusToBusinessMessage(statusCode);
                    LOG.info("Speditions-Worker: Fachliche Ablehnung — " + reason);

                    HashMap<String, Object> errorVars = new HashMap<>();
                    errorVars.put("statusCode", statusCode);
                    errorVars.put("transportError", reason);
                    externalTaskService.complete(externalTask, errorVars);
                }

            } catch (Exception e) {
                // Technischer Fehler (Netzwerk, Timeout) — Retry mit Backoff
                LOG.severe("Speditions-Worker: Technischer Fehler — " + e.getMessage());
                externalTaskService.handleFailure(externalTask,
                        "Speditions-API nicht erreichbar",
                        e.toString(),
                        MAX_RETRIES, RETRY_TIMEOUT_MS);
            }

        }).open();

        LOG.info("Speditions-Worker registriert auf Topic: " + TOPIC);
    }

    /**
     * Extrahiert und validiert die API-Antwort.
     * Gibt null zurück wenn Pflichtfelder fehlen.
     */
    private static HashMap<String, Object> extractAndValidateResponse(JSONObject response) {
        try {
            String trackingNumber = response.optString("orderId", null);
            String pickupDateStr = response.optString("pickupdate", null);
            String deliveryDateStr = response.optString("deliverydate", null);

            if (trackingNumber == null || pickupDateStr == null || deliveryDateStr == null) {
                LOG.warning("Speditions-Worker: API-Antwort unvollständig — "
                        + "tracking=" + trackingNumber
                        + ", pickup=" + pickupDateStr
                        + ", delivery=" + deliveryDateStr);
                return null;
            }

            // Datumsfelder parsen und validieren
            LocalDate pickupLocal = LocalDate.parse(pickupDateStr);
            LocalDate deliveryLocal = LocalDate.parse(deliveryDateStr);

            if (deliveryLocal.isBefore(pickupLocal)) {
                LOG.warning("Speditions-Worker: Lieferdatum liegt vor Abholdatum — "
                        + "Daten möglicherweise fehlerhaft");
            }

            Date pickupDate = Date.from(pickupLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date deliveryDate = Date.from(deliveryLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

            HashMap<String, Object> result = new HashMap<>();
            result.put("trackingNumber", trackingNumber);
            result.put("dateOfPickup", pickupDate);
            result.put("expectedDeliveryDate", deliveryDate);
            return result;

        } catch (Exception e) {
            LOG.warning("Speditions-Worker: Fehler beim Parsen der API-Antwort — " + e.getMessage());
            return null;
        }
    }

    /**
     * Prüft ob der HTTP-Status auf einen technischen / transient Fehler hindeutet.
     * Diese dürfen NICHT als BPMN Error behandelt werden, sondern müssen
     * über handleFailure() mit Retries abgefangen werden.
     */
    private static boolean isTechnicalError(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503;
    }

    /**
     * Abstraktionsschicht: Mappt technische HTTP-Statuscodes
     * auf fachlich verständliche Meldungen.
     * Rohe Fehlercodes erreichen nie den Geschäftsprozess.
     */
    private static String mapStatusToBusinessMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Die Versanddaten sind ungültig — bitte telefonisch beauftragen";
            case 403 -> "Zugang zur Spedition verweigert — bitte IT kontaktieren";
            case 404 -> "Speditionsservice nicht verfügbar — bitte telefonisch beauftragen";
            case 409 -> "Transportauftrag existiert bereits — bitte Sendungsnummer prüfen";
            case 422 -> "Lieferung in dieses Gebiet nicht möglich — bitte telefonisch klären";
            case 429 -> "Spedition überlastet — Retry wird durchgeführt";
            case 500, 502, 503 -> "Speditionssystem vorübergehend nicht erreichbar — Retry wird durchgeführt";
            default ->
                "Spedition konnte Auftrag nicht annehmen (Fehler " + statusCode + ") — bitte telefonisch beauftragen";
        };
    }

    private static String getStringVar(org.camunda.bpm.client.task.ExternalTask task, String name) {
        Object val = task.getVariable(name);
        return val != null ? val.toString() : null;
    }
}
