package io.github.pmoustopoulos.sftpconsole;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sftp.console")
public class SftpConsoleProperties {

    /** Whether the in-memory SFTP server and console are active. Off by default. */
    private boolean enabled = false;

    /** Port the embedded SFTP server listens on. */
    private int port = 2222;

    /**
     * Network interface the embedded SFTP server binds to. Defaults to localhost so the
     * server is not exposed on all interfaces.
     */
    private String host = "localhost";

    /** Username SFTP clients authenticate with. */
    private String username = "user";

    /** Password SFTP clients authenticate with. */
    private String password = "password";

    /** When true, the server accepts any username/password. */
    private boolean acceptAnyCredentials = false;

    /** Base URL path where the console UI and its API are served. */
    private String path = "/sftp-console";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAcceptAnyCredentials() {
        return acceptAnyCredentials;
    }

    public void setAcceptAnyCredentials(boolean acceptAnyCredentials) {
        this.acceptAnyCredentials = acceptAnyCredentials;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
