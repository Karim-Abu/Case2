package com.example.droolsengine;

public class Logistics {
    private double weight;
    private DeliveryCountry destination;
    private DeliveryType deliveryType;

    public Logistics(double weight, DeliveryCountry destination) {
        this.weight = weight;
        this.destination = destination;
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

    public void setDestination(DeliveryCountry destination) {
        this.destination = destination;
    }

    public DeliveryType getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(DeliveryType deliveryType) {
        this.deliveryType = deliveryType;
    }
}
