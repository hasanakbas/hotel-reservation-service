package com.example.hotel.repository;

import com.example.hotel.model.BookingException;
import com.example.hotel.model.Guest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryGuestRepository implements GuestRepository {

    private final Map<String, Guest> guests = new LinkedHashMap<>();
    private int seq = 1;

    @Override
    public String nextId() {
        return "G-" + seq++;
    }

    @Override
    public void insert(Guest guest) {
        if (existsByEmail(guest.getEmail().toLowerCase())) {
            throw new BookingException(BookingException.Code.DUPLICATE_EMAIL, "Email already registered");
        }
        guests.put(guest.getId(), guest);
    }

    @Override
    public void update(Guest guest) {
        guests.put(guest.getId(), guest);
    }

    @Override
    public Optional<Guest> findById(String id) {
        return Optional.ofNullable(id == null ? null : guests.get(id));
    }

    @Override
    public boolean existsByEmail(String lowerCaseEmail) {
        return guests.values().stream().anyMatch(g -> g.getEmail().equalsIgnoreCase(lowerCaseEmail));
    }
}
