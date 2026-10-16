package com.example.hotel.repository;

import com.example.hotel.model.Guest;
import java.util.Optional;

public interface GuestRepository {

    /** Reserves the next guest id ("G-n"). */
    String nextId();

    /** @throws com.example.hotel.model.BookingException with DUPLICATE_EMAIL if the email already exists */
    void insert(Guest guest);

    void update(Guest guest);

    Optional<Guest> findById(String id);

    boolean existsByEmail(String lowerCaseEmail);
}
