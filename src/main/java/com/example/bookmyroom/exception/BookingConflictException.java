package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class BookingConflictException extends ApiException {

    public BookingConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
