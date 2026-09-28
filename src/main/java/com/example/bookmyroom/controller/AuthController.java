package com.example.bookmyroom.controller;

import com.example.bookmyroom.dto.AuthResponse;
import com.example.bookmyroom.dto.LoginRequest;
import com.example.bookmyroom.dto.RegisterRequest;
import com.example.bookmyroom.entity.User;
import com.example.bookmyroom.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_USER_ID = "BOOKMYROOM_USER_ID";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        try {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(authService.register(request));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpSession session) {

        try {
            User user = authService.authenticate(request);

            session.setAttribute(
                    SESSION_USER_ID,
                    user.getId()
            );

            return ResponseEntity.ok(
                    authService.toResponse(
                            user,
                            "Login successful."
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {

        Object userId =
                session.getAttribute(SESSION_USER_ID);

        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Not logged in."
                    ));
        }

        return ResponseEntity.ok(
                Map.of(
                        "loggedIn", true,
                        "userId", userId
                )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpSession session) {

        session.invalidate();

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Logged out successfully."
                )
        );
    }
}