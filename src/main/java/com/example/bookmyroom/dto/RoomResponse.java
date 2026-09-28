package com.example.bookmyroom.dto;

import com.example.bookmyroom.entity.Room;
import com.example.bookmyroom.entity.RoomType;

import java.math.BigDecimal;

public record RoomResponse(
        Long id,
        String roomNumber,
        RoomType roomType,
        int capacity,
        BigDecimal ratePerNight,
        boolean available
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getRoomNumber(), room.getRoomType(),
                room.getCapacity(), room.getRatePerNight(), room.isAvailable());
    }
}
