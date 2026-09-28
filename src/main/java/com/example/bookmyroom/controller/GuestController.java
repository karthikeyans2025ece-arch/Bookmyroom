package com.example.bookmyroom.controller;

import com.example.bookmyroom.dto.GuestRequest;
import com.example.bookmyroom.dto.GuestResponse;
import com.example.bookmyroom.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
@Tag(name = "Guests", description = "Manage guest records")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @Operation(summary = "Add a new guest")
    @PostMapping
    public ResponseEntity<GuestResponse> addGuest(@Valid @RequestBody GuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.addGuest(request));
    }

    @Operation(summary = "List all guests")
    @GetMapping
    public ResponseEntity<List<GuestResponse>> getAllGuests() {
        return ResponseEntity.ok(guestService.getAllGuests());
    }

    @Operation(summary = "Get a guest by id")
    @GetMapping("/{id}")
    public ResponseEntity<GuestResponse> getGuest(@PathVariable Long id) {
        return ResponseEntity.ok(guestService.getGuestById(id));
    }

    @Operation(summary = "Update a guest")
    @PutMapping("/{id}")
    public ResponseEntity<GuestResponse> updateGuest(@PathVariable Long id,
                                                     @Valid @RequestBody GuestRequest request) {
        return ResponseEntity.ok(guestService.updateGuest(id, request));
    }

    @Operation(summary = "Delete a guest (only if they have no booking history)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long id) {
        guestService.deleteGuest(id);
        return ResponseEntity.noContent().build();
    }
}
