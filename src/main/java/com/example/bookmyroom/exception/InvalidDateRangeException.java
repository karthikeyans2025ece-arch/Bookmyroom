package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends ApiException {

    public InvalidDateRangeException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
