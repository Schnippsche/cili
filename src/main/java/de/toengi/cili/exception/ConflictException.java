package de.toengi.cili.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends CiliException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
