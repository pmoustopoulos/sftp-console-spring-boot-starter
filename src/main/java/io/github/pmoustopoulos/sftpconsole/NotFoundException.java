package io.github.pmoustopoulos.sftpconsole;

/** Thrown by the service when a requested path does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
