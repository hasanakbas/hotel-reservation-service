package com.example.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.hotel.model.RoomType;
import org.junit.jupiter.api.Test;

/** Smoke test: verifies that the build and test tooling work. */
class SmokeTest {

    @Test
    void roomTypesHaveExpectedCapacities() {
        assertEquals(1, RoomType.SINGLE.capacity());
        assertEquals(3, RoomType.DOUBLE.capacity());
        assertEquals(4, RoomType.SUITE.capacity());
    }
}
