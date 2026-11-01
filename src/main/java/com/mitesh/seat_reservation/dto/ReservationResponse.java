package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        @JsonProperty("reservation_id") Long reservationId,
        @JsonProperty("show_id") Long showId,
        @JsonProperty("user_id") Long userId,
        List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        // API values: confirmed, cancelled.
        String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("cancelled_at") Instant cancelledAt
) {
}
