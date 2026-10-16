package com.example.hotel.model;

import java.math.BigDecimal;
import java.util.List;

public record Quote(
        int nights,
        BigDecimal roomCharge,
        BigDecimal extraGuestCharge,
        BigDecimal discount,
        BigDecimal vat,
        BigDecimal tourismFee,
        BigDecimal total,
        List<String> appliedDiscounts) {
}
