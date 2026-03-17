package com.example.droolsengine;

/**
 * Request-Body für POST /decisions/manual.
 *
 * Wird vom Camunda DecisionLog-Worker gesendet, nachdem ein Sachbearbeiter
 * im User Task "Spedition manuell auswählen" eine Entscheidung getroffen hat.
 *
 * processInstanceId verknüpft diesen HUMAN/FINAL-Eintrag mit dem
 * vorausgegangenen
 * DROOLS/MANUAL_REVIEW-Eintrag in decision_log.
 */
public class ManualDecisionRequest {

    private String deliveryCountry;
    private double weight;
    private String deliveryType;
    private String manualReason;
    /**
     * Camunda Prozessinstanz-ID. Darf nicht leer sein — Pflicht für Verknüpfung.
     */
    private String processInstanceId;
    private String selectedCarrier;

    public ManualDecisionRequest() {
    }

    public String getDeliveryCountry() {
        return deliveryCountry;
    }

    public void setDeliveryCountry(String deliveryCountry) {
        this.deliveryCountry = deliveryCountry;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(String deliveryType) {
        this.deliveryType = deliveryType;
    }

    public String getManualReason() {
        return manualReason;
    }

    public void setManualReason(String manualReason) {
        this.manualReason = manualReason;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getSelectedCarrier() {
        return selectedCarrier;
    }

    public void setSelectedCarrier(String selectedCarrier) {
        this.selectedCarrier = selectedCarrier;
    }
}
