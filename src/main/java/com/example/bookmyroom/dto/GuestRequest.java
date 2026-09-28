package com.example.bookmyroom.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GuestRequest(
        @NotBlank(message = "Guest name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email,

        @NotBlank(message = "Phone number is required")
        String phone,

        String address
) {
}
