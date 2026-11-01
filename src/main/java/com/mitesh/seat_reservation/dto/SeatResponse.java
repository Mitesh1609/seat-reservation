package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SeatResponse(
        Long id,
        @JsonProperty("seat_number") String seatNumber,
        // API values: available, held, confirmed.
        String status
) {
}
