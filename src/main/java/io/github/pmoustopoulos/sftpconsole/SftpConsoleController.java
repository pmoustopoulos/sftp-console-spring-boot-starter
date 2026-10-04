package io.github.pmoustopoulos.sftpconsole;

import io.github.pmoustopoulos.sftpconsole.dto.FileContent;
import io.github.pmoustopoulos.sftpconsole.dto.FileEntry;
import io.github.pmoustopoulos.sftpconsole.dto.PreviewContent;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Hidden
@RestController
@RequestMapping("${sftp.console.path:/sftp-console}")
public class SftpConsoleController {

    private final SftpConsoleService service;
    private final SftpConsoleProperties properties;

    /** Response header telling the console a text preview was cut at the preview size limit. */
    static final String TRUNCATED_HEADER = "X-Preview-Truncated";

    private volatile String cachedTemplate;

    public SftpConsoleController(SftpConsoleService service, SftpConsoleProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> console(HttpServletRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderPage(request.getContextPath()));
    }

    private String renderPage(String contextPath) {

        String template = cachedTemplate;

        if (template == null) {

            try {
                byte[] bytes = new ClassPathResource("sftp-console/index.html")
                        .getInputStream().readAllBytes();

                template = new String(bytes, StandardCharsets.UTF_8);
                cachedTemplate = template;

            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load SFTP console page", ex);
            }
        }

        String basePath = contextPath + properties.getPath();

        return template.replace("__BASE_PATH__", basePath)
                .replace("__REFRESH_MS__", String.valueOf(properties.getRefreshInterval().toMillis()))
                .replace("__SFTP_HOST__", jsString(properties.getHost()))
                .replace("__SFTP_PORT__", String.valueOf(properties.getPort()))
                .replace("__SFTP_USER__", jsString(properties.getUsername()));
    }

    /** Escapes a config value for use inside a double-quoted JS string in an inline script. */
    static String jsString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\' || c == '<' || c == '>' || c == '&' || c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @GetMapping("/api/files")
    public List<FileEntry> list(@RequestParam(defaultValue = "/") String path) {
        return service.list(path);
    }

    @GetMapping("/api/files/download")
    public ResponseEntity<byte[]> download(@RequestParam String path) {
        FileContent content = service.download(path);
        return fileResponse(content.data(), content.contentType(), content.filename(), "attachment").build();
    }

    @GetMapping("/api/files/preview")
    public ResponseEntity<byte[]> preview(@RequestParam String path) {

        PreviewContent preview = service.preview(path);

        if (!preview.previewable()) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        String contentType = preview.contentType();
        boolean pdf = MediaType.APPLICATION_PDF_VALUE.equals(contentType);

        // Previews are served from the host app's own origin, and the files come from whoever can
        // reach the SFTP server. Never let one run as a page: text (including .html, .js, .svg
        // source) goes out as plain text, and everything except PDFs is sandboxed (an opened SVG
        // can't run script). PDFs are exempt because browsers refuse to render them sandboxed.
        if (contentType.startsWith("text/") || !contentType.startsWith("image/") && !pdf) {
            contentType = "text/plain;charset=UTF-8";
        }

        ResponseEntity.BodyBuilder response = fileResponse(preview.data(), contentType, null, "inline")
                .header(TRUNCATED_HEADER, String.valueOf(preview.truncated()));
        if (!pdf) {
            response.header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'; style-src 'unsafe-inline'");
        }
        return response.body(preview.data());
    }

    @PostMapping("/api/files/upload")
    public ResponseEntity<Void> upload(@RequestParam(defaultValue = "/") String path,
                                       @RequestParam("file") MultipartFile file) throws IOException {

        service.upload(path, file.getOriginalFilename(), file.getBytes());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/files/folder")
    public ResponseEntity<Void> folder(@RequestParam String path) {

        service.createFolder(path);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/files/rename")
    public ResponseEntity<Void> rename(@RequestBody RenameRequest request) {

        service.rename(request.from(), request.to());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/files")
    public ResponseEntity<Void> delete(@RequestParam String path) {

        service.delete(path);

        return ResponseEntity.noContent().build();
    }

    private ResponseEntity.BodyBuilder fileResponse(byte[] data, String contentType,
                                                    String filename, String disposition) {

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(contentType);

        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition.Builder builder = ContentDisposition.builder(disposition);

        if (filename != null) {
            builder = builder.filename(filename);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, builder.build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .contentLength(data.length);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // A short plain message for the console's toast instead of Spring's JSON error body.
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<String> handleIo(UncheckedIOException ex) {
        IOException cause = ex.getCause();
        String detail = cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("File operation failed (" + detail + ")");
    }

    public record RenameRequest(String from, String to) {
    }
}
