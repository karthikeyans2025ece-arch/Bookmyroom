package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class InvalidRoomException extends ApiException {

    public InvalidRoomException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
