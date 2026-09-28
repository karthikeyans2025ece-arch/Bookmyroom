package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class CapacityExceededException extends ApiException {

    public CapacityExceededException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
