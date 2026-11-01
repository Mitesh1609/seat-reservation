package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SeatCountsResponse(
        long available,
        long held,
        long confirmed,
        @JsonProperty("total_seats") long totalSeats
) {
}
