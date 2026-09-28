package com.example.bookmyroom.dto;

import java.math.BigDecimal;

public record OccupancyReportResponse(
        String month,
        long totalRooms,
        long bookedRooms,
        long availableRooms,
        long confirmedBookings,
        long cancelledBookings,
        long occupiedRoomNights,
        BigDecimal totalRevenue
) {
}
