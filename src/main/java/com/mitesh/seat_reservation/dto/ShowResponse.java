package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ShowResponse(
        Long id,
        String name,
        @JsonProperty("price_paise") Integer pricePaise,
        @JsonProperty("per_user_limit") Integer perUserLimit,
        List<SeatResponse> seats,
        SeatCountsResponse counts
) {
}
