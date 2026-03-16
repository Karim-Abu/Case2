package com.example.droolstest;

import com.example.droolsengine.DeliveryCountry;
import com.example.droolsengine.DeliveryType;
import com.example.droolsengine.Logistics;
import com.example.droolsengine.LogisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DroolsTestApplicationTests {

    @Autowired
    private LogisticsService logisticsService;

    // Argentina Tests
    @Test
    public void testArgentinaLightWeight_StandardMail() {
        Logistics logistics = new Logistics(50, "AR");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.STANDARD_MAIL, logistics.getDeliveryType());
    }

    @Test
    public void testArgentinaMediumWeight_SpecialFreight() {
        Logistics logistics = new Logistics(250, "AR");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.SPECIAL_FREIGHT, logistics.getDeliveryType());
    }

    @Test
    public void testArgentinaHeavyWeight_ManualReview() {
        Logistics logistics = new Logistics(600, "AR");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
    }

    // Japan Tests
    @Test
    public void testJapanValidWeight_AirFreight() {
        Logistics logistics = new Logistics(150, "JP");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.AIR_FREIGHT, logistics.getDeliveryType());
    }

    @Test
    public void testJapanOverweight_FallbackToManualReview() {
        Logistics logistics = new Logistics(250, "JP");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
    }

    // Russia Test
    @Test
    public void testRussiaAnyWeight_ManualReview() {
        Logistics logistics = new Logistics(100, "RU");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
    }

    // Switzerland and Germany Tests
    @Test
    public void testSwitzerlandValidWeight_StandardFreight() {
        Logistics logistics = new Logistics(50, "CH");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.STANDARD_FREIGHT, logistics.getDeliveryType());
    }

    @Test
    public void testGermanyValidWeight_StandardFreight() {
        Logistics logistics = new Logistics(1000, "DE");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.STANDARD_FREIGHT, logistics.getDeliveryType());
    }

    // Edge Cases and Fallbacks
    @Test
    public void testInvalidWeightZero_FallbackToManualReview() {
        Logistics logistics = new Logistics(0, "CH");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
    }

    @Test
    public void testUnknownCountry_ManualReview() {
        Logistics logistics = new Logistics(50, "RS");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
    }

    @Test
    public void testJapanOverweight_ExplicitManualReview() {
        Logistics logistics = new Logistics(250, "JP");
        logisticsService.logisticDecisionManager(logistics);
        assertEquals(DeliveryType.MANUAL_REVIEW, logistics.getDeliveryType());
        assertNotNull(logistics.getRuleName());
    }

}
