package com.example.hotel.repository;

import com.example.hotel.model.Reservation;
import com.example.hotel.model.ReservationStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private int seq = 1;

    @Override
    public String nextId() {
        return "R-" + seq++;
    }

    @Override
    public void insert(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
    }

    @Override
    public void update(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return Optional.ofNullable(id == null ? null : reservations.get(id));
    }

    @Override
    public List<Reservation> findByStatus(ReservationStatus status) {
        List<Reservation> result = new ArrayList<>();
        for (Reservation r : reservations.values()) {
            if (r.getStatus() == status) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public boolean isRoomOccupied(int roomNumber, LocalDate checkIn, LocalDate checkOut, String ignoreId) {
        for (Reservation r : reservations.values()) {
            if (r.getRoomNumber() != roomNumber || !r.getStatus().occupiesRoom() || r.getId().equals(ignoreId)) {
                continue;
            }
            if (r.getCheckIn().isBefore(checkOut) && checkIn.isBefore(r.getCheckOut())) {
                return true;
            }
        }
        return false;
    }
}
