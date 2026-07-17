package de.toengi.cili.exception;

import org.springframework.http.HttpStatus;

public class CiliException extends RuntimeException {
    private final HttpStatus status;

    public CiliException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
