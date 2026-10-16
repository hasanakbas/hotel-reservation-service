package com.example.hotel.repository;

import java.util.function.Supplier;

/** Runs a unit of work atomically. A RuntimeException thrown by the work rolls everything back. */
public interface TransactionRunner {

    <T> T execute(Supplier<T> work);
}
