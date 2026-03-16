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
 * - isManualDecision==true → POST /decisions/manual (MANUAL_REVIEW +
 * NO_RULE_MATCH)
 * - isManualDecision==false → Überspringen (AUTO wurde bereits im
 * LogisticsController geloggt)
 *
 * Keine Duplikate: AUTO-Entscheidungen werden NUR vom Controller geloggt,
 * manuelle NUR vom Worker — nie beide.
 *
 * Logging-Fehler blockieren den Prozess nicht (try/catch → complete()).
 */
public class DecisionLogWorker {

    private static final Logger LOG = Logger.getLogger(DecisionLogWorker.class.getName());

    private static final String LOG_SERVICE_URL = "http://localhost:8080/decisions/manual";
    private static final String TOPIC = "group2_logDecision";

    public static void register(ExternalTaskClient client) {

        client.subscribe(TOPIC).lockDuration(30000).handler((externalTask, externalTaskService) -> {

            LOG.info("Log-Worker: Task empfangen, id=" + externalTask.getId());

            try {
                // Prozessvariablen lesen
                String country = (String) externalTask.getVariable("deliveryCountry");
                Long weight = (Long) externalTask.getVariable("weight");
                String deliveryType = (String) externalTask.getVariable("deliveryType");
                String processInstanceId = externalTask.getProcessInstanceId();
                String decisionStatus = (String) externalTask.getVariable("decisionStatus");

                Boolean isManual = (Boolean) externalTask.getVariable("isManualDecision");
                String reason = (String) externalTask.getVariable("manualDecisionReason");

                // Nur manuelle Entscheidungen loggen —
                // automatische werden bereits vom Drools-Controller geloggt (kein Duplikat)
                if (Boolean.TRUE.equals(isManual)) {
                    JSONObject logBody = new JSONObject();
                    logBody.put("deliveryCountry", country != null ? country : "UNKNOWN");
                    logBody.put("weight", weight != null ? weight : 0);
                    logBody.put("deliveryType", deliveryType != null ? deliveryType : "UNKNOWN");
                    logBody.put("manualReason", reason != null ? reason : "Keine Begründung angegeben");
                    logBody.put("processInstanceId", processInstanceId != null ? processInstanceId : "");

                    JSONObject response = HttpHelper.post(LOG_SERVICE_URL, logBody);

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
