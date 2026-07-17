package de.toengi.cili.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CiliException {
    public ResourceNotFoundException(String entity, Long id) {
        super(entity + " not found with id: " + id, HttpStatus.NOT_FOUND);
    }
    public ResourceNotFoundException(String entity, String id) {
        super(entity + " not found with id: " + id, HttpStatus.NOT_FOUND);
    }
}
