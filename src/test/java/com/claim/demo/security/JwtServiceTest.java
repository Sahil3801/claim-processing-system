package com.claim.demo.security;

import com.auth0.jwt.JWT;
import com.claim.demo.dto.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void signsAndVerifiesConfiguredIssuerAndUserIdentity() {
        JwtService jwtService = new JwtService(
                "test-secret-with-more-than-thirty-two-characters",
                "claims-test",
                60_000);
        UserDTO user = new UserDTO(1L, "admin", "admin@example.com", "ADMIN", "active");

        String token = jwtService.createToken(user);

        assertEquals("admin", jwtService.verifyAndGetUsername(token));
        assertEquals("claims-test", JWT.decode(token).getIssuer());
        assertEquals("ADMIN", JWT.decode(token).getClaim("role").asString());
    }

    @Test
    void rejectsWeakRuntimeSecret() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("too-short", "claims-test", 60_000));
    }
}
