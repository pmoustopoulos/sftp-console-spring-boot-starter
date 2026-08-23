package io.github.pmoustopoulos.sftpconsole;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When the console is enabled, raises Spring's multipart upload limit to
 * {@code sftp.console.max-upload-size} (default 10MB) so larger files can be uploaded through the
 * console (Spring Boot's own default is only 1MB). Added at lowest precedence, so an application's
 * own {@code spring.servlet.multipart.*} settings still win, and only applied when
 * {@code sftp.console.enabled=true}.
 */
public class SftpConsoleEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "sftpConsoleDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

        if (!environment.getProperty("sftp.console.enabled", Boolean.class, false)) {
            return;
        }

        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        String maxUploadSize = environment.getProperty("sftp.console.max-upload-size", "10MB");

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.servlet.multipart.max-file-size", maxUploadSize);
        defaults.put("spring.servlet.multipart.max-request-size", maxUploadSize);

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        // After config data (application.yaml) is loaded so sftp.console.* is visible.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
