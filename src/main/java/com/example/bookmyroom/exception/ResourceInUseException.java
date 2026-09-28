package com.example.bookmyroom.exception;

import org.springframework.http.HttpStatus;

public class ResourceInUseException extends ApiException {

    public ResourceInUseException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
