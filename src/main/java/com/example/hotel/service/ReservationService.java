package com.example.hotel.service;

import static com.example.hotel.model.BookingException.Code.DUPLICATE_EMAIL;
import static com.example.hotel.model.BookingException.Code.GUEST_NOT_FOUND;
import static com.example.hotel.model.BookingException.Code.INVALID_DATES;
import static com.example.hotel.model.BookingException.Code.INVALID_EMAIL;
import static com.example.hotel.model.BookingException.Code.INVALID_NAME;
import static com.example.hotel.model.BookingException.Code.INVALID_STATE;
import static com.example.hotel.model.BookingException.Code.NO_AVAILABILITY;
import static com.example.hotel.model.BookingException.Code.PAST_DATE;
import static com.example.hotel.model.BookingException.Code.RESERVATION_NOT_FOUND;
import static com.example.hotel.model.BookingException.Code.STAY_TOO_LONG;
import static com.example.hotel.model.BookingException.Code.TOO_FAR_IN_ADVANCE;

import com.example.hotel.model.BookingException;
import com.example.hotel.model.Guest;
import com.example.hotel.model.LoyaltyTier;
import com.example.hotel.model.Quote;
import com.example.hotel.model.Reservation;
import com.example.hotel.model.ReservationStatus;
import com.example.hotel.model.Room;
import com.example.hotel.model.RoomType;
import com.example.hotel.repository.DirectTransactionRunner;
import com.example.hotel.repository.GuestRepository;
import com.example.hotel.repository.InMemoryGuestRepository;
import com.example.hotel.repository.InMemoryReservationRepository;
import com.example.hotel.repository.ReservationRepository;
import com.example.hotel.repository.TransactionRunner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reservation use cases. Every state-changing use case runs in one transaction and persists the entities it
 * touched through the repositories. Public methods are additionally synchronized on the service instance
 * (single-JVM deployment). Rules are documented in the README (G-*, R-*, C-*, D-*).
 */
public class ReservationService {

    public static final int MAX_NIGHTS = 30;
    public static final int MAX_ADVANCE_DAYS = 365;
    public static final Duration PENDING_TTL = Duration.ofMinutes(30);

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final Clock clock;
    private final PricingService pricing;
    private final List<Room> rooms;
    private final GuestRepository guests;
    private final ReservationRepository reservations;
    private final TransactionRunner tx;

    public ReservationService(Clock clock, PricingService pricing, List<Room> rooms, GuestRepository guests,
            ReservationRepository reservations, TransactionRunner tx) {
        this.clock = clock;
        this.pricing = pricing;
        this.rooms = new ArrayList<>(rooms);
        this.rooms.sort(Comparator.comparingInt(Room::number));
        this.guests = guests;
        this.reservations = reservations;
        this.tx = tx;
    }

    /** Service backed by non-persistent in-memory repositories. */
    public static ReservationService inMemory(Clock clock, PricingService pricing, List<Room> rooms) {
        return new ReservationService(clock, pricing, rooms, new InMemoryGuestRepository(),
                new InMemoryReservationRepository(), new DirectTransactionRunner());
    }

    // ---------------------------------------------------------------- guests

    public synchronized Guest registerGuest(String name, String email) {
        if (name == null || name.isBlank() || name.strip().length() > 50) {
            throw new BookingException(INVALID_NAME, "Name must be 1-50 characters");
        }
        if (email == null || !EMAIL.matcher(email.strip()).matches()) {
            throw new BookingException(INVALID_EMAIL, "Invalid email address");
        }
        String normalized = email.strip().toLowerCase();
        return tx.execute(() -> {
            if (guests.existsByEmail(normalized)) {
                throw new BookingException(DUPLICATE_EMAIL, "Email already registered");
            }
            Guest guest = new Guest(guests.nextId(), name.strip(), normalized);
            guests.insert(guest);
            return guest;
        });
    }

    public synchronized Guest getGuest(String id) {
        return requireGuest(id);
    }

    // ---------------------------------------------------------- availability

    public synchronized List<Room> availableRooms(RoomType type, LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
            throw new BookingException(INVALID_DATES, "Check-out must be after check-in");
        }
        List<Room> result = new ArrayList<>();
        for (Room r : rooms) {
            if (r.type() == type && !reservations.isRoomOccupied(r.number(), checkIn, checkOut, null)) {
                result.add(r);
            }
        }
        return result;
    }

    public synchronized Quote quote(RoomType type, LocalDate checkIn, LocalDate checkOut, int guestCount,
            String guestId, String promo) {
        validateStay(checkIn, checkOut);
        if (guestId == null) {
            return pricing.quote(type, checkIn, checkOut, guestCount, LoyaltyTier.BRONZE, promo, false);
        }
        Guest g = requireGuest(guestId);
        return pricing.quote(type, checkIn, checkOut, guestCount, g.tier(), promo, g.getCompletedStays() == 0);
    }

    // ---------------------------------------------------------- reservations

    public synchronized Reservation createReservation(String guestId, RoomType type, LocalDate checkIn,
            LocalDate checkOut, int guestCount, String promo) {
        return tx.execute(() -> {
            Guest guest = requireGuest(guestId);
            validateStay(checkIn, checkOut);
            Quote quote = pricing.quote(type, checkIn, checkOut, guestCount, guest.tier(), promo,
                    guest.getCompletedStays() == 0);
            Room room = findFreeRoom(type, checkIn, checkOut)
                    .orElseThrow(() -> new BookingException(NO_AVAILABILITY, "No " + type + " room available"));
            Reservation r = new Reservation(reservations.nextId(), guestId, room.number(), type, checkIn, checkOut,
                    guestCount, promo, quote, now());
            reservations.insert(r);
            return r;
        });
    }

    public synchronized Reservation getReservation(String id) {
        return requireReservation(id);
    }

    public synchronized Reservation confirm(String id) {
        Reservation result = tx.execute(() -> {
            Reservation r = requireReservation(id);
            requireStatus(r, ReservationStatus.PENDING);
            // Expiry is persisted here and the error is raised after the transaction has committed.
            r.setStatus(isStale(r) ? ReservationStatus.EXPIRED : ReservationStatus.CONFIRMED);
            reservations.update(r);
            return r;
        });
        if (result.getStatus() == ReservationStatus.EXPIRED) {
            throw new BookingException(INVALID_STATE, "Reservation expired before confirmation");
        }
        return result;
    }

    public synchronized int expireStalePending() {
        return tx.execute(() -> {
            int count = 0;
            for (Reservation r : reservations.findByStatus(ReservationStatus.PENDING)) {
                if (isStale(r)) {
                    r.setStatus(ReservationStatus.EXPIRED);
                    reservations.update(r);
                    count++;
                }
            }
            return count;
        });
    }

    public synchronized Reservation checkIn(String id) {
        return tx.execute(() -> {
            Reservation r = requireReservation(id);
            requireStatus(r, ReservationStatus.CONFIRMED);
            LocalDate today = today();
            if (today.isBefore(r.getCheckIn())) {
                throw new BookingException(INVALID_STATE, "Too early to check in");
            }
            if (!today.isBefore(r.getCheckOut())) {
                throw new BookingException(INVALID_STATE, "Stay period is over");
            }
            r.setStatus(ReservationStatus.CHECKED_IN);
            reservations.update(r);
            return r;
        });
    }

    public synchronized Reservation checkOut(String id) {
        return tx.execute(() -> {
            Reservation r = requireReservation(id);
            requireStatus(r, ReservationStatus.CHECKED_IN);
            r.setStatus(ReservationStatus.CHECKED_OUT);
            reservations.update(r);
            Guest g = requireGuest(r.getGuestId());
            g.addPoints(r.getQuote().total().divideToIntegralValue(BigDecimal.TEN).intValue());
            g.incrementCompletedStays();
            guests.update(g);
            return r;
        });
    }

    public synchronized Reservation cancel(String id) {
        return tx.execute(() -> {
            Reservation r = requireReservation(id);
            if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED) {
                throw new BookingException(INVALID_STATE, "Cannot cancel a reservation in status " + r.getStatus());
            }
            BigDecimal refund = BigDecimal.ZERO;
            if (r.getStatus() == ReservationStatus.CONFIRMED) {
                long daysBefore = ChronoUnit.DAYS.between(today(), r.getCheckIn());
                LoyaltyTier tier = requireGuest(r.getGuestId()).tier();
                refund = r.getQuote().total().multiply(refundRate(daysBefore, tier)).setScale(2,
                        RoundingMode.HALF_UP);
            }
            r.setRefundAmount(refund);
            r.setStatus(ReservationStatus.CANCELLED);
            reservations.update(r);
            return r;
        });
    }

    public synchronized Reservation markNoShow(String id) {
        return tx.execute(() -> {
            Reservation r = requireReservation(id);
            requireStatus(r, ReservationStatus.CONFIRMED);
            if (!today().isAfter(r.getCheckIn())) {
                throw new BookingException(INVALID_STATE,
                        "Guest can only be marked no-show after the check-in date");
            }
            r.setRefundAmount(BigDecimal.ZERO.setScale(2));
            r.setStatus(ReservationStatus.NO_SHOW);
            reservations.update(r);
            return r;
        });
    }

    public synchronized Reservation changeDates(String id, LocalDate newCheckIn, LocalDate newCheckOut) {
        return tx.execute(() -> {
            Reservation r = requireReservation(id);
            if (r.getStatus() != ReservationStatus.PENDING && r.getStatus() != ReservationStatus.CONFIRMED) {
                throw new BookingException(INVALID_STATE, "Cannot change dates in status " + r.getStatus());
            }
            validateStay(newCheckIn, newCheckOut);
            if (reservations.isRoomOccupied(r.getRoomNumber(), newCheckIn, newCheckOut, r.getId())) {
                throw new BookingException(NO_AVAILABILITY,
                        "Room " + r.getRoomNumber() + " is not free for new dates");
            }
            Guest g = requireGuest(r.getGuestId());
            Quote quote = pricing.quote(r.getRoomType(), newCheckIn, newCheckOut, r.getGuests(), g.tier(),
                    r.getPromoCode(), g.getCompletedStays() == 0);
            r.reschedule(newCheckIn, newCheckOut, quote);
            reservations.update(r);
            return r;
        });
    }

    // --------------------------------------------------------------- helpers

    static BigDecimal refundRate(long daysBefore, LoyaltyTier tier) {
        if (tier == LoyaltyTier.PLATINUM && daysBefore >= 3) {
            return BigDecimal.ONE;
        }
        if (daysBefore >= 7) {
            return BigDecimal.ONE;
        }
        if (daysBefore >= 3) {
            return new BigDecimal("0.50");
        }
        if (daysBefore >= 1) {
            return new BigDecimal("0.25");
        }
        return BigDecimal.ZERO;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private boolean isStale(Reservation r) {
        return !now().isBefore(r.getCreatedAt().plus(PENDING_TTL));
    }

    private void validateStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
            throw new BookingException(INVALID_DATES, "Check-out must be after check-in");
        }
        LocalDate today = today();
        if (checkIn.isBefore(today)) {
            throw new BookingException(PAST_DATE, "Check-in date is in the past");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw new BookingException(STAY_TOO_LONG, "Maximum stay is " + MAX_NIGHTS + " nights");
        }
        if (checkIn.isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new BookingException(TOO_FAR_IN_ADVANCE,
                    "Reservations open at most " + MAX_ADVANCE_DAYS + " days in advance");
        }
    }

    private Optional<Room> findFreeRoom(RoomType type, LocalDate checkIn, LocalDate checkOut) {
        for (Room room : rooms) {
            if (room.type() == type && !reservations.isRoomOccupied(room.number(), checkIn, checkOut, null)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    private Guest requireGuest(String id) {
        return guests.findById(id).orElseThrow(() -> new BookingException(GUEST_NOT_FOUND, "Guest not found: " + id));
    }

    private Reservation requireReservation(String id) {
        return reservations.findById(id)
                .orElseThrow(() -> new BookingException(RESERVATION_NOT_FOUND, "Reservation not found: " + id));
    }

    private void requireStatus(Reservation r, ReservationStatus expected) {
        if (r.getStatus() != expected) {
            throw new BookingException(INVALID_STATE,
                    "Reservation is " + r.getStatus() + ", expected " + expected);
        }
    }
}
