package com.example.hotel.model;

public enum ReservationStatus {
    PENDING(true),
    CONFIRMED(true),
    CHECKED_IN(true),
    CHECKED_OUT(false),
    CANCELLED(false),
    NO_SHOW(false),
    EXPIRED(false);

    private final boolean occupiesRoom;

    ReservationStatus(boolean occupiesRoom) {
        this.occupiesRoom = occupiesRoom;
    }

    public boolean occupiesRoom() {
        return occupiesRoom;
    }
}
