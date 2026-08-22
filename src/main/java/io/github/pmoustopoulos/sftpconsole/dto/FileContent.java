package io.github.pmoustopoulos.sftpconsole.dto;

/** Raw bytes of a file for download. */
public record FileContent(byte[] data, String contentType, String filename) {
}
