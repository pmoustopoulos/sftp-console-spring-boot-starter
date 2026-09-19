package io.github.pmoustopoulos.sftpconsole.security;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.pmoustopoulos.sftpconsole.SftpConsoleAutoConfiguration;
import io.github.pmoustopoulos.sftpconsole.SftpConsoleController;
import io.github.pmoustopoulos.sftpconsole.SftpConsoleProperties;
import io.github.pmoustopoulos.sftpconsole.SftpConsoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The console previews PDFs in a same-origin {@code <iframe>}. Spring Security's default
 * {@code X-Frame-Options: DENY} would block that (leaving the preview blank), so the console's
 * filter chain must relax framing to SAMEORIGIN for its own path.
 */
@SpringBootTest(
        classes = SftpConsolePreviewFrameHeaderTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sftp.console.enabled=true")
class SftpConsolePreviewFrameHeaderTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SftpConsoleService service;

    @Test
    void previewAllowsSameOriginFraming() throws Exception {
        service.upload("/", "doc.pdf", new byte[]{'%', 'P', 'D', 'F'});

        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port
                                + "/sftp-console/api/files/preview?path=/doc.pdf"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
    }

    // Minimal app: the security chain under test plus the controller/service it protects.
    // The main SftpConsoleAutoConfiguration is excluded so no embedded SFTP server is started.
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = SftpConsoleAutoConfiguration.class)
    @Import(SftpConsoleSecurityConfiguration.class)
    static class App {

        @Bean
        SftpConsoleService sftpConsoleService() {
            FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                    .setWorkingDirectory("/").build());
            return new SftpConsoleService(fs);
        }

        @Bean
        SftpConsoleController sftpConsoleController(SftpConsoleService service,
                                                   SftpConsoleProperties properties) {
            return new SftpConsoleController(service, properties);
        }
    }
}
