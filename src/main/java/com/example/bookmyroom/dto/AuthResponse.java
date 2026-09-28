package com.example.bookmyroom.dto;

public class AuthResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String message;

    public AuthResponse(Long id, String name, String email, String phone,
                        String address, String message) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getMessage() {
        return message;
    }
}