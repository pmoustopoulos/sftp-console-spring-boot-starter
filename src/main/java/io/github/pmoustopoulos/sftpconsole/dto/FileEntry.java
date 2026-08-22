package io.github.pmoustopoulos.sftpconsole.dto;

/**
 * One row in a directory listing. {@code path} is the absolute path within the
 * SFTP root (always starting with '/'); {@code contentType} is null for directories.
 */
public record FileEntry(
        String name,
        String path,
        boolean directory,
        long size,
        String lastModified,
        String contentType) {
}
