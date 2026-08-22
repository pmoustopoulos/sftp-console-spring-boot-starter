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
import java.nio.charset.StandardCharsets;
import java.util.List;

@Hidden
@RestController
@RequestMapping("${sftp.console.path:/sftp-console}")
public class SftpConsoleController {

    private final SftpConsoleService service;
    private final SftpConsoleProperties properties;

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

        return template.replace("__BASE_PATH__", basePath);
    }

    @GetMapping("/api/files")
    public List<FileEntry> list(@RequestParam(defaultValue = "/") String path) {
        return service.list(path);
    }

    @GetMapping("/api/files/download")
    public ResponseEntity<byte[]> download(@RequestParam String path) {
        FileContent content = service.download(path);
        return fileResponse(content.data(), content.contentType(), content.filename(), "attachment");
    }

    @GetMapping("/api/files/preview")
    public ResponseEntity<byte[]> preview(@RequestParam String path) {

        PreviewContent preview = service.preview(path);

        if (!preview.previewable()) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        return fileResponse(preview.data(), preview.contentType(), null, "inline");
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

    private ResponseEntity<byte[]> fileResponse(byte[] data, String contentType,
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
                .contentType(mediaType)
                .body(data);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    public record RenameRequest(String from, String to) {
    }
}
