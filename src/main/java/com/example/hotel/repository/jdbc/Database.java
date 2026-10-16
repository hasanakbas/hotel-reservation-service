package com.example.hotel.repository.jdbc;

import com.example.hotel.repository.TransactionRunner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Owns the JDBC connection handling and the schema. A transaction is bound to the calling thread; repository calls
 * made inside {@link #execute(Supplier)} share its connection, calls made outside use a short-lived auto-commit one.
 */
public class Database implements TransactionRunner {

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }

    private final String url;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    /** Embedded H2 database stored in files under the given directory (created if missing). */
    public static Database file(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory " + directory, e);
        }
        return new Database("jdbc:h2:file:" + directory.toAbsolutePath().resolve("hotel"));
    }

    /** Private in-memory H2 database, handy for integration tests. */
    public static Database inMemory(String name) {
        return new Database("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
    }

    public Database(String url) {
        this.url = url;
        initSchema();
    }

    public <R> R withConnection(SqlFunction<Connection, R> work) {
        Connection bound = current.get();
        if (bound != null) {
            return run(bound, work);
        }
        try (Connection c = DriverManager.getConnection(url)) {
            return run(c, work);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open connection", e);
        }
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        if (current.get() != null) {
            return work.get();
        }
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);
            current.set(c);
            try {
                T result = work.get();
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                current.remove();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transaction failed", e);
        }
    }

    private static <R> R run(Connection c, SqlFunction<Connection, R> work) {
        try {
            return work.apply(c);
        } catch (SQLException e) {
            throw new DataAccessException("SQL error", e);
        }
    }

    private void initSchema() {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on the classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema.sql", e);
        }
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot initialise schema", e);
        }
    }
}
