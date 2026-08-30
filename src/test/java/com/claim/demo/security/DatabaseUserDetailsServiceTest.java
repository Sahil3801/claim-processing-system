package com.claim.demo.security;

import com.claim.demo.domain.UserRole;
import com.claim.demo.entity.User;
import com.claim.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsCurrentAuthorityFromDatabaseRole() {
        User user = new User();
        user.setUsername("officer");
        user.setPasswordHash("encoded");
        user.setRole(UserRole.CLAIMS_OFFICER);
        user.setStatus("active");
        when(userRepository.findByUsername("officer")).thenReturn(user);

        UserDetails details = new DatabaseUserDetailsService(userRepository)
                .loadUserByUsername("officer");

        assertEquals("officer", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CLAIMS_OFFICER")));
    }
}
