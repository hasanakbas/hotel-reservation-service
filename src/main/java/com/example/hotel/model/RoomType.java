package com.example.hotel.model;

import java.math.BigDecimal;

public enum RoomType {
    SINGLE("80.00", 1),
    DOUBLE("120.00", 3),
    SUITE("250.00", 4);

    private final BigDecimal baseRate;
    private final int capacity;

    RoomType(String baseRate, int capacity) {
        this.baseRate = new BigDecimal(baseRate);
        this.capacity = capacity;
    }

    public BigDecimal baseRate() {
        return baseRate;
    }

    public int capacity() {
        return capacity;
    }
}
