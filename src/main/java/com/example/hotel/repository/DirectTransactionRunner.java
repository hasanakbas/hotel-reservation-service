package com.example.hotel.repository;

import java.util.function.Supplier;

/** No real transaction: just runs the work. Used with the in-memory repositories. */
public class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }
}
