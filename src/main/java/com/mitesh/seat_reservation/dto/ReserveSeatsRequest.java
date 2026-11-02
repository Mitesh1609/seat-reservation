package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;
import java.util.List;

// User identity must come from the authenticated principal, not this request.
public record ReserveSeatsRequest(
        @NotEmpty(message = "At least one seat is required")
        @UniqueElements(message = "Seat numbers must be unique within a request")
        List<@NotBlank(message = "Seat numbers must not be null or blank")
                @Size(max = 255, message = "Seat numbers must be at most 255 characters") String> seats,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 255, message = "Idempotency key must be at most 255 characters")
        @JsonProperty("idempotency_key") String idempotencyKey
) {
}
