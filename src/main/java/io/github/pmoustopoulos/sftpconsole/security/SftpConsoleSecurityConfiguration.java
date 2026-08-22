package io.github.pmoustopoulos.sftpconsole.security;

import io.github.pmoustopoulos.sftpconsole.SftpConsoleProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Registers a permissive security filter chain scoped to the console's path, so the console
 * works whatever the host app's security setup is. Only active when Spring Security is on the
 * classpath and the console is enabled.
 *
 * <p><b>Development only.</b> If the host app has no {@code SecurityFilterChain} of its own,
 * this becomes the only one and leaves the rest of the app unsecured — so define your own
 * chain before enabling the console.
 */
@AutoConfiguration
@ConditionalOnClass({SecurityFilterChain.class, EnableWebSecurity.class})
@EnableConfigurationProperties(SftpConsoleProperties.class)
@ConditionalOnProperty(prefix = "sftp.console", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SftpConsoleSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain sftpConsoleSecurityFilterChain(
            HttpSecurity http,
            SftpConsoleProperties properties) throws Exception {

        String pattern = properties.getPath() + "/**";

        http.securityMatcher(pattern)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
