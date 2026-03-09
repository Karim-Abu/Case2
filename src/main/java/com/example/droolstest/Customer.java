package com.example.droolstest;

public class Customer {

    public enum Country {
        DE,
        CH,
        OTHER
    }

    private int years;
    private Country country;

    private int discount;

    // Standard getters and setters

    public int getDiscount() {
        return discount;
    }

    public void setDiscount(int discount) {
        this.discount = discount;
    }

    public int getYears() {
        return years;
    }

    public void setYears(int years) {
        this.years = years;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }
}
