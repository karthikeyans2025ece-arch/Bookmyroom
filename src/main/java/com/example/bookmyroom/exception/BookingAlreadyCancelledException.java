package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class BookingAlreadyCancelledException extends ApiException {

    public BookingAlreadyCancelledException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
