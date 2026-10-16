package com.example.hotel.service;

import static com.example.hotel.model.BookingException.Code.INVALID_DATES;
import static com.example.hotel.model.BookingException.Code.INVALID_GUESTS;
import static com.example.hotel.model.BookingException.Code.INVALID_PROMO;

import com.example.hotel.model.BookingException;
import com.example.hotel.model.LoyaltyTier;
import com.example.hotel.model.Quote;
import com.example.hotel.model.RoomType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Computes the price of a stay. All rules are documented in the README (rules P-01 .. P-12).
 */
public class PricingService {

    static final BigDecimal EXTRA_GUEST_FEE = new BigDecimal("20.00");
    static final BigDecimal TOURISM_FEE = new BigDecimal("1.50");
    static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    static final BigDecimal WEEKEND_FACTOR = new BigDecimal("1.15");
    static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("0.25");
    static final int INCLUDED_GUESTS = 2;

    public Quote quote(RoomType type, LocalDate checkIn, LocalDate checkOut, int guests, LoyaltyTier tier,
            String promoCode, boolean firstBooking) {
        if (checkIn == null || checkOut == null) {
            throw new BookingException(INVALID_DATES, "Check-in and check-out dates are required");
        }
        long nightsL = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nightsL < 1) {
            throw new BookingException(INVALID_DATES, "Check-out must be after check-in");
        }
        if (guests < 1 || guests > type.capacity()) {
            throw new BookingException(INVALID_GUESTS,
                    type + " accepts between 1 and " + type.capacity() + " guests");
        }
        int nights = (int) nightsL;

        BigDecimal roomCharge = BigDecimal.ZERO;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            roomCharge = roomCharge.add(nightlyRate(type, d));
        }

        int extraGuests = Math.max(0, guests - INCLUDED_GUESTS);
        BigDecimal extraCharge = EXTRA_GUEST_FEE
                .multiply(BigDecimal.valueOf(extraGuests))
                .multiply(BigDecimal.valueOf(nights));

        BigDecimal subtotal = roomCharge.add(extraCharge);

        List<String> applied = new ArrayList<>();
        BigDecimal percent = BigDecimal.ZERO;
        BigDecimal flat = BigDecimal.ZERO;

        BigDecimal losPercent = lengthOfStayDiscount(nights);
        if (losPercent.signum() > 0) {
            percent = percent.add(losPercent);
            applied.add("LENGTH_OF_STAY_" + losPercent.movePointRight(2).intValue() + "%");
        }

        if (tier.discount().signum() > 0) {
            percent = percent.add(tier.discount());
            applied.add("LOYALTY_" + tier.name());
        }

        String promo = normalizePromo(promoCode);
        if (promo != null) {
            LocalDate lastNight = checkOut.minusDays(1);
            switch (promo) {
                case "WELCOME10" -> {
                    if (!firstBooking) {
                        throw new BookingException(INVALID_PROMO, "WELCOME10 is only valid for first-time guests");
                    }
                    percent = percent.add(new BigDecimal("0.10"));
                }
                case "SUMMER20" -> {
                    boolean sameYear = checkIn.getYear() == lastNight.getYear();
                    boolean inSummer = MonthDay.from(checkIn).compareTo(MonthDay.of(6, 1)) >= 0
                            && MonthDay.from(lastNight).compareTo(MonthDay.of(8, 31)) <= 0;
                    if (!sameYear || !inSummer || nights < 3) {
                        throw new BookingException(INVALID_PROMO,
                                "SUMMER20 requires at least 3 nights between 1 June and 31 August");
                    }
                    percent = percent.add(new BigDecimal("0.20"));
                }
                case "FLAT50" -> {
                    if (subtotal.compareTo(new BigDecimal("200.00")) < 0) {
                        throw new BookingException(INVALID_PROMO, "FLAT50 requires a subtotal of at least 200.00");
                    }
                    flat = new BigDecimal("50.00");
                }
                default -> throw new BookingException(INVALID_PROMO, "Unknown promo code: " + promo);
            }
            applied.add("PROMO_" + promo);
        }

        BigDecimal discount = subtotal.multiply(percent).add(flat);
        BigDecimal cap = subtotal.multiply(MAX_DISCOUNT_RATE);
        if (discount.compareTo(cap) > 0) {
            discount = cap;
            applied.add("CAPPED_AT_25%");
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discounted = subtotal.subtract(discount);
        BigDecimal vat = discounted.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = TOURISM_FEE.multiply(BigDecimal.valueOf((long) guests * nights));
        BigDecimal total = discounted.add(vat).add(fee);

        return new Quote(nights, roomCharge, extraCharge, discount, vat, fee, total, applied);
    }

    BigDecimal nightlyRate(RoomType type, LocalDate night) {
        BigDecimal rate = type.baseRate().multiply(seasonFactor(night));
        DayOfWeek dow = night.getDayOfWeek();
        if (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) {
            rate = rate.multiply(WEEKEND_FACTOR);
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal seasonFactor(LocalDate d) {
        MonthDay md = MonthDay.from(d);
        if (md.compareTo(MonthDay.of(12, 20)) >= 0 || md.compareTo(MonthDay.of(1, 5)) <= 0) {
            return new BigDecimal("1.50");
        }
        if (md.compareTo(MonthDay.of(6, 15)) >= 0 && md.compareTo(MonthDay.of(9, 15)) <= 0) {
            return new BigDecimal("1.30");
        }
        boolean low = (md.compareTo(MonthDay.of(11, 1)) >= 0 && md.compareTo(MonthDay.of(12, 15)) <= 0)
                || (md.compareTo(MonthDay.of(1, 6)) >= 0 && md.compareTo(MonthDay.of(3, 15)) <= 0);
        return low ? new BigDecimal("0.90") : BigDecimal.ONE;
    }

    BigDecimal lengthOfStayDiscount(int nights) {
        if (nights >= 14) {
            return new BigDecimal("0.15");
        }
        if (nights >= 7) {
            return new BigDecimal("0.10");
        }
        if (nights >= 3) {
            return new BigDecimal("0.05");
        }
        return BigDecimal.ZERO;
    }

    private static String normalizePromo(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return null;
        }
        return promoCode.trim().toUpperCase(Locale.ROOT);
    }
}
