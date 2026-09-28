package com.example.bookmyroom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Note: there is intentionally no totalCost here - the server calculates it. */
public record BookingRequest(
        @NotNull(message = "Guest id is required")
        Long guestId,

        @NotNull(message = "Room id is required")
        Long roomId,

        @NotNull(message = "Check-in date is required")
        LocalDate checkInDate,

        @NotNull(message = "Check-out date is required")
        LocalDate checkOutDate,

        @NotNull(message = "Number of guests is required")
        @Positive(message = "Number of guests must be greater than 0")
        Integer numberOfGuests
) {
}
