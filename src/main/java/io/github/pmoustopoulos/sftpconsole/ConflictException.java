package io.github.pmoustopoulos.sftpconsole;

/** Thrown by the service when an operation would overwrite an existing path. Maps to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
