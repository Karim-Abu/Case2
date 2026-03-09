package com.example.droolsengine;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.SessionStatus;

import java.util.HashMap;
import java.util.Map;

@RestController

public class LogisticsController {

     private final LogisticsService logisticsService;

    public LogisticsController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @GetMapping(path = "/ping", produces = "application/json")
    public HashMap<String, Boolean> ping() {
        HashMap<String, Boolean> result = new HashMap<>();
        result.put("ping", true);
        return result;
    }

    @PostMapping(path = "/deliveryRuleManager", produces = "application/json")
    public ResponseEntity<Logistics> deliveryRuleCall(@RequestBody Logistics logistics, SessionStatus sessionStatus) {
        System.out.println(logistics.getWeight());
        System.out.println(logistics.getDestination());
        logisticsService.logisticDecisionManager(logistics);
        if (logistics.getDeliveryType() != DeliveryType.MANUAL_REVIEW) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(logistics);
        } else {
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(logistics);
        }
    }
}
