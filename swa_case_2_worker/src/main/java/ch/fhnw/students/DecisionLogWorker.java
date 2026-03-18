package ch.fhnw.students;

import org.camunda.bpm.client.ExternalTaskClient;
import org.json.JSONObject;

import java.util.logging.Logger;

/**
 * External Task Worker für Topic: group2_logDecision
 *
 * Protokolliert Entscheidungen über den REST-Endpunkt des Drools-Service
 * in die MySQL-DB.
 *
 * Logik:
 * - manualDecisionReason vorhanden → POST /decisions/manual
 * (MANUAL_REVIEW + INVALID_INPUT, nach User Task)
 * - sonst → Überspringen (AUTO wurde bereits im LogisticsController geloggt)
 *
 * Keine Duplikate: AUTO-Entscheidungen werden NUR vom Controller geloggt,
 * manuelle NUR vom Worker — nie beide.
 *
 * Logging-Fehler blockieren den Prozess nicht (try/catch → complete()).
 */
public class DecisionLogWorker {

    private static final Logger LOG = Logger.getLogger(DecisionLogWorker.class.getName());

    private static final String TOPIC = "group2_logDecision";

    public static void register(ExternalTaskClient client) {

        client.subscribe(TOPIC).lockDuration(30000).handler((externalTask, externalTaskService) -> {

            LOG.info("Log-Worker: Task empfangen, id=" + externalTask.getId());

            try {
                // Prozessvariablen lesen — deliveryCountry mit Fallback auf destination
                String country = (String) externalTask.getVariable("deliveryCountry");
                if (country == null || country.isBlank()) {
                    country = (String) externalTask.getVariable("destination");
                }
                // weight: Camunda-Form kann Long oder Integer liefern → Number-Cast
                Number weightNum = (Number) externalTask.getVariable("weight");
                double weight = (weightNum != null) ? weightNum.doubleValue() : 0.0;

                String deliveryType = (String) externalTask.getVariable("deliveryType");
                String processInstanceId = externalTask.getProcessInstanceId();
                String decisionStatus = (String) externalTask.getVariable("decisionStatus");
                String selectedCarrier = (String) externalTask.getVariable("selectedCarrier");

                String reason = (String) externalTask.getVariable("manualDecisionReason");
                Boolean isManual = (Boolean) externalTask.getVariable("isManualDecision");

                // Trigger: manualDecisionReason vorhanden (verlässlichster Indikator,
                // da nur im User Task "Spedition manuell auswählen" gesetzt).
                // Fallback: isManualDecision==true
                boolean shouldLog = (reason != null && !reason.isBlank())
                        || Boolean.TRUE.equals(isManual);

                if (shouldLog) {
                    JSONObject logBody = new JSONObject();
                    logBody.put("deliveryCountry", country != null ? country : "UNKNOWN");
                    logBody.put("weight", weight);
                    logBody.put("deliveryType", deliveryType != null ? deliveryType : "UNKNOWN");
                    logBody.put("manualReason", reason != null ? reason : "Keine Begründung angegeben");
                    logBody.put("processInstanceId", processInstanceId != null ? processInstanceId : "");
                    logBody.put("selectedCarrier", selectedCarrier != null ? selectedCarrier : "");

                    String logUrl = WorkerMain.DROOLS_URL + "/decisions/manual";
                    JSONObject response = HttpHelper.post(logUrl, logBody);

                    LOG.info("Log-Worker: Manuelle Entscheidung protokolliert — "
                            + country + " / " + deliveryType
                            + " (vorheriger Status: " + decisionStatus + ")");
                } else {
                    LOG.info("Log-Worker: AUTO-Entscheidung — bereits geloggt, überspringe.");
                }

                externalTaskService.complete(externalTask);

            } catch (Exception e) {
                LOG.warning("Log-Worker: Logging fehlgeschlagen — " + e.getMessage()
                        + ". Task wird trotzdem abgeschlossen (Logging darf Prozess nicht blockieren).");
                // Logging-Fehler dürfen den Geschäftsprozess nicht blockieren
                externalTaskService.complete(externalTask);
            }

        }).open();

        LOG.info("Decision-Log-Worker registriert auf Topic: " + TOPIC);
    }
}
