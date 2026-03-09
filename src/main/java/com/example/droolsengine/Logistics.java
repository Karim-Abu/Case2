package com.example.droolsengine;

import com.fasterxml.jackson.annotation.JsonInclude;

public class Logistics {
    private double weight;
    private DeliveryCountry destination;
    private DeliveryType deliveryType;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String deliveryCountryManualCheck;

    public Logistics() {
    }

    public Logistics(double weight, String destination) {
        this.weight = weight;
        this.destination = DeliveryCountry.fromString(destination);
        if (this.destination == DeliveryCountry.NOT_DEFINED) {
            this.deliveryCountryManualCheck = destination;
        }
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public DeliveryCountry getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = DeliveryCountry.fromString(destination);
    }

    public DeliveryType getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(DeliveryType deliveryType) {
        this.deliveryType = deliveryType;
    }

    public String getDeliveryCountryManualCheck() {
        return deliveryCountryManualCheck;
    }

    public void setDeliveryCountryManualCheck(String deliveryCountryManualCheck) {
        this.deliveryCountryManualCheck = deliveryCountryManualCheck;
    }
}
