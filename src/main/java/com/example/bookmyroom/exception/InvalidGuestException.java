package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class InvalidGuestException extends ApiException {

    public InvalidGuestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
