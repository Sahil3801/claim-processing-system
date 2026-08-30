package com.claim.demo.security;

import com.claim.demo.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUsingAuthorityReloadedFromDatabaseUserDetails() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(jwtService.verifyAndGetUsername("signed-token")).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(new User(
                "admin", "encoded", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        new JwtAuthenticationFilter(jwtService, userDetailsService).doFilter(request, response, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        verify(userDetailsService).loadUserByUsername("admin");
        verify(chain).doFilter(request, response);
    }
}
