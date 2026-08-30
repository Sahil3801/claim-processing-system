package com.claim.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Removes the per-request database user lookup only for the isolated claim-cache
 * benchmark. Production authentication remains database-backed so status and role
 * changes take effect without waiting for a token to expire.
 */
@Configuration
@Profile("benchmark")
@ConditionalOnProperty(name = "benchmark.security.static-users", havingValue = "true")
public class BenchmarkSecurityConfiguration {

    @Bean
    @Primary
    UserDetailsService benchmarkUserDetailsService() {
        return username -> switch (username) {
            case "bench-admin" -> User.withUsername(username)
                    .password("{noop}benchmark-only")
                    .roles("ADMIN")
                    .build();
            case "bench-claimant" -> User.withUsername(username)
                    .password("{noop}benchmark-only")
                    .roles("CLAIMANT")
                    .build();
            default -> throw new UsernameNotFoundException(
                    "Unknown static benchmark user: " + username);
        };
    }
}
