package io.github.pmoustopoulos.sftpconsole.dto;

/**
 * Inline preview payload. If {@code previewable} is false, don't render {@code data} —
 * the file isn't a previewable type (text, image, or PDF).
 */
public record PreviewContent(boolean previewable, byte[] data, String contentType) {
}
