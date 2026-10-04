package io.github.pmoustopoulos.sftpconsole.dto;

/**
 * Inline preview payload. If {@code previewable} is false, don't render {@code data} —
 * the file isn't a previewable type (text, image, or PDF). {@code truncated} is true when a
 * text file was larger than the preview limit and only its beginning is included.
 */
public record PreviewContent(boolean previewable, byte[] data, String contentType, boolean truncated) {

    public PreviewContent(boolean previewable, byte[] data, String contentType) {
        this(previewable, data, contentType, false);
    }
}
