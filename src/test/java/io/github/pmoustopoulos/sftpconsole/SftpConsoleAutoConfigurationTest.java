package io.github.pmoustopoulos.sftpconsole;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SftpConsoleAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SftpConsoleAutoConfiguration.class));

    @Test
    void beansPresentWhenEnabled() {
        runner.withPropertyValues("sftp.console.enabled=true", "sftp.console.port=0")
                .run(context -> {
                    assertThat(context).hasSingleBean(SshServer.class);
                    assertThat(context).hasSingleBean(SftpConsoleService.class);
                    assertThat(context).hasSingleBean(SftpConsoleController.class);
                });
    }

    @Test
    void beansAbsentWhenDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(SshServer.class));
    }

    @Test
    void fileUploadedOverSftpIsVisibleToTheService() {

        runner.withPropertyValues(
                        "sftp.console.enabled=true",
                        "sftp.console.port=0",
                        "sftp.console.username=tester",
                        "sftp.console.password=secret")
                .run(context -> {
                    SshServer server = context.getBean(SshServer.class);
                    SftpConsoleService service = context.getBean(SftpConsoleService.class);
                    int port = server.getPort();

                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();

                    try (ClientSession session = client
                            .connect("tester", "localhost", port)
                            .verify(5, TimeUnit.SECONDS).getSession()) {
                        session.addPasswordIdentity("secret");
                        session.auth().verify(5, TimeUnit.SECONDS);

                        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
                             OutputStream out = sftp.write("/over-sftp.txt")) {
                            out.write("from client".getBytes(StandardCharsets.UTF_8));
                        }

                    } finally {
                        client.stop();
                    }

                    // The shared Jimfs must still be open and contain the uploaded file.
                    assertThat(service.list("/")).extracting(e -> e.name())
                            .contains("over-sftp.txt");
                    assertThat(new String(service.download("/over-sftp.txt").data(),
                            StandardCharsets.UTF_8)).isEqualTo("from client");
                });
    }

    @Test
    void fileCanBeReadBackOverSftp() {
        runner.withPropertyValues(
                        "sftp.console.enabled=true",
                        "sftp.console.port=0",
                        "sftp.console.username=tester",
                        "sftp.console.password=secret")
                .run(context -> {
                    SshServer server = context.getBean(SshServer.class);
                    int port = server.getPort();

                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();
                    try (ClientSession session = client
                            .connect("tester", "localhost", port)
                            .verify(5, TimeUnit.SECONDS).getSession()) {
                        session.addPasswordIdentity("secret");
                        session.auth().verify(5, TimeUnit.SECONDS);

                        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                            try (OutputStream out = sftp.write("/read-back.txt")) {
                                out.write("round trip".getBytes(StandardCharsets.UTF_8));
                            }
                            // stat the file (what a real client / MINA's stream reader does)
                            assertThat(sftp.stat("/read-back.txt").getSize()).isEqualTo(10L);
                            // read it back via the stream API (issues a stat internally)
                            try (java.io.InputStream in = sftp.read("/read-back.txt")) {
                                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                                        .isEqualTo("round trip");
                            }
                        }
                    } finally {
                        client.stop();
                    }
                });
    }

    @Test
    void fileStoragePersistsUploadsToDisk(@TempDir Path dataDir) {
        runner.withPropertyValues(
                        "sftp.console.enabled=true",
                        "sftp.console.port=0",
                        "sftp.console.username=tester",
                        "sftp.console.password=secret",
                        "sftp.console.storage=file",
                        "sftp.console.directory=" + dataDir.toString().replace('\\', '/'))
                .run(context -> {
                    SshServer server = context.getBean(SshServer.class);
                    int port = server.getPort();

                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();
                    try (ClientSession session = client
                            .connect("tester", "localhost", port)
                            .verify(5, TimeUnit.SECONDS).getSession()) {
                        session.addPasswordIdentity("secret");
                        session.auth().verify(5, TimeUnit.SECONDS);

                        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
                             OutputStream out = sftp.write("/persisted.txt")) {
                            out.write("on disk".getBytes(StandardCharsets.UTF_8));
                        }
                    } finally {
                        client.stop();
                    }

                    // The upload landed on the real disk under the configured directory.
                    assertThat(Files.readString(dataDir.resolve("persisted.txt"))).isEqualTo("on disk");
                });
    }

    @Test
    void rejectsBadCredentials() {

        runner.withPropertyValues(
                        "sftp.console.enabled=true",
                        "sftp.console.port=0",
                        "sftp.console.username=tester",
                        "sftp.console.password=secret")
                .run(context -> {
                    SshServer server = context.getBean(SshServer.class);
                    int port = server.getPort();

                    SshClient client = SshClient.setUpDefaultClient();
                    client.start();
                    boolean authFailed = false;

                    try (ClientSession session = client
                            .connect("tester", "localhost", port)
                            .verify(5, TimeUnit.SECONDS).getSession()) {
                        session.addPasswordIdentity("wrong");
                        session.auth().verify(5, TimeUnit.SECONDS);

                    } catch (Exception ex) {
                        authFailed = true;

                    } finally {
                        client.stop();
                    }

                    assertThat(authFailed).isTrue();
                });
    }
}
