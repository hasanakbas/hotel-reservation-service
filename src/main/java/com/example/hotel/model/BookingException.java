package com.example.hotel.model;

public class BookingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Code {
        INVALID_DATES, PAST_DATE, STAY_TOO_LONG, TOO_FAR_IN_ADVANCE, INVALID_GUESTS, NO_AVAILABILITY,
        GUEST_NOT_FOUND, RESERVATION_NOT_FOUND, INVALID_STATE, INVALID_PROMO, DUPLICATE_EMAIL,
        INVALID_EMAIL, INVALID_NAME
    }

    private final Code code;

    public BookingException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
