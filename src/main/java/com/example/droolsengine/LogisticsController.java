package com.example.droolsengine;

import org.apache.http.HttpStatus;
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
    public Logistics deliveryRuleCall(@RequestBody Logistics logistics, SessionStatus sessionStatus) {
        logisticsService.logisticDecisionManager(logistics);

        return logistics;
    }
}
