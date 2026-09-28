package com.example.bookmyroom.service;

import com.example.bookmyroom.dto.AuthResponse;
import com.example.bookmyroom.dto.LoginRequest;
import com.example.bookmyroom.dto.RegisterRequest;
import com.example.bookmyroom.entity.User;
import com.example.bookmyroom.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest request) {

        String email = clean(request.getEmail());

        if (email.isBlank() || request.getPassword() == null ||
                request.getPassword().length() < 6) {
            throw new IllegalArgumentException(
                    "Email is required and password must contain at least 6 characters.");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        User user = new User();

        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone().trim());
        user.setAddress(
                request.getAddress() == null
                        ? ""
                        : request.getAddress().trim()
        );

        userRepository.save(user);

        return toResponse(user, "Registration successful.");
    }

    public User authenticate(LoginRequest request) {

        String email = clean(request.getEmail());

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password."
                        ));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new IllegalArgumentException(
                    "Invalid email or password.");
        }

        return user;
    }

    public AuthResponse toResponse(User user, String message) {

        return new AuthResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress(),
                message
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}