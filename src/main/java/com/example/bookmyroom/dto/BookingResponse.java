package com.example.bookmyroom.dto;

import com.example.bookmyroom.entity.Booking;
import com.example.bookmyroom.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        String bookingReference,
        Long guestId,
        Long roomId,
        String roomNumber,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int numberOfGuests,
        BigDecimal totalCost,
        BookingStatus status,
        LocalDateTime createdAt
) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getBookingReference(),
                b.getGuest().getId(), b.getRoom().getId(), b.getRoom().getRoomNumber(),
                b.getCheckInDate(), b.getCheckOutDate(), b.getNumberOfGuests(),
                b.getTotalCost(), b.getStatus(), b.getCreatedAt());
    }
}
