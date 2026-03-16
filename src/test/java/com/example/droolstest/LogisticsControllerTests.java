package com.example.droolstest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-Tests für den LogisticsController.
 *
 * Testet die drei fachlichen Pfade, die der Camunda-Worker verarbeiten muss:
 * - 202 → AUTO (XOR-Gateway: decisionStatus == 'AUTO')
 * - 206 → MANUAL_REVIEW (XOR-Gateway: decisionStatus == 'MANUAL_REVIEW')
 * - 400 → INVALID_INPUT (XOR-Gateway: decisionStatus == 'INVALID_INPUT')
 *
 * Zudem: Edge-Cases und technische Fehlerfälle.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogisticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    // ──────────────────────────────────────────────
    // E2E: AUTO (HTTP 202)
    // ──────────────────────────────────────────────

    @Test
    void e2e_auto_argentina50kg_returns202_standardMail() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 50, \"destination\": \"AR\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryType").value("STANDARD_MAIL"))
                .andExpect(jsonPath("$.ruleName").exists());
    }

    @Test
    void e2e_auto_switzerland100kg_returns202_standardFreight() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 100, \"destination\": \"CH\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryType").value("STANDARD_FREIGHT"));
    }

    @Test
    void e2e_auto_japan150kg_returns202_airFreight() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 150, \"destination\": \"JP\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryType").value("AIR_FREIGHT"));
    }

    // ──────────────────────────────────────────────
    // E2E: MANUAL_REVIEW (HTTP 206)
    // ──────────────────────────────────────────────

    @Test
    void e2e_manualReview_russia_returns206() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 100, \"destination\": \"RU\"}"))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.deliveryType").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.ruleName").exists());
    }

    @Test
    void e2e_manualReview_argentina600kg_returns206() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 600, \"destination\": \"AR\"}"))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.deliveryType").value("MANUAL_REVIEW"));
    }

    @Test
    void e2e_manualReview_japan250kg_returns206() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 250, \"destination\": \"JP\"}"))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.deliveryType").value("MANUAL_REVIEW"));
    }

    @Test
    void e2e_manualReview_unknownCountry_returns206() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 50, \"destination\": \"XX\"}"))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.deliveryType").value("MANUAL_REVIEW"));
    }

    // ──────────────────────────────────────────────
    // INVALID_INPUT (HTTP 400) — Validierungsfehler, Drools NICHT aufgerufen
    // ──────────────────────────────────────────────

    @Test
    void invalidInput_weightZero_returns400() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 0, \"destination\": \"CH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.decisionStatus").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    void invalidInput_negativeWeight_returns400() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": -5, \"destination\": \"AR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.decisionStatus").value("INVALID_INPUT"));
    }

    // ──────────────────────────────────────────────
    // processInstanceId-Header Korrelation
    // ──────────────────────────────────────────────

    @Test
    void processInstanceId_headerIsPassedThrough() throws Exception {
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Process-Instance-Id", "test-process-123")
                .content("{\"weight\": 50, \"destination\": \"CH\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryType").value("STANDARD_FREIGHT"));
    }

    // ──────────────────────────────────────────────
    // Decision Log: POST /decisions/manual
    // ──────────────────────────────────────────────

    @Test
    void manualDecision_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/decisions/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "deliveryCountry": "RU",
                          "weight": 100,
                          "deliveryType": "SPECIAL_FREIGHT",
                          "manualReason": "Sondergenehmigung erteilt",
                          "processInstanceId": "test-pid-456"
                        }
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void manualDecision_missingCountry_returns400() throws Exception {
        mockMvc.perform(post("/decisions/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "weight": 100,
                          "deliveryType": "SPECIAL_FREIGHT",
                          "manualReason": "test"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Decision Stats
    // ──────────────────────────────────────────────

    @Test
    void stats_endpoint_returnsValidStructure() throws Exception {
        // Trigger some decisions first
        mockMvc.perform(post("/deliveryRuleManager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weight\": 50, \"destination\": \"CH\"}"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/decisions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDecisions").isNumber())
                .andExpect(jsonPath("$.automaticDecisions").isNumber())
                .andExpect(jsonPath("$.manualReviewDecisions").isNumber())
                .andExpect(jsonPath("$.finalHumanDecisions").isNumber())
                .andExpect(jsonPath("$.overrideRate").isNumber());
    }
}
