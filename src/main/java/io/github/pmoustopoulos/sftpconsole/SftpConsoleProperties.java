package io.github.pmoustopoulos.sftpconsole;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "sftp.console")
public class SftpConsoleProperties {

    /** Where the served files live. */
    public enum Storage {
        /** Google Jimfs in the JVM heap — fast, isolated, and cleared on restart (default). */
        MEMORY,
        /** A real directory on disk (see {@link #directory}) — files survive restarts. */
        FILE
    }

    /** Whether the embedded SFTP server and console are active. Off by default. */
    private boolean enabled = false;

    /**
     * Backing store for the served files: {@code memory} (in-heap, cleared on restart) or
     * {@code file} (a directory on disk that persists across restarts — like H2's file mode).
     */
    private Storage storage = Storage.MEMORY;

    /**
     * Directory used when {@link #storage} is {@code file}. Created if missing; it becomes the SFTP
     * root ("/"), sandboxed so clients cannot escape it. Ignored for {@code memory} storage.
     */
    private String directory = "sftp-data";

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

    /**
     * Maximum size of a file that can be uploaded through the console
     */
    private String maxUploadSize = "10MB";

    /**
     * Maximum size of a file whose text content is shown inline in the preview
     */
    private DataSize maxPreviewSize = DataSize.ofMegabytes(1);

    /**
     * How often the console UI auto-refreshes the current folder
     */
    private Duration refreshInterval = Duration.ofSeconds(4);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
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

    public String getMaxUploadSize() {
        return maxUploadSize;
    }

    public void setMaxUploadSize(String maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }

    public DataSize getMaxPreviewSize() {
        return maxPreviewSize;
    }

    public void setMaxPreviewSize(DataSize maxPreviewSize) {
        this.maxPreviewSize = maxPreviewSize;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }
}
