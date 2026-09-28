package com.example.bookmyroom.dto;

import com.example.bookmyroom.entity.Guest;

public record GuestResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address
) {
    public static GuestResponse from(Guest guest) {
        return new GuestResponse(guest.getId(), guest.getName(), guest.getEmail(),
                guest.getPhone(), guest.getAddress());
    }
}
