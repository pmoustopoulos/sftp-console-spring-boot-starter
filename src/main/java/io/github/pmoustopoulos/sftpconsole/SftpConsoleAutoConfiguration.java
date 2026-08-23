package io.github.pmoustopoulos.sftpconsole;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.pmoustopoulos.sftpconsole.fs.SharedFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(SftpConsoleProperties.class)
@ConditionalOnProperty(prefix = "sftp.console", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SftpConsoleAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public FileSystem sftpFileSystem() {
        // Working directory "/" so the SFTP subsystem's default dir is the root.
        return Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setWorkingDirectory("/").build());
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public SshServer sftpServer(FileSystem sftpFileSystem, SftpConsoleProperties properties)
            throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(properties.getPort());
        sshd.setHost(properties.getHost());
        // No key file: an in-memory host key is generated per JVM start (dev only).
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator(passwordAuthenticator(properties));
        sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new SharedFileSystemFactory(sftpFileSystem));
        sshd.start();
        return sshd;
    }

    private PasswordAuthenticator passwordAuthenticator(SftpConsoleProperties properties) {
        return (username, password, session) -> {
            if (properties.isAcceptAnyCredentials()) {
                return true;
            }
            return properties.getUsername().equals(username)
                    && properties.getPassword().equals(password);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public SftpConsoleService sftpConsoleService(
            FileSystem sftpFileSystem, SftpConsoleProperties properties) {
        return new SftpConsoleService(sftpFileSystem, properties.getMaxPreviewSize().toBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    public SftpConsoleController sftpConsoleController(
            SftpConsoleService sftpConsoleService, SftpConsoleProperties properties) {
        return new SftpConsoleController(sftpConsoleService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SftpConsoleStartupLogger sftpConsoleStartupLogger(
            SftpConsoleProperties properties, Environment environment, SshServer sftpServer) {
        return new SftpConsoleStartupLogger(properties, environment, sftpServer);
    }
}
