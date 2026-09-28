package com.example.bookmyroom.dto;

import com.example.bookmyroom.entity.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        @NotNull(message = "Room type is required (SINGLE, DOUBLE or DELUXE)")
        RoomType roomType,

        @NotNull(message = "Capacity is required")
        @Positive(message = "Capacity must be greater than 0")
        Integer capacity,

        @NotNull(message = "Rate per night is required")
        @Positive(message = "Rate per night must be greater than 0")
        BigDecimal ratePerNight,

        // optional - a new room is available unless stated otherwise
        Boolean available
) {
}
