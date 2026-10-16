package com.example.hotel.repository.jdbc;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sqlState;

    public DataAccessException(String message, SQLException cause) {
        super(message + ": " + cause.getMessage(), cause);
        this.sqlState = cause.getSQLState();
    }

    public String getSqlState() {
        return sqlState;
    }
}
