package com.example.droolsengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-Endpunkte für das Entscheidungsprotokoll.
 *
 * GET /decisions/stats — KPI-Monitoring: Override-Rate, Entscheidungsverteilung
 * POST /decisions/manual — Manuellen Entscheid erfassen (aufgerufen vom Camunda
 * DecisionLog-Worker)
 *
 * Sicherheitshinweis: In Produktion sollte /decisions/** mit Spring Security
 * abgesichert werden.
 */
@RestController
@RequestMapping("/decisions")
public class DecisionLogController {

    private static final Logger log = LoggerFactory.getLogger(DecisionLogController.class);

    private final DecisionLogService decisionLogService;

    public DecisionLogController(DecisionLogService decisionLogService) {
        this.decisionLogService = decisionLogService;
    }

    /**
     * KPI-Endpunkt für Monitoring.
     *
     * Beispiel-Antwort:
     * {
     * "totalDecisions": 47,
     * "automaticDecisions": 35,
     * "manualReviewDecisions": 6,
     * "finalHumanDecisions": 6,
     * "overrideRate": 0.127
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<DecisionStats> getStats() {
        return ResponseEntity.ok(decisionLogService.getStats());
    }

    /**
     * Endpunkt für den Camunda DecisionLog-Worker.
     *
     * Aufgerufen nach dem Merge-Gateway, wenn isManualDecision=true.
     * Erstellt einen neuen HUMAN/FINAL-Eintrag — überschreibt NICHT den
     * DROOLS-Eintrag.
     *
     * Request-Beispiel:
     * {
     * "deliveryCountry": "RU",
     * "weight": 100.0,
     * "deliveryType": "SPECIAL_FREIGHT",
     * "manualReason": "Sondergenehmigung erteilt",
     * "processInstanceId": "abc-123-xyz"
     * }
     */
    @PostMapping("/manual")
    public ResponseEntity<Void> logManualDecision(@RequestBody ManualDecisionRequest request) {
        if (request.getDeliveryCountry() == null || request.getDeliveryType() == null) {
            log.warn("Unvollständige manuelle Entscheidung abgelehnt: {}", request);
            return ResponseEntity.badRequest().build();
        }
        try {
            decisionLogService.logManualDecision(request);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception e) {
            log.error("Fehler beim Protokollieren der manuellen Entscheidung: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
