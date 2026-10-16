package com.example.hotel.repository;

import com.example.hotel.model.Reservation;
import com.example.hotel.model.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    /** Reserves the next reservation id ("R-n"). */
    String nextId();

    void insert(Reservation reservation);

    /** Persists the current state of an already inserted reservation. */
    void update(Reservation reservation);

    Optional<Reservation> findById(String id);

    List<Reservation> findByStatus(ReservationStatus status);

    /**
     * True if a reservation in an occupying status (PENDING, CONFIRMED, CHECKED_IN) overlaps the nights
     * [checkIn, checkOut) of the given room. The reservation with id {@code ignoreId} is skipped (may be null).
     */
    boolean isRoomOccupied(int roomNumber, LocalDate checkIn, LocalDate checkOut, String ignoreId);
}
