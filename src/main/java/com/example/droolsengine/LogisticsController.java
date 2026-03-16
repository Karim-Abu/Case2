package com.example.droolsengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST-Endpunkt für die Versandregelauswertung.
 *
 * HTTP-Statuscodes (Konvention für den Camunda-Worker):
 *
 * 202 ACCEPTED → AUTO: Drools hat eindeutige Versandart bestimmt.
 * Worker ruft complete(decisionStatus='AUTO').
 * 206 PARTIAL_CONTENT → MANUAL_REVIEW: Drools-Regel schreibt menschliche
 * Prüfung vor.
 * Worker ruft complete(decisionStatus='MANUAL_REVIEW') — KEIN bpmnError!
 * 400 BAD_REQUEST → INVALID_INPUT: Eingabedaten ungültig, Drools NICHT
 * aufgerufen.
 * Worker ruft complete(decisionStatus='INVALID_INPUT') — KEIN bpmnError!
 * 500 INTERNAL ERROR → Technischer Fehler.
 * Worker ruft handleFailure() mit Retries auf.
 *
 * ALLE drei fachlichen Statuscodes (202/206/400) sind normale
 * Prozessabschlüsse.
 * Das XOR-Gateway im BPMN routet anhand von decisionStatus:
 * - AUTO → direkt zu „Daten validieren"
 * - MANUAL_REVIEW || INVALID_INPUT → User Task „Spedition manuell auswählen"
 *
 * Wichtig: MANUAL_REVIEW ≠ INVALID_INPUT
 * - MANUAL_REVIEW: Drools wurde aufgerufen, Regel schreibt menschliche Prüfung
 *   vor (z.B. RU, JP>200kg, unbekanntes Land)
 * - INVALID_INPUT: Validierung fehlgeschlagen, Drools wurde NIE aufgerufen
 *   (z.B. weight ≤ 0, destination fehlt)
 *
 * Camunda-Worker übergibt optional den Header X-Process-Instance-Id für die
 * Verknüpfung von DROOLS- und HUMAN-Einträgen in decision_log.
 */
@RestController
public class LogisticsController {

    private static final Logger log = LoggerFactory.getLogger(LogisticsController.class);

    private final LogisticsService logisticsService;
    private final DecisionLogService decisionLogService;

    public LogisticsController(LogisticsService logisticsService, DecisionLogService decisionLogService) {
        this.logisticsService = logisticsService;
        this.decisionLogService = decisionLogService;
    }

    @GetMapping(path = "/ping", produces = "application/json")
    public HashMap<String, Boolean> ping() {
        HashMap<String, Boolean> result = new HashMap<>();
        result.put("ping", true);
        return result;
    }

    @PostMapping(path = "/deliveryRuleManager", produces = "application/json")
    public ResponseEntity<?> deliveryRuleCall(
            @RequestBody Logistics logistics,
            @RequestHeader(value = "X-Process-Instance-Id", required = false) String processInstanceId) {

        // --- Input-Validierung → INVALID_INPUT (400) ---
        // Drools wird bei ungültigen Eingaben NICHT aufgerufen.
        // Das ist kein fachliches Drools-Ergebnis, sondern ein Validierungsfehler.
        if (logistics.getWeight() <= 0) {
            log.warn("Abgelehnt: weight={} ist ungültig (muss > 0 sein)", logistics.getWeight());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "decisionStatus", "INVALID_INPUT",
                    "reason", "Gewicht muss > 0 sein",
                    "weight", logistics.getWeight(),
                    "destination", logistics.getDestination() != null ? logistics.getDestination().name() : "null"));
        }
        if (logistics.getDestination() == null) {
            log.warn("Abgelehnt: destination fehlt im Request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "decisionStatus", "INVALID_INPUT",
                    "reason", "Zielland fehlt im Request",
                    "weight", logistics.getWeight()));
        }

        log.info("Request: weight={}, destination={}, processInstanceId={}",
                logistics.getWeight(), logistics.getDestination(), processInstanceId);

        try {
            logisticsService.logisticDecisionManager(logistics);
        } catch (RuntimeException e) {
            log.error("Drools-Fehler für processInstanceId={}: {}", processInstanceId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }

        // Entscheidung protokollieren — IMMER, auch bei MANUAL_REVIEW.
        // Logging-Fehler blockieren den Prozess nicht.
        try {
            decisionLogService.logDroolsDecision(logistics, processInstanceId);
        } catch (Exception e) {
            log.error("Logging fehlgeschlagen (Prozess läuft weiter): ", e);
        }

        if (logistics.getDeliveryType() != DeliveryType.MANUAL_REVIEW) {
            // AUTO: XOR-Gateway → decisionStatus=='AUTO' → weiter zu "Daten validieren"
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(logistics);
        } else {
            // MANUAL_REVIEW: XOR-Gateway → decisionStatus=='MANUAL_REVIEW' → User Task
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(logistics);
        }
    }
}
