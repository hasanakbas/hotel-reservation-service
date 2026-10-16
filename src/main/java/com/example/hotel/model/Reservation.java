package com.example.hotel.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class Reservation {
    private final String id;
    private final String guestId;
    private final RoomType roomType;
    private final Instant createdAt;
    private int roomNumber;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int guests;
    private String promoCode;
    private ReservationStatus status;
    private Quote quote;
    private BigDecimal refundAmount;
    private BigDecimal priceAdjustment;

    public Reservation(String id, String guestId, int roomNumber, RoomType roomType, LocalDate checkIn,
            LocalDate checkOut, int guests, String promoCode, Quote quote, Instant createdAt) {
        this.id = id;
        this.guestId = guestId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
        this.promoCode = promoCode;
        this.quote = quote;
        this.createdAt = createdAt;
        this.status = ReservationStatus.PENDING;
    }

    /** Restores a persisted reservation with all of its state. */
    public Reservation(String id, String guestId, int roomNumber, RoomType roomType, LocalDate checkIn,
            LocalDate checkOut, int guests, String promoCode, ReservationStatus status, Quote quote,
            BigDecimal refundAmount, BigDecimal priceAdjustment, Instant createdAt) {
        this(id, guestId, roomNumber, roomType, checkIn, checkOut, guests, promoCode, quote, createdAt);
        this.status = status;
        this.refundAmount = refundAmount;
        this.priceAdjustment = priceAdjustment;
    }

    public String getId() {
        return id;
    }

    public String getGuestId() {
        return guestId;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public int getGuests() {
        return guests;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Quote getQuote() {
        return quote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public BigDecimal getPriceAdjustment() {
        return priceAdjustment;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public void reschedule(LocalDate checkIn, LocalDate checkOut, Quote newQuote) {
        this.priceAdjustment = newQuote.total().subtract(this.quote.total());
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.quote = newQuote;
    }
}
