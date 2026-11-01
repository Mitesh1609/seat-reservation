package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CreateShowRequest(
        String name,
        List<String> seats,
        @JsonProperty("price_paise") Integer pricePaise,
        @JsonProperty("per_user_limit") Integer perUserLimit
) {
    public CreateShowRequest {
        if (perUserLimit == null) {
            perUserLimit = 4;
        }
    }
}
