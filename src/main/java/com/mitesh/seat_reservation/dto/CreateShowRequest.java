package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateShowRequest(
        @NotBlank(message = "Show name is required")
        @Size(max = 255, message = "Show name must be at most 255 characters")
        String name,
        @NotEmpty(message = "At least one seat is required")
        List<@NotBlank(message = "Seat numbers must not be null or blank")
                @Size(max = 255, message = "Seat numbers must be at most 255 characters") String> seats,
        @NotNull(message = "Price in paise is required")
        @PositiveOrZero(message = "Price in paise must not be negative")
        @JsonProperty("price_paise") Integer pricePaise,
        @NotNull(message = "Per-user limit is required")
        @Positive(message = "Per-user limit must be greater than zero")
        @JsonProperty("per_user_limit") Integer perUserLimit
) {
    public CreateShowRequest {
        if (perUserLimit == null) {
            perUserLimit = 4;
        }
    }
}
