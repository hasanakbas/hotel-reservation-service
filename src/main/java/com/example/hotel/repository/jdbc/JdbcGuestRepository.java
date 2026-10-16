package com.example.hotel.repository.jdbc;

import com.example.hotel.model.BookingException;
import com.example.hotel.model.Guest;
import com.example.hotel.repository.GuestRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

public class JdbcGuestRepository implements GuestRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final Database db;

    public JdbcGuestRepository(Database db) {
        this.db = db;
    }

    @Override
    public String nextId() {
        long n = db.withConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT NEXT VALUE FOR guest_seq")) {
                rs.next();
                return rs.getLong(1);
            }
        });
        return "G-" + n;
    }

    @Override
    public void insert(Guest g) {
        try {
            db.withConnection(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO guests (id, name, email, points, completed_stays) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, g.getId());
                    ps.setString(2, g.getName());
                    ps.setString(3, g.getEmail());
                    ps.setInt(4, g.getPoints());
                    ps.setInt(5, g.getCompletedStays());
                    return ps.executeUpdate();
                }
            });
        } catch (DataAccessException e) {
            if (UNIQUE_VIOLATION.equals(e.getSqlState())) {
                throw new BookingException(BookingException.Code.DUPLICATE_EMAIL, "Email already registered");
            }
            throw e;
        }
    }

    @Override
    public void update(Guest g) {
        db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE guests SET name = ?, email = ?, points = ?, completed_stays = ? WHERE id = ?")) {
                ps.setString(1, g.getName());
                ps.setString(2, g.getEmail());
                ps.setInt(3, g.getPoints());
                ps.setInt(4, g.getCompletedStays());
                ps.setString(5, g.getId());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Guest> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return db.withConnection(c -> {
            try (PreparedStatement ps = c
                    .prepareStatement("SELECT id, name, email, points, completed_stays FROM guests WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<Guest>empty();
                    }
                    return Optional.of(new Guest(rs.getString("id"), rs.getString("name"), rs.getString("email"),
                            rs.getInt("points"), rs.getInt("completed_stays")));
                }
            }
        });
    }

    @Override
    public boolean existsByEmail(String lowerCaseEmail) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM guests WHERE LOWER(email) = ?")) {
                ps.setString(1, lowerCaseEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) > 0;
                }
            }
        });
    }
}
