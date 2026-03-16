package com.example.droolsengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Protokolliert alle Versandentscheidungen in der Datenbank.
 *
 * Designprinzipien:
 * - Einträge sind unveränderlich (INSERT only, kein UPDATE).
 * - Automatische UND manuelle Entscheidungen werden unabhängig gespeichert.
 * - Verknüpfung erfolgt über processInstanceId, nicht durch Überschreiben.
 * - Logging-Fehler dürfen den Hauptprozess nie blockieren (Caller-seitig im
 * try/catch).
 */
@Service
public class DecisionLogService {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogService.class);

    private final DecisionLogRepository repository;

    @Value("${app.rule-version:1.0}")
    private String ruleVersion;

    public DecisionLogService(DecisionLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Protokolliert eine automatische Drools-Entscheidung.
     *
     * Wird bei JEDEM /deliveryRuleManager-Aufruf gespeichert,
     * auch wenn das Ergebnis MANUAL_REVIEW ist — so ist nachvollziehbar,
     * was Drools empfohlen hätte, bevor der Mensch eingriff.
     *
     * @param logistics         Das von Drools ausgewertete Objekt.
     * @param processInstanceId Camunda-Prozessinstanz-ID. Kann null sein (z.B. bei
     *                          direkten REST-Tests).
     */
    public void logDroolsDecision(Logistics logistics, String processInstanceId, String businessKey) {
        DecisionStatus status = (logistics.getDeliveryType() == DeliveryType.MANUAL_REVIEW)
                ? DecisionStatus.MANUAL_REVIEW
                : DecisionStatus.AUTO;

        DecisionLog entry = DecisionLog.builder()
                .deliveryCountry(logistics.getDestination() != null
                        ? logistics.getDestination().name()
                        : "UNKNOWN")
                .weight(logistics.getWeight())
                .deliveryType(logistics.getDeliveryType() != null
                        ? logistics.getDeliveryType().name()
                        : "UNKNOWN")
                .decisionSource(DecisionSource.DROOLS)
                .decisionStatus(status)
                .ruleName(logistics.getRuleName())
                .ruleVersion(ruleVersion)
                .processInstanceId(processInstanceId)
                .businessKey(businessKey)
                .build();

        repository.save(entry);
        log.info("Drools-Entscheidung protokolliert: status={}, deliveryType={}, processInstanceId={}, businessKey={}",
                status, logistics.getDeliveryType(), processInstanceId, businessKey);
    }

    /**
     * Protokolliert die finale menschliche Entscheidung nach einem MANUAL_REVIEW.
     *
     * Dieser Eintrag (HUMAN/FINAL) ergänzt den vorhandenen
     * DROOLS/MANUAL_REVIEW-Eintrag
     * derselben processInstanceId. Er überschreibt ihn NICHT.
     *
     * @param request Die manuelle Entscheidung vom Camunda-Worker.
     */
    public void logManualDecision(ManualDecisionRequest request) {
        if (request.getProcessInstanceId() == null || request.getProcessInstanceId().isBlank()) {
            log.warn("Manuelle Entscheidung ohne processInstanceId — Verknüpfung nicht möglich!");
        }
        if (request.getManualReason() == null || request.getManualReason().isBlank()) {
            log.warn("Manuelle Entscheidung ohne Begründung für processInstanceId={}",
                    request.getProcessInstanceId());
        }

        DecisionLog entry = DecisionLog.builder()
                .deliveryCountry(request.getDeliveryCountry())
                .weight(request.getWeight())
                .deliveryType(request.getDeliveryType())
                .decisionSource(DecisionSource.HUMAN)
                .decisionStatus(DecisionStatus.FINAL)
                .manualReason(request.getManualReason())
                .processInstanceId(request.getProcessInstanceId())
                .businessKey(request.getBusinessKey())
                .selectedCarrier(request.getSelectedCarrier())
                .build();

        repository.save(entry);
        log.info("Manuelle Entscheidung protokolliert: deliveryType={}, processInstanceId={}",
                request.getDeliveryType(), request.getProcessInstanceId());
    }

    /**
     * Liefert KPI-Statistiken für das Monitoring-Dashboard.
     *
     * overrideRate = FINAL-Entscheidungen / (AUTO + MANUAL_REVIEW).
     * Bezugsgrösse sind nur Drools-Entscheidungen (nicht die FINAL-Einträge
     * selbst),
     * damit die Rate nicht durch den wachsenden Nenner verfälscht wird.
     * Steigt dieser Wert, stimmen die Drools-Regeln nicht mehr mit der Realität
     * überein — Signal, ein ML-Modell einzuführen.
     */
    public DecisionStats getStats() {
        long total = repository.count();
        long auto = repository.countByDecisionStatus(DecisionStatus.AUTO);
        long manualReview = repository.countByDecisionStatus(DecisionStatus.MANUAL_REVIEW);
        long finalHuman = repository.countByDecisionStatus(DecisionStatus.FINAL);
        long droolsDecisions = auto + manualReview;
        double overrideRate = (droolsDecisions == 0) ? 0.0 : (double) finalHuman / droolsDecisions;

        return new DecisionStats(total, auto, manualReview, finalHuman, overrideRate);
    }
}
