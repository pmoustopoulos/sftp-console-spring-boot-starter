package io.github.pmoustopoulos.sftpconsole.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SftpConsoleSecurityConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SftpConsoleSecurityConfiguration.class,
                    SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class));

    @Test
    void registersFilterChainWhenEnabled() {
        runner.withPropertyValues("sftp.console.enabled=true")
                .run(context -> assertThat(context).hasBean("sftpConsoleSecurityFilterChain"));
    }

    @Test
    void backsOffWhenDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean("sftpConsoleSecurityFilterChain"));
    }
}
