package com.claim.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkSecurityConfigurationTest {

    private final UserDetailsService userDetailsService =
            new BenchmarkSecurityConfiguration().benchmarkUserDetailsService();

    @Test
    void suppliesOnlyTheTwoExpectedBenchmarkPrincipals() {
        UserDetails admin = userDetailsService.loadUserByUsername("bench-admin");
        UserDetails claimant = userDetailsService.loadUserByUsername("bench-claimant");

        assertThat(admin.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(claimant.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CLAIMANT");
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("synthetic-claimant-1"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
