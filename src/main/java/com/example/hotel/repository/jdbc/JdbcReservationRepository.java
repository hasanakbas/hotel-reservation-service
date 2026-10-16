package com.example.hotel.repository.jdbc;

import com.example.hotel.model.Quote;
import com.example.hotel.model.Reservation;
import com.example.hotel.model.ReservationStatus;
import com.example.hotel.model.RoomType;
import com.example.hotel.repository.ReservationRepository;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class JdbcReservationRepository implements ReservationRepository {

    private static final String COLUMNS = "id, guest_id, room_number, room_type, check_in, check_out, guests, "
            + "promo_code, status, created_at_millis, nights, room_charge, extra_guest_charge, discount, vat, "
            + "tourism_fee, total, applied_discounts, refund_amount, price_adjustment";

    private final Database db;

    public JdbcReservationRepository(Database db) {
        this.db = db;
    }

    @Override
    public String nextId() {
        long n = db.withConnection(c -> {
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT NEXT VALUE FOR reservation_seq")) {
                rs.next();
                return rs.getLong(1);
            }
        });
        return "R-" + n;
    }

    @Override
    public void insert(Reservation r) {
        db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO reservations (" + COLUMNS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getGuestId());
                ps.setInt(3, r.getRoomNumber());
                ps.setString(4, r.getRoomType().name());
                ps.setObject(5, r.getCheckIn());
                ps.setObject(6, r.getCheckOut());
                ps.setInt(7, r.getGuests());
                ps.setString(8, r.getPromoCode());
                ps.setString(9, r.getStatus().name());
                ps.setLong(10, r.getCreatedAt().toEpochMilli());
                bindQuote(ps, 11, r.getQuote());
                ps.setBigDecimal(19, r.getRefundAmount());
                ps.setBigDecimal(20, r.getPriceAdjustment());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void update(Reservation r) {
        db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE reservations SET room_number = ?, check_in = ?, "
                    + "check_out = ?, status = ?, nights = ?, room_charge = ?, extra_guest_charge = ?, discount = ?, "
                    + "vat = ?, tourism_fee = ?, total = ?, applied_discounts = ?, refund_amount = ?, "
                    + "price_adjustment = ? WHERE id = ?")) {
                ps.setInt(1, r.getRoomNumber());
                ps.setObject(2, r.getCheckIn());
                ps.setObject(3, r.getCheckOut());
                ps.setString(4, r.getStatus().name());
                bindQuote(ps, 5, r.getQuote());
                ps.setBigDecimal(13, r.getRefundAmount());
                ps.setBigDecimal(14, r.getPriceAdjustment());
                ps.setString(15, r.getId());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Reservation> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM reservations WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Reservation>empty();
                }
            }
        });
    }

    @Override
    public List<Reservation> findByStatus(ReservationStatus status) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM reservations WHERE status = ? ORDER BY id")) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Reservation> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                    return list;
                }
            }
        });
    }

    @Override
    public boolean isRoomOccupied(int roomNumber, LocalDate checkIn, LocalDate checkOut, String ignoreId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM reservations WHERE room_number = ? "
                    + "AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN') AND check_in < ? AND check_out > ? "
                    + "AND id <> ?")) {
                ps.setInt(1, roomNumber);
                ps.setObject(2, checkOut);
                ps.setObject(3, checkIn);
                ps.setString(4, ignoreId == null ? "" : ignoreId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) > 0;
                }
            }
        });
    }

    private static void bindQuote(PreparedStatement ps, int start, Quote q) throws SQLException {
        ps.setInt(start, q.nights());
        ps.setBigDecimal(start + 1, q.roomCharge());
        ps.setBigDecimal(start + 2, q.extraGuestCharge());
        ps.setBigDecimal(start + 3, q.discount());
        ps.setBigDecimal(start + 4, q.vat());
        ps.setBigDecimal(start + 5, q.tourismFee());
        ps.setBigDecimal(start + 6, q.total());
        ps.setString(start + 7, String.join("|", q.appliedDiscounts()));
    }

    private static Reservation map(ResultSet rs) throws SQLException {
        String applied = rs.getString("applied_discounts");
        List<String> discounts = applied.isEmpty() ? List.of() : Arrays.asList(applied.split("\\|"));
        Quote quote = new Quote(rs.getInt("nights"), rs.getBigDecimal("room_charge"),
                rs.getBigDecimal("extra_guest_charge"), rs.getBigDecimal("discount"), rs.getBigDecimal("vat"),
                rs.getBigDecimal("tourism_fee"), rs.getBigDecimal("total"), discounts);
        BigDecimal refund = rs.getBigDecimal("refund_amount");
        BigDecimal adjustment = rs.getBigDecimal("price_adjustment");
        return new Reservation(rs.getString("id"), rs.getString("guest_id"), rs.getInt("room_number"),
                RoomType.valueOf(rs.getString("room_type")), rs.getObject("check_in", LocalDate.class),
                rs.getObject("check_out", LocalDate.class), rs.getInt("guests"), rs.getString("promo_code"),
                ReservationStatus.valueOf(rs.getString("status")), quote, refund, adjustment,
                Instant.ofEpochMilli(rs.getLong("created_at_millis")));
    }
}
