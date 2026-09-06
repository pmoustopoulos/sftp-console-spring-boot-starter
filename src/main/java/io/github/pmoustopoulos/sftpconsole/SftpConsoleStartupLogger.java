package io.github.pmoustopoulos.sftpconsole;

import org.apache.sshd.server.SshServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.nio.file.Paths;

/**
 * Logs the console URL and the SFTP endpoint (with credentials) once the web server is up,
 * so they're easy to spot in the startup output.
 */
public class SftpConsoleStartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(SftpConsoleStartupLogger.class);

    private final SftpConsoleProperties properties;
    private final Environment environment;
    private final SshServer sshServer;

    public SftpConsoleStartupLogger(
            SftpConsoleProperties properties, Environment environment, SshServer sshServer) {
        this.properties = properties;
        this.environment = environment;
        this.sshServer = sshServer;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String consoleUrl = "http://localhost:" + port + contextPath + properties.getPath();
        String credentials = properties.isAcceptAnyCredentials()
                ? "any username / any password"
                : properties.getUsername() + " / " + properties.getPassword();

        String storage = properties.getStorage() == SftpConsoleProperties.Storage.FILE
                ? "file: " + Paths.get(properties.getDirectory()).toAbsolutePath().normalize()
                : "in-memory (cleared on restart)";

        log.info("");
        log.info("----------------------------------------------------------------");
        log.info("  SFTP console:        {}", consoleUrl);
        log.info("  SFTP endpoint:       {}:{}  ({})", properties.getHost(), sshServer.getPort(), credentials);
        log.info("  Storage:             {}", storage);
        log.info("----------------------------------------------------------------");
        log.info("");
    }
}
