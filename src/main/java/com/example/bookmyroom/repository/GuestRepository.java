package com.example.bookmyroom.repository;

import com.example.bookmyroom.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {
}
