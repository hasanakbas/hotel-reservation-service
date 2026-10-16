package com.example.hotel.model;

import java.math.BigDecimal;

public enum LoyaltyTier {
    BRONZE(0, "0.00"),
    SILVER(500, "0.03"),
    GOLD(1500, "0.05"),
    PLATINUM(4000, "0.08");

    private final int minPoints;
    private final BigDecimal discount;

    LoyaltyTier(int minPoints, String discount) {
        this.minPoints = minPoints;
        this.discount = new BigDecimal(discount);
    }

    public int minPoints() {
        return minPoints;
    }

    public BigDecimal discount() {
        return discount;
    }

    public static LoyaltyTier fromPoints(int points) {
        LoyaltyTier result = BRONZE;
        for (LoyaltyTier t : values()) {
            if (points >= t.minPoints) {
                result = t;
            }
        }
        return result;
    }
}
