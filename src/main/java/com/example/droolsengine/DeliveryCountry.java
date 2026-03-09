package com.example.droolsengine;

import java.util.Objects;

public enum DeliveryCountry {
    AR,
    JP,
    RU,
    CH,
    DE,
    NOT_DEFINED;


    public static DeliveryCountry fromString(String countryCode) {
        for (DeliveryCountry country : DeliveryCountry.values()) {
            if (Objects.equals(countryCode, country.toString())) {
                return country;
            }
        }
        return NOT_DEFINED;
    }
}
