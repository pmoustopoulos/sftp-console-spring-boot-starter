package io.github.pmoustopoulos.sftpconsole;

import io.github.pmoustopoulos.sftpconsole.dto.FileContent;
import io.github.pmoustopoulos.sftpconsole.dto.FileEntry;
import io.github.pmoustopoulos.sftpconsole.dto.PreviewContent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * File operations over the shared in-memory (Jimfs) filesystem that the embedded
 * SFTP server also serves. All incoming paths are resolved against the filesystem
 * root and verified to stay within it (no traversal escape) before any I/O.
 */
public class SftpConsoleService {

    private static final long TEXT_PREVIEW_MAX = 1_000_000L;

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/plain"),
            Map.entry("properties", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"),
            Map.entry("yaml", "text/yaml"),
            Map.entry("yml", "text/yaml"),
            Map.entry("html", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "application/javascript"),
            Map.entry("java", "text/x-java-source"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"));

    private final FileSystem fileSystem;
    private final Path root;

    public SftpConsoleService(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.root = fileSystem.getPath("/").toAbsolutePath().normalize();
    }

    public List<FileEntry> list(String path) {

        Path dir = resolve(path);

        if (!Files.exists(dir)) {
            throw new NotFoundException("No such directory: " + path);
        }

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        try (Stream<Path> entries = Files.list(dir)) {
            return entries.sorted(directoriesFirst()).map(this::toEntry).toList();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FileContent download(String path) {

        Path file = requireFile(resolve(path), path);

        try {
            byte[] data = Files.readAllBytes(file);
            String name = file.getFileName().toString();

            return new FileContent(data, contentType(name), name);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PreviewContent preview(String path) {

        Path file = requireFile(resolve(path), path);
        String ct = contentType(file.getFileName().toString());
        boolean previewable = isText(ct) || isImage(ct) || isPdf(ct);

        if (!previewable) {
            return new PreviewContent(false, new byte[0], ct);
        }

        try {

            byte[] data = Files.readAllBytes(file);

            if (isText(ct) && data.length > TEXT_PREVIEW_MAX) {
                data = Arrays.copyOf(data, (int) TEXT_PREVIEW_MAX);
            }

            return new PreviewContent(true, data, ct);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void upload(String dirPath, String filename, byte[] data) {

        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\")
                || filename.equals(".") || filename.equals("..")) {

            throw new IllegalArgumentException("Invalid filename: " + filename);
        }

        Path dir = resolve(dirPath);
        Path target = dir.resolve(filename).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes root: " + filename);
        }

        try {

            Files.createDirectories(dir);
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void createFolder(String path) {

        Path dir = resolve(path);

        if (dir.equals(root)) {
            throw new IllegalArgumentException("Cannot create the root directory");
        }

        try {
            Files.createDirectories(dir);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void rename(String from, String to) {

        Path src = resolve(from);
        Path dst = resolve(to);

        if (!Files.exists(src)) {
            throw new NotFoundException("No such path: " + from);
        }

        if (dst.equals(root)) {
            throw new IllegalArgumentException("Cannot overwrite the root directory");
        }

        try {
            Path parent = dst.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String path) {

        Path target = resolve(path);

        if (target.equals(root)) {
            throw new IllegalArgumentException("Cannot delete the root directory");
        }

        if (!Files.exists(target)) {
            throw new NotFoundException("No such path: " + path);
        }

        try {

            if (Files.isDirectory(target)) {

                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {

                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            } else {
                Files.delete(target);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- helpers ----

    private Path resolve(String userPath) {

        String p = (userPath == null || userPath.isBlank()) ? "/" : userPath.trim();

        for (String segment : p.split("/")) {
            if (segment.equals("..")) {
                // Jimfs's Path.normalize() silently collapses a leading ".." at the
                // filesystem root (e.g. "/../etc" -> "/etc"), which would otherwise let
                // traversal attempts slip past the startsWith(root) check below.
                throw new IllegalArgumentException("Path escapes root: " + userPath);
            }
        }

        if (p.startsWith("/")) {
            p = p.substring(1);
        }

        Path resolved = root.resolve(p).normalize();

        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes root: " + userPath);
        }

        return resolved;
    }

    private Path requireFile(Path p, String original) {

        if (!Files.exists(p)) {
            throw new NotFoundException("No such file: " + original);
        }

        if (Files.isDirectory(p)) {
            throw new IllegalArgumentException("Not a file: " + original);
        }

        return p;
    }

    private FileEntry toEntry(Path p) {

        try {

            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            boolean dir = attrs.isDirectory();
            String name = p.getFileName().toString();
            String rel = "/" + root.relativize(p).toString().replace(fileSystem.getSeparator(), "/");

            return new FileEntry(
                    name,
                    rel,
                    dir,
                    dir ? 0L : attrs.size(),
                    attrs.lastModifiedTime().toInstant().toString(),
                    dir ? null : contentType(name));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Comparator<Path> directoriesFirst() {
        return Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    private String contentType(String filename) {

        int dot = filename.lastIndexOf('.');

        if (dot >= 0 && dot < filename.length() - 1) {

            String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
            String ct = CONTENT_TYPES.get(ext);

            if (ct != null) {
                return ct;
            }
        }
        return "application/octet-stream";
    }

    private boolean isText(String contentType) {
        return contentType.startsWith("text/")
                || contentType.equals("application/json")
                || contentType.equals("application/xml")
                || contentType.equals("application/javascript");
    }

    private boolean isImage(String contentType) {
        return contentType.startsWith("image/");
    }

    private boolean isPdf(String contentType) {
        return contentType.equals("application/pdf");
    }
}
